package com.arturo254.opentune.library

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.arturo254.opentune.DesktopPreferences
import com.arturo254.opentune.innertube.models.Artist
import com.arturo254.opentune.innertube.models.SongItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object DownloadsManager {
    private val defaultDownloadsDir = File(System.getProperty("user.home"), ".opentune/downloads")
    private var downloadsDir: File = resolveDir().also { it.mkdirs() }
    private val file = File(System.getProperty("user.home"), ".opentune/downloaded_songs.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val _downloadedSongs = mutableListOf<SongItem>()

    val downloadedSongs: List<SongItem> get() = _downloadedSongs.toList()

    /** Song ids currently being fetched by [com.arturo254.opentune.player.PlayerManager.downloadSong] -
     * backed by Compose state so any UI showing a song's artwork can react to it directly (e.g.
     * a progress ring around the thumbnail) without needing its own polling loop. */
    var downloadingIds by mutableStateOf<Set<String>>(emptySet()); private set

    fun markDownloading(id: String) { downloadingIds = downloadingIds + id }
    fun clearDownloading(id: String) { downloadingIds = downloadingIds - id }

    private val orphanExtensions = setOf("webm", "m4a", "mp3", "opus")
    // Matches the current "Artist - Title [videoId].ext" naming so orphaned files (found on
    // disk but missing from downloaded_songs.json, e.g. after manually copying files in) can
    // still be restored with a readable title/artist instead of just the raw video id.
    private val namedFileRegex = Regex("^(.+) - (.+) \\[([A-Za-z0-9_-]{5,20})]\\.[A-Za-z0-9]+$")

    // Must run after the properties above are initialized (scanDirForOrphans() below uses them).
    init { load(); scanDirForOrphans() }

    private fun resolveDir(): File {
        val custom = DesktopPreferences.downloadsPath
        return if (custom.isNotBlank()) File(custom) else defaultDownloadsDir
    }

    fun isDownloaded(id: String): Boolean = _downloadedSongs.any { it.id == id }

    fun addDownload(song: SongItem) {
        if (!isDownloaded(song.id)) {
            _downloadedSongs.add(0, song)
            save()
        }
    }

    fun removeDownload(song: SongItem) {
        _downloadedSongs.removeAll { it.id == song.id }
        downloadsDir.listFiles()?.filter { fileMatchesId(it.name, song.id) }?.forEach { it.delete() }
        save()
    }

    // Downloaded files are now named "Artist - Title [id].ext" for readability, but are still
    // matched by the video id embedded in brackets (or, for files saved before this change,
    // by the old plain "id.ext" naming) so lookups and deletions keep working either way.
    private fun fileMatchesId(fileName: String, id: String): Boolean =
        fileName.startsWith("$id.") || fileName.contains("[$id]")

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
        return downloadsDir.listFiles()?.find { fileMatchesId(it.name, songId) && it.length() > 0 }
    }

    fun getDownloadsDir(): File = downloadsDir

    fun defaultDownloadsDirPath(): String = defaultDownloadsDir.absolutePath

    /**
     * Changes where downloaded song files are stored. Moves any existing files from the current
     * folder into the new one (best effort) so previously downloaded songs keep working, then
     * persists the choice and rescans. Returns false if the new folder couldn't be used.
     */
    fun setDownloadsDir(newPath: String): Boolean {
        return try {
            val newDir = File(newPath)
            newDir.mkdirs()
            if (!newDir.isDirectory) return false
            val oldDir = downloadsDir
            if (oldDir.absolutePath != newDir.absolutePath && oldDir.isDirectory) {
                oldDir.listFiles()?.forEach { f ->
                    try {
                        val target = File(newDir, f.name)
                        if (!target.exists()) {
                            f.copyTo(target, overwrite = false)
                        }
                        f.delete()
                    } catch (_: Exception) {
                        // Best effort: leave the file in the old folder if it couldn't be moved.
                    }
                }
            }
            downloadsDir = newDir
            DesktopPreferences.updateDownloadsPath(newPath)
            refresh()
            true
        } catch (e: Exception) {
            println("[DownloadsManager] setDownloadsDir error: ${e.message}")
            false
        }
    }

    fun refresh() {
        _downloadedSongs.clear()
        load()
        scanDirForOrphans()
    }

    private fun scanDirForOrphans() {
        val files = downloadsDir.listFiles()?.filter { it.extension.lowercase() in orphanExtensions && it.length() > 0 } ?: return
        val knownIds = _downloadedSongs.map { it.id }.toSet()
        var changed = false
        for (file in files) {
            val match = namedFileRegex.find(file.name)
            val videoId = match?.groupValues?.get(3) ?: file.nameWithoutExtension
            if (videoId !in knownIds) {
                val artistName = match?.groupValues?.get(1)
                val titleName = match?.groupValues?.get(2) ?: videoId
                _downloadedSongs.add(
                    SongItem(
                        id = videoId,
                        title = titleName,
                        artists = if (!artistName.isNullOrBlank()) listOf(Artist(name = artistName, id = null)) else emptyList(),
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
