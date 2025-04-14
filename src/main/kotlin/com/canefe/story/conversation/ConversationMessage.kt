package com.canefe.story.conversation

data class ConversationMessage(
    val role: String,
    val content: String
){
    override fun toString(): String {
        return "ConversationMessage(role='$role', content='$content')"
    }
}