package com.todorpg.app.data

import kotlinx.serialization.json.Json
import java.io.File

/** Saves the whole game as one JSON file in app storage. */
class GameRepository(private val file: File) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun load(): GameState? = try {
        if (file.exists()) json.decodeFromString(GameState.serializer(), file.readText()) else null
    } catch (e: Exception) {
        // Keep a copy of the unreadable save rather than silently losing it.
        file.copyTo(File(file.parentFile, file.name + ".bad"), overwrite = true)
        null
    }

    fun save(state: GameState) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(GameState.serializer(), state))
        tmp.renameTo(file)
    }
}
