package com.arturo254.opentune.library

import androidx.compose.runtime.mutableStateListOf
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class FollowedArtist(
    val id: String,
    val name: String,
    val thumbnail: String? = null,
    val followedAt: Long = System.currentTimeMillis(),
)

/**
 * Artists the user has explicitly followed/subscribed to from within the app, persisted
 * locally so the "artists" rail in the left corner and the "Más de tus artistas" row on Home
 * are driven by that choice instead of by whatever was merely listened to recently.
 *
 * When the linked YouTube account is already subscribed to an artist, [ArtistScreen] adopts
 * that into here too the first time that artist page is opened (see App.kt), so existing
 * YouTube Music subscriptions show up here without the user having to re-follow every one.
 */
object FollowedArtistsManager {
    private val file = File(System.getProperty("user.home"), ".opentune/followed_artists.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val _artists = mutableStateListOf<FollowedArtist>()

    val artists: List<FollowedArtist> get() = _artists.toList()

    init { load() }

    fun isFollowing(id: String): Boolean = _artists.any { it.id == id }

    fun follow(id: String, name: String, thumbnail: String?) {
        if (id.isBlank() || isFollowing(id)) return
        _artists.add(0, FollowedArtist(id = id, name = name, thumbnail = thumbnail))
        save()
    }

    fun unfollow(id: String) {
        _artists.removeAll { it.id == id }
        save()
    }

    fun toggle(id: String, name: String, thumbnail: String?) {
        if (isFollowing(id)) unfollow(id) else follow(id, name, thumbnail)
    }

    private fun save() {
        try {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(_artists.toList()))
        } catch (e: Exception) {
            println("[FollowedArtistsManager] Save error: ${e.message}")
        }
    }

    private fun load() {
        try {
            if (file.exists() && file.length() > 0) {
                _artists.addAll(json.decodeFromString<List<FollowedArtist>>(file.readText()))
            }
        } catch (e: Exception) {
            println("[FollowedArtistsManager] Load error: ${e.message}")
        }
    }
}
