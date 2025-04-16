package com.canefe.story.command.story.location

import com.canefe.story.ConversationManager
import com.canefe.story.GroupConversation
import com.canefe.story.LocationManager
import com.canefe.story.NPCManager
import com.canefe.story.Story
import com.canefe.story.command.base.CommandComponentUtils
import com.canefe.story.util.Msg.sendError
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.command.CommandSender

class LocationCommandUtils {
    val story: Story = Story.instance
    val mm: MiniMessage = story.miniMessage
    val locationManager: LocationManager = story.locationManager

}