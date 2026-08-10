package com.arturo254.opentune.library

import com.arturo254.opentune.innertube.models.SongItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object LikedSongsManager {
    private val file = File(System.getProperty("user.home"), ".opentune/liked_songs.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val _likedSongs = mutableListOf<SongItem>()

    val likedSongs: List<SongItem> get() = _likedSongs.toList()

    init { load() }

    fun isLiked(id: String): Boolean = _likedSongs.any { it.id == id }

    fun toggleLike(song: SongItem) {
        val idx = _likedSongs.indexOfFirst { it.id == song.id }
        if (idx >= 0) _likedSongs.removeAt(idx) else _likedSongs.add(song)
        save()
    }

    private fun save() {
        try {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(_likedSongs))
        } catch (e: Exception) {
            println("[LikedSongsManager] Save error: ${e.message}")
        }
    }

    private fun load() {
        try {
            if (file.exists() && file.length() > 0) {
                _likedSongs.clear()
                _likedSongs.addAll(json.decodeFromString<List<SongItem>>(file.readText()))
            }
        } catch (e: Exception) {
            println("[LikedSongsManager] Load error: ${e.message}")
        }
    }
}
