package com.arturo254.opentune

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

@Serializable
data class LyricsResponse(
    @SerialName("syncedLyrics") val syncedLyrics: String? = null,
    @SerialName("plainLyrics") val plainLyrics: String? = null
)

@Serializable
private data class LyricsTrack(
    @SerialName("artistName") val artistName: String? = null,
    @SerialName("trackName") val trackName: String? = null,
    @SerialName("syncedLyrics") val syncedLyrics: String? = null,
    @SerialName("plainLyrics") val plainLyrics: String? = null
)

data class LyricLine(val timeMs: Long, val text: String)

object LyricsManager {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchLyrics(artist: String, title: String): LyricsResponse? {
        return fetchExact(artist, title) ?: fetchSearch(artist, title)
    }

    private fun httpGet(url: String): String? {
        val conn = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (_: Exception) { return null }
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "Luma-Music/2.1.0 (https://github.com/madelrandel-blip/Luma-Music)")
            conn.setRequestProperty("Accept", "application/json")
            if (conn.responseCode == 200) {
                return conn.inputStream.bufferedReader().use { it.readText() }
            }
        } catch (_: Exception) { return null } finally { conn.disconnect() }
        return null
    }

    private suspend fun fetchExact(artist: String, title: String): LyricsResponse? = withContext(Dispatchers.IO) {
        try {
            val url = "https://lrclib.net/api/get?artist_name=${enc(artist)}&track_name=${enc(title)}"
            val body = httpGet(url) ?: return@withContext null
            json.decodeFromString<LyricsResponse>(body)
        } catch (_: Exception) { null }
    }

    private suspend fun fetchSearch(artist: String, title: String): LyricsResponse? = withContext(Dispatchers.IO) {
        try {
            val url = "https://lrclib.net/api/search?q=${enc("$artist $title")}&artist_name=${enc(artist)}&track_name=${enc(title)}"
            val body = httpGet(url) ?: return@withContext null
            val tracks = json.decodeFromString<List<LyricsTrack>>(body)
            tracks.firstOrNull { !it.syncedLyrics.isNullOrBlank() || !it.plainLyrics.isNullOrBlank() }
                ?.let { LyricsResponse(it.syncedLyrics, it.plainLyrics) }
        } catch (_: Exception) { null }
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    fun parseSynced(lrc: String): List<LyricLine> {
        val regex = Regex("\\[(\\d+):(\\d+)(?:\\.(\\d+))?]")
        val result = mutableListOf<LyricLine>()
        for (raw in lrc.lines()) {
            val matches = regex.findAll(raw).toList()
            if (matches.isEmpty()) continue
            val text = raw.substring(matches.last().range.last + 1).trim()
            if (text.isEmpty()) continue
            for (m in matches) {
                val minutes = m.groupValues[1].toLongOrNull() ?: continue
                val seconds = m.groupValues[2].toLongOrNull() ?: continue
                val frac = m.groupValues[3].let { if (it.isEmpty()) "0" else it.padEnd(2, '0').take(2) }.toLongOrNull() ?: 0
                result.add(LyricLine(minutes * 60_000L + seconds * 1000L + frac * 10L, text))
            }
        }
        return result.sortedBy { it.timeMs }
    }
}
