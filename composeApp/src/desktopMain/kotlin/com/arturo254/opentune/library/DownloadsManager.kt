package com.arturo254.opentune.library

import com.arturo254.opentune.innertube.models.SongItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object DownloadsManager {
    private val downloadsDir = File(System.getProperty("user.home"), ".opentune/downloads").also { it.mkdirs() }
    private val file = File(System.getProperty("user.home"), ".opentune/downloaded_songs.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val _downloadedSongs = mutableListOf<SongItem>()

    val downloadedSongs: List<SongItem> get() = _downloadedSongs.toList()

    init { load(); scanDirForOrphans() }

    fun isDownloaded(id: String): Boolean = _downloadedSongs.any { it.id == id }

    fun addDownload(song: SongItem) {
        if (!isDownloaded(song.id)) {
            _downloadedSongs.add(0, song)
            save()
        }
    }

    fun removeDownload(song: SongItem) {
        _downloadedSongs.removeAll { it.id == song.id }
        downloadsDir.listFiles()?.filter { it.name.startsWith(song.id) }?.forEach { it.delete() }
        save()
    }

    fun toggleDownload(song: SongItem): Boolean {
        return if (isDownloaded(song.id)) {
            removeDownload(song)
            false
        } else {
            addDownload(song)
            true
        }
    }

    fun getDownloadFile(songId: String): File? {
        return downloadsDir.listFiles()?.find { it.name.startsWith(songId) && it.length() > 0 }
    }

    fun getDownloadsDir(): File = downloadsDir

    fun refresh() {
        _downloadedSongs.clear()
        load()
        scanDirForOrphans()
    }

    private fun scanDirForOrphans() {
        val files = downloadsDir.listFiles()?.filter { it.name.endsWith(".webm") && it.length() > 0 } ?: return
        val knownIds = _downloadedSongs.map { it.id }.toSet()
        var changed = false
        for (file in files) {
            val videoId = file.nameWithoutExtension
            if (videoId !in knownIds) {
                _downloadedSongs.add(
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
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(_downloadedSongs))
        } catch (e: Exception) {
            println("[DownloadsManager] Save error: ${e.message}")
        }
    }

    private fun load() {
        try {
            if (file.exists() && file.length() > 0) {
                _downloadedSongs.clear()
                _downloadedSongs.addAll(json.decodeFromString<List<SongItem>>(file.readText()))
            }
        } catch (e: Exception) {
            println("[DownloadsManager] Load error: ${e.message}")
        }
    }
}
