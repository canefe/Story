package com.canefe.story.npc.data

import com.canefe.story.StoryLocation
import com.canefe.story.conversation.ConversationMessage

data class NPCData(
    val name: String,
    val role: String,
    val storyLocation: StoryLocation,
    val context: String,
    private val conversationHistory: List<ConversationMessage>
) {
    val avatar: String = ""

    fun getConversationHistory(): List<ConversationMessage> {
        return conversationHistory.toList() // Defensive copy
    }

    override fun toString(): String {
        return "NPCData{name=$name, role=$role, location=$storyLocation, context=$context}"
    }
}
