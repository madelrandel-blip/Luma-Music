package com.arturo254.opentune.library

import com.arturo254.opentune.innertube.models.SongItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object CacheMetadataManager {
    private val cacheDir = File(System.getProperty("user.home"), ".opentune/cache").also { it.mkdirs() }
    private val metadataFile = File(System.getProperty("user.home"), ".opentune/cache_metadata.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val _cachedSongs = mutableListOf<SongItem>()

    val cachedSongs: List<SongItem> get() = _cachedSongs.toList()

    init { load(); scanDirForOrphans() }

    fun saveMetadata(song: SongItem) {
        if (_cachedSongs.any { it.id == song.id }) return
        _cachedSongs.add(0, song)
        save()
    }

    fun removeMetadata(songId: String) {
        _cachedSongs.removeAll { it.id == songId }
        save()
    }

    fun getSong(songId: String): SongItem? = _cachedSongs.find { it.id == songId }

    fun hasMetadata(songId: String): Boolean = _cachedSongs.any { it.id == songId }

    fun getActualCachedSongs(): List<SongItem> {
        syncWithCacheDir()
        return _cachedSongs.filter { song ->
            cacheDir.listFiles()?.any { it.name.startsWith(song.id) && it.length() > 0 } == true
        }
    }

    fun syncWithCacheDir() {
        val cacheFiles = cacheDir.listFiles()
            ?.filter { it.length() > 0 }
            ?.map { it.nameWithoutExtension }
            ?.toSet() ?: emptySet()
        val removed = _cachedSongs.removeAll { it.id !in cacheFiles }
        if (removed) save()
    }

    fun refresh() {
        _cachedSongs.clear()
        load()
        scanDirForOrphans()
    }

    private fun scanDirForOrphans() {
        val files = cacheDir.listFiles()?.filter { it.name.endsWith(".webm") && it.length() > 0 } ?: return
        val knownIds = _cachedSongs.map { it.id }.toSet()
        var changed = false
        for (file in files) {
            val videoId = file.nameWithoutExtension
            if (videoId !in knownIds) {
                _cachedSongs.add(
                    SongItem(
                        id = videoId,
                        title = videoId,
                        artists = emptyList(),
                        thumbnail = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                    )
                )
                changed = true
            }
        }
        if (changed) save()
    }

    private fun save() {
        try {
            metadataFile.parentFile?.mkdirs()
            metadataFile.writeText(json.encodeToString(_cachedSongs))
        } catch (e: Exception) {
            println("[CacheMetadataManager] Save error: ${e.message}")
        }
    }

    private fun load() {
        try {
            if (metadataFile.exists() && metadataFile.length() > 0) {
                _cachedSongs.clear()
                _cachedSongs.addAll(json.decodeFromString<List<SongItem>>(metadataFile.readText()))
            }
        } catch (e: Exception) {
            println("[CacheMetadataManager] Load error: ${e.message}")
        }
    }
}
