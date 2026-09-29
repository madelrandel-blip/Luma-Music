/*
 * Luma Music
 * The new Android UI (v1): a 3-tab layout (Inicio, Buscar, Favoritos) with a persistent
 * mini-player and a full player screen — all styled to match the desktop app's rose accent.
 */

package com.lumamusic.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.lumamusic.android.ui.components.MiniPlayerBar
import com.lumamusic.android.ui.screens.FavoritesScreen
import com.lumamusic.android.ui.screens.FullPlayerScreen
import com.lumamusic.android.ui.screens.HomeScreen
import com.lumamusic.android.ui.screens.SearchScreen
import com.lumamusic.android.ui.theme.LumaMusicTheme

class MainActivity : ComponentActivity() {
    private val viewModel: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LumaMusicTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    LumaApp(viewModel)
                }
            }
        }
    }
}

private enum class LumaTab(val label: String, val icon: String) {
    HOME("Inicio", "🏠"),
    SEARCH("Buscar", "🔍"),
    FAVORITES("Favoritos", "♥"),
}

@Composable
private fun LumaApp(viewModel: PlayerViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableStateOf(LumaTab.HOME) }
    var showFullPlayer by remember { mutableStateOf(false) }

    BackHandler(enabled = showFullPlayer) { showFullPlayer = false }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    LumaTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            icon = { Text(tab.icon) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        LumaTab.HOME -> HomeScreen(viewModel)
                        LumaTab.SEARCH -> SearchScreen(viewModel)
                        LumaTab.FAVORITES -> FavoritesScreen(viewModel)
                    }
                }

                uiState.currentSong?.let { song ->
                    MiniPlayerBar(
                        song = song,
                        isPlaying = uiState.isPlaying,
                        isBuffering = uiState.isBuffering,
                        onTogglePlayPause = viewModel::togglePlayPause,
                        onClick = { showFullPlayer = true },
                    )
                }
            }
        }

        if (showFullPlayer) {
            FullPlayerScreen(
                viewModel = viewModel,
                onClose = { showFullPlayer = false },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
