package com.canefe.story.audio

import com.canefe.story.Story
import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPluginMessage
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.bukkit.entity.Player
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.sound.sampled.*

/**
 * Manages audio generation using the ElevenLabs API
 * This class handles generating speech from text, caching audio files,
 * and sending voice files to the client mod
 */
class ElevenLabsAudioManager(private val plugin: Story) {

	private val client = OkHttpClient.Builder()
		.connectTimeout(30, TimeUnit.SECONDS)
		.readTimeout(30, TimeUnit.SECONDS)
		.writeTimeout(30, TimeUnit.SECONDS)
		.build()

	private val gson = Gson()

	// Voice ID mapping for different character types
	private val voiceMapping = mutableMapOf<String, String>()

	// Cache for generated audio files
	private val audioCache = ConcurrentHashMap<String, ByteArray>()

	// Base URL for ElevenLabs API
	private val baseUrl = "https://api.elevenlabs.io/v1/"

	private var apiKey: String = plugin.config.elevenLabsApiKey ?: ""

	// Audio cache directory
	private val cacheDir = File(plugin.dataFolder, "audio_cache")

	init {
		// Load configuration
		loadConfig()
		// Create cache directory if it doesn't exist
		if (!cacheDir.exists()) {
			cacheDir.mkdirs()
		}

		// Initialize MP3 support
		initializeAudioCodecs()
	}

	/**
	 * Initialize audio codecs for MP3 support
	 */
	private fun initializeAudioCodecs() {
		try {
			// Check if MP3 support is available by examining supported file types
			val formats = AudioSystem.getAudioFileTypes()
			plugin.logger.info("Supported audio formats: ${formats.joinToString(", ")}")

			// Try to register MP3 SPI manually if needed
			try {
				Class.forName("javazoom.spi.mpeg.sampled.file.MpegAudioFileReader")
				plugin.logger.info("MP3 SPI classes are available")
			} catch (e: ClassNotFoundException) {
				plugin.logger.warning("MP3 SPI classes not found - MP3 conversion may not work")
			}

			// Test if we can create a dummy MP3 input stream to verify MP3 support
			try {
				val testMp3Header = byteArrayOf(
					0xFF.toByte(),
					0xFB.toByte(),
					0x90.toByte(),
					0x00.toByte(), // MP3 header
				)
				val testInputStream = ByteArrayInputStream(testMp3Header)
				AudioSystem.getAudioInputStream(testInputStream)
				plugin.logger.info("MP3 decoding support confirmed")
			} catch (e: Exception) {
				plugin.logger.warning("MP3 decoding test failed: ${e.message}")
			}
		} catch (e: Exception) {
			plugin.logger.warning("Failed to initialize audio codecs: ${e.message}")
		}
	}

	/**
	 * Loads voice configuration from the plugin config
	 */
	fun loadConfig() {
		val config = plugin.config

		// API Key
		apiKey = config.elevenLabsApiKey ?: ""
		if (apiKey.isEmpty()) {
			plugin.logger.warning("No ElevenLabs API key configured. Voice generation will not work.")
		}

		// Default voices - you can add more mappings in your plugin config
		val voicesSection = config.elevenLabsVoices
		if (voicesSection != null) {
			for ((key, value) in voicesSection.entries) {
				voiceMapping[key] = value.toString()
			}
		} else {
			plugin.logger.warning("No ElevenLabs voices configured")
		}

		// Add default voice mapping if none exist
		if (voiceMapping.isEmpty()) {
			voiceMapping["default"] = "pNInz6obpgDQGcFmaJgB" // Default ElevenLabs voice
		}

		// Load cached audio files into memory
		loadAudioCache()
	}

	/**
	 * Load cached audio files from disk into memory
	 */
	private fun loadAudioCache() {
		try {
			if (!cacheDir.exists()) {
				plugin.logger.info("Audio cache directory doesn't exist, skipping cache loading")
				return
			}

			val cacheFiles = cacheDir.listFiles { _, name -> name.endsWith(".wav") }
			if (cacheFiles == null || cacheFiles.isEmpty()) {
				plugin.logger.info("No cached audio files found")
				return
			}

			var loadedCount = 0
			var totalSize = 0L

			for (cacheFile in cacheFiles) {
				try {
					val cacheKey = cacheFile.nameWithoutExtension
					val audioData = cacheFile.readBytes()
					audioCache[cacheKey] = audioData
					loadedCount++
					totalSize += audioData.size
					plugin.logger.fine("Loaded cached audio: ${cacheFile.name} (${audioData.size} bytes)")
				} catch (e: Exception) {
					plugin.logger.warning("Failed to load cached audio file ${cacheFile.name}: ${e.message}")
				}
			}

			if (loadedCount > 0) {
				val totalSizeMB = totalSize / (1024.0 * 1024.0)
				plugin.logger.info("Loaded $loadedCount cached audio files (%.2f MB) into memory".format(totalSizeMB))
			} else {
				plugin.logger.info("No cached audio files could be loaded")
			}
		} catch (e: Exception) {
			plugin.logger.warning("Failed to load audio cache: ${e.message}")
		}
	}

	/**
	 * Returns true if the system is configured correctly for audio generation
	 */
	fun isConfigured(): Boolean = apiKey.isNotEmpty()

	/**
	 * Generate speech from text and send it to the player
	 * @param player The player to send the audio to
	 * @param text The text to convert to speech
	 * @param voiceId The voice ID to use (optional, uses default if not specified)
	 * @param npcName The name of the NPC speaking (for caching purposes)
	 */
	fun generateAndSendSpeech(
		player: Player,
		text: String,
		voiceId: String? = null,
		npcName: String = "unknown",
	): CompletableFuture<Boolean> = generateSpeechOnce(text, voiceId ?: getDefaultVoiceId(), npcName)
		.thenApply { audioData ->
			if (audioData != null) {
				sendAudioToPlayer(player, audioData)
				true
			} else {
				false
			}
		}

	/**
	 * Generate speech from text and return raw audio data (for sending to multiple players)
	 * @param text The text to convert to speech
	 * @param voiceId The voice ID to use
	 * @param npcName The name of the NPC speaking (for caching purposes)
	 * @return CompletableFuture containing the audio data or null if generation failed
	 */
	fun generateSpeechOnce(text: String, voiceId: String, npcName: String): CompletableFuture<ByteArray?> {
		if (!isConfigured()) {
			plugin.logger.warning("ElevenLabs is not configured properly")
			return CompletableFuture.completedFuture(null)
		}

		// Generate cache key
		val cacheKey = generateCacheKey(text, voiceId, npcName)

		// Check cache first
		audioCache[cacheKey]?.let { cachedAudio ->
			plugin.logger.info("Using cached audio for NPC $npcName")
			return CompletableFuture.completedFuture(cachedAudio)
		}

		// Check file cache
		val cacheFile = File(cacheDir, "$cacheKey.wav")
		if (cacheFile.exists()) {
			try {
				val audioData = cacheFile.readBytes()
				audioCache[cacheKey] = audioData
				plugin.logger.info("Loaded audio from file cache for NPC $npcName")
				return CompletableFuture.completedFuture(audioData)
			} catch (e: Exception) {
				plugin.logger.warning("Failed to read cached audio file: ${e.message}")
			}
		}

		// Generate new audio
		return generateSpeech(text, voiceId)
			.thenApply { audioData ->
				if (audioData != null) {
					// Cache the audio
					audioCache[cacheKey] = audioData

					// Save to file cache
					try {
						cacheFile.writeBytes(audioData)
						plugin.logger.info("Saved generated audio to cache for NPC $npcName")
					} catch (e: Exception) {
						plugin.logger.warning("Failed to save audio to cache: ${e.message}")
					}

					audioData
				} else {
					null
				}
			}
			.exceptionally { throwable ->
				plugin.logger.severe("Failed to generate speech: ${throwable.message}")
				null
			}
	}

	/**
	 * Generate speech from text using ElevenLabs API
	 */
	fun generateSpeech(text: String, voiceId: String): CompletableFuture<ByteArray?> {
		val future = CompletableFuture<ByteArray?>()

		// Create request body
		val requestBody = JsonObject().apply {
			addProperty("text", text)
			addProperty("model_id", "eleven_flash_v2_5")
			add(
				"voice_settings",
				JsonObject().apply {
					addProperty("stability", 0.5)
					addProperty("similarity_boost", 0.5)
				},
			)
		}

		val request = Request.Builder()
			.url("${baseUrl}text-to-speech/$voiceId")
			.post(requestBody.toString().toRequestBody("application/json".toMediaTypeOrNull()))
			.addHeader("Accept", "audio/mpeg")
			.addHeader("Content-Type", "application/json")
			.addHeader("xi-api-key", apiKey)
			.build()

		// Execute request asynchronously
		client.newCall(request).enqueue(object : Callback {
			override fun onFailure(call: Call, e: IOException) {
				plugin.logger.severe("ElevenLabs API request failed: ${e.message}")
				future.complete(null)
			}

			override fun onResponse(call: Call, response: Response) {
				try {
					if (response.isSuccessful) {
						val mp3Data = response.body?.bytes()
						if (mp3Data != null) {
							plugin.logger.info("Successfully generated ${mp3Data.size} bytes of MP3 audio")
							// Convert MP3 to WAV
							val wavData = convertMp3ToWav(mp3Data)
							if (wavData != null) {
								plugin.logger.info("Successfully converted to ${wavData.size} bytes of WAV audio")
								future.complete(wavData)
							} else {
								plugin.logger.warning("Failed to convert MP3 to WAV, sending MP3 as fallback")
								future.complete(mp3Data)
							}
						} else {
							plugin.logger.warning("Empty response from ElevenLabs API")
							future.complete(null)
						}
					} else {
						plugin.logger.warning("ElevenLabs API returned error: ${response.code} - ${response.message}")
						plugin.logger.warning("Response body: ${response.body?.string()}")
						future.complete(null)
					}
				} catch (e: Exception) {
					plugin.logger.severe("Error processing ElevenLabs response: ${e.message}")
					future.complete(null)
				} finally {
					response.close()
				}
			}
		})

		return future
	}

	/**
	 * Convert MP3 data to WAV format using FFmpeg
	 */
	private fun convertMp3ToWav(mp3Data: ByteArray): ByteArray? {
		return try {
			// Create temporary files
			val tempDir = File(plugin.dataFolder, "temp")
			if (!tempDir.exists()) {
				tempDir.mkdirs()
			}

			val tempMp3File = File(tempDir, "temp_${System.currentTimeMillis()}.mp3")
			val tempWavFile = File(tempDir, "temp_${System.currentTimeMillis()}.wav")

			try {
				// Write MP3 data to temporary file
				tempMp3File.writeBytes(mp3Data)

				// Use ProcessBuilder to call ffmpeg
				val processBuilder = ProcessBuilder(
					"ffmpeg", "-i", tempMp3File.absolutePath,
					"-acodec", "pcm_s16le", "-ar", "44100",
					"-y", // Overwrite output file if it exists
					tempWavFile.absolutePath,
				)

				// Redirect error stream to avoid hanging
				processBuilder.redirectErrorStream(true)

				val process = processBuilder.start()
				val exitCode = process.waitFor()

				if (exitCode == 0 && tempWavFile.exists()) {
					plugin.logger.info("Successfully converted MP3 to WAV using FFmpeg")
					return tempWavFile.readBytes()
				} else {
					plugin.logger.warning("FFmpeg conversion failed with exit code: $exitCode")
					return null
				}
			} finally {
				// Clean up temporary files
				if (tempMp3File.exists()) {
					tempMp3File.delete()
				}
				if (tempWavFile.exists()) {
					tempWavFile.delete()
				}
			}
		} catch (e: Exception) {
			plugin.logger.severe("Failed to convert MP3 to WAV using FFmpeg: ${e.message}")
			null
		}
	}

	/**
	 * Send audio data to a player using PacketEvents with chunking support
	 */
	fun sendAudioToPlayer(player: Player, audioData: ByteArray) {
		try {
			// For now, let's try a very conservative approach for testing
			// Always use chunking for files over 50KB to avoid packet size issues
			val maxChunkSize = 900 * 1024 // 50KB chunks

			if (audioData.size <= maxChunkSize) {
				// Send as single packet
				plugin.logger.info("Sending ${audioData.size} bytes of audio to player ${player.name}")

				try {
					val user = PacketEvents.getAPI().playerManager.getUser(player)
					val channelId = "story:play_audio"

					val packet = WrapperPlayServerPluginMessage(channelId, audioData)
					user.sendPacket(packet)
					plugin.logger.info("Successfully sent ${audioData.size} bytes of audio to player ${player.name}")
				} catch (e: Exception) {
					plugin.logger.severe("Failed to send single packet: ${e.message}")
					// Fall back to chunking
					sendAudioInChunks(player, audioData)
				}
			} else {
				// Always chunk larger files
				sendAudioInChunks(player, audioData)
			}
		} catch (e: Exception) {
			plugin.logger.severe("Failed to send audio packet to player ${player.name}: ${e.message}")
			e.printStackTrace()
		}
	}

	/**
	 * Get the voice ID for a given NPC name or default
	 */
	fun getVoiceId(npcName: String): String = voiceMapping[npcName] ?: voiceMapping["default"] ?: "pNInz6obpgDQGcFmaJgB"

	/**
	 * Get the default voice ID
	 */
	private fun getDefaultVoiceId(): String = voiceMapping["default"] ?: "pNInz6obpgDQGcFmaJgB"

	/**
	 * Set voice mapping for an NPC
	 */
	fun setVoiceMapping(npcName: String, voiceId: String) {
		voiceMapping[npcName] = voiceId
	}

	/**
	 * Generate a cache key for the given text, voice, and NPC
	 */
	private fun generateCacheKey(text: String, voiceId: String, npcName: String): String {
		val input = "$text|$voiceId|$npcName"
		val digest = MessageDigest.getInstance("MD5")
		val hashBytes = digest.digest(input.toByteArray())
		return hashBytes.joinToString("") { "%02x".format(it) }
	}

	/**
	 * Voice data class for available voices
	 */
	data class Voice(val voiceId: String, val name: String, val category: String = "unknown")

	/**
	 * Get available voices from ElevenLabs API
	 */
	fun getAvailableVoices(): CompletableFuture<List<Voice>> {
		val future = CompletableFuture<List<Voice>>()

		if (!isConfigured()) {
			future.complete(emptyList())
			return future
		}

		val request = Request.Builder()
			.url("${baseUrl}voices")
			.get()
			.addHeader("xi-api-key", apiKey)
			.build()

		client.newCall(request).enqueue(object : Callback {
			override fun onFailure(call: Call, e: IOException) {
				plugin.logger.warning("Failed to fetch available voices: ${e.message}")
				future.complete(emptyList())
			}

			override fun onResponse(call: Call, response: Response) {
				try {
					if (response.isSuccessful) {
						val responseBody = response.body?.string()
						if (responseBody != null) {
							val voicesJson = gson.fromJson(responseBody, JsonObject::class.java)
							val voicesArray = voicesJson.getAsJsonArray("voices")
							val voices = mutableListOf<Voice>()

							for (voiceElement in voicesArray) {
								val voiceObj = voiceElement.asJsonObject
								val voiceId = voiceObj.get("voice_id").asString
								val name = voiceObj.get("name").asString
								val category = voiceObj.get("category")?.asString ?: "unknown"
								voices.add(Voice(voiceId, name, category))
							}

							future.complete(voices)
						} else {
							future.complete(emptyList())
						}
					} else {
						plugin.logger.warning("Failed to fetch voices: ${response.code}")
						future.complete(emptyList())
					}
				} catch (e: Exception) {
					plugin.logger.warning("Error parsing voices response: ${e.message}")
					future.complete(emptyList())
				} finally {
					response.close()
				}
			}
		})

		return future
	}

	/**
	 * Clear the audio cache
	 */
	fun clearCache() {
		audioCache.clear()
		try {
			// Also clear file cache
			if (cacheDir.exists()) {
				cacheDir.listFiles()?.forEach { file ->
					if (file.isFile && file.name.endsWith(".wav")) {
						file.delete()
					}
				}
			}
			plugin.logger.info("Audio cache cleared")
		} catch (e: Exception) {
			plugin.logger.warning("Failed to clear file cache: ${e.message}")
		}
	}

	/**
	 * Send audio data in chunks to avoid packet size limits
	 */
	private fun sendAudioInChunks(player: Player, audioData: ByteArray) {
		try {
			val maxChunkSize = 500 * 1024 // 30KB chunks to be safe
			val totalChunks = (audioData.size + maxChunkSize - 1) / maxChunkSize

			plugin.logger.info("Splitting ${audioData.size} bytes into $totalChunks chunks for player ${player.name}")

			val user = PacketEvents.getAPI().playerManager.getUser(player)

			// Generate a unique audio ID for this transmission
			val audioId = UUID.randomUUID().toString()

			for (i in 0 until totalChunks) {
				val start = i * maxChunkSize
				val end = minOf(start + maxChunkSize, audioData.size)
				val chunk = audioData.sliceArray(start until end)

				// Create packet format that matches client expectations:
				// [audioId_length (4 bytes)] + [audioId] + [chunkIndex (4 bytes)] + [totalChunks (4 bytes)] + [chunk_data]

				val audioIdBytes = audioId.toByteArray(Charsets.UTF_8)
				val audioIdLength = audioIdBytes.size

				val packetData = ByteArray(4 + audioIdLength + 4 + 4 + chunk.size)
				var offset = 0

				// Write audioId length (4 bytes, big endian to match client)
				packetData[offset++] = ((audioIdLength shr 24) and 0xFF).toByte()
				packetData[offset++] = ((audioIdLength shr 16) and 0xFF).toByte()
				packetData[offset++] = ((audioIdLength shr 8) and 0xFF).toByte()
				packetData[offset++] = (audioIdLength and 0xFF).toByte()

				// Write audioId
				System.arraycopy(audioIdBytes, 0, packetData, offset, audioIdLength)
				offset += audioIdLength

				// Write chunkIndex (4 bytes, big endian)
				packetData[offset++] = ((i shr 24) and 0xFF).toByte()
				packetData[offset++] = ((i shr 16) and 0xFF).toByte()
				packetData[offset++] = ((i shr 8) and 0xFF).toByte()
				packetData[offset++] = (i and 0xFF).toByte()

				// Write totalChunks (4 bytes, big endian)
				packetData[offset++] = ((totalChunks shr 24) and 0xFF).toByte()
				packetData[offset++] = ((totalChunks shr 16) and 0xFF).toByte()
				packetData[offset++] = ((totalChunks shr 8) and 0xFF).toByte()
				packetData[offset++] = (totalChunks and 0xFF).toByte()

				// Write chunk data
				System.arraycopy(chunk, 0, packetData, offset, chunk.size)

				val channelId = "story:play_audio"
				val packet = WrapperPlayServerPluginMessage(channelId, packetData)
				user.sendPacket(packet)

				plugin.logger.info("Sent chunk ${i + 1}/$totalChunks (${chunk.size} bytes) to player ${player.name}")

				// Small delay between chunks to prevent overwhelming the client
				if (i < totalChunks - 1) {
					Thread.sleep(50) // 50ms delay
				}
			}
		} catch (e: Exception) {
			plugin.logger.severe("Failed to send audio chunks to player ${player.name}: ${e.message}")
		}
	}
}
