package com.canefe.story.command.base

import com.canefe.story.NPCUtils
import com.canefe.story.Story
import com.canefe.story.command.conversation.ConvCommand
import com.canefe.story.command.story.StoryCommand
import com.canefe.story.util.Msg.sendError
import com.canefe.story.util.Msg.sendSuccess
import dev.jorel.commandapi.CommandAPI
import dev.jorel.commandapi.CommandAPIBukkitConfig
import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.executors.PlayerCommandExecutor
import eu.decentsoftware.holograms.api.DHAPI
import net.citizensnpcs.api.CitizensAPI
import net.citizensnpcs.npc.CitizensNPC
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.command.CommandExecutor
import org.bukkit.entity.Player

/**
 * Centralized command manager that registers and manages all plugin commands.
 */
class CommandManager(private val plugin: Story) {

    private val commandExecutors = mutableMapOf<String, CommandExecutor>()
    /**
     * Called during plugin load to initialize CommandAPI
     */
    fun onLoad() {
        CommandAPI.onLoad(CommandAPIBukkitConfig(plugin).silentLogs(true))
    }

    /**
     * Called during plugin enable to register all commands
     */
    fun registerCommands() {
        // Register CommandAPI commands
        registerCommandAPICommands()
    }

    /**
     * Called during plugin disable to clean up commands
     */
    fun onDisable() {
        CommandAPI.onDisable()
    }

    private fun registerCommandAPICommands() {
        // Register CommandAPI
        CommandAPI.onEnable()

        // Register structured commands
        ConvCommand(plugin).register()
        StoryCommand(plugin).register()

        // Register simpler commands
        registerSimpleCommands()
    }

    private fun registerSimpleCommands() {
        // Register simple commands using CommandAPI
        CommandAPICommand("togglechat")
            .withPermission("storymaker.chat.toggle")
            .withOptionalArguments(dev.jorel.commandapi.arguments.PlayerArgument("target"))
            .executesPlayer(PlayerCommandExecutor { player, args ->
                val target = args.getOptional("target").orElse(player) as? Player

                plugin.toggleChatForPlayer(player, target)
            })
            .executes(dev.jorel.commandapi.executors.CommandExecutor { _, args ->
                val target = args.get("target") as? Player

                plugin.toggleChatForPlayer(null, target)
            })
            .register()

        CommandAPICommand("maketalk")
            .withPermission("storymaker.chat.toggle")
            .withArguments(dev.jorel.commandapi.arguments.GreedyStringArgument("npc"))
            .executes(dev.jorel.commandapi.executors.CommandExecutor { sender, args ->
                val npc = args.get("npc") as String

                // Fetch NPC conversation
                val conversation = plugin.conversationManager.getActiveNPCConversation(npc);
                if (conversation == null) {
                    val errorMessage = plugin.miniMessage.deserialize("<red>No active conversation found for NPC '$npc'.")
                    sender.sendMessage(errorMessage)
                    throw CommandAPI.failWithString("No active conversation found for NPC '$npc'.");
                }

                val successMessage = plugin.miniMessage.deserialize("<green>NPC '$npc' is now talking.</green>")
                sender.sendMessage(successMessage)
                // Generate NPC responses
                plugin.conversationManager.generateGroupNPCResponses(conversation, null, npc);
            })
            .register()

        // togglegpt
        CommandAPICommand("togglegpt")
            .withPermission("storymaker.chat.toggle")
            .executes(dev.jorel.commandapi.executors.CommandExecutor { sender, _ ->
                plugin.isChatEnabled = !plugin.isChatEnabled
                if (plugin.isChatEnabled) {
                    sender.sendSuccess("Chat with NPCs enabled.")
                } else {
                    sender.sendError("Chat with NPCs disabled.")
                }
            })
            .register()

        // g command
        CommandAPICommand("g")
            .withPermission("storymaker.chat.toggle")
            .withArguments(dev.jorel.commandapi.arguments.GreedyStringArgument("message"))
            .executesPlayer(PlayerCommandExecutor { player, args ->
                val message = args.get("message") as String
                // Get Current NPC
                val currentNPC = plugin.playerCurrentNPC[player.uniqueId]
                if (currentNPC == null) {
                    player.sendError("Please select an NPC first.")
                    throw CommandAPI.failWithString("You are not in a conversation with any NPC.")
                }
                // Check if NPC exists
                val npc = CitizensAPI.getNPCRegistry().getByUniqueId(currentNPC);
                if (npc == null) {
                    player.sendError("NPC not found.")
                    throw CommandAPI.failWithString("NPC not found.")
                }

                val npcName = npc.name;
                plugin.conversationManager.addNPCMessage(npcName, message)
                plugin.conversationManager.showThinkingHolo(npc as CitizensNPC)
                val npcContext = plugin.npcUtils.getOrCreateContextForNPC(npcName)

                
                Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                    // remove hologram after a delay
                    val taskIdToRemove = plugin.conversationManager.hologramTasks[npcName]
                    Bukkit.getScheduler().cancelTask(taskIdToRemove ?: -1)
                    plugin.conversationManager.removeHologramTask(npcName)
                    DHAPI.removeHologram(plugin.getNPCUUID(npcName).toString())

                    val color = plugin.randomColor(npcName)

                    plugin.broadcastNPCMessage(message, npcName, false, npc, null, null, npcContext.avatar, color)
                }, 60L) // 20 ticks = 1 second

                
            })
            .register()

            //setcurnpc
        CommandAPICommand("setcurnpc")
            .withPermission("storymaker.chat.toggle")
            .withArguments(dev.jorel.commandapi.arguments.TextArgument("npc"))
            .withOptionalArguments(dev.jorel.commandapi.arguments.IntegerArgument("npc_id"))
            .executesPlayer(PlayerCommandExecutor { player, args ->
                val npc = args.get("npc") as String
                // integer
                val npcId = args.getOptional("npc_id").orElse(null) as? Int
                // There might be multiple NPCs with the same name, if so, check player radius. If not, ask player to select one.
                for (npc in CitizensAPI.getNPCRegistry()) {
                    if (npc.name.equals(args["npc"])) {
                        if (!npc.isSpawned) {
                            continue
                        }
                        val npcLocation = npc.entity.location
                        val playerLocation = player.location
                        if (playerLocation.distance(npcLocation) <= 15) {
                            plugin.playerCurrentNPC[player.uniqueId] = npc.uniqueId
                            player.sendSuccess("Current NPC set to $npc")
                            return@PlayerCommandExecutor
                        } else {
                            // print all possible npcs with the same name their ids next to it (make them clickable)
                            val npcList = CitizensAPI.getNPCRegistry().filter { it.name.equals(args["npc"]) }
                            for (npc in npcList) {
                                val clickableNpc = CommandComponentUtils.createButton(plugin.miniMessage, "Select ${npc.name} (${npc.id})", "green",
                                    "run_command", "/setcurnpc ${npc.name} ${npc.id}",
                                    "Set current NPC to ${npc.name} (${npc.id})")

                                player.sendMessage(clickableNpc)
                            }
                        }
                    }
                }
            })
            .register()

    }
}