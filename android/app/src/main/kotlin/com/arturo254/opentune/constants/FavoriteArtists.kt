/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.arturo254.opentune.constants

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/** The artists picked during onboarding; Home is built only from these. */
val FavoriteArtistsKey = stringPreferencesKey("favoriteArtists")

/** Set once the onboarding picker has been shown automatically. */
val FavoriteArtistsPromptShownKey = booleanPreferencesKey("favoriteArtistsPromptShown")

const val FavoriteArtistsRequired = 4

data class FavoriteArtist(
    val id: String,
    val name: String,
    val thumbnail: String?,
)

private const val FieldSeparator = '\u001F'
private const val RecordSeparator = '\u001E'

fun List<FavoriteArtist>.encodeFavoriteArtists(): String =
    joinToString(RecordSeparator.toString()) { artist ->
        listOf(artist.id, artist.name, artist.thumbnail.orEmpty())
            .joinToString(FieldSeparator.toString()) { it.replace(FieldSeparator, ' ').replace(RecordSeparator, ' ') }
    }

fun String?.decodeFavoriteArtists(): List<FavoriteArtist> =
    this.orEmpty()
        .split(RecordSeparator)
        .mapNotNull { record ->
            val fields = record.split(FieldSeparator)
            val id = fields.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            FavoriteArtist(
                id = id,
                name = fields.getOrNull(1).orEmpty(),
                thumbnail = fields.getOrNull(2)?.takeIf { it.isNotBlank() },
            )
        }
