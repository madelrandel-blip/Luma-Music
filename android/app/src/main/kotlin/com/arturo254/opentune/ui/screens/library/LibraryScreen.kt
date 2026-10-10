/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.arturo254.opentune.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.arturo254.opentune.constants.LibraryFilter

/**
 * "Your Library" tab. Draws its own Spotify-style header (avatar, title,
 * search, "+") and shows the mixed list for Library / Playlists / Artists /
 * Albums; the Songs, On device and Spotify chips open the existing screens.
 */
@Composable
fun LibraryScreen(navController: NavController) {
    var filterType by rememberSaveable { mutableStateOf(LibraryFilter.LIBRARY) }
    var showCreatePlaylist by remember { mutableStateOf(false) }
    val addLocalMusic = rememberAddLocalMusicLauncher(
        onImported = { filterType = LibraryFilter.ON_DEVICE },
    )

    val chips: @Composable () -> Unit = {
        YourLibraryChips(current = filterType, onSelect = { filterType = it })
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        when (filterType) {
            LibraryFilter.SONGS -> LibrarySongsScreen(
                navController,
                { filterType = LibraryFilter.LIBRARY }
            )

            LibraryFilter.ON_DEVICE -> LocalSongsScreen(
                navController,
                { filterType = LibraryFilter.LIBRARY }
            )

            LibraryFilter.SPOTIFY -> LibrarySpotifyPlaylistsScreen(
                navController = navController,
                filterContent = chips,
            )

            else -> YourLibraryList(
                navController = navController,
                filter = filterType,
                onFilterChange = { filterType = it },
                onAddLocalMusic = addLocalMusic,
            )
        }

        YourLibraryHeader(
            onCreatePlaylist = { showCreatePlaylist = true },
            onAddLocalMusic = addLocalMusic,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }

    YourLibraryCreatePlaylist(
        visible = showCreatePlaylist,
        onDismiss = { showCreatePlaylist = false },
    )
}
