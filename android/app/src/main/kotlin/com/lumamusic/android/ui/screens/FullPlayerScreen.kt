/*
 * Luma Music
 * The full player screen: big art, title/artist, a seek bar, play/pause/next/previous and a
 * favorite toggle. Opened by tapping the mini-player bar; closed with the down-chevron or the
 * system back gesture.
 */

package com.lumamusic.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lumamusic.android.PlayerViewModel

@Composable
fun FullPlayerScreen(
    viewModel: PlayerViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val song = uiState.currentSong

    LaunchedEffect(song) {
        if (song == null) onClose()
    }
    if (song == null) return

    // Local drag state for the seek bar: while the user is dragging, show the dragged position
    // instead of the (still ticking) real playback position, and only actually seek on release.
    var dragPositionMs by remember { mutableStateOf<Float?>(null) }
    val durationMs = uiState.durationMs.coerceAtLeast(0L)
    val sliderMax = if (durationMs > 0L) durationMs.toFloat() else 1f
    val displayedPositionMs = (dragPositionMs ?: uiState.positionMs.toFloat()).coerceIn(0f, sliderMax)

    Surface(modifier = modifier, color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onClose) {
                    Text("⌄", fontSize = 28.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            AsyncImage(
                model = song.thumbnail,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp)),
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = song.title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = song.artists.joinToString { it.name },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Slider(
                value = displayedPositionMs,
                onValueChange = { dragPositionMs = it },
                onValueChangeFinished = {
                    dragPositionMs?.let { viewModel.seekTo(it.toLong()) }
                    dragPositionMs = null
                },
                valueRange = 0f..sliderMax,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(formatMs(displayedPositionMs.toLong()), style = MaterialTheme.typography.labelSmall)
                Text(formatMs(durationMs), style = MaterialTheme.typography.labelSmall)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(32.dp),
            ) {
                IconButton(onClick = viewModel::previous, enabled = uiState.hasPrevious) {
                    Text("⏮", fontSize = 28.sp)
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp),
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        IconButton(onClick = viewModel::togglePlayPause) {
                            if (uiState.isBuffering) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Text(text = if (uiState.isPlaying) "⏸" else "▶", fontSize = 28.sp)
                            }
                        }
                    }
                }
                IconButton(onClick = viewModel::next, enabled = uiState.hasNext) {
                    Text("⏭", fontSize = 28.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val isFavorite = uiState.favoriteIds.contains(song.id)
            IconButton(onClick = { viewModel.toggleFavorite(song) }) {
                Text(
                    text = if (isFavorite) "♥" else "♡",
                    fontSize = 24.sp,
                    color = if (isFavorite) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
