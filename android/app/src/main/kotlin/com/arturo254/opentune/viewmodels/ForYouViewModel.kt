/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.arturo254.opentune.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arturo254.opentune.R
import com.arturo254.opentune.constants.FavoriteArtist
import com.arturo254.opentune.constants.FavoriteArtistsKey
import com.arturo254.opentune.constants.decodeFavoriteArtists
import com.arturo254.opentune.innertube.YouTube
import com.arturo254.opentune.innertube.models.AlbumItem
import com.arturo254.opentune.innertube.models.ArtistItem
import com.arturo254.opentune.innertube.models.PlaylistItem
import com.arturo254.opentune.innertube.models.SongItem
import com.arturo254.opentune.innertube.models.YTItem
import com.arturo254.opentune.innertube.pages.ArtistPage
import com.arturo254.opentune.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ForYouSection(
    val key: String,
    val title: String,
    val subtitle: String? = null,
    val items: List<YTItem>,
)

/**
 * Builds Home only from the artists picked during onboarding: their top
 * songs, albums, playlists and similar artists, plus a mixed "For you" row.
 */
@HiltViewModel
class ForYouViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val favoriteArtists: StateFlow<List<FavoriteArtist>?> =
        context.dataStore.data
            .map { it[FavoriteArtistsKey].decodeFavoriteArtists() }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _sections = MutableStateFlow<List<ForYouSection>>(emptyList())
    val sections = _sections.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _hasError = MutableStateFlow(false)
    val hasError = _hasError.asStateFlow()

    private var loadedFor: List<FavoriteArtist>? = null

    init {
        viewModelScope.launch {
            favoriteArtists.collect { artists ->
                if (artists != null && artists != loadedFor) load(artists)
            }
        }
    }

    fun refresh() {
        favoriteArtists.value?.let { load(it) }
    }

    private fun load(artists: List<FavoriteArtist>) {
        loadedFor = artists
        if (artists.isEmpty()) {
            _sections.value = emptyList()
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _hasError.value = false

            val pages = artists.map { artist ->
                async { artist to YouTube.artist(artist.id).getOrNull() }
            }.awaitAll()

            if (pages.all { it.second == null }) {
                _hasError.value = true
            }
            _sections.value = buildSections(pages)
            _isLoading.value = false
        }
    }

    private fun buildSections(pages: List<Pair<FavoriteArtist, ArtistPage?>>): List<ForYouSection> {
        val songsByArtist = pages.map { (_, page) ->
            page?.sections.orEmpty().flatMap { it.items }.filterIsInstance<SongItem>()
        }

        return buildList {
            // "For you": the artists' top songs, interleaved.
            val mixed = buildList {
                val maxSize = songsByArtist.maxOfOrNull { it.size } ?: 0
                for (i in 0 until maxSize) {
                    songsByArtist.forEach { songs -> songs.getOrNull(i)?.let(::add) }
                }
            }.distinctBy { it.id }.take(20)
            if (mixed.isNotEmpty()) {
                add(ForYouSection("for_you", context.getString(R.string.home_for_you), items = mixed))
            }

            pages.forEach { (artist, page) ->
                if (page == null) return@forEach
                val name = page.artist.title.ifBlank { artist.name }
                val items = page.sections.flatMap { it.items }

                val songs = items.filterIsInstance<SongItem>().distinctBy { it.id }
                if (songs.isNotEmpty()) {
                    add(
                        ForYouSection(
                            key = "songs_${artist.id}",
                            title = context.getString(R.string.home_because_you_like, name),
                            items = songs,
                        )
                    )
                }
                val albums = items.filterIsInstance<AlbumItem>().distinctBy { it.id }
                if (albums.isNotEmpty()) {
                    add(
                        ForYouSection(
                            key = "albums_${artist.id}",
                            title = context.getString(R.string.home_albums_by, name),
                            items = albums,
                        )
                    )
                }
                val playlists = items.filterIsInstance<PlaylistItem>().distinctBy { it.id }
                if (playlists.isNotEmpty()) {
                    add(
                        ForYouSection(
                            key = "playlists_${artist.id}",
                            title = context.getString(R.string.home_playlists_with, name),
                            items = playlists,
                        )
                    )
                }
                val similar = items.filterIsInstance<ArtistItem>()
                    .filter { it.id != artist.id }
                    .distinctBy { it.id }
                if (similar.isNotEmpty()) {
                    add(
                        ForYouSection(
                            key = "similar_${artist.id}",
                            title = context.getString(R.string.home_similar_to, name),
                            items = similar,
                        )
                    )
                }
            }
        }
    }
}
