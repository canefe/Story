package com.canefe.story.command.conversation

import com.canefe.story.Story
import com.canefe.story.command.base.BaseCommand
import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.executors.PlayerCommandExecutor

class ConvCommand(
	private val plugin: Story,
) : BaseCommand {
	private val commandUtils = ConvCommandUtils()

	override fun register() {
		CommandAPICommand("conv")
			.withPermission("story.conv")
			.withSubcommand(getListSubcommand())
			.withSubcommand(getNPCSubcommand())
			.withSubcommand(getRemoveSubcommand())
			.withSubcommand(getFeedSubcommand())
			.withSubcommand(getEndSubcommand())
			.withSubcommand(getForceEndSubcommand())
			.withSubcommand(getEndAllSubcommand())
			.withSubcommand(getMuteSubcommand())
			.withSubcommand(getAddSubcommand())
			.withSubcommand(getToggleSubcommand())
			.withSubcommand(getSpySubcommand())
			.withSubcommand(getLockSubcommand())
			.withSubcommand(getToggleGlobalHearingSubcommand())
			.register()
	}

	private fun getListSubcommand(): CommandAPICommand = ConvListCommand(commandUtils).getCommand()

	private fun getNPCSubcommand(): CommandAPICommand = ConvNPCCommand(commandUtils).getCommand()

	private fun getRemoveSubcommand(): CommandAPICommand = ConvRemoveCommand(commandUtils).getCommand()

	private fun getFeedSubcommand(): CommandAPICommand = ConvFeedCommand(commandUtils).getCommand()

	private fun getEndSubcommand(): CommandAPICommand = ConvEndCommand(commandUtils).getCommand()

	private fun getForceEndSubcommand(): CommandAPICommand = ConvForceEndCommand(commandUtils).getCommand()

	private fun getEndAllSubcommand(): CommandAPICommand = ConvEndAllCommand(commandUtils).getCommand()

	private fun getMuteSubcommand(): CommandAPICommand = ConvMuteCommand(commandUtils).getCommand()

	private fun getAddSubcommand(): CommandAPICommand = ConvAddCommand(commandUtils).getCommand()

	private fun getToggleSubcommand(): CommandAPICommand = ConvToggleCommand(commandUtils).getCommand()

	private fun getSpySubcommand(): CommandAPICommand = ConvSpyCommand(commandUtils).getCommand()

	private fun getLockSubcommand(): CommandAPICommand = ConvLockCommand(commandUtils).getCommand()

	private fun getToggleGlobalHearingSubcommand(): CommandAPICommand =
		CommandAPICommand("toggleglobal")
			.executesPlayer(
				PlayerCommandExecutor { player, _ ->

					val disabledHearing = commandUtils.story.playerManager.disabledHearing

					val isGlobalHearingEnabled = !disabledHearing.contains(player.uniqueId)

					if (isGlobalHearingEnabled) {
						disabledHearing.add(player.uniqueId)

						player.sendMessage("Global hearing disabled.")
					} else {
						disabledHearing.remove(player.uniqueId)

						player.sendMessage("Global hearing enabled.")
					}
				},
			)
}
