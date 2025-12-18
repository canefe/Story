package com.canefe.story.event

import com.canefe.story.Story
import org.bukkit.Bukkit
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener

class EventManager(
    private val plugin: Story,
) {
    private val listeners = mutableListOf<Listener>()

    fun registerEvents() {
        registerListener(PlayerEventListener(plugin))

        if (Bukkit.getPluginManager().isPluginEnabled("Citizens")) {
            registerListener(NPCInteractionListener(plugin))
            plugin.logger.info("Citizens detected, NPCInteractionListener registered")
        } else {
            plugin.logger.info("Citizens not detected, skipping NPCInteractionListener registration")
        }

        if (Bukkit.getPluginManager().isPluginEnabled("ReviveMe")) {
            registerListener(ReviveMeEventListener(plugin))
            plugin.logger.info("ReviveMe detected, ReviveMeEventListener registered")
        } else {
            plugin.logger.info("ReviveMe not detected, skipping ReviveMeEventListener registration")
        }

        if (Bukkit.getPluginManager().isPluginEnabled("BetterHealthBar")) {
            val healthBarListener = HealthBarListener()
            registerListener(healthBarListener)
            healthBarListener.onEnable()
            plugin.logger.info("HealthBar detected, HealthBarListener registered")
        } else {
            plugin.logger.info("HealthBar not detected, skipping HealthBarListener registration")
        }

        plugin.logger.info("Registered ${listeners.size} event listeners")
    }

    private fun registerListener(listener: Listener) {
        plugin.server.pluginManager.registerEvents(listener, plugin)
        listeners.add(listener)
    }

    fun unregisterAll() {
        listeners.forEach { HandlerList.unregisterAll(it) }
        listeners.clear()
    }
}
