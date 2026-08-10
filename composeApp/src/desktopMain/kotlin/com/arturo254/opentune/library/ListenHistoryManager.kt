package com.arturo254.opentune.library

import androidx.compose.runtime.mutableStateListOf
import com.arturo254.opentune.DesktopPreferences
import com.arturo254.opentune.innertube.models.SongItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object ListenHistoryManager {
    private const val MAX_ENTRIES = 200

    private val file = File(System.getProperty("user.home"), ".opentune/listen_history.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val _entries = mutableStateListOf<SongItem>()

    val entries: List<SongItem> get() = _entries.toList()

    init { load() }

    fun record(song: SongItem) {
        if (DesktopPreferences.pauseListenHistory) return
        _entries.removeAll { it.id == song.id }
        _entries.add(0, song)
        while (_entries.size > MAX_ENTRIES) {
            _entries.removeAt(_entries.size - 1)
        }
        save()
    }

    fun clear() {
        _entries.clear()
        save()
    }

    private fun save() {
        try {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(_entries.toList()))
        } catch (e: Exception) {
            println("[ListenHistoryManager] Save error: ${e.message}")
        }
    }

    private fun load() {
        try {
            if (file.exists() && file.length() > 0) {
                val data = json.decodeFromString<List<SongItem>>(file.readText())
                _entries.addAll(data)
            }
        } catch (e: Exception) {
            println("[ListenHistoryManager] Load error: ${e.message}")
        }
    }
}
