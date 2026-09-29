/*
 * Luma Music
 * Holds the MediaController connection to PlaybackService, current queue/search state, and
 * exposes simple actions (search, play, togglePlay, next, previous) for the Compose UI.
 */

package com.lumamusic.android

import android.app.Application
import android.content.ComponentName
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.arturo254.opentune.innertube.models.SongItem
import com.lumamusic.android.data.SearchRepository
import com.lumamusic.android.playback.PlaybackService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

data class PlayerUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val results: List<SongItem> = emptyList(),
    val errorMessage: String? = null,
    val currentSong: SongItem? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var controller: MediaController? = null

    init {
        val sessionToken = SessionToken(
            application,
            ComponentName(application, PlaybackService::class.java),
        )
        viewModelScope.launch {
            controller = MediaController.Builder(application, sessionToken).buildAsync().await()
            controller?.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    _uiState.value = _uiState.value.copy(
                        isBuffering = playbackState == Player.STATE_BUFFERING,
                    )
                }
            })
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

    fun play(song: SongItem) {
        val mediaController = controller ?: return
        val mediaItem = MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(song.id)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artists.joinToString { it.name })
                    .setArtworkUri(song.thumbnail.toUri())
                    .build()
            )
            .build()
        mediaController.setMediaItem(mediaItem)
        mediaController.prepare()
        mediaController.play()
        _uiState.value = _uiState.value.copy(currentSong = song)
    }

    fun togglePlayPause() {
        val mediaController = controller ?: return
        if (mediaController.isPlaying) mediaController.pause() else mediaController.play()
    }

    override fun onCleared() {
        controller?.release()
        controller = null
        super.onCleared()
    }
}
