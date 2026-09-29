/*
 * Luma Music
 * Local favorites persistence, deliberately NOT using Room (the original database was dropped
 * on purpose when this app was scoped down to its own UI over the reused playback engine).
 * Favorited songs are stored as a JSON-encoded list in a small DataStore Preferences file --
 * SongItem is already @Serializable, so no extra mapping layer is needed.
 */

package com.lumamusic.android.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.arturo254.opentune.innertube.models.SongItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.favoritesDataStore by preferencesDataStore(name = "luma_favorites")

class FavoritesStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val favoritesKey = stringPreferencesKey("favorite_songs_json")

    val favorites: Flow<List<SongItem>> = context.favoritesDataStore.data.map { prefs ->
        val raw = prefs[favoritesKey]
        if (raw.isNullOrEmpty()) {
            emptyList()
        } else {
            runCatching { json.decodeFromString<List<SongItem>>(raw) }.getOrDefault(emptyList())
        }
    }

    suspend fun toggleFavorite(song: SongItem) {
        context.favoritesDataStore.edit { prefs ->
            val current = prefs[favoritesKey]?.let { raw ->
                runCatching { json.decodeFromString<List<SongItem>>(raw) }.getOrDefault(emptyList())
            } ?: emptyList()

            val updated = if (current.any { it.id == song.id }) {
                current.filterNot { it.id == song.id }
            } else {
                current + song
            }

            prefs[favoritesKey] = json.encodeToString(updated)
        }
    }
}
