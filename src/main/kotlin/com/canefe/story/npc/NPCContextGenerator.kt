package com.canefe.story.npc

import com.canefe.story.Story
import com.canefe.story.npc.data.NPCContext
import com.canefe.story.npc.data.NPCData
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import kotlin.random.Random

/**
 * Service responsible for generating and updating NPC context information.
 */
class NPCContextGenerator(private val plugin: Story) {
	/**
	 * Generates a default context for a new NPC.
	 */
	private fun generateDefaultContext(npcName: String): String {
		val random = Random(System.currentTimeMillis())

		// Randomly select personality traits
		val trait = plugin.config.traitList.random(random)
		val quirk = plugin.config.quirkList.random(random)
		val motivation = plugin.config.motivationList.random(random)
		val flaw = plugin.config.flawList.random(random)
		val tone = plugin.config.toneList.random(random)

		// Construct personality description
		val personality =
			" is $trait, has the quirk of $quirk, " +
				"is motivated by $motivation, and their flaw is $flaw. " +
				"They speak in a $tone tone."

		// Construct the context
		return "$npcName$personality"
	}

	fun getOrCreateContextForNPC(npcName: String): NPCContext? {
		try {
			// Load existing NPC data including all data and memories
			val npcData =
				plugin.npcDataManager.getNPCData(npcName) ?: NPCData(
					npcName,
					"Default role",
					plugin.locationManager.getLocation("Village")
						?: plugin.locationManager.createLocation("Village", null)
						?: return null,
					context =
					generateDefaultContext(
						npcName,
					),
				)

			// Save updated NPC data with existing memories preserved
			plugin.npcDataManager.saveNPCData(npcName, npcData)

			return NPCContext(
				npcName,
				role = npcData.role,
				context = npcData.context,
				appearance = npcData.appearance,
				location = npcData.storyLocation!!,
				avatar = npcData.avatar ?: "",
				memories = npcData.memory,
				customVoice = npcData.customVoice, // Pass the custom voice ID
			)
		} catch (e: Exception) {
			plugin.logger.warning("Error while updating NPC context: ${e.message}")
			e.printStackTrace()
			return null
		}
	}

	private val generalContexts: MutableList<String> = mutableListOf()
	private val generalContextFile = File(plugin.dataFolder, "general-contexts.yml")

	init {
		loadGeneralContexts()
	}

	fun loadConfig() {
		loadGeneralContexts()
	}

	private fun loadGeneralContexts() {
		generalContexts.clear()

		if (!generalContextFile.exists()) {
			return
		}

		val config = YamlConfiguration.loadConfiguration(generalContextFile)
		generalContexts.addAll(config.getStringList("contexts"))
	}

	private fun saveGeneralContexts() {
		val config = YamlConfiguration()
		config.set("contexts", generalContexts)
		config.save(generalContextFile)
	}

	fun getGeneralContexts(): List<String> = generalContexts.toList()

	fun addGeneralContext(context: String) {
		if (!generalContexts.contains(context)) {
			generalContexts.add(context)
			saveGeneralContexts()
		}
	}

	fun removeGeneralContext(context: String) {
		if (generalContexts.remove(context)) {
			saveGeneralContexts()
		}
	}
}
