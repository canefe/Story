package com.canefe.story.command.story.quest

import com.canefe.story.Story
import com.canefe.story.command.base.CommandComponentUtils
import com.canefe.story.location.LocationManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage

class QuestCommandUtils {
	val story: Story = Story.instance
	val mm: MiniMessage = story.miniMessage
	val locationManager: LocationManager = story.locationManager

	fun createButton(
		label: String,
		color: String,
		clickAction: String,
		command: String,
		hoverText: String,
	): Component = CommandComponentUtils.createButton(mm, label, color, clickAction, command, hoverText)
}
