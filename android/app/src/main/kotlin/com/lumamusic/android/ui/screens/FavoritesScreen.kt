/*
 * Luma Music
 * The Favoritos tab: a list of favorited songs (stored locally via FavoritesStore, no Room).
 * Tapping a favorite loads the favorites list as the playback queue, starting at that song.
 */

package com.lumamusic.android.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lumamusic.android.PlayerViewModel
import com.lumamusic.android.ui.components.SongRow

@Composable
fun FavoritesScreen(viewModel: PlayerViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val favorites = uiState.favorites

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Favoritos",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
        )

        if (favorites.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Aún no tienes canciones favoritas.\nToca el corazón en el reproductor para agregar una.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp),
            ) {
                items(favorites, key = { it.id }) { song ->
                    SongRow(
                        song = song,
                        isCurrent = uiState.currentSong?.id == song.id,
                        isPlaying = uiState.isPlaying && uiState.currentSong?.id == song.id,
                        onClick = {
                            viewModel.playQueue(favorites, favorites.indexOf(song))
                        },
                    )
                }
            }
        }
    }
}
