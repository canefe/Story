package com.canefe.story.npc.data

import com.canefe.story.Story
import com.canefe.story.conversation.ConversationMessage
import net.citizensnpcs.api.CitizensAPI
import net.citizensnpcs.api.npc.NPC
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.IOException

class NPCDataManager private constructor(private val plugin: JavaPlugin) {
    private val npcDataMap: MutableMap<String, NPCData> = HashMap()
    val npcDirectory: File = File(plugin.dataFolder, "npcs").apply {
        if (!exists()) {
            mkdirs() // Create the directory if it doesn't exist
        }
    }

    /**
     * Gets a list of all NPC names by scanning the NPC directory for YAML files.
     *
     * @return A list of all NPC names without the .yml extension
     */
    fun getAllNPCNames(): List<String> {
        val npcNames = ArrayList<String>()

        val files = npcDirectory.listFiles { _, name -> name.endsWith(".yml") }
        files?.forEach { file ->
            val fileName = file.name
            // Remove the .yml extension to get the NPC name
            val npcName = fileName.substring(0, fileName.length - 4)
            npcNames.add(npcName)
        }

        return npcNames
    }

    fun loadNPCData(npcName: String): FileConfiguration {
        val npcFile = File(npcDirectory, "$npcName.yml")
        if (!npcFile.exists()) {
            return YamlConfiguration() // Return an empty configuration if no file exists
        }

        return YamlConfiguration.loadConfiguration(npcFile)
    }

    fun saveNPCFile(npcName: String, config: FileConfiguration) {
        val npcFile = File(npcDirectory, "$npcName.yml")
        try {
            config.save(npcFile)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun getNPC(npcName: String): NPC? {
        val npcFile = File(npcDirectory, "$npcName.yml")
        if (!npcFile.exists()) {
            return null // Return null if no file exists
        }

        // Try to find npc by name in citizens registry
        return CitizensAPI.getNPCRegistry().find { it.name.equals(npcName, ignoreCase = true) }
    }

    fun deleteNPCFile(npcName: String) {
        val npcFile = File(npcDirectory, "$npcName.yml")
        if (npcFile.exists()) {
            npcFile.delete()
        }
    }

    companion object {
        private var instance: NPCDataManager? = null

        @JvmStatic
        fun getInstance(plugin: Story): NPCDataManager {
            return instance ?: NPCDataManager(plugin).also { instance = it }
        }
    }
}