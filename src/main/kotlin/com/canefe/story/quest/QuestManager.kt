package com.canefe.story.quest

import com.canefe.story.Story
import com.canefe.story.util.Msg.sendInfo
import com.canefe.story.util.Msg.sendSuccess
import net.citizensnpcs.api.npc.NPC
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.io.File
import java.io.IOException
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class QuestManager private constructor(
	private val plugin: Story,
) {
	private val quests = ConcurrentHashMap<String, Quest>()
	private val playerQuests = ConcurrentHashMap<UUID, MutableMap<String, PlayerQuest>>()

	private var questReference: YamlConfiguration? = null

	// Load the reference data
	private fun loadQuestReference() {
		val referenceFile = File(plugin.dataFolder, "quest_reference.yml")

		// Copy default if doesn't exist
		if (!referenceFile.exists()) {
			plugin.saveResource("quest_reference.yml", false)
		}

		questReference = YamlConfiguration.loadConfiguration(referenceFile)
		plugin.logger.info("Loaded quest reference data")
	}

	// Get valid collectible items
	fun getValidCollectibles(): List<String> = questReference?.getStringList("collectibleItems") ?: emptyList()

	// Get valid killable entities
	fun getValidKillTargets(): List<String> = questReference?.getStringList("killableEntities") ?: emptyList()

	// Get valid locations
	fun getValidLocations(): List<String> = questReference?.getStringList("locations") ?: emptyList()

	fun getValidTalkTargets(npc: NPC): List<String> {
		// First get the location of npc (Get his npcData)
		val npcData = plugin.npcDataManager.getNPCData(npc.name)
		val location = npcData?.storyLocation

		// get all npc names from that StoryLocation
		for (npcName in plugin.npcDataManager.getAllNPCNames()) {
			val npcFile = File(plugin.npcDataManager.npcDirectory, "$npcName.yml")
			val npcConfig = YamlConfiguration.loadConfiguration(npcFile)
			val npcLocation = npcConfig.getString("location") ?: continue

			if (npcLocation == location?.name) {
				return listOf(npcName)
			}
		}

		return listOf(npc.name)
	}

	private val questFolder: File =
		File(plugin.dataFolder, "quests").apply {
			if (!exists()) {
				mkdirs()
			}
		}

	private val playerQuestFolder: File =
		File(plugin.dataFolder, "playerquests").apply {
			if (!exists()) {
				mkdirs()
			}
		}

	init {
		loadAllQuests()
		loadAllPlayerQuests()
	}

	fun loadAllQuests() {
		quests.clear()
		val files = questFolder.listFiles { _, name -> name.endsWith(".yml") } ?: return

		for (file in files) {
			try {
				val questId = file.name.replace(".yml", "")
				val quest = loadQuest(questId)
				if (quest != null) {
					quests[questId] = quest
					plugin.logger.info("Loaded quest: ${quest.title} (ID: $questId)")
				}
			} catch (e: Exception) {
				plugin.logger.warning("Error loading quest from file: ${file.name}")
				e.printStackTrace()
			}
		}
		plugin.logger.info("Loaded ${quests.size} quests")
	}

	fun loadQuest(questId: String): Quest? {
		val questFile = File(questFolder, "$questId.yml")
		if (!questFile.exists()) {
			return null
		}

		val config = YamlConfiguration.loadConfiguration(questFile)

		val title = config.getString("title") ?: return null
		val description = config.getString("description") ?: ""
		val type = QuestType.valueOf(config.getString("type", "MAIN") ?: "MAIN")
		val prerequisites = config.getStringList("prerequisites").toMutableList()
		val nextQuests = config.getStringList("nextQuests").toMutableList()

		val objectives = mutableListOf<QuestObjective>()
		val objectivesSection = config.getConfigurationSection("objectives") ?: return null

		for (key in objectivesSection.getKeys(false)) {
			val objDesc = objectivesSection.getString("$key.description") ?: continue
			val objType = ObjectiveType.valueOf(objectivesSection.getString("$key.type") ?: continue)
			val objTarget = objectivesSection.getString("$key.target") ?: ""
			val objRequired = objectivesSection.getInt("$key.required", 1)

			objectives.add(QuestObjective(objDesc, objType, objTarget, 0, objRequired))
		}

		val rewards = mutableListOf<QuestReward>()
		val rewardsSection = config.getConfigurationSection("rewards")

		if (rewardsSection != null) {
			for (key in rewardsSection.getKeys(false)) {
				val rewardType = RewardType.valueOf(rewardsSection.getString("$key.type") ?: continue)
				val amount = rewardsSection.getInt("$key.amount", 0)

				// TODO: Handle item rewards properly once you have a serialization system
				rewards.add(QuestReward(rewardType, amount))
			}
		}

		return Quest(questId, title, description, type, objectives, rewards, prerequisites, nextQuests)
	}

	fun loadAllPlayerQuests() {
		val playerFiles = playerQuestFolder.listFiles { _, name -> name.endsWith(".yml") } ?: return

		for (file in playerFiles) {
			try {
				val playerId = UUID.fromString(file.nameWithoutExtension)
				loadPlayerQuests(playerId)
			} catch (ex: Exception) {
				plugin.logger.warning("Error loading player quests from ${file.name}: ${ex.message}")
			}
		}

		plugin.logger.info("Loaded quests for ${playerQuests.size} players")
	}

	fun saveQuest(quest: Quest) {
		val questFile = File(questFolder, "${quest.id}.yml")
		val config = YamlConfiguration()

		config.set("title", quest.title)
		config.set("description", quest.description)
		config.set("type", quest.type.name)
		config.set("prerequisites", quest.prerequisites)
		config.set("nextQuests", quest.nextQuests)

		// Save objectives
		for (i in quest.objectives.indices) {
			val obj = quest.objectives[i]
			val path = "objectives.$i"
			config.set("$path.description", obj.description)
			config.set("$path.type", obj.type.name)
			config.set("$path.target", obj.target)
			config.set("$path.required", obj.required)
		}

		// Save rewards
		for (i in quest.rewards.indices) {
			val reward = quest.rewards[i]
			val path = "rewards.$i"
			config.set("$path.type", reward.type.name)
			config.set("$path.amount", reward.amount)
			// TODO: Handle item rewards serialization
		}

		try {
			config.save(questFile)
		} catch (e: IOException) {
			plugin.logger.severe("Could not save quest: ${quest.id}")
			e.printStackTrace()
		}
	}

	fun getQuest(questId: String): Quest? = quests[questId]

	fun getAllQuests(): Collection<Quest> = quests.values

	// A function to return all quests with associated players
	fun getAllQuestsWithPlayers(): Map<String, List<OfflinePlayer>> {
		val questPlayerMap = mutableMapOf<String, MutableList<OfflinePlayer>>()

		for ((playerId, playerQuestMap) in playerQuests) {
			for (questId in playerQuestMap.keys) {
				val player = plugin.server.getOfflinePlayer(playerId)
				questPlayerMap.getOrPut(questId) { mutableListOf() }.add(player)
			}
		}

		return questPlayerMap
	}

	// Player quest management
	fun assignQuestToPlayer(
		player: Player,
		questId: String,
	): Boolean {
		val quest = getQuest(questId) ?: return false

		// Check prerequisites
		for (prerequisiteId in quest.prerequisites) {
			val prereqStatus = getPlayerQuestStatus(player, prerequisiteId)
			if (prereqStatus != QuestStatus.COMPLETED) {
				return false
			}
		}

		val playerQuest = PlayerQuest(questId, player.uniqueId, QuestStatus.IN_PROGRESS)

		val playerQuestMap = playerQuests.getOrPut(player.uniqueId) { mutableMapOf() }
		playerQuestMap[questId] = playerQuest

		savePlayerQuest(player.uniqueId, playerQuest)
		plugin.playerManager.setPlayerQuest(player, quest.title, quest.objectives[0].description)
		return true
	}

	fun getPlayerQuests(playerId: UUID): Map<String, PlayerQuest> {
		loadPlayerQuests(playerId)
		return playerQuests.getOrDefault(playerId, mutableMapOf())
	}

	fun getPlayerQuestStatus(
		player: OfflinePlayer,
		questId: String,
	): QuestStatus {
		val playerQuestMap = getPlayerQuests(player.uniqueId)
		return playerQuestMap[questId]?.status ?: QuestStatus.NOT_STARTED
	}

	/**
	 * Registers a dynamically generated quest into the quests collection and saves it to disk
	 */
	fun registerQuest(
		quest: Quest,
		npc: NPC,
	) {
		val turnInObjective =
			QuestObjective(
				description = "Return to ${npc.name}",
				type = ObjectiveType.TALK,
				target = npc.name,
				required = 1,
			)

		val newQuest =
			Quest(
				id = quest.id,
				title = quest.title,
				description = quest.description,
				type = quest.type,
				objectives = quest.objectives + turnInObjective,
				rewards = quest.rewards,
				prerequisites = quest.prerequisites,
				nextQuests = quest.nextQuests,
			)

		quests[newQuest.id] = newQuest
		saveQuest(newQuest)
		plugin.logger.info("Registered new quest: ${newQuest.title} (ID: ${newQuest.id})")
	}

	fun updateObjectiveProgress(
		player: Player,
		questId: String,
		objectiveType: ObjectiveType,
		target: String,
		progress: Int = 1,
	) {
		val playerQuestMap = getPlayerQuests(player.uniqueId)
		val playerQuest = playerQuestMap[questId] ?: return

		val quest = getQuest(questId) ?: return

		if (playerQuest.status != QuestStatus.IN_PROGRESS) return

		var allCompleted = true
		var currentQuest: Quest? = null
		var objectiveFinished = false
		for (i in quest.objectives.indices) {
			val objective = quest.objectives[i]

			// Check if this objective matches the criteria
			// lowercase string

			if (objective.type == objectiveType &&
				(objective.target.isEmpty() || objective.target.lowercase() == target.lowercase())
			) {
				val currentProgress = playerQuest.objectiveProgress.getOrDefault(i, 0)

				// If the objective is already completed, skip it
				if (currentProgress >= objective.required) {
					continue
				}

				// Don't exceed the required amount
				val newProgress = minOf(currentProgress + progress, objective.required)
				playerQuest.objectiveProgress[i] = newProgress

				currentQuest = quest

				player.sendMessage("§7Quest progress: §e${objective.description} §7(§a$newProgress§7/§6${objective.required}§7)")

				if (newProgress >= objective.required) {
					objectiveFinished = true
					player.sendMessage("§aObjective completed: §e${objective.description}")
				}
			}

			// Check if objective is completed
			val objectiveProgress = playerQuest.objectiveProgress.getOrDefault(i, 0)
			if (objectiveProgress < objective.required) {
				allCompleted = false
			}
		}

		// check if the currentQuest is not null
		currentQuest
			?: return

		// If all objectives are completed, complete the quest
		if (allCompleted) {
			completeQuest(player, questId)
		} else {
			savePlayerQuest(player.uniqueId, playerQuest)
			// Print next objective
			val nextObjectiveIndex =
				quest.objectives.indexOfFirst {
					it.required >
						playerQuest.objectiveProgress.getOrDefault(quest.objectives.indexOf(it), 0)
				}

			val nextObjective = nextObjectiveIndex?.let { quest.objectives[it] }
			if (nextObjective != null && objectiveFinished) {
				player.sendSuccess(
					"<gray>Next objective: <yellow>${nextObjective.description} <gray>(<green>${playerQuest.objectiveProgress.getOrDefault(
						nextObjectiveIndex,
						0,
					)}<gray>/<gold>${nextObjective.required}<gray>)",
				)
			}
		}
	}

	fun updatePlaceholders(
		player: Player,
		quest: Quest,
	) {
		val currentObjectiveMap = quest.id.let { plugin.questManager.getCurrentObjective(player, it) }
		val currentObjectiveIndex = currentObjectiveMap?.keys?.firstOrNull()
		val currentObjective = currentObjectiveIndex?.let { currentObjectiveMap[it] }
		if (currentObjective != null) {
			plugin.playerManager.setPlayerQuest(player, quest.title, currentObjective.description)
		} else {
			plugin.playerManager.clearPlayerQuest(player)
		}
	}

	fun completeQuest(
		player: Player,
		questId: String,
	) {
		val mm = plugin.miniMessage
		val playerQuestMap = getPlayerQuests(player.uniqueId)
		val playerQuest = playerQuestMap[questId] ?: return

		val quest = getQuest(questId) ?: return

		playerQuest.status = QuestStatus.COMPLETED
		playerQuest.completionDate = System.currentTimeMillis()

		// Give rewards
		giveRewards(player, quest)

		player.sendSuccess("Quest completed: <yellow>${quest.title}")
		val audience = Audience.audience(player)
		val title =
			Title.title(
				mm.deserialize("<gold><b>Quest Completed</b>"),
				mm.deserialize("<gray>${quest.title}"),
				Title.Times.times(Duration.ofSeconds(1), Duration.ofSeconds(3), Duration.ofSeconds(1)),
			)
		audience.showTitle(title)
		plugin.logger.info("Player ${player.name} completed quest: ${quest.title}")
		plugin.playerManager.clearPlayerQuest(player)

		// Make next quests available
		for (nextQuestId in quest.nextQuests) {
			val nextQuest = getQuest(nextQuestId)
			if (nextQuest != null) {
				player.sendInfo("New quest available: <yellow>${nextQuest.title}")
			}
		}

		savePlayerQuest(player.uniqueId, playerQuest)
	}

	private fun giveRewards(
		player: Player,
		quest: Quest,
	) {
		for (reward in quest.rewards) {
			when (reward.type) {
				RewardType.ITEM -> {
					// Handle item rewards when implemented
					// player.inventory.addItem(reward.getItemStack())
				}
				RewardType.EXPERIENCE -> {
					player.giveExp(reward.amount)
					player.sendMessage("§7Received §a${reward.amount} §7experience")
				}
				RewardType.REPUTATION -> {
					// Integrate with your reputation system when implemented
					player.sendMessage("§7Received §a${reward.amount} §7reputation")
				}
				RewardType.MONEY -> {
					// Integrate with economy plugin if needed
					player.sendMessage("§7Received §a${reward.amount} §7coins")
				}
			}
		}
	}

	private fun loadPlayerQuests(playerId: UUID) {
		if (playerQuests.containsKey(playerId)) return

		val playerQuestsMap = mutableMapOf<String, PlayerQuest>()
		val playerFile = File(playerQuestFolder, "$playerId.yml")

		if (playerFile.exists()) {
			val config = YamlConfiguration.loadConfiguration(playerFile)

			for (questId in config.getKeys(false)) {
				val statusStr = config.getString("$questId.status") ?: continue
				val status = QuestStatus.valueOf(statusStr)
				val completionDate = config.getLong("$questId.completionDate", 0)

				val playerQuest = PlayerQuest(questId, playerId, status, completionDate)

				// Load objective progress
				config.getConfigurationSection("$questId.progress")?.let { progressSection ->
					for (key in progressSection.getKeys(false)) {
						val index = key.toIntOrNull() ?: continue
						val progress = progressSection.getInt(key)
						playerQuest.objectiveProgress[index] = progress
					}
				}

				playerQuestsMap[questId] = playerQuest
			}
		}

		playerQuests[playerId] = playerQuestsMap

		val currentQuest = playerQuestsMap.values.firstOrNull()
		if (currentQuest != null) {
			val quest = getQuest(currentQuest.questId)
			if (quest != null) {
				Bukkit.getPlayer(playerId)?.let { updatePlaceholders(it, quest) }
			}
		}
	}

	/**
	 * Returns the current objective that a player should be working on for a quest
	 * @param player The player
	 * @param questId The quest ID
	 * @return The current objective, or null if the quest is completed or not found
	 */
	fun getCurrentObjective(
		player: OfflinePlayer,
		questId: String,
	): Map<Int, QuestObjective>? {
		// Get player quest data
		val playerQuestMap = getPlayerQuests(player.uniqueId)
		val playerQuest = playerQuestMap[questId] ?: return null

		// If quest is completed, there's no current objective
		if (playerQuest.status != QuestStatus.IN_PROGRESS) {
			return null
		}

		// Get the quest data
		val quest = getQuest(questId) ?: return null

		// Look through objectives to find the first incomplete one
		for (i in quest.objectives.indices) {
			val objective = quest.objectives[i]
			val currentProgress = playerQuest.objectiveProgress.getOrDefault(i, 0)

			// If this objective isn't complete, it's the current one
			if (currentProgress < objective.required) {
				return mapOf(i to objective)
			}
		}

		// If no incomplete objectives were found but the quest is in progress,
		// the player should complete the quest
		return null
	}

	private fun savePlayerQuest(
		playerId: UUID,
		playerQuest: PlayerQuest,
	) {
		val playerFile = File(playerQuestFolder, "$playerId.yml")
		val config =
			if (playerFile.exists()) {
				YamlConfiguration.loadConfiguration(playerFile)
			} else {
				YamlConfiguration()
			}

		val questId = playerQuest.questId
		config.set("$questId.status", playerQuest.status.name)
		config.set("$questId.completionDate", playerQuest.completionDate)

		// Save objective progress
		for ((index, progress) in playerQuest.objectiveProgress) {
			config.set("$questId.progress.$index", progress)
		}

		try {
			config.save(playerFile)

			// Update the in-memory map instead of clearing and reloading
			playerQuests.getOrPut(playerId) { mutableMapOf() }[questId] = playerQuest
		} catch (e: IOException) {
			plugin.logger.severe("Could not save player quest for player $playerId, quest $questId")
			e.printStackTrace()
		}
	}

	fun clearPlayerQuests(playerId: UUID) {
		playerQuests.remove(playerId)
		val playerFile = File(playerQuestFolder, "$playerId.yml")
		if (playerFile.exists()) {
			playerFile.delete()
		}
	}

	fun loadConfig() {
		// reset cache
		quests.clear()
		playerQuests.clear()
		loadQuestReference()
		loadAllQuests()
		loadAllPlayerQuests()
	}

	companion object {
		private var instance: QuestManager? = null

		@JvmStatic
		fun getInstance(plugin: Story): QuestManager = instance ?: QuestManager(plugin).also { instance = it }
	}
}
