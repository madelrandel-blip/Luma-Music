package com.arturo254.opentune.library

import androidx.compose.runtime.mutableStateListOf
import com.arturo254.opentune.innertube.models.SongItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val createdAt: Long,
    val songs: List<SongItem> = emptyList(),
    val thumbnail: String? = null,
)

object PlaylistsManager {
    private val file = File(System.getProperty("user.home"), ".opentune/playlists.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val _playlists = mutableStateListOf<Playlist>()

    val playlists: List<Playlist> get() = _playlists.toList()

    init { load() }

    fun playlist(id: String): Playlist? = _playlists.firstOrNull { it.id == id }

    fun create(name: String, thumbnail: String? = null): String {
        val id = UUID.randomUUID().toString()
        _playlists.add(Playlist(id = id, name = name.trim(), createdAt = System.currentTimeMillis(), thumbnail = thumbnail))
        save()
        return id
    }

    fun rename(id: String, name: String) {
        val idx = _playlists.indexOfFirst { it.id == id }
        if (idx >= 0) {
            _playlists[idx] = _playlists[idx].copy(name = name.trim())
            save()
        }
    }

    fun delete(id: String) {
        _playlists.removeAll { it.id == id }
        save()
    }

    fun addSong(id: String, song: SongItem) {
        val idx = _playlists.indexOfFirst { it.id == id }
        if (idx >= 0 && _playlists[idx].songs.none { it.id == song.id }) {
            _playlists[idx] = _playlists[idx].copy(songs = _playlists[idx].songs + song)
            save()
        }
    }

    fun removeSong(id: String, songId: String) {
        val idx = _playlists.indexOfFirst { it.id == id }
        if (idx >= 0) {
            _playlists[idx] = _playlists[idx].copy(songs = _playlists[idx].songs.filterNot { it.id == songId })
            save()
        }
    }

    fun containsSong(id: String, songId: String): Boolean =
        _playlists.firstOrNull { it.id == id }?.songs?.any { it.id == songId } == true

    private fun save() {
        try {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(_playlists.toList()))
        } catch (e: Exception) {
            println("[PlaylistsManager] Save error: ${e.message}")
        }
    }

    private fun load() {
        try {
            if (file.exists() && file.length() > 0) {
                _playlists.addAll(json.decodeFromString<List<Playlist>>(file.readText()))
            }
        } catch (e: Exception) {
            println("[PlaylistsManager] Load error: ${e.message}")
        }
    }
}
