package com.arturo254.opentune.library

import com.arturo254.opentune.innertube.models.SongItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object CacheMetadataManager {
    private val cacheDir = File(System.getProperty("user.home"), ".opentune/cache").also { it.mkdirs() }

    /** Where cached audio lives - also used by the player and the storage settings screen. */
    val cacheDirectory: File get() = cacheDir

    /** Every extension a finished cached track can have. yt-dlp picks the container YouTube
     * serves, which these days is usually .m4a - assuming .webm (as the settings screen used to)
     * makes a full cache look empty. */
    private val COMPLETE_EXTENSIONS = setOf("webm", "m4a", "mp4", "opus", "mp3", "ogg")

    /**
     * True only for a finished, playable file. An interrupted download leaves `.part`, `.ytdl`
     * or `.temp.*` files behind, and those are NOT playable: handing one to ffmpeg is what makes
     * a song sit at 0:00 forever instead of playing, so nothing may ever treat them as cached.
     */
    fun isCompleteCacheFile(file: File): Boolean {
        val name = file.name.lowercase()
        if (name.endsWith(".part") || name.endsWith(".ytdl") || name.endsWith(".tmp") || name.contains(".temp.")) return false
        return file.isFile && file.length() > 0 && file.extension.lowercase() in COMPLETE_EXTENSIONS
    }

    /** Leftovers from an interrupted download - unplayable, and pure wasted disk. */
    fun isLeftoverFile(file: File): Boolean {
        val name = file.name.lowercase()
        return file.isFile && (name.endsWith(".part") || name.endsWith(".ytdl") || name.endsWith(".tmp") || name.contains(".temp."))
    }

    /** Every finished cached file for one video (a video can have more than one container). */
    fun filesFor(videoId: String): List<File> =
        cacheDir.listFiles()?.filter { it.name.startsWith("$videoId.") && isCompleteCacheFile(it) } ?: emptyList()

    /** Total size of what is actually cached, leftovers included so the number matches the disk. */
    fun cacheSizeBytes(): Long =
        cacheDir.listFiles()?.filter { isCompleteCacheFile(it) || isLeftoverFile(it) }?.sumOf { it.length() } ?: 0L

    /** Called right after a song's cached files are actually deleted, so anything else holding
     * on to that song (the player's resolved-URL memo, above all - see [onSongDeleted]'s doc at
     * its call site in PlayerManager) can forget it too instead of silently reusing it. */
    var onSongDeleted: ((videoId: String) -> Unit)? = null

    /** Same idea as [onSongDeleted], fired once after [clearAll] instead of once per song. */
    var onAllCleared: (() -> Unit)? = null

    /** Deletes every cached file for one song (leftovers too) and forgets its metadata.
     * Returns false if some file could not be deleted - on Windows that means it is still open,
     * normally because that very song is playing right now. */
    fun deleteSong(videoId: String): Boolean {
        val files = cacheDir.listFiles()?.filter { it.name.startsWith("$videoId.") } ?: emptyList()
        val allGone = files.all { runCatching { it.delete() }.getOrDefault(false) || !it.exists() }
        removeMetadata(videoId)
        onSongDeleted?.invoke(videoId)
        return allGone
    }

    /** Empties the cache - every container, plus interrupted-download leftovers. */
    fun clearAll(): Boolean {
        val files = cacheDir.listFiles()?.filter { isCompleteCacheFile(it) || isLeftoverFile(it) } ?: emptyList()
        val allGone = files.all { runCatching { it.delete() }.getOrDefault(false) || !it.exists() }
        _cachedSongs.clear()
        save()
        onAllCleared?.invoke()
        return allGone
    }

    /** Removes interrupted-download leftovers that nothing is writing any more. */
    fun purgeStaleLeftovers(olderThanMs: Long = 60 * 60 * 1000L) {
        val cutoff = System.currentTimeMillis() - olderThanMs
        cacheDir.listFiles()?.filter { isLeftoverFile(it) && it.lastModified() < cutoff }?.forEach {
            runCatching { it.delete() }
        }
    }
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
        // Some files reach the cache directory without ever going through saveMetadata (a song
        // preloaded ahead of time but never actually played, for instance) - scanning for those
        // here, not only at startup, is what makes the library tab match the disk instead of
        // needing a full app restart to catch up.
        scanDirForOrphans()
        return _cachedSongs.filter { song -> filesFor(song.id).isNotEmpty() }
    }

    fun syncWithCacheDir() {
        val cacheFiles = cacheDir.listFiles()
            ?.filter { isCompleteCacheFile(it) }
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
        val files = cacheDir.listFiles()?.filter { isCompleteCacheFile(it) } ?: return
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
