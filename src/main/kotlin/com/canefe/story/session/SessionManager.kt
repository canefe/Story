package com.canefe.story.session

import com.canefe.story.Story
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicReference

/**
 * Manager to track gameplay sessions and persist them to YAML files.
 */
class SessionManager private constructor(private val plugin: Story) {
    private val sessionFolder: File =
        File(plugin.dataFolder, "sessions").apply { if (!exists()) mkdirs() }

    private val current = AtomicReference<Session?>(null)

    /** Start a new session if none is active. */
    fun startSession() {
        if (current.get() != null) return
        val session = Session(plugin.timeService.getCurrentGameTime())
        current.set(session)
    }

    /** Add a player name to the active session. */
    fun addPlayer(name: String) {
        current.get()?.players?.add(name)
    }

    /** Append arbitrary text to the session history. */
    fun feed(text: String) {
        current.get()?.history?.add(text)
    }

    /** End the active session and persist it to disk. */
    fun endSession() {
        val session = current.getAndSet(null) ?: return
        session.endTime = plugin.timeService.getCurrentGameTime()
        saveSession(session)
    }

    private fun saveSession(session: Session) {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM-dd-yyyy_HH-mm-ss"))
        val file = File(sessionFolder, "session-$timestamp.yml")
        val config = YamlConfiguration()
        config.set("startTime", session.startTime)
        session.endTime?.let { config.set("endTime", it) }
        config.set("players", session.players.toList())
        config.set("history", session.history)
        config.save(file)
    }

    /** Persist and clear the current session. */
    fun shutdown() {
        current.getAndSet(null)?.let { session ->
            session.endTime = plugin.timeService.getCurrentGameTime()
            saveSession(session)
        }
    }

    companion object {
        private var instance: SessionManager? = null
        @JvmStatic
        fun getInstance(plugin: Story): SessionManager =
            instance ?: synchronized(this) { instance ?: SessionManager(plugin).also { instance = it } }
    }
}
