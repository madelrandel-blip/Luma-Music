package com.arturo254.opentune.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.arturo254.opentune.DesktopPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object SearchHistoryManager {
    private const val MAX_ENTRIES = 20

    private val file = File(System.getProperty("user.home"), ".opentune/search_history.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private var _entries by mutableStateOf<List<String>>(emptyList())

    val entries: List<String> get() = _entries

    init { load() }

    fun add(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        if (DesktopPreferences.pauseSearchHistory) return
        val updated = _entries - q
        val list = listOf(q) + updated
        _entries = if (list.size > MAX_ENTRIES) list.take(MAX_ENTRIES) else list
        save()
    }

    fun remove(query: String) {
        _entries = _entries - query
        save()
    }

    fun clear() {
        _entries = emptyList()
        save()
    }

    private fun save() {
        try {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(_entries))
        } catch (e: Exception) {
            println("[SearchHistoryManager] Save error: ${e.message}")
        }
    }

    private fun load() {
        try {
            if (file.exists() && file.length() > 0) {
                _entries = json.decodeFromString<List<String>>(file.readText())
            }
        } catch (e: Exception) {
            println("[SearchHistoryManager] Load error: ${e.message}")
        }
    }
}
