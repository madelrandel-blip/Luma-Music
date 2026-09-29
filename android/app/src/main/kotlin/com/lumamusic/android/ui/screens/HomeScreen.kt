/*
 * Luma Music
 * The Inicio (home) tab: horizontal carousels of suggested songs pulled from
 * YouTube.home() (via HomeRepository). v1 scope note: YouTube Music's home feed mixes songs,
 * albums, playlists and artists (YTItem); this screen only surfaces the SongItem entries from
 * each section (playable directly, matching Search/Favorites/Queue) and drops non-song entries,
 * since browsing into an album/playlist/artist page needs its own screens that are out of scope
 * for this pass.
 */

package com.lumamusic.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.arturo254.opentune.innertube.models.SongItem
import com.lumamusic.android.HomeSection
import com.lumamusic.android.PlayerViewModel

@Composable
fun HomeScreen(viewModel: PlayerViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        if (uiState.homeSections.isEmpty() && !uiState.isLoadingHome) {
            viewModel.loadHome()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Luma Music",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
        )

        when {
            uiState.isLoadingHome -> Box(
                modifier = Modifier.weight(1f).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            uiState.homeErrorMessage != null -> Box(
                modifier = Modifier.weight(1f).fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(uiState.homeErrorMessage.orEmpty(), color = MaterialTheme.colorScheme.error)
            }

            uiState.homeSections.isEmpty() -> Box(
                modifier = Modifier.weight(1f).fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Sin sugerencias por ahora.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }

            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(uiState.homeSections, key = { it.title }) { section ->
                    HomeSectionRow(
                        section = section,
                        onSongClick = { song ->
                            viewModel.playQueue(section.songs, section.songs.indexOf(song))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeSectionRow(section: HomeSection, onSongClick: (SongItem) -> Unit) {
    Column(modifier = Modifier.padding(top = 12.dp)) {
        Text(
            text = section.title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(section.songs, key = { it.id }) { song ->
                HomeSongCard(song = song, onClick = { onSongClick(song) })
            }
        }
    }
}

@Composable
private fun HomeSongCard(song: SongItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = song.thumbnail,
            contentDescription = null,
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(12.dp)),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = song.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = song.artists.joinToString { it.name },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
    }
}
