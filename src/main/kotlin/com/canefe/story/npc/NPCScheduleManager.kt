package com.canefe.story.npc

import com.canefe.story.Story
import com.canefe.story.location.data.StoryLocation
import net.citizensnpcs.api.npc.NPC
import net.citizensnpcs.trait.EntityPoseTrait
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.ArrayList

class NPCScheduleManager private constructor(
	private val plugin: Story,
) {
	val schedules = ConcurrentHashMap<String, NPCSchedule>()
	private val scheduleFolder: File =
		File(plugin.dataFolder, "schedules").apply {
			if (!exists()) {
				mkdirs()
			}
		}
	private var scheduleTask: BukkitTask? = null

	private val npcMovementQueue = LinkedList<NPC>()

	init {
		loadAllSchedules()
		startScheduleRunner()
	}

	fun reloadSchedules() {
		// Stop the current task
		scheduleTask?.cancel()

		// Reload all schedules
		loadAllSchedules()

		// Restart the schedule runner
		startScheduleRunner()
	}

	fun loadAllSchedules() {
		schedules.clear()
		val files = scheduleFolder.listFiles { _, name -> name.endsWith(".yml") } ?: return

		for (file in files) {
			try {
				val npcName = file.name.replace(".yml", "")
				val schedule = loadSchedule(npcName)
				if (schedule != null) {
					schedules[npcName.lowercase()] = schedule
					plugin.logger.info("Loaded schedule for NPC: $npcName")
				}
			} catch (e: Exception) {
				plugin.logger.warning("Error loading schedule from file: ${file.name}")
				e.printStackTrace()
			}
		}
		plugin.logger.info("Loaded ${schedules.size} NPC schedules")
	}

	fun loadSchedule(npcName: String): NPCSchedule? {
		val scheduleFile = File(scheduleFolder, "$npcName.yml")
		if (!scheduleFile.exists()) {
			return null
		}

		val config = YamlConfiguration.loadConfiguration(scheduleFile)
		val schedule = NPCSchedule(npcName)

		// Load all time entries
		val scheduleSection = config.getConfigurationSection("schedule") ?: return schedule

		for (timeKey in scheduleSection.getKeys(false)) {
			try {
				val time = timeKey.toInt()
				val locationName = config.getString("schedule.$timeKey.location")
				val action = config.getString("schedule.$timeKey.action", "idle")
				val dialogue = config.getString("schedule.$timeKey.dialogue")

				val entry = ScheduleEntry(time, locationName, action, dialogue)
				schedule.addEntry(entry)
			} catch (e: NumberFormatException) {
				plugin.logger.warning("Invalid time format in schedule for $npcName: $timeKey")
			}
		}

		return schedule
	}

	fun saveSchedule(schedule: NPCSchedule) {
		val scheduleFile = File(scheduleFolder, "${schedule.npcName}.yml")
		val config = YamlConfiguration()

		// Save all time entries
		for (entry in schedule.entries) {
			val timePath = "schedule.${entry.time}"
			config.set("$timePath.location", entry.locationName)
			config.set("$timePath.action", entry.action)
			if (entry.dialogue != null) {
				config.set("$timePath.dialogue", entry.dialogue)
			}
		}

		try {
			config.save(scheduleFile)
		} catch (e: IOException) {
			plugin.logger.severe("Could not save schedule for ${schedule.npcName}")
			e.printStackTrace()
		}
	}

	fun getEmptyScheduleTemplate(npcName: String): NPCSchedule {
		val schedule = NPCSchedule(npcName)
		// From 6-23 hours
		for (hour in 6..23) {
			schedule.addEntry(ScheduleEntry(hour, "", "idle", ""))
		}
		return schedule
	}

	fun getSchedule(npcName: String): NPCSchedule? = schedules[npcName.lowercase()]

	private fun startScheduleRunner() {
		// Stop any existing task
		scheduleTask?.cancel()

		// Run every minute to check for schedule updates
		scheduleTask =
			object : BukkitRunnable() {
				override fun run() {
					// if no players are online, skip the schedule check
					if (plugin.server.onlinePlayers.isEmpty()) {
						return
					}

					val gameTime = plugin.server.worlds[0].time

					// Convert to 24-hour format (0-23)
					val hour = ((gameTime / 1000 + 6) % 24).toInt() // +6 because MC day starts at 6am

					// Handle NPCs with schedules
					if (plugin.config.scheduleEnabled) {
						for (schedule in schedules.values) {
							val currentEntry = schedule.getEntryForTime(hour)
							if (currentEntry != null) {
								executeScheduleEntry(schedule.npcName, currentEntry)
							}
						}
					}

// Handle NPCs without schedules or with empty location entries
					if (plugin.config.randomPathingEnabled) {
						// Pre-filter NPCs that are candidates for random movement
						val candidateNPCs =
							plugin.npcDataManager
								.getAllNPCNames()
								.asSequence()
								.filter { npcName ->
									val npc = plugin.npcDataManager.getNPC(npcName) ?: return@filter false
									if (!npc.isSpawned) return@filter false

									// Check schedule status
									val hasSchedule = schedules.containsKey(npcName.lowercase())
									val hasLocationForCurrentTime =
										hasSchedule &&
											schedules[npcName.lowercase()]?.getEntryForTime(hour)?.locationName?.isNotEmpty() == true

									!hasSchedule || !hasLocationForCurrentTime
								}.mapNotNull { plugin.npcDataManager.getNPC(it) }
								.toList()

						// Only process candidates that have a chance of moving
						if (candidateNPCs.isNotEmpty()) {
							val randomChance = plugin.config.randomPathingChance

							// Using ThreadLocalRandom instead of Math.random() for better performance
							val random =
								java.util.concurrent.ThreadLocalRandom
									.current()

							if (plugin.config.randomPathingEnabled) {
								// Find candidate NPCs as before

								// Add eligible NPCs to the queue
								for (npc in candidateNPCs) {
									if (random.nextDouble() < randomChance && hasNearbyPlayers(npc)) {
										npcMovementQueue.add(npc)
									}
								}

								// Process a few NPCs from the queue
								val maxProcessPerTick = 3
								for (i in 0 until maxProcessPerTick) {
									if (npcMovementQueue.isEmpty()) break
									val nextNPC = npcMovementQueue.poll()
									moveNPCToRandomSublocation(nextNPC)
								}
							}
						}
					}
				}
			}.runTaskTimer(plugin, 20L, plugin.config.scheduleTaskPeriod * 20L) // Check every minute
	}

	private fun hasNearbyPlayers(npc: NPC): Boolean {
		val radius = plugin.config.rangeBeforeTeleport * 2
		val nearbyPlayers = plugin.getNearbyPlayers(npc, radius, ignoreY = true)
		return nearbyPlayers.isNotEmpty()
	}

	private fun moveNPCToRandomSublocation(npc: NPC) {
		// Early returns for invalid conditions
		val currentLocation = npc.entity?.location ?: return
		if (plugin.conversationManager.isInConversation(npc)) return // Don't move NPCs in conversation

		// Get story location and potential sublocations more efficiently
		val currentStoryLocation = plugin.locationManager.getLocationByPosition(currentLocation)

		// Get candidate sublocations (using more direct calls)
		val allSublocations =
			when {
				currentStoryLocation?.hasParent() == true ->
					plugin.locationManager.getSublocations(currentStoryLocation.parentLocationName!!)
				currentStoryLocation != null ->
					plugin.locationManager.getSublocations(currentStoryLocation.name)
				else ->
					plugin.locationManager.getAllLocations().filter { it.isSubLocation }
			}

		// Check if we have any sublocations before filtering
		if (allSublocations.isEmpty()) return

		// Filter eligible sublocations (optimized to a single pass)
		val eligibleLocations =
			allSublocations.filter {
				it.bukkitLocation != null &&
					(it.allowedNPCs.isEmpty() || it.allowedNPCs.contains(npc.name))
			}

		if (eligibleLocations.isEmpty()) return

		// Use ThreadLocalRandom for better performance in concurrent environments
		val randomIndex =
			java.util.concurrent.ThreadLocalRandom
				.current()
				.nextInt(eligibleLocations.size)
		val randomSublocation = eligibleLocations[randomIndex]

// Only clone the bukkit location when we're about to modify it
		val baseLocation = randomSublocation.bukkitLocation!! // Safe because we filtered nulls
		val randomOffsetX =
			java.util.concurrent.ThreadLocalRandom
				.current()
				.nextDouble(-3.0, 3.0)
		val randomOffsetZ =
			java.util.concurrent.ThreadLocalRandom
				.current()
				.nextDouble(-3.0, 3.0)

// Create the target location with random offsets
		val targetLocation =
			Location(
				baseLocation.world,
				baseLocation.x + randomOffsetX,
				baseLocation.y, // Initial Y, will be adjusted if needed
				baseLocation.z + randomOffsetZ,
				baseLocation.yaw,
				baseLocation.pitch,
			)

// Find safe ground within 2 blocks up or down
		val safeLocation = findNearbyGround(targetLocation, maxBlocksCheck = 2)
		if (safeLocation != null) {
			// Move the NPC to the safe location
			moveNPCToLocation(npc, safeLocation)
			plugin.logger.info("Moving ${npc.name} to random sublocation: ${randomSublocation.name}")
		} else {
			// If no safe location found, use the original sublocation
			plugin.logger.info("No safe ground found near random position, using base location for ${npc.name}")
			moveNPCToLocation(npc, baseLocation)
		}
	}

	/**
	 * Finds a safe ground position for an NPC to stand on within a limited vertical range
	 * @param location The initial location to check
	 * @param maxBlocksCheck Maximum blocks to check up and down (default: 2)
	 * @return A safe location or null if none found
	 */
	private fun findNearbyGround(
		location: Location,
		maxBlocksCheck: Int = 2,
	): Location? {
		val world = location.world
		val x = location.x
		val z = location.z
		val startY = location.y.toInt()

		// First check the exact position
		val exactBlock = world.getBlockAt(x.toInt(), startY - 1, z.toInt())
		val blockAtFeet = world.getBlockAt(x.toInt(), startY, z.toInt())
		val blockAtHead = world.getBlockAt(x.toInt(), startY + 1, z.toInt())

		// If current position is already valid (solid ground below, space for NPC)
		if (exactBlock.type.isSolid && !blockAtFeet.type.isSolid && !blockAtHead.type.isSolid) {
			return location.clone()
		}

		// Check downward first (more likely to find ground below)
		for (yOffset in 1..maxBlocksCheck) {
			val y = startY - yOffset

			// Don't check below world
			if (y <= 0) continue

			val block = world.getBlockAt(x.toInt(), y - 1, z.toInt())
			val blockAbove = world.getBlockAt(x.toInt(), y, z.toInt())
			val blockAboveTwo = world.getBlockAt(x.toInt(), y + 1, z.toInt())

			// Check if the block is solid with 2 air blocks above (space for NPC)
			if (block.type.isSolid && !blockAbove.type.isSolid && !blockAboveTwo.type.isSolid) {
				return Location(world, x, y.toDouble(), z, location.yaw, location.pitch)
			}
		}

		// Then check upward
		for (yOffset in 1..maxBlocksCheck) {
			val y = startY + yOffset

			// Don't check above world height
			if (y >= world.maxHeight - 1) continue

			val block = world.getBlockAt(x.toInt(), y - 1, z.toInt())
			val blockAbove = world.getBlockAt(x.toInt(), y, z.toInt())
			val blockAboveTwo = world.getBlockAt(x.toInt(), y + 1, z.toInt())

			// Check if the block is solid with 2 air blocks above (space for NPC)
			if (block.type.isSolid && !blockAbove.type.isSolid && !blockAboveTwo.type.isSolid) {
				return Location(world, x, y.toDouble(), z, location.yaw, location.pitch)
			}
		}

		// No safe location found
		return null
	}

	private fun executeScheduleEntry(
		npcName: String,
		entry: ScheduleEntry,
	) {
		// Get NPC entity through your NPC system
		val npc = plugin.npcDataManager.getNPC(npcName) ?: return
		val npcEntity = npc.entity ?: return
		// Handle location movement
		val locationName = entry.locationName
		if (!locationName.isNullOrEmpty()) {
			val location = plugin.locationManager.getLocation(locationName)
			if (location != null) {
				// Check if currently in conversation.
				val isInConversation = plugin.conversationManager.isInConversation(npc)

				// Check if the NPC needs to move
				val shouldMove =
					location.bukkitLocation?.let {
						npcEntity.location.distance(it) >= plugin.config.radiantRadius
					} ?: false

				if (shouldMove) {
					// if the entry.type is Work, make NPC say goodbye (unless they are already in the location)
					if (entry.action == "work" && isInConversation) {
						val goodbyeContext =
							mutableListOf(
								"\"You have a work to do at ${location.name}. Tell the people in the conversation that you are leaving.\"",
							)
						plugin.conversationManager.endConversationWithGoodbye(npc, goodbyeContext)
					}

					// Use your existing NPC movement system or teleport
					moveNPCToLocation(npc, location)
				} else {
					plugin.logger.info("${npc.name} is already at ${location.name}, skipping movement.")
				}
			}
		}

		// Handle action
		if (entry.action != null) {
			executeAction(npc, entry.action)
		}

		// Handle dialogue (announcement)
		if (entry.dialogue != null) {
			// add some random delay from 1 to 4 seconds
			val randomDelay = (1..4).random() * 20L // Convert to ticks
			Bukkit.getScheduler().runTaskLater(
				plugin,
				Runnable {
					plugin.npcMessageService.broadcastNPCMessage(entry.dialogue, npc)
				},
				randomDelay,
			)
		}
	}

	private fun moveNPCToLocation(
		npc: NPC,
		location: Location,
	) {
		val range =
			plugin.config.rangeBeforeTeleport // Distance before teleporting
		// Check if the NPC is spawned
		if (!npc.isSpawned) {
			plugin.logger.warning("NPC ${npc.name} is not spawned, cannot move.")
			return
		}
		// Check if spawned and check if there is any online players in 200 blocks
		val nearbyPlayers = plugin.getNearbyPlayers(npc, range, ignoreY = true)
		val shouldTeleport =
			nearbyPlayers.isEmpty() // If no players are nearby, teleport the NPC to the location

		// Check if the target location and the NPC's current location are in the same world
		if (npc.entity.location.world != location.world) {
			plugin.logger.warning("NPC ${npc.name} is in a different world, cannot move.")
			return
		}

		if (shouldTeleport) {
			npc.teleport(location, PlayerTeleportEvent.TeleportCause.PLUGIN)
			plugin.logger.info("Teleporting NPC ${npc.name} to $location")
			return
		} else {
			// If there are players nearby, walk to the location
			plugin.logger.info("Walking NPC ${npc.name} to $location")
			plugin.npcManager.walkToLocation(npc, location, 0.1, 1f, 30, null, null)
		}
	}

	private fun moveNPCToLocation(
		npc: NPC,
		location: StoryLocation,
	) {
		// reset pose
		npc.getOrAddTrait(EntityPoseTrait::class.java).pose = EntityPoseTrait.EntityPose.STANDING

		val bukkitLocation = location.bukkitLocation
		if (bukkitLocation == null) {
			plugin.logger.warning("No Bukkit location found for ${location.name}")
			return
		}
		moveNPCToLocation(npc, bukkitLocation)
	}

	private fun executeAction(
		npc: NPC,
		action: String,
	) {
		// Implement actions like sitting, working, etc.
		when (action.lowercase()) {
			"sit" -> {
				// Make NPC sit
				npc.getOrAddTrait(EntityPoseTrait::class.java).pose = EntityPoseTrait.EntityPose.SITTING
			}
			"work" -> {
				// Make NPC perform work animation
				npc.getOrAddTrait(EntityPoseTrait::class.java).pose = EntityPoseTrait.EntityPose.STANDING
			}
			"sleep" -> {
				// Make NPC sleep
				npc.getOrAddTrait(EntityPoseTrait::class.java).pose = EntityPoseTrait.EntityPose.SLEEPING
			}
			"idle" -> {
				// Default idle behavior
				npc.getOrAddTrait(EntityPoseTrait::class.java).pose = EntityPoseTrait.EntityPose.STANDING
			}
			else -> {
				plugin.logger.warning("Unknown action: $action for NPC: ${npc.name}")
			}
		}
	}

	fun shutdown() {
		scheduleTask?.cancel()
	}

	class NPCSchedule(
		val npcName: String,
	) {
		val entries: MutableList<ScheduleEntry> = ArrayList()

		fun addEntry(entry: ScheduleEntry) {
			entries.add(entry)
			// Sort entries by time
			entries.sortBy { it.time }
		}

		fun getEntryForTime(currentHour: Int): ScheduleEntry? {
			// Find the entry with the closest time <= current hour
			var bestEntry: ScheduleEntry? = null
			var bestTimeDiff = 24 // Maximum possible difference

			for (entry in entries) {
				val entryTime = entry.time

				// Check if this entry is applicable for current time
				if (entryTime <= currentHour) {
					val diff = currentHour - entryTime
					if (diff < bestTimeDiff) {
						bestTimeDiff = diff
						bestEntry = entry
					}
				}
			}

			// If no entry found before current hour, use the last one (evening)
			if (bestEntry == null && entries.isNotEmpty()) {
				return entries[entries.size - 1]
			}

			return bestEntry
		}
	}

	class ScheduleEntry(
		val time: Int, // Hour (0-23)
		var locationName: String?,
		val action: String?,
		val dialogue: String?,
	)

	companion object {
		private var instance: NPCScheduleManager? = null

		@JvmStatic
		fun getInstance(plugin: Story): NPCScheduleManager = instance ?: NPCScheduleManager(plugin).also { instance = it }
	}
}
