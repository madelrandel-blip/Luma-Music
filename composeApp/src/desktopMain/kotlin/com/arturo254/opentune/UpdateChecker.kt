package com.arturo254.opentune

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

const val APP_VERSION = "2.0.1"
const val GITHUB_REPO = "madelrandel-blip/Luma-Music"

@Serializable
data class LatestRelease(
    @SerialName("tag_name") val tagName: String = "",
    @SerialName("html_url") val htmlUrl: String = ""
)

object UpdateChecker {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseVersion(tag: String): List<Int> =
        tag.trim().removePrefix("v").split('.').mapNotNull { it.toIntOrNull() }

    fun isNewerVersion(latestTag: String, current: String): Boolean {
        val latest = parseVersion(latestTag)
        val now = parseVersion(current)
        val size = maxOf(latest.size, now.size)
        for (i in 0 until size) {
            val l = latest.getOrNull(i) ?: 0
            val n = now.getOrNull(i) ?: 0
            if (l != n) return l > n
        }
        return false
    }

    suspend fun checkForUpdate(): LatestRelease? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/repos/$GITHUB_REPO/releases/latest")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "Luma-Music")
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val release = json.decodeFromString<LatestRelease>(body)
                if (release.tagName.isNotBlank() && isNewerVersion(release.tagName, APP_VERSION)) release else null
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
