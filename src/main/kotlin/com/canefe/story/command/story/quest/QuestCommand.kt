package com.canefe.story.command.story.quest

import com.canefe.story.Story
import com.canefe.story.quest.ObjectiveType
import com.canefe.story.quest.QuestStatus
import com.canefe.story.util.Msg.sendError
import com.canefe.story.util.Msg.sendRaw
import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.OutlinePane
import com.github.stefvanschie.inventoryframework.pane.PaginatedPane
import com.github.stefvanschie.inventoryframework.pane.Pane
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.arguments.GreedyStringArgument
import dev.jorel.commandapi.arguments.OfflinePlayerArgument
import dev.jorel.commandapi.arguments.PlayerArgument
import dev.jorel.commandapi.arguments.StringArgument
import dev.jorel.commandapi.executors.CommandExecutor
import dev.jorel.commandapi.executors.PlayerCommandExecutor
import org.bukkit.Material
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import java.util.*

class QuestCommand(
	private val plugin: Story,
) {
	private val commandUtils = QuestCommandUtils()

	fun getCommand(): CommandAPICommand =
		CommandAPICommand("quest")
			.withAliases("quests", "q")
			.withPermission("story.quest")
			.executesPlayer(
				PlayerCommandExecutor { player, args ->

					val target =
						if (args[0] is OfflinePlayer) {
							args[0] as OfflinePlayer
						} else {
							null
						}

					if (plugin.questManager.getPlayerQuests(player.uniqueId).isEmpty()) {
						player.sendError("You have no quests.")
						return@PlayerCommandExecutor
					}

					getQuestChestGUI(player, target)
				},
			).withSubcommand(getListCommand())
			.withSubcommand(getInfoCommand())
			.withSubcommands(getBookCommand())
			.withSubcommand(getAssignCommand())
			.withSubcommand(getCompleteCommand())
			.withSubcommand(getProgressCommand())
			.withSubcommand(getReloadCommand())
			.withSubcommand(getViewCommand())

	private fun getViewCommand(): CommandAPICommand =
		CommandAPICommand("view")
			.withPermission("story.quest.view")
			.withArguments(OfflinePlayerArgument("player").withPermission("story.quest.admin"))
			.executesPlayer(
				PlayerCommandExecutor { player, args ->
					val target =
						if (args[0] is OfflinePlayer) {
							args[0] as OfflinePlayer
						} else {
							null
						}

					getQuestChestGUI(player, target)
				},
			)

	private fun getQuestChestGUI(
		player: Player,
		target: OfflinePlayer? = null,
	) {
		// Determine which player's quests to display
		val targetPlayer = target ?: player
		val isAdmin = target != null && player.hasPermission("story.quest.admin")
		val mm = plugin.miniMessage

		// Create GUI with appropriate title
		val guiTitle = if (isAdmin) "${targetPlayer.name}'s Quest Book" else "Quest Book"
		val gui = ChestGui(3, guiTitle)
		gui.setOnGlobalClick { event -> event.isCancelled = true }

		// Retrieve the target player's quests
		val quests = plugin.questManager.getPlayerQuests(targetPlayer.uniqueId)
		// only filter quests that are not completed quests is a Map
		val filteredQuests = quests.filter { it.value.status != QuestStatus.COMPLETED }

		if (quests.isEmpty()) {
			player.sendError("${if (isAdmin) "${targetPlayer.name} has" else "You have"} no quests.")
			return
		}

		val pages = PaginatedPane(0, 0, 9, 5)
		pages.populateWithGuiItems(
			filteredQuests.values.map { playerQuest ->
				val quest = plugin.questManager.getQuest(playerQuest.questId)
				val status = plugin.questManager.getPlayerQuestStatus(targetPlayer, playerQuest.questId)
				val statusColor = getStatusColor(status)

				val questItem = ItemStack(Material.PAPER)
				val questName = "<white>${quest?.title}"
				// add name and lore to the item
				questItem.itemMeta =
					questItem.itemMeta?.apply {
						displayName(mm.deserialize(questName))
						lore(
							listOf(
								mm.deserialize("<gray>${quest?.description}"),
								mm.deserialize("<gray>Status: $statusColor$status"),
								mm.deserialize(
									if (isAdmin) "<gray>Viewing ${targetPlayer.name}'s quest" else "<gray>Click to set this quest as active",
								),
							),
						)
					}
				GuiItem(
					questItem,
				) { event: InventoryClickEvent ->
					event.isCancelled = true
					player.sendRaw(
						"<yellow>===== <gold>${if (isAdmin) "${targetPlayer.name}'s " else ""}Quest Info</gold> =====</yellow>",
					)
					player.sendRaw("<gray>ID:</gray> <white>${quest?.id}</white>")
					player.sendRaw("<gray>Title:</gray> <white>${quest?.title}</white>")
					player.sendRaw("<gray>Description:</gray> <white>${quest?.description}</white>")
					player.sendRaw("<gray>Status:</gray> <white>${statusColor}$status</white>")
					player.sendRaw("<gray>Objectives:</gray>")

					val currentObjectiveMap = quest?.id?.let { plugin.questManager.getCurrentObjective(targetPlayer, it) }
					val currentObjectiveIndex = currentObjectiveMap?.keys?.firstOrNull()
					val currentObjective = currentObjectiveIndex?.let { currentObjectiveMap[it] }

					quest?.objectives?.forEachIndexed { index, obj ->
						val done =
							when {
								// Completed objectives (index less than current objective index)
								currentObjectiveIndex != null && index < currentObjectiveIndex -> "<st><green>✔"
								// Current objective
								currentObjectiveIndex != null && index == currentObjectiveIndex -> "<yellow>⟳"
								// Not yet reached objectives
								else -> "<red>✘"
							}

						player.sendRaw("$done - ${obj.description} (${obj.required} ${obj.type} <gold>${obj.target}</gold>)")
					}
					val title = quest?.title ?: "Unknown"

					if (currentObjective != null) {
						player.sendRaw("<gray>Current Objective: <white>${currentObjective.description}</white>")
						// targetPlayer might be either OnlinePlayer or OfflinePlayer
						// plugin.playerManager.setPlayerQuest(targetPlayer, quest.id, currentObjective.description)
						if (isAdmin) {
							targetPlayer.player?.let { targetPlayer ->
								plugin.playerManager.setPlayerQuest(targetPlayer, quest.title, currentObjective.description)
							}
						} else {
							plugin.playerManager.setPlayerQuest(player, quest.title, currentObjective.description)
						}
					} else {
						player.sendRaw("<gray>No current objective.")
					}
				}
			},
		)
		pages.setOnClick { event: InventoryClickEvent? -> }

		gui.addPane(pages)

		val background = OutlinePane(0, 5, 9, 1)
		background.addItem(GuiItem(ItemStack(Material.BLACK_STAINED_GLASS_PANE)))
		background.setRepeat(true)
		background.priority = Pane.Priority.LOWEST

		gui.addPane(background)

		val navigation = StaticPane(0, 5, 9, 1)
		navigation.addItem(
			GuiItem(
				ItemStack(Material.RED_WOOL),
			) { event: InventoryClickEvent? ->
				if (pages.page > 0) {
					pages.page = pages.page - 1
					gui.update()
				}
			},
			0,
			0,
		)

		navigation.addItem(
			GuiItem(
				ItemStack(Material.GREEN_WOOL),
			) { event: InventoryClickEvent? ->
				if (pages.page < pages.pages - 1) {
					pages.page = pages.page + 1
					gui.update()
				}
			},
			8,
			0,
		)

		navigation.addItem(
			GuiItem(
				ItemStack(Material.BARRIER),
			) { event: InventoryClickEvent ->
				event.whoClicked.closeInventory()
			},
			4,
			0,
		)

		gui.addPane(navigation)
		gui.show(player)
	}

	private fun getBookCommand(): CommandAPICommand = QuestBookCommand(commandUtils).getCommand()

	private fun getListCommand(): CommandAPICommand {
		return CommandAPICommand("list")
			.withPermission("story.quest.list")
			.withOptionalArguments(PlayerArgument("player"))
			.executesPlayer(
				PlayerCommandExecutor { player, args ->
					val targetPlayer =
						if (args[0] is Player) {
							args[0] as Player
						} else {
							player
						}
					val questsWithPlayers = plugin.questManager.getAllQuestsWithPlayers()
					if (questsWithPlayers.isEmpty()) {
						player.sendError("No quests found.")
						return@PlayerCommandExecutor
					}

					player.sendRaw("<yellow>===== <gold>Quests</gold> =====</yellow>")
					questsWithPlayers.forEach { (questId, players) ->
						val quest = plugin.questManager.getQuest(questId) ?: return@forEach

						// Get status of this quest for current viewing player
						val status = plugin.questManager.getPlayerQuestStatus(targetPlayer, questId)
						val statusColor = getStatusColor(status)

						// Create header for the quest
						player.sendRaw("$statusColor${quest.id} <gray>- <white>${quest.title}")

						// Create button with the quest title instead of ID
						commandUtils
							.createButton(
								"<white>${quest.title}",
								"green",
								"run_command",
								"/story quest info ${quest.id}",
								"Click to view quest details",
							).let { button ->
								player.sendRaw(plugin.miniMessage.serialize(button))
							}

						// Show assigned players (max 5 to avoid spam)
						if (players.isNotEmpty()) {
							val displayPlayers = players.take(5)
							val playersText = displayPlayers.joinToString(", ") { it.name ?: "Unknown" }
							val extraCount = if (players.size > 5) " <gray>and ${players.size - 5} more..." else ""

							player.sendRaw("<gray>  Assigned to: <white>$playersText$extraCount")
						} else {
							player.sendRaw("<gray>  Not assigned to any players")
						}
					}
				},
			)
	}

	private fun getInfoCommand(): CommandAPICommand {
		return CommandAPICommand("info")
			.withPermission("story.quest.info")
			.withArguments(
				StringArgument("quest_id").replaceSuggestions { info, builder ->
					val quests = plugin.questManager.getAllQuests()

					val suggestions =
						quests
							.map { it.id }
							.distinct()

					suggestions.forEach {
						builder.suggest(it)
					}

					builder.buildFuture()
				},
			).executesPlayer(
				PlayerCommandExecutor { player, args ->
					val questId = args[0] as String
					val quest = plugin.questManager.getQuest(questId)

					if (quest == null) {
						player.sendError("Quest with ID $questId not found.")
						return@PlayerCommandExecutor
					}

					player.sendMessage("e===== 6Quest: ${quest.title} e=====")
					player.sendMessage("7ID: f${quest.id}")
					player.sendMessage("7Type: f${quest.type}")
					player.sendMessage("7Description: f${quest.description}")

					player.sendMessage("7Objectives:")
					quest.objectives.forEach { obj ->
						player.sendMessage("8- f${obj.description} (${obj.required} ${obj.type})")
					}

					player.sendMessage("7Rewards:")
					quest.rewards.forEach { reward ->
						player.sendMessage("8- f${reward.amount} ${reward.type}")
					}

					val status = plugin.questManager.getPlayerQuestStatus(player, quest.id)
					player.sendMessage("7Status: ${getStatusColor(status)}$status")
				},
			)
	}

	private fun getAssignCommand(): CommandAPICommand =
		CommandAPICommand("assign")
			.withPermission("story.quest.assign")
			.withArguments(PlayerArgument("player"))
			.withArguments(
				StringArgument("quest_id").replaceSuggestions { info, builder ->
					val quests = plugin.questManager.getAllQuests()

					val suggestions =
						quests
							.map { it.id }
							.distinct()

					suggestions.forEach {
						builder.suggest(it)
					}

					builder.buildFuture()
				},
			).executes(
				CommandExecutor { sender, args ->
					val player = args[0] as Player
					val questId = args[1] as String

					val success = plugin.questManager.assignQuestToPlayer(player, questId)
					if (success) {
						val quest = plugin.questManager.getQuest(questId)
						sender.sendMessage("aSuccessfully assigned quest ${quest?.title ?: questId} to ${player.name}")
						player.sendMessage("aNew quest: e${quest?.title ?: questId}")
					} else {
						sender.sendMessage("cFailed to assign quest $questId to ${player.name}")
					}
				},
			)

	private fun getCompleteCommand(): CommandAPICommand =
		CommandAPICommand("complete")
			.withPermission("story.quest.complete")
			.withArguments(PlayerArgument("player"))
			.withArguments(
				StringArgument("quest_id").replaceSuggestions { info, builder ->
					val quests = plugin.questManager.getAllQuests()

					val suggestions =
						quests
							.map { it.id }
							.distinct()

					suggestions.forEach {
						builder.suggest(it)
					}

					builder.buildFuture()
				},
			).executes(
				CommandExecutor { sender, args ->
					val player = args[0] as Player
					val questId = args[1] as String

					plugin.questManager.completeQuest(player, questId)
					sender.sendMessage("aQuest $questId completed for ${player.name}")
				},
			)

	private fun getProgressCommand(): CommandAPICommand =
		CommandAPICommand("progress")
			.withPermission("story.quest.progress")
			.withArguments(PlayerArgument("player"))
			.withArguments(
				StringArgument("quest_id").replaceSuggestions { info, builder ->
					val quests = plugin.questManager.getAllQuests()

					val suggestions =
						quests
							.map { it.id }
							.distinct()

					suggestions.forEach {
						builder.suggest(it)
					}

					builder.buildFuture()
				},
			).withArguments(
				StringArgument("objective_type").replaceSuggestions { info, builder ->
					val objectives = ObjectiveType.values().map { it.name }
					objectives.forEach {
						builder.suggest(it)
					}
					builder.buildFuture()
				},
			).withArguments(GreedyStringArgument("target"))
			.executes(
				CommandExecutor { sender, args ->
					val player = args[0] as Player
					val questId = args[1] as String
					val objectiveType = ObjectiveType.valueOf(args[2] as String)
					val target = args[3] as String

					plugin.questManager.updateObjectiveProgress(player, questId, objectiveType, target)
					sender.sendMessage("aUpdated progress for quest $questId for ${player.name}")
				},
			)

	private fun getReloadCommand(): CommandAPICommand =
		CommandAPICommand("reload")
			.withPermission("story.quest.reload")
			.executes(
				CommandExecutor { sender, _ ->
					plugin.questManager.loadAllQuests()
					sender.sendMessage("aQuests reloaded")
				},
			)

	private fun getStatusColor(status: QuestStatus): String =
		when (status) {
			QuestStatus.NOT_STARTED -> "<gray>"
			QuestStatus.IN_PROGRESS -> "<yellow>"
			QuestStatus.COMPLETED -> "<green>"
			QuestStatus.FAILED -> "<red>"
		}
}
