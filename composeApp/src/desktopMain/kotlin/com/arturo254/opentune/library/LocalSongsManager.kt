package com.arturo254.opentune.library

import com.arturo254.opentune.innertube.models.Artist
import com.arturo254.opentune.innertube.models.SongItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class LocalSong(
    val path: String,
    val title: String,
    val artist: String,
)

object LocalSongsManager {
    val AUDIO_EXTENSIONS = setOf(
        "mp3", "m4a", "m4b", "aac", "flac", "ogg", "opus", "wav", "webm", "wma", "mp4"
    )

    private const val LOCAL_PREFIX = "local:"

    private val file = File(System.getProperty("user.home"), ".opentune/local_songs.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val _songs = mutableListOf<LocalSong>()

    val songs: List<SongItem> get() = _songs.map { it.toSongItem() }

    init { load() }

    fun isLocalId(id: String): Boolean = id.startsWith(LOCAL_PREFIX)

    fun pathFromId(id: String): String = id.removePrefix(LOCAL_PREFIX)

    fun addFolder(dir: File): Int = addFiles(collectAudioFiles(dir))

    fun addFiles(files: List<File>): Int {
        val existing = _songs.map { it.path }.toSet()
        var added = 0
        for (f in files) {
            if (!f.isFile || f.length() <= 0) continue
            if (f.extension.lowercase() !in AUDIO_EXTENSIONS) continue
            val path = f.absolutePath
            if (path in existing) continue
            _songs.add(parse(path))
            added++
        }
        if (added > 0) save()
        return added
    }

    fun remove(path: String) {
        _songs.removeAll { it.path == path }
        save()
    }

    fun collectAudioFiles(dir: File): List<File> {
        val result = mutableListOf<File>()
        val stack = ArrayDeque<File>()
        dir.listFiles()?.let { stack.addAll(it) }
        while (stack.isNotEmpty()) {
            val f = stack.removeLast()
            if (f.isDirectory) {
                f.listFiles()?.let { stack.addAll(it) }
            } else if (f.extension.lowercase() in AUDIO_EXTENSIONS && f.length() > 0) {
                result.add(f)
            }
        }
        return result
    }

    private fun parse(path: String): LocalSong {
        val name = File(path).nameWithoutExtension
        val dash = name.indexOf(" - ")
        return if (dash > 0 && dash < name.length - 3) {
            LocalSong(path, name.substring(dash + 3).trim(), name.substring(0, dash).trim())
        } else {
            LocalSong(path, name, "")
        }
    }

    private fun LocalSong.toSongItem() = SongItem(
        id = "$LOCAL_PREFIX$path",
        title = title.ifBlank { File(path).nameWithoutExtension },
        artists = if (artist.isNotBlank()) listOf(Artist(artist, null)) else emptyList(),
        thumbnail = ""
    )

    private fun save() {
        try {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(_songs))
        } catch (e: Exception) {
            println("[LocalSongsManager] Save error: ${e.message}")
        }
    }

    private fun load() {
        try {
            if (file.exists() && file.length() > 0) {
                _songs.clear()
                _songs.addAll(json.decodeFromString<List<LocalSong>>(file.readText()))
                val missing = _songs.filter { !File(it.path).exists() }
                if (missing.isNotEmpty()) {
                    _songs.removeAll(missing)
                    save()
                }
            }
        } catch (e: Exception) {
            println("[LocalSongsManager] Load error: ${e.message}")
        }
    }
}
