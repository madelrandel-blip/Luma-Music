/*
 * Luma Music
 * Thin wrapper around the innertube module's YouTube.search — the reused "YouTube Music
 * client" piece of the playback engine, kept independent of any local database.
 */

package com.lumamusic.android.data

import com.arturo254.opentune.innertube.YouTube
import com.arturo254.opentune.innertube.models.SongItem

object SearchRepository {
    suspend fun searchSongs(query: String): Result<List<SongItem>> =
        YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).map { result ->
            result.items.filterIsInstance<SongItem>()
        }
}
