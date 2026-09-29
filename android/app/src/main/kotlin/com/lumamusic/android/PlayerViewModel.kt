/*
 * Luma Music
 * Holds the MediaController connection to PlaybackService, the current queue/search/home state,
 * playback position, and favorites, and exposes simple actions (search, playQueue, next,
 * previous, seekTo, toggleFavorite...) for the Compose UI.
 */

package com.lumamusic.android

import android.app.Application
import android.content.ComponentName
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.arturo254.opentune.innertube.models.SongItem
import com.lumamusic.android.data.FavoritesStore
import com.lumamusic.android.data.HomeRepository
import com.lumamusic.android.data.SearchRepository
import com.lumamusic.android.playback.PlaybackService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

/** A row of songs on the Inicio tab (e.g. "Quick picks", "Mixed for you"). */
data class HomeSection(
    val title: String,
    val songs: List<SongItem>,
)

data class PlayerUiState(
    // Search tab
    val query: String = "",
    val isSearching: Boolean = false,
    val results: List<SongItem> = emptyList(),
    val errorMessage: String? = null,

    // Playback / queue
    val queue: List<SongItem> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,

    // Inicio (home) tab
    val isLoadingHome: Boolean = false,
    val homeSections: List<HomeSection> = emptyList(),
    val homeErrorMessage: String? = null,

    // Favorites
    val favorites: List<SongItem> = emptyList(),
) {
    val currentSong: SongItem? get() = queue.getOrNull(currentIndex)
    val hasNext: Boolean get() = currentIndex in queue.indices && currentIndex < queue.lastIndex
    val hasPrevious: Boolean get() = currentIndex > 0
    val favoriteIds: Set<String> get() = favorites.mapTo(HashSet(favorites.size)) { it.id }
}

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var controller: MediaController? = null
    private val favoritesStore = FavoritesStore(application)

    init {
        val sessionToken = SessionToken(
            application,
            ComponentName(application, PlaybackService::class.java),
        )
        viewModelScope.launch {
            val mediaController = MediaController.Builder(application, sessionToken).buildAsync().await()
            controller = mediaController

            _uiState.value = _uiState.value.copy(
                isPlaying = mediaController.isPlaying,
                currentIndex = mediaController.currentMediaItemIndex,
                durationMs = mediaController.duration.coerceAtLeast(0L),
            )

            mediaController.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    _uiState.value = _uiState.value.copy(
                        isBuffering = playbackState == Player.STATE_BUFFERING,
                        durationMs = mediaController.duration.coerceAtLeast(0L),
                    )
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    _uiState.value = _uiState.value.copy(
                        currentIndex = mediaController.currentMediaItemIndex,
                        positionMs = 0L,
                    )
                }
            })
        }

        // MediaController doesn't push position updates on its own; poll it at a UI-friendly
        // rate to drive the full player's seek bar. Cheap no-op while nothing is loaded.
        viewModelScope.launch {
            while (true) {
                controller?.let { mediaController ->
                    if (mediaController.mediaItemCount > 0) {
                        _uiState.value = _uiState.value.copy(
                            positionMs = mediaController.currentPosition.coerceAtLeast(0L),
                            durationMs = mediaController.duration.coerceAtLeast(0L),
                        )
                    }
                }
                delay(500)
            }
        }

        viewModelScope.launch {
            favoritesStore.favorites.collect { favorites ->
                _uiState.value = _uiState.value.copy(favorites = favorites)
            }
        }
    }

    fun onQueryChange(newQuery: String) {
        _uiState.value = _uiState.value.copy(query = newQuery)
    }

    fun search() {
        val query = _uiState.value.query.trim()
        if (query.isEmpty()) return
        _uiState.value = _uiState.value.copy(isSearching = true, errorMessage = null)
        viewModelScope.launch {
            SearchRepository.searchSongs(query)
                .onSuccess { songs ->
                    _uiState.value = _uiState.value.copy(isSearching = false, results = songs)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isSearching = false,
                        errorMessage = error.message ?: "Search failed",
                    )
                }
        }
    }

    fun loadHome() {
        if (_uiState.value.isLoadingHome) return
        _uiState.value = _uiState.value.copy(isLoadingHome = true, homeErrorMessage = null)
        viewModelScope.launch {
            HomeRepository.loadHome()
                .onSuccess { homePage ->
                    val sections = homePage.sections.mapNotNull { section ->
                        val songs = section.items.filterIsInstance<SongItem>()
                        if (songs.isEmpty()) null else HomeSection(section.title, songs)
                    }
                    _uiState.value = _uiState.value.copy(isLoadingHome = false, homeSections = sections)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingHome = false,
                        homeErrorMessage = error.message ?: "No se pudo cargar el inicio",
                    )
                }
        }
    }

    /** Loads [songs] as the whole playback queue and starts playing at [startIndex]. */
    fun playQueue(songs: List<SongItem>, startIndex: Int) {
        val mediaController = controller ?: return
        if (songs.isEmpty() || startIndex !in songs.indices) return
        val mediaItems = songs.map { it.toMediaItem() }
        mediaController.setMediaItems(mediaItems, startIndex, 0L)
        mediaController.prepare()
        mediaController.play()
        _uiState.value = _uiState.value.copy(
            queue = songs,
            currentIndex = startIndex,
            positionMs = 0L,
        )
    }

    fun togglePlayPause() {
        val mediaController = controller ?: return
        if (mediaController.isPlaying) mediaController.pause() else mediaController.play()
    }

    fun next() {
        controller?.let { mediaController ->
            if (mediaController.hasNextMediaItem()) mediaController.seekToNextMediaItem()
        }
    }

    fun previous() {
        controller?.let { mediaController ->
            if (mediaController.hasPreviousMediaItem()) mediaController.seekToPreviousMediaItem()
        }
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        _uiState.value = _uiState.value.copy(positionMs = positionMs)
    }

    fun toggleFavorite(song: SongItem) {
        viewModelScope.launch { favoritesStore.toggleFavorite(song) }
    }

    override fun onCleared() {
        controller?.release()
        controller = null
        super.onCleared()
    }
}

private fun SongItem.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(id)
        .setUri(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artists.joinToString { it.name })
                .setArtworkUri(thumbnail.toUri())
                .build()
        )
        .build()
