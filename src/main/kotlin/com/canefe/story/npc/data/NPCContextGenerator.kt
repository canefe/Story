package com.canefe.story.npc.data

import com.canefe.story.Story
import com.canefe.story.npc.memory.Memory
import com.canefe.story.npc.relationship.Relationship
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

	/**
	 * Generates a temporary personality context for generic NPCs.
	 * This creates new personality traits for each conversation while preserving custom context.
	 */
	private fun generateTemporaryPersonality(npcName: String, customContext: String): String {
		val random = Random(System.currentTimeMillis())

		// Randomly select personality traits
		val trait = plugin.config.traitList.random(random)
		val quirk = plugin.config.quirkList.random(random)
		val motivation = plugin.config.motivationList.random(random)
		val flaw = plugin.config.flawList.random(random)
		val tone = plugin.config.toneList.random(random)

		// Construct temporary personality description
		val temporaryPersonality =
			"$npcName is $trait, has the quirk of $quirk, " +
				"is motivated by $motivation, and their flaw is $flaw. " +
				"They speak in a $tone tone. " +
				"The time is ${plugin.timeService.getHours()}:${plugin.timeService.getMinutes()} and the season is ${plugin.timeService.getSeason()}."

		// Combine with custom context if it exists
		return if (customContext.isNotBlank()) {
			"$temporaryPersonality $customContext"
		} else {
			temporaryPersonality
		}
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

			// For generic NPCs, generate temporary personality while preserving custom context
			val finalContext = if (npcData.generic) {
				generateTemporaryPersonality(npcName, npcData.context)
			} else {
				npcData.context
			}

			// For generic NPCs, use empty memories list instead of persistent memories
			val memories = if (npcData.generic) {
				emptyList<Memory>()
			} else {
				npcData.memory
			}

			// Get relationships for this NPC
			val relationships = plugin.relationshipManager.getAllRelationships(npcName)

			return NPCContext(
				npcName,
				role = npcData.role,
				context = finalContext,
				appearance = npcData.appearance,
				location = npcData.storyLocation!!,
				avatar = npcData.avatar,
				memories = memories,
				relationships = relationships,
				customVoice = npcData.customVoice,
				generic = npcData.generic,
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
