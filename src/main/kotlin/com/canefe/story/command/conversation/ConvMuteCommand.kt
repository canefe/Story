package com.canefe.story.command.conversation

import com.canefe.story.util.Msg.sendError
import com.canefe.story.util.Msg.sendInfo
import com.canefe.story.util.Msg.sendSuccess
import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.arguments.ArgumentSuggestions
import dev.jorel.commandapi.arguments.GreedyStringArgument
import dev.jorel.commandapi.arguments.IntegerArgument
import dev.jorel.commandapi.executors.CommandExecutor

class ConvMuteCommand(
    private val commandUtils: ConvCommandUtils
) {
    fun getCommand(): CommandAPICommand {
        return CommandAPICommand("mute")
            .withArguments(IntegerArgument("conversation_id"))
            .withArguments(GreedyStringArgument("npc_name").replaceSuggestions(ArgumentSuggestions.strings { info ->
                // Get the conversation ID from previous args
                val id = info.previousArgs()["conversation_id"] as Int
                if (id != null) {
                    val conversation = commandUtils.conversationManager.getConversationById(id)

                    // Return NPC names from this conversation if it exists
                    return@strings conversation?.npcNames?.toTypedArray() ?: arrayOf()
                }
                arrayOf<String>()
            }))
            .executes(CommandExecutor { sender, args ->
                val id = args.get("conversation_id") as Int
                val npcName = args.get("npc_name") as String

                // Get conversation and check if it exists
                val convo = commandUtils.getConversation(id, sender) ?: return@CommandExecutor

                // Check if NPC exists in the conversation
                if (!convo.npcNames.contains(npcName)) {
                    sender.sendError("NPC not in this conversation.")
                    return@CommandExecutor
                }

                // Toggle mute status for the NPC
                val isMuted = commandUtils.story.isNPCDisabled(npcName)
                if (isMuted) {
                    // Unmute the NPC
                    commandUtils.story.enableNPCTalking(npcName)
                    sender.sendSuccess("NPC $npcName is now unmuted.")
                } else {
                    // Mute the NPC
                    commandUtils.story.disableNPCTalking(npcName)
                    sender.sendInfo("NPC $npcName is now muted.")
                }

            })
    }
}