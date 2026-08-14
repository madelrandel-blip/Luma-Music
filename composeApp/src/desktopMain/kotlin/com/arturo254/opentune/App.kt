package com.arturo254.opentune

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode as InfiniteRepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.arturo254.opentune.innertube.YouTube
import com.arturo254.opentune.innertube.models.AlbumItem
import com.arturo254.opentune.innertube.models.Artist
import com.arturo254.opentune.innertube.models.ArtistItem
import com.arturo254.opentune.innertube.models.PlaylistItem
import com.arturo254.opentune.innertube.models.SongItem
import com.arturo254.opentune.innertube.models.YouTubeLocale
import com.arturo254.opentune.innertube.models.YTItem
import com.arturo254.opentune.innertube.pages.AlbumPage
import com.arturo254.opentune.innertube.pages.ArtistPage
import com.arturo254.opentune.innertube.pages.PlaylistPage
import com.arturo254.opentune.innertube.pages.SearchSummaryPage
import com.arturo254.opentune.innertube.utils.completed
import com.arturo254.opentune.library.CacheMetadataManager
import com.arturo254.opentune.library.DownloadsManager
import com.arturo254.opentune.library.LikedSongsManager
import com.arturo254.opentune.library.ListenHistoryManager
import com.arturo254.opentune.library.LocalSongsManager
import com.arturo254.opentune.library.PlaylistsManager
import com.arturo254.opentune.library.SearchHistoryManager
import com.arturo254.opentune.player.PlayerManager
import com.arturo254.opentune.player.RepeatMode
import com.arturo254.opentune.ui.EqualizerBars
import com.arturo254.opentune.ui.NowPlayingState
import com.arturo254.opentune.ui.theme.LumaMusicTheme
import java.awt.Component
import java.io.File
import java.util.Locale
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import org.cef.browser.CefBrowser
import kotlinx.coroutines.Dispatchers
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class Screen(val labelKey: String, val icon: ImageVector) {
    val label: String get() = tr(labelKey)
    data object Home : Screen("Inicio", Icons.Filled.Home)
    data object Search : Screen("Buscar", Icons.Filled.Search)
    data object Explore : Screen("Explorar", Icons.Filled.Explore)
    data object Library : Screen("Biblioteca", Icons.Filled.LibraryMusic)
    data object Settings : Screen("Ajustes", Icons.Filled.Settings)
}

sealed class DetailScreen {
    abstract val title: String

    data class Album(
        val browseId: String,
        override val title: String,
        val thumbnail: String?,
        val artists: List<com.arturo254.opentune.innertube.models.Artist>?
    ) : DetailScreen()

    data class Playlist(
        val playlistId: String,
        override val title: String,
        val thumbnail: String?
    ) : DetailScreen()

    data class Artist(
        val browseId: String,
        override val title: String,
        val thumbnail: String?
    ) : DetailScreen()

    data class LocalPlaylist(
        val playlistId: String,
        override val title: String
    ) : DetailScreen()
}

sealed class SettingsSubScreen(val labelKey: String) {
    val label: String get() = tr(labelKey)
    data object Main : SettingsSubScreen("Ajustes")
    data object Appearance : SettingsSubScreen("Apariencia")
    data object PalettePicker : SettingsSubScreen("Paleta de colores")
    data object PlayerAudio : SettingsSubScreen("Reproductor y audio")
    data object Storage : SettingsSubScreen("Almacenamiento")
    data object Privacy : SettingsSubScreen("Privacidad")
    data object Content : SettingsSubScreen("Contenido")
    data object Account : SettingsSubScreen("Cuenta de YouTube")
    data object About : SettingsSubScreen("Acerca de")
}

@Composable
fun App() {
    LumaMusicTheme {
        var updateAvailable by remember { mutableStateOf<LatestRelease?>(null) }
        var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
        var settingsSubScreen by remember { mutableStateOf<SettingsSubScreen>(SettingsSubScreen.Main) }
        var detailScreen by remember { mutableStateOf<DetailScreen?>(null) }
        var searchQuery by remember { mutableStateOf("") }
        var searchFieldFocused by remember { mutableStateOf(false) }
        var queueVisible by remember { mutableStateOf(false) }
        var lyricsVisible by remember { mutableStateOf(false) }

        // Hoisted search state — survives navigation
        var searchResults by remember { mutableStateOf<SearchSummaryPage?>(null) }
        var searchSongs by remember { mutableStateOf<List<SongItem>>(emptyList()) }
        var searchLoading by remember { mutableStateOf(false) }
        var searchError by remember { mutableStateOf<String?>(null) }
        var searchHasQuery by remember { mutableStateOf(false) }
        var lastSearchedQuery by remember { mutableStateOf("") }
        val searchScrollState = rememberLazyListState()

        // Debounced auto-search: waits 400ms after last keystroke
        LaunchedEffect(searchQuery) {
            if (searchQuery.isBlank()) {
                searchHasQuery = false
                searchResults = null
                searchSongs = emptyList()
                lastSearchedQuery = ""
                return@LaunchedEffect
            }
            searchHasQuery = true
            kotlinx.coroutines.delay(400)
            // Only fire if query actually changed since last search
            if (searchQuery != lastSearchedQuery) {
                searchLoading = true
                searchError = null
                coroutineScope {
                    val summaryDeferred = async { YouTube.searchSummary(searchQuery) }
                    val songsDeferred = async { YouTube.search(searchQuery, YouTube.SearchFilter.FILTER_SONG) }
                    summaryDeferred.await().onSuccess { page ->
                        // Only apply if still the latest query
                        if (searchQuery == lastSearchedQuery || page.summaries.isNotEmpty()) {
                            searchResults = page
                            lastSearchedQuery = searchQuery
                        }
                    }.onFailure { e ->
                        searchError = mapError(e)
                    }
                    songsDeferred.await().onSuccess { result ->
                        if (searchQuery == lastSearchedQuery || result.items.isNotEmpty()) {
                            searchSongs = result.items.filterIsInstance<SongItem>()
                        }
                    }.onFailure { e ->
                        searchError = mapError(e)
                    }
                }
                searchLoading = false
            }
        }

        LaunchedEffect(DesktopPreferences.contentLanguage) {
            val locale = Locale.getDefault()
            YouTube.locale = YouTubeLocale(
                gl = locale.country.takeIf { it.length == 2 } ?: "US",
                hl = I18n.current()
            )
        }

        // Keep NowPlayingState in sync with PlayerManager
        LaunchedEffect(Unit) {
            NowPlayingState.update()
            PlayerManager.addListener { NowPlayingState.update() }
        }

        // Check GitHub for a newer version on startup
        LaunchedEffect(Unit) {
            updateAvailable = UpdateChecker.checkForUpdate()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .onPreviewKeyEvent { keyEvent ->
                    if (searchFieldFocused) return@onPreviewKeyEvent false
                    if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val ctrl = keyEvent.isCtrlPressed
                    when (keyEvent.key) {
                        Key.Spacebar -> { PlayerManager.playPause(); true }
                        Key.M -> { PlayerManager.toggleMute(); true }
                        Key.L -> { PlayerManager.currentSong?.let { LikedSongsManager.toggleLike(it) }; true }
                        Key.R -> { PlayerManager.toggleRepeatMode(); true }
                        Key.Q -> { queueVisible = !queueVisible; true }
                        Key.DirectionRight -> {
                            if (ctrl) PlayerManager.next() else PlayerManager.seekTo(PlayerManager.position + 5000)
                            true
                        }
                        Key.DirectionLeft -> {
                            if (ctrl) PlayerManager.previous() else PlayerManager.seekTo(PlayerManager.position - 5000)
                            true
                        }
                        Key.DirectionUp -> {
                            if (ctrl) { PlayerManager.setVolume(PlayerManager.volume + 0.05f); PlayerManager.persistVolume(); true } else false
                        }
                        Key.DirectionDown -> {
                            if (ctrl) { PlayerManager.setVolume(PlayerManager.volume - 0.05f); PlayerManager.persistVolume(); true } else false
                        }
                        else -> false
                    }
                }
        ) {
            Row(modifier = Modifier.weight(1f)) {
                AppNavigationRail(
                    currentScreen = currentScreen,
                    onScreenSelected = {
                        currentScreen = it
                        detailScreen = null
                        if (it is Screen.Settings) settingsSubScreen = SettingsSubScreen.Main
                    }
                )

                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    val detail = detailScreen
                    if (currentScreen is Screen.Settings && settingsSubScreen !is SettingsSubScreen.Main) {
                        SettingsTopBar(
                            title = (settingsSubScreen as SettingsSubScreen).label,
                            onBack = { settingsSubScreen = SettingsSubScreen.Main }
                        )
                    } else if (detail != null) {
                        SettingsTopBar(
                            title = detail.title,
                            onBack = { detailScreen = null }
                        )
                    } else {
                        AppTopBar(
                            searchQuery = searchQuery,
                            onSearchQueryChange = { searchQuery = it },
                            onSearch = {
                                if (searchQuery.isNotBlank()) SearchHistoryManager.add(searchQuery)
                            },
                            onSearchFieldFocusChange = { searchFieldFocused = it }
                        )
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        if (detail != null) {
                            when (detail) {
                                is DetailScreen.Album -> AlbumScreen(
                                    browseId = detail.browseId,
                                    fallbackTitle = detail.title,
                                    fallbackThumbnail = detail.thumbnail,
                                    fallbackArtists = detail.artists
                                )
                                is DetailScreen.Playlist -> PlaylistScreen(
                                    playlistId = detail.playlistId,
                                    fallbackTitle = detail.title,
                                    fallbackThumbnail = detail.thumbnail
                                )
                                is DetailScreen.Artist -> ArtistScreen(
                                    browseId = detail.browseId,
                                    fallbackTitle = detail.title,
                                    fallbackThumbnail = detail.thumbnail,
                                    onOpenDetail = { detailScreen = it }
                                )
                                is DetailScreen.LocalPlaylist -> LocalPlaylistScreen(
                                    playlistId = detail.playlistId,
                                    onBack = { detailScreen = null }
                                )
                            }
                        } else {
                            when (currentScreen) {
                            is Screen.Home -> HomeScreen(onOpenDetail = { detailScreen = it })
                            is Screen.Search -> SearchScreen(
                                query = searchQuery,
                                results = searchResults,
                                songs = searchSongs,
                                loading = searchLoading,
                                error = searchError,
                                hasQuery = searchHasQuery,
                                history = SearchHistoryManager.entries,
                                onHistoryClick = { searchQuery = it },
                                onRemoveHistory = { SearchHistoryManager.remove(it) },
                                onClearHistory = { SearchHistoryManager.clear() },
                                onOpenDetail = { detailScreen = it },
                                scrollState = searchScrollState
                            )
                            is Screen.Explore -> ExploreScreen(onOpenDetail = { detailScreen = it })
                            is Screen.Library -> LibraryScreen(
                                onOpenDetail = { detailScreen = it },
                                onOpenAccount = {
                                    currentScreen = Screen.Settings
                                    settingsSubScreen = SettingsSubScreen.Account
                                }
                            )
                            is Screen.Settings -> {
                                when (settingsSubScreen) {
                                    is SettingsSubScreen.Main -> SettingsScreen(
                                        onNavigate = { settingsSubScreen = it }
                                    )
                                    is SettingsSubScreen.Appearance -> AppearanceSettings(
                                        onBack = { settingsSubScreen = SettingsSubScreen.Main },
                                        onNavigate = { settingsSubScreen = it }
                                    )
                                    is SettingsSubScreen.PalettePicker -> PalettePickerScreen(onBack = { settingsSubScreen = SettingsSubScreen.Appearance })
                                    is SettingsSubScreen.PlayerAudio -> PlayerAudioSettings(onBack = { settingsSubScreen = SettingsSubScreen.Main })
                                    is SettingsSubScreen.Storage -> StorageSettings(onBack = { settingsSubScreen = SettingsSubScreen.Main })
                                    is SettingsSubScreen.Privacy -> PrivacySettings(onBack = { settingsSubScreen = SettingsSubScreen.Main })
                                    is SettingsSubScreen.Content -> ContentSettings(onBack = { settingsSubScreen = SettingsSubScreen.Main })
                                    is SettingsSubScreen.Account -> AccountSettings(onBack = { settingsSubScreen = SettingsSubScreen.Main })
                                    is SettingsSubScreen.About -> AboutScreen(onBack = { settingsSubScreen = SettingsSubScreen.Main })
                                }
                            }
                        }
                    }
                }
            }
        }

        updateAvailable?.let { release ->
            AlertDialog(
                onDismissRequest = { updateAvailable = null },
                title = { Text(tr("Nueva versión disponible")) },
                text = { Text(tr("Hay una versión más reciente de Luma Music disponible ({0}). ¿Quieres actualizar ahora?", release.tagName)) },
                confirmButton = {
                    TextButton(onClick = {
                        try {
                            java.awt.Desktop.getDesktop().browse(java.net.URI(release.htmlUrl))
                        } catch (_: Exception) {}
                        updateAvailable = null
                    }) { Text(tr("Actualizar")) }
                },
                dismissButton = {
                    TextButton(onClick = { updateAvailable = null }) { Text(tr("Seguir con la versión actual")) }
                }
            )
        }

        PlayerBar(
            onToggleQueue = { queueVisible = !queueVisible },
            onToggleLyrics = { lyricsVisible = !lyricsVisible }
        )

        if (queueVisible) {
            QueueDialog(onDismiss = { queueVisible = false })
        }
        if (lyricsVisible) {
            LyricsScreen(onDismiss = { lyricsVisible = false })
        }
    }
}
}

@Composable
fun AppNavigationRail(currentScreen: Screen, onScreenSelected: (Screen) -> Unit) {
    val screens = listOf(Screen.Home, Screen.Search, Screen.Explore, Screen.Library, Screen.Settings)

    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        header = {
            Text(
                "Luma Music",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(12.dp)
            )
        }
    ) {
        screens.forEach { screen ->
            NavigationRailItem(
                selected = currentScreen == screen,
                onClick = { onScreenSelected(screen) },
                icon = { Icon(screen.icon, contentDescription = screen.label) },
                label = { Text(screen.label, fontSize = 11.sp) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                )
            )
        }
    }
}

@Composable
fun AppTopBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearch: () -> Unit = {},
    onSearchFieldFocusChange: (Boolean) -> Unit = {}
) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(64.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text(tr("Buscar música..."), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .onFocusChanged { onSearchFieldFocusChange(it.isFocused) }
            )
        }
    }
}

@Composable
fun SettingsTopBar(title: String, onBack: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(64.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = tr("Atrás"), tint = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun PlayerBar(
    onToggleQueue: () -> Unit = {},
    onToggleLyrics: () -> Unit = {}
) {
    var song by remember { mutableStateOf<SongItem?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var repeatMode by remember { mutableStateOf(RepeatMode.SEQUENTIAL) }
    var isLiked by remember { mutableStateOf(false) }
    var isDownloaded by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var volume by remember { mutableFloatStateOf(1f) }
    var isMuted by remember { mutableStateOf(false) }
    var addToPlaylistOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(250)
            val s = PlayerManager.currentSong
            song = s
            isPlaying = PlayerManager.isPlaying
            isLoading = PlayerManager.isLoading
            position = PlayerManager.position
            duration = PlayerManager.duration
            repeatMode = PlayerManager.repeatMode
            isLiked = s?.let { LikedSongsManager.isLiked(it.id) } == true
            isDownloaded = s?.let { DownloadsManager.isDownloaded(it.id) } == true
            volume = PlayerManager.volume
            isMuted = PlayerManager.isMuted
            progress = if (duration > 0) (position.toFloat() / duration.toFloat()) else 0f
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth().height(80.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 4.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Slider(
                value = progress,
                onValueChange = { pos ->
                    if (duration > 0) PlayerManager.seekTo((pos * duration).toLong())
                },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            )

            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val current = song
                if (current != null) {
                    if (current.thumbnail.isBlank()) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        AsyncImage(
                            model = current.thumbnail,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            current.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            current.artists.joinToString { it.name },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { PlayerManager.seekTo((position - 10000).coerceAtLeast(0)) }) {
                        Icon(Icons.Filled.Replay10, contentDescription = tr("Retroceder 10s"), tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { PlayerManager.previous() }) {
                        Icon(Icons.Filled.SkipPrevious, contentDescription = tr("Anterior"), tint = MaterialTheme.colorScheme.onSurface)
                    }
                    FilledIconButton(
                        onClick = { PlayerManager.playPause() },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.size(48.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) tr("Pausa") else tr("Reproducir")
                            )
                        }
                    }
                    IconButton(onClick = { PlayerManager.next() }) {
                        Icon(Icons.Filled.SkipNext, contentDescription = tr("Siguiente"), tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { PlayerManager.seekTo((position + 30000).coerceAtMost(duration)) }) {
                        Icon(Icons.Filled.Forward30, contentDescription = tr("Adelantar 30s"), tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { PlayerManager.toggleRepeatMode() }) {
                        Icon(
                            when (repeatMode) {
                                RepeatMode.SEQUENTIAL -> Icons.Filled.Repeat
                                RepeatMode.SHUFFLE -> Icons.Filled.Shuffle
                                RepeatMode.LOOP -> Icons.Filled.RepeatOne
                            },
                            contentDescription = when (repeatMode) {
                                RepeatMode.SEQUENTIAL -> tr("Secuencial")
                                RepeatMode.SHUFFLE -> tr("Aleatorio")
                                RepeatMode.LOOP -> tr("Repetir")
                            },
                            tint = if (repeatMode == RepeatMode.SEQUENTIAL) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onToggleQueue) {
                        Icon(Icons.Filled.QueueMusic, contentDescription = tr("Cola"), tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = onToggleLyrics) {
                        Icon(Icons.Filled.Lyrics, contentDescription = tr("Letras"), tint = MaterialTheme.colorScheme.onSurface)
                    }
                }

                if (song != null) {
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        "${formatTime(position)} / ${formatTime(duration)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = {
                        song?.let { LikedSongsManager.toggleLike(it) }
                    }) {
                        Icon(
                            if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = tr("Me gusta"),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (song?.id?.startsWith("local:") != true) {
                        IconButton(onClick = {
                            song?.let { s ->
                                if (!isDownloaded && !isDownloading) {
                                    isDownloading = true
                                    PlayerManager.downloadSong(s)
                                }
                            }
                        }) {
                            Icon(
                                when {
                                    isDownloaded -> Icons.Filled.DownloadDone
                                    isDownloading -> Icons.Filled.Downloading
                                    else -> Icons.Filled.Download
                                },
                                contentDescription = tr("Descargar"),
                                tint = when {
                                    isDownloaded -> MaterialTheme.colorScheme.primary
                                    isDownloading -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                    IconButton(onClick = { addToPlaylistOpen = true }) {
                        Icon(
                            Icons.Filled.PlaylistAdd,
                            contentDescription = tr("Añadir a lista"),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                VolumeControl(volume = volume, isMuted = isMuted)
            }
        }
    }

    if (addToPlaylistOpen && song != null) {
        AddToPlaylistDialog(song = song!!, onDismiss = { addToPlaylistOpen = false })
    }
}

@Composable
fun VolumeControl(volume: Float, isMuted: Boolean) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(volume) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val icon = when {
            isMuted || effectiveVolumeNow(volume, isMuted, dragging, dragValue) <= 0f -> Icons.Filled.VolumeOff
            effectiveVolumeNow(volume, isMuted, dragging, dragValue) < 0.5f -> Icons.Filled.VolumeDown
            else -> Icons.Filled.VolumeUp
        }
        IconButton(onClick = { PlayerManager.toggleMute() }) {
            Icon(icon, contentDescription = tr("Volumen"), tint = MaterialTheme.colorScheme.onSurface)
        }
        Slider(
            value = if (dragging) dragValue else if (isMuted) 0f else volume,
            onValueChange = {
                dragging = true
                dragValue = it
                PlayerManager.setVolume(it)
            },
            onValueChangeFinished = {
                dragging = false
                PlayerManager.persistVolume()
            },
            modifier = Modifier.width(130.dp).height(34.dp)
        )
    }
}

private fun effectiveVolumeNow(
    volume: Float,
    isMuted: Boolean,
    dragging: Boolean,
    dragValue: Float
): Float = if (dragging) dragValue else if (isMuted) 0f else volume

@Composable
fun QueueDialog(onDismiss: () -> Unit) {
    var queue by remember { mutableStateOf(PlayerManager.queue.toList()) }
    var currentIndex by remember { mutableStateOf(PlayerManager.currentIndex) }
    var isPlaying by remember { mutableStateOf(PlayerManager.isPlaying) }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(400)
            val q = PlayerManager.queue.toList()
            if (q != queue) queue = q
            currentIndex = PlayerManager.currentIndex
            isPlaying = PlayerManager.isPlaying
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .width(520.dp)
                .heightIn(min = 320.dp, max = 600.dp)
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Escape) {
                        onDismiss(); true
                    } else false
                }
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        tr("Cola ({0})", queue.size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    if (queue.isNotEmpty()) {
                        TextButton(onClick = { PlayerManager.clearQueue() }) {
                            Text(tr("Vaciar"), color = MaterialTheme.colorScheme.error)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = tr("Cerrar"))
                    }
                }

                if (queue.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(tr("La cola está vacía"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        itemsIndexed(queue) { index, song ->
                            val isCurrent = index == currentIndex
                            val nowPlayingId = PlayerManager.currentSong?.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                        else Color.Transparent
                                    )
                                    .clickable { PlayerManager.jumpToIndex(index) }
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(40.dp)) {
                                    if (song.thumbnail.isNotBlank()) {
                                        AsyncImage(
                                            model = song.thumbnail,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(6.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Filled.MusicNote,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    if (song.id == nowPlayingId) {
                                        Row(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(3.dp)
                                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 3.dp, vertical = 2.dp)
                                        ) {
                                            EqualizerBars(
                                                color = MaterialTheme.colorScheme.primary,
                                                animated = isPlaying,
                                                maxHeight = 9.dp
                                            )
                                        }
                                    }
                                }
                                Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                                    Text(
                                        song.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                    Text(
                                        song.artists.joinToString { it.name },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { PlayerManager.removeFromQueue(index) }) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = tr("Quitar de la cola"),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

sealed interface LyricsUiState {
    data object Loading : LyricsUiState
    data object NoLyrics : LyricsUiState
    data class Plain(val text: String) : LyricsUiState
    data class Synced(val lines: List<LyricLine>) : LyricsUiState
}

@Composable
fun LyricsScreen(onDismiss: () -> Unit) {
    var songId by remember { mutableStateOf<String?>(null) }
    var songInfo by remember { mutableStateOf(PlayerManager.currentSong) }
    var state by remember { mutableStateOf<LyricsUiState>(LyricsUiState.Loading) }
    var position by remember { mutableLongStateOf(PlayerManager.position) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(400)
            val s = PlayerManager.currentSong
            if (s?.id != songId) {
                songId = s?.id
                songInfo = s
                state = LyricsUiState.Loading
                if (s != null) {
                    val resp = LyricsManager.fetchLyrics(s.artists.joinToString { it.name }, s.title)
                    state = when {
                        resp == null -> LyricsUiState.NoLyrics
                        !resp.syncedLyrics.isNullOrBlank() -> {
                            val lines = LyricsManager.parseSynced(resp.syncedLyrics)
                            if (lines.isNotEmpty()) LyricsUiState.Synced(lines) else LyricsUiState.NoLyrics
                        }
                        !resp.plainLyrics.isNullOrBlank() -> LyricsUiState.Plain(resp.plainLyrics)
                        else -> LyricsUiState.NoLyrics
                    }
                } else {
                    state = LyricsUiState.NoLyrics
                }
            }
            position = PlayerManager.position
        }
    }

    val currentLine = (state as? LyricsUiState.Synced)?.let { synced ->
        synced.lines.indexOfLast { it.timeMs <= position }
    } ?: -1

    LaunchedEffect(currentLine) {
        if (currentLine >= 0) {
            listState.animateScrollToItem((currentLine - 2).coerceAtLeast(0))
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .width(620.dp)
                .heightIn(min = 320.dp, max = 680.dp)
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Escape) {
                        onDismiss(); true
                    } else false
                }
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            tr("Letras"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        songInfo?.let { info ->
                            Text(
                                "${info.title} • ${info.artists.joinToString { it.name }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = tr("Cerrar"))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                when (val st = state) {
                    is LyricsUiState.Loading -> Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }

                    is LyricsUiState.NoLyrics -> Box(
                        modifier = Modifier.fillMaxWidth().weight(1f).padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            tr("No se encontraron letras para esta canción"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    is LyricsUiState.Plain -> Box(
                        modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                    ) {
                        Text(st.text, style = MaterialTheme.typography.bodyMedium)
                    }

                    is LyricsUiState.Synced -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    ) {
                        itemsIndexed(st.lines) { i, line ->
                            val isCurrent = i == currentLine
                            Text(
                                line.text,
                                style = if (isCurrent) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}

fun mapError(e: Throwable?): String {
    val msg = e?.message?.lowercase() ?: return tr("Error desconocido")
    return when {
        "timeout" in msg || "connect" in msg || "network" in msg || "unresolved" in msg ||
        "refused" in msg || "unknown host" in msg || "no route" in msg || "internet" in msg ->
            tr("No se pudo conectar a la red")
        else -> tr("No se pudo conectar a la red")
    }
}

// ===================== HOME =====================
@Composable
fun HomeScreen(onOpenDetail: (DetailScreen) -> Unit) {
    val history = ListenHistoryManager.entries

    val albums = remember(history) {
        history.mapNotNull { song ->
            val album = song.album ?: return@mapNotNull null
            AlbumItem(
                browseId = album.id,
                playlistId = song.id,
                title = album.name,
                artists = song.artists.takeIf { it.isNotEmpty() },
                thumbnail = song.thumbnail,
                explicit = song.explicit
            )
        }.distinctBy { it.browseId }
    }
    val baseArtists = remember(history) {
        history.flatMap { it.artists }
            .filter { !it.id.isNullOrBlank() }
            .map { artist ->
                ArtistItem(
                    id = artist.id!!,
                    title = artist.name,
                    thumbnail = null,
                    shuffleEndpoint = null,
                    radioEndpoint = null
                )
            }
            .distinctBy { it.id }
    }

    // Real artist photos + more songs from those artists
    var artists by remember { mutableStateOf<List<ArtistItem>>(emptyList()) }
    var artistSongs by remember { mutableStateOf<List<SongItem>>(emptyList()) }
    var loadingMore by remember { mutableStateOf(false) }

    val artistKey = remember(baseArtists) { baseArtists.take(8).joinToString(",") { it.id } }
    LaunchedEffect(artistKey) {
        if (baseArtists.isEmpty()) return@LaunchedEffect
        loadingMore = true
        val fetchedArtists = mutableListOf<ArtistItem>()
        val fetchedSongs = mutableListOf<SongItem>()
        coroutineScope {
            val deferred = baseArtists.take(8).map { artist ->
                async { artist to YouTube.artist(artist.id) }
            }
            deferred.forEach { d ->
                val (_, result) = d.await()
                result.onSuccess { page ->
                    fetchedArtists += page.artist
                    page.sections.firstOrNull { section -> section.items.any { it is SongItem } }
                        ?.items
                        ?.filterIsInstance<SongItem>()
                        ?.let { fetchedSongs += it }
                }
            }
        }
        artists = fetchedArtists.distinctBy { it.id }
        artistSongs = fetchedSongs.distinctBy { it.id }.filter { s -> history.none { it.id == s.id } }
        loadingMore = false
    }

    when {
        history.isEmpty() -> EmptyScreen(tr("Reproduce algo de música para obtener recomendaciones personalizadas"))
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Volver a escuchar",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    MediaRow(items = history, onOpenDetail = onOpenDetail)
                }
            }
            if (artistSongs.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            tr("Más de tus artistas"),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        MediaRow(items = artistSongs, onOpenDetail = onOpenDetail)
                    }
                }
            }
            if (albums.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            tr("Álbumes que has escuchado"),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        MediaRow(items = albums, onOpenDetail = onOpenDetail)
                    }
                }
            }
            if (baseArtists.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            tr("Artistas que has escuchado"),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        MediaRow(items = if (artists.isNotEmpty()) artists else baseArtists, onOpenDetail = onOpenDetail)
                    }
                }
            }
        }
    }
}

// ===================== SEARCH =====================
@Composable
fun SearchScreen(
    query: String,
    results: SearchSummaryPage?,
    songs: List<SongItem>,
    loading: Boolean,
    error: String?,
    hasQuery: Boolean,
    history: List<String>,
    onHistoryClick: (String) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    onOpenDetail: (DetailScreen) -> Unit,
    scrollState: LazyListState
) {
    when {
        !hasQuery -> {
            if (history.isEmpty()) {
                EmptyScreen(tr("Escribe para buscar..."))
            } else {
                LazyColumn(
                    state = scrollState,
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                tr("Búsquedas recientes"),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            TextButton(onClick = onClearHistory) {
                                Text(tr("Borrar"), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    items(history, key = { it }) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onHistoryClick(entry) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                entry,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            IconButton(onClick = { onRemoveHistory(entry) }) {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = tr("Quitar del historial"),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        error != null && results == null -> ErrorScreen(error)
        (results == null || results.summaries.isEmpty()) && songs.isEmpty() && loading -> LoadingScreen()
        else -> LazyColumn(
            state = scrollState,
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Text(
                        tr("Resultados para \"{0}\"", query),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (loading) {
                        Spacer(modifier = Modifier.width(12.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            val summaries = results?.summaries.orEmpty()
            summaries.forEach { summary ->
                // All-song sections are rendered as the full song list at the bottom
                val isSongSection = summary.items.isNotEmpty() &&
                    summary.items.all { it is SongItem }
                if (!isSongSection && summary.items.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                summary.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            MediaRow(items = summary.items, onOpenDetail = onOpenDetail)
                        }
                    }
                }
            }
            if (songs.isNotEmpty()) {
                item {
                    Text(
                        tr("Canciones"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                items(songs, key = { it.id }) { song ->
                    SongListItem(
                        song = song,
                        onClick = { PlayerManager.playSong(song, songs) },
                        onLike = { LikedSongsManager.toggleLike(song) },
                        isLiked = LikedSongsManager.isLiked(song.id)
                    )
                }
            }
        }
    }
}

// ===================== EXPLORE =====================
private data class ExploreSection(val title: String, val items: List<YTItem>)

private object ExploreCache {
    var sections: List<ExploreSection>? = null
    var error: String? = null
}

@Composable
fun ExploreScreen(onOpenDetail: (DetailScreen) -> Unit) {
    var sections by remember { mutableStateOf(ExploreCache.sections ?: emptyList()) }
    var loading by remember { mutableStateOf(ExploreCache.sections == null) }
    var error by remember { mutableStateOf<String?>(ExploreCache.error) }

    LaunchedEffect(Unit) {
        if (ExploreCache.sections != null) return@LaunchedEffect
        loading = true
        error = null
        val allSections = mutableListOf<ExploreSection>()

        val homeResult = YouTube.home()
        homeResult.onSuccess { page ->
            // Base home shelves
            allSections += page.sections.map { ExploreSection(it.title, it.items) }
            // Each home chip (Relax, Fiesta, Entrenamiento, ...) loads its own shelves
            page.chips.orEmpty().forEach { chip ->
                val endpoint = chip.endpoint ?: return@forEach
                YouTube.browse(endpoint.browseId, endpoint.params).onSuccess { browseResult ->
                    browseResult.items.forEach { item ->
                        allSections += ExploreSection(item.title ?: chip.title, item.items)
                    }
                }.onFailure { e ->
                    error = mapError(e)
                }
            }
        }.onFailure { e ->
            error = mapError(e)
        }

        sections = allSections.distinctBy { it.title }
        ExploreCache.sections = sections
        ExploreCache.error = error
        loading = false
    }

    when {
        loading -> LoadingScreen()
        error != null -> ErrorScreen(error!!)
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            sections.forEach { section ->
                if (section.items.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                section.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            MediaRow(items = section.items, onOpenDetail = onOpenDetail)
                        }
                    }
                }
            }
        }
    }
}

// ===================== ALBUM / PLAYLIST / ARTIST =====================
@Composable
fun AlbumScreen(
    browseId: String,
    fallbackTitle: String,
    fallbackThumbnail: String?,
    fallbackArtists: List<Artist>?
) {
    var page by remember { mutableStateOf<AlbumPage?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(browseId) {
        loading = true
        error = null
        YouTube.album(browseId).onSuccess { p ->
            page = p
            loading = false
        }.onFailure { e ->
            error = mapError(e)
            loading = false
        }
    }

    when {
        loading -> LoadingScreen()
        error != null -> ErrorScreen(error!!)
        else -> {
            val album = page!!.album
            val songs = page!!.songs
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(140.dp).clip(RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            val thumb = album.thumbnail.ifBlank { fallbackThumbnail }
                            if (thumb.isNullOrBlank()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                ) {
                                    Icon(
                                        Icons.Filled.MusicNote,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.align(Alignment.Center).size(48.dp)
                                    )
                                }
                            } else {
                                AsyncImage(
                                    model = thumb,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(20.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                album.title.ifBlank { fallbackTitle },
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                album.artists?.joinToString { it.name }
                                    ?: fallbackArtists?.joinToString { it.name }
                                    ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            album.year?.let {
                                Text(
                                    it.toString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            FilledTonalButton(
                                onClick = { if (songs.isNotEmpty()) PlayerManager.playSong(songs.first(), songs) }
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(tr("Reproducir todo"))
                            }
                        }
                    }
                }
                items(songs, key = { it.id }) { song ->
                    SongListItem(
                        song = song,
                        onClick = { PlayerManager.playSong(song, songs) },
                        onLike = { LikedSongsManager.toggleLike(song) },
                        isLiked = LikedSongsManager.isLiked(song.id)
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistScreen(
    playlistId: String,
    fallbackTitle: String,
    fallbackThumbnail: String?
) {
    var page by remember { mutableStateOf<PlaylistPage?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(playlistId) {
        loading = true
        error = null
        YouTube.playlist(playlistId).completed().onSuccess { p ->
            page = p
            loading = false
        }.onFailure { e ->
            error = mapError(e)
            loading = false
        }
    }

    when {
        loading -> LoadingScreen()
        error != null -> ErrorScreen(error!!)
        else -> {
            val playlist = page!!.playlist
            val songs = page!!.songs
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(140.dp).clip(RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            val thumb = playlist.thumbnail ?: fallbackThumbnail
                            if (thumb.isNullOrBlank()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                ) {
                                    Icon(
                                        Icons.Filled.QueueMusic,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.align(Alignment.Center).size(48.dp)
                                    )
                                }
                            } else {
                                AsyncImage(
                                    model = thumb,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(20.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                playlist.title.ifBlank { fallbackTitle },
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                playlist.author?.name ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            playlist.songCountText?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            FilledTonalButton(
                                onClick = { if (songs.isNotEmpty()) PlayerManager.playSong(songs.first(), songs) }
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(tr("Reproducir todo"))
                            }
                        }
                    }
                }
                items(songs, key = { it.id }) { song ->
                    SongListItem(
                        song = song,
                        onClick = { PlayerManager.playSong(song, songs) },
                        onLike = { LikedSongsManager.toggleLike(song) },
                        isLiked = LikedSongsManager.isLiked(song.id)
                    )
                }
            }
        }
    }
}

@Composable
fun ArtistScreen(
    browseId: String,
    fallbackTitle: String,
    fallbackThumbnail: String?,
    onOpenDetail: (DetailScreen) -> Unit
) {
    var page by remember { mutableStateOf<ArtistPage?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(browseId) {
        loading = true
        error = null
        YouTube.artist(browseId).onSuccess { p ->
            page = p
            loading = false
        }.onFailure { e ->
            error = mapError(e)
            loading = false
        }
    }

    when {
        loading -> LoadingScreen()
        error != null -> ErrorScreen(error!!)
        else -> {
            val artist = page!!.artist
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(120.dp).clip(CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            val thumb = artist.thumbnail ?: fallbackThumbnail
                            if (thumb.isNullOrBlank()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                ) {
                                    Icon(
                                        Icons.Filled.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.align(Alignment.Center).size(48.dp)
                                    )
                                }
                            } else {
                                AsyncImage(
                                    model = thumb,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(20.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                artist.title.ifBlank { fallbackTitle },
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            page!!.description?.let { d ->
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    d,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                page!!.sections.forEach { section ->
                    if (section.items.isNotEmpty()) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    section.title,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                MediaRow(items = section.items, onOpenDetail = onOpenDetail)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ===================== LIBRARY =====================
@Composable
fun LibraryScreen(onOpenDetail: (DetailScreen) -> Unit, onOpenAccount: () -> Unit) {
    var likedSongs by remember { mutableStateOf(LikedSongsManager.likedSongs) }
    var downloadedSongs by remember { mutableStateOf(DownloadsManager.downloadedSongs) }
    var cachedSongs by remember { mutableStateOf(CacheMetadataManager.getActualCachedSongs()) }
    var localSongs by remember { mutableStateOf(LocalSongsManager.songs) }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(tr("Favoritas"), tr("Descargadas"), tr("En caché"), tr("Locales"), tr("Listas de reproducción"))

    // Refresh all data when switching tabs
    LaunchedEffect(selectedTab) {
        likedSongs = LikedSongsManager.likedSongs
        downloadedSongs = DownloadsManager.downloadedSongs
        cachedSongs = CacheMetadataManager.getActualCachedSongs()
        localSongs = LocalSongsManager.songs
    }

    // Also refresh on first composition
    LaunchedEffect(Unit) {
        likedSongs = LikedSongsManager.likedSongs
        downloadedSongs = DownloadsManager.downloadedSongs
        cachedSongs = CacheMetadataManager.getActualCachedSongs()
        localSongs = LocalSongsManager.songs
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            title,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        when (selectedTab) {
            0 -> LibrarySongList(
                songs = likedSongs,
                emptyMessage = tr("Aún no hay canciones que te gusten"),
                onPlayAll = { if (likedSongs.isNotEmpty()) PlayerManager.playSong(likedSongs.first(), likedSongs) },
                onLike = { song -> LikedSongsManager.toggleLike(song); likedSongs = LikedSongsManager.likedSongs }
            )
            1 -> LibrarySongList(
                songs = downloadedSongs,
                emptyMessage = tr("No hay canciones descargadas"),
                onPlayAll = { if (downloadedSongs.isNotEmpty()) PlayerManager.playSong(downloadedSongs.first(), downloadedSongs) },
                onLike = { song -> LikedSongsManager.toggleLike(song) },
                onDelete = { song ->
                    DownloadsManager.removeDownload(song)
                    downloadedSongs = DownloadsManager.downloadedSongs
                }
            )
            2 -> LibrarySongList(
                songs = cachedSongs,
                emptyMessage = tr("No hay canciones en caché"),
                onPlayAll = { if (cachedSongs.isNotEmpty()) PlayerManager.playSong(cachedSongs.first(), cachedSongs) },
                onLike = { song -> LikedSongsManager.toggleLike(song) },
                onDelete = { song ->
                    File(System.getProperty("user.home"), ".opentune/cache").listFiles()
                        ?.filter { it.name.startsWith(song.id) }?.forEach { it.delete() }
                    CacheMetadataManager.removeMetadata(song.id)
                    cachedSongs = CacheMetadataManager.getActualCachedSongs()
                }
            )
            3 -> LocalSongList(
                songs = localSongs,
                onSongsChanged = { localSongs = LocalSongsManager.songs }
            )
            4 -> PlaylistsLibrary(onOpenDetail = onOpenDetail, onOpenAccount = onOpenAccount)
        }
    }
}

@Composable
fun LibrarySongList(
    songs: List<SongItem>,
    emptyMessage: String,
    onPlayAll: () -> Unit,
    onLike: ((SongItem) -> Unit)? = null,
    onDelete: ((SongItem) -> Unit)? = null
) {
    if (songs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(emptyMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${songs.size} songs",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                FilledTonalButton(onClick = onPlayAll) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(tr("Reproducir todo"))
                }
            }
        }
        items(songs, key = { it.id }) { song ->
            SongListItem(
                song = song,
                onClick = { PlayerManager.playSong(song, songs) },
                onLike = if (onLike != null) {{ onLike(song) }} else null,
                isLiked = if (onLike != null) LikedSongsManager.isLiked(song.id) else false,
                onDelete = if (onDelete != null) {{ onDelete(song) }} else null
            )
        }
    }
}

@Composable
fun LocalSongList(
    songs: List<SongItem>,
    onSongsChanged: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var scanning by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun runImport(task: () -> Int) {
        scanning = true
        message = null
        scope.launch {
            val added = withContext(Dispatchers.IO) { task() }
            scanning = false
            message = if (added > 0) tr("Se añadieron {0} canción(es)", added) else tr("No se encontraron canciones nuevas")
            onSongsChanged()
        }
    }

    fun chooseFolder() {
        val chooser = JFileChooser().apply {
            dialogTitle = tr("Elige una carpeta con canciones")
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isAcceptAllFileFilterUsed = false
        }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile?.let { dir -> runImport { LocalSongsManager.addFolder(dir) } }
        }
    }

    fun chooseFiles() {
        val chooser = JFileChooser().apply {
            dialogTitle = tr("Elegir archivos de canciones")
            isMultiSelectionEnabled = true
            fileSelectionMode = JFileChooser.FILES_ONLY
            fileFilter = FileNameExtensionFilter(
                tr("Archivos de audio"),
                *LocalSongsManager.AUDIO_EXTENSIONS.toTypedArray()
            )
        }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            val files = chooser.selectedFiles.toList()
            if (files.isNotEmpty()) runImport { LocalSongsManager.addFiles(files) }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    tr("{0} canciones", songs.size),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (message != null) {
                    Text(
                        message ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { chooseFolder() }) {
                    Icon(Icons.Filled.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(tr("Añadir carpeta"))
                }
                FilledTonalButton(onClick = { chooseFiles() }) {
                    Icon(Icons.Filled.LibraryMusic, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(tr("Añadir archivos"))
                }
            }
        }

        when {
            scanning -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(tr("Importando canciones..."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            songs.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(tr("Aún no hay canciones locales"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))
                    FilledTonalButton(onClick = { chooseFolder() }) {
                        Icon(Icons.Filled.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(tr("Elegir carpeta"))
                    }
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(songs, key = { it.id }) { song ->
                    SongListItem(
                        song = song,
                        onClick = { PlayerManager.playSong(song, songs) },
                        onLike = { LikedSongsManager.toggleLike(song); onSongsChanged() },
                        isLiked = LikedSongsManager.isLiked(song.id),
                        onDelete = {
                            LocalSongsManager.remove(LocalSongsManager.pathFromId(song.id))
                            onSongsChanged()
                        }
                    )
                }
            }
        }
    }
}

// ===================== LOCAL PLAYLISTS =====================
@Composable
fun PlaylistNameDialog(
    title: String,
    initialName: String = "",
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(tr("Nombre de la lista")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) { onConfirm(name); onDismiss() } },
                enabled = name.isNotBlank()
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(tr("Cancelar")) }
        }
    )
}

@Composable
fun PlaylistsLibrary(onOpenDetail: (DetailScreen) -> Unit, onOpenAccount: () -> Unit) {
    val playlists = PlaylistsManager.playlists
    var showCreate by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    val linked = AccountManager.isLinked

    if (showCreate) {
        PlaylistNameDialog(
            title = tr("Nueva lista"),
            confirmLabel = tr("Crear"),
            onConfirm = { name -> PlaylistsManager.create(name) },
            onDismiss = { showCreate = false }
        )
    }
    if (showImport) {
        ImportFromYouTubeDialog(onDismiss = { showImport = false })
    }
    if (showExport) {
        ExportToYouTubeDialog(onDismiss = { showExport = false })
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showImport = true },
                    enabled = linked
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(tr("Importar de YouTube"))
                }
                OutlinedButton(
                    onClick = { showExport = true },
                    enabled = linked
                ) {
                    Icon(Icons.Filled.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(tr("Exportar a YouTube"))
                }
            }
            FilledTonalButton(onClick = { showCreate = true }) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(tr("Nueva lista"))
            }
        }

        if (!linked) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        tr("Vincula tu cuenta de YouTube para importar y exportar playlists de YouTube Music."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onOpenAccount) { Text(tr("Vincular cuenta")) }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (playlists.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(tr("Aún no hay listas. Crea la primera."), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(playlists, key = { it.id }) { playlist ->
                    Card(
                        onClick = { onOpenDetail(DetailScreen.LocalPlaylist(playlist.id, playlist.name)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                contentAlignment = Alignment.Center
                            ) {
                                if (playlist.thumbnail != null) {
                                    AsyncImage(
                                        model = playlist.thumbnail,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(Icons.Filled.QueueMusic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    playlist.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    tr("{0} canciones", playlist.songs.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LocalPlaylistScreen(playlistId: String, onBack: () -> Unit) {
    val playlist = PlaylistsManager.playlist(playlistId)
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    if (playlist == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(tr("Esta lista está vacía"), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    if (showRename) {
        PlaylistNameDialog(
            title = tr("Renombrar"),
            initialName = playlist.name,
            confirmLabel = tr("Renombrar"),
            onConfirm = { name -> PlaylistsManager.rename(playlist.id, name) },
            onDismiss = { showRename = false }
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(tr("Eliminar")) },
            text = {
                Column {
                    Text(tr("¿Eliminar la lista \"{0}\"?", playlist.name))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        tr("Se eliminará de forma permanente."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    PlaylistsManager.delete(playlist.id)
                    showDelete = false
                    onBack()
                }) { Text(tr("Eliminar"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text(tr("Cancelar")) }
            }
        )
    }

    val songs = playlist.songs
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    if (playlist.thumbnail != null) {
                        AsyncImage(
                            model = playlist.thumbnail,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            Icons.Filled.QueueMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(20.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        playlist.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        tr("{0} canciones", songs.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = { if (songs.isNotEmpty()) PlayerManager.playSong(songs.first(), songs) }
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(tr("Reproducir todo"))
                        }
                        OutlinedButton(onClick = { showRename = true }) {
                            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(tr("Renombrar"))
                        }
                        OutlinedButton(onClick = { showDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(tr("Eliminar"), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        if (songs.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(tr("Esta lista está vacía"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(songs, key = { it.id }) { song ->
                SongListItem(
                    song = song,
                    onClick = { PlayerManager.playSong(song, songs) },
                    onLike = { LikedSongsManager.toggleLike(song) },
                    isLiked = LikedSongsManager.isLiked(song.id),
                    onDelete = { PlaylistsManager.removeSong(playlist.id, song.id) }
                )
            }
        }
    }
}

@Composable
fun AddToPlaylistDialog(song: SongItem, onDismiss: () -> Unit) {
    val playlists = PlaylistsManager.playlists
    var newName by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .width(440.dp)
                .heightIn(max = 540.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    tr("Añadir a lista"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text(tr("Nombre de la lista")) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    FilledTonalButton(
                        onClick = {
                            if (newName.isNotBlank()) {
                                val id = PlaylistsManager.create(newName)
                                PlaylistsManager.addSong(id, song)
                                newName = ""
                            }
                        },
                        enabled = newName.isNotBlank()
                    ) { Text(tr("Crear")) }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (playlists.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(tr("Aún no hay listas. Crea la primera."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                    ) {
                        items(playlists, key = { it.id }) { p ->
                            val contains = PlaylistsManager.containsSong(p.id, song.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (contains) PlaylistsManager.removeSong(p.id, song.id)
                                        else PlaylistsManager.addSong(p.id, song)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (contains) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                                    contentDescription = null,
                                    tint = if (contains) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        p.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        tr("{0} canciones", p.songs.size),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text(tr("Cerrar"))
                }
            }
        }
    }
}

// ===================== YOUTUBE ACCOUNT / IMPORT / EXPORT =====================
private sealed interface EmbeddedPhase {
    data object Idle : EmbeddedPhase
    data class Initializing(val message: String) : EmbeddedPhase
    data object Ready : EmbeddedPhase
    data object Linking : EmbeddedPhase
    data class Failed(val message: String) : EmbeddedPhase
}

@Composable
fun AccountSettings(onBack: () -> Unit) {
    AccountContent()
}

/** Mini window with the YouTube account management, opened from [SettingsScreen]. */
@Composable
fun AccountDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(600.dp).height(640.dp),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        tr("Cuenta de YouTube"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = tr("Cerrar"))
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Box(modifier = Modifier.weight(1f)) {
                    AccountContent()
                }
            }
        }
    }
}

/**
 * Account management content shared by the full settings pane
 * ([AccountSettings]) and the mini window opened from [SettingsScreen].
 */
@Composable
fun AccountContent() {
    val scope = rememberCoroutineScope()
    var cookieText by remember { mutableStateOf("") }
    var linkError by remember { mutableStateOf<String?>(null) }
    var showUnlink by remember { mutableStateOf(false) }
    var browserOpened by remember { mutableStateOf(false) }
    var readingBrowser by remember { mutableStateOf(false) }
    var showManual by remember { mutableStateOf(false) }
    var embeddedVisible by remember { mutableStateOf(false) }
    var embeddedPhase by remember { mutableStateOf<EmbeddedPhase>(EmbeddedPhase.Idle) }
    var embeddedBrowser by remember { mutableStateOf<CefBrowser?>(null) }
    var embeddedComponent by remember { mutableStateOf<Component?>(null) }
    var embeddedError by remember { mutableStateOf<String?>(null) }
    val linked = AccountManager.isLinked
    val accountInfo = AccountManager.accountInfo

    fun closeEmbedded() {
        EmbeddedBrowserLogin.disposeBrowser(embeddedBrowser)
        embeddedBrowser = null
        embeddedComponent = null
        embeddedError = null
        embeddedVisible = false
        embeddedPhase = EmbeddedPhase.Idle
    }

    if (embeddedVisible) {
        val phase = embeddedPhase
        Dialog(onDismissRequest = { closeEmbedded() }) {
            Surface(
                modifier = Modifier.width(760.dp).height(620.dp),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                    Text(tr("Iniciar sesión en YouTube Music"), style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(12.dp))
                    when (phase) {
                        is EmbeddedPhase.Initializing -> {
                            Column(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    phase.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        is EmbeddedPhase.Ready -> {
                            Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                if (embeddedError != null) {
                                    Text(
                                        embeddedError!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                }
                                Text(
                                    tr("Inicia sesión en la ventana de abajo y pulsa \"Ya inicié sesión — Continuar\"."),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                val component = embeddedComponent
                                if (component != null) {
                                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                        SwingPanel(factory = { component }, modifier = Modifier.fillMaxSize())
                                    }
                                }
                            }
                        }
                        is EmbeddedPhase.Linking -> {
                            Column(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    tr("Leyendo la sesión del navegador integrado..."),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        is EmbeddedPhase.Failed -> {
                            Column(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    phase.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        EmbeddedPhase.Idle -> {}
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(
                            onClick = { closeEmbedded() },
                            enabled = phase !is EmbeddedPhase.Linking
                        ) { Text(tr("Cancelar")) }
                        Button(
                            onClick = {
                                embeddedError = null
                                scope.launch {
                                    val browser = embeddedBrowser ?: return@launch
                                    embeddedPhase = EmbeddedPhase.Linking
                                    val result = runCatching {
                                        val cookie = EmbeddedBrowserLogin.readCookies().getOrThrow()
                                        AccountManager.link(cookie)
                                    }
                                    result.onSuccess {
                                        closeEmbedded()
                                    }.onFailure { e ->
                                        embeddedPhase = EmbeddedPhase.Ready
                                        embeddedError = tr("La cuenta no se pudo vincular. Comprueba las cookies.") +
                                            (if (e.message != null) "\n${e.message}" else "")
                                    }
                                }
                            },
                            enabled = phase is EmbeddedPhase.Ready && !AccountManager.loading
                        ) { Text(tr("Ya inicié sesión — Continuar")) }
                    }
                }
            }
        }
    }

    if (showUnlink) {
        AlertDialog(
            onDismissRequest = { showUnlink = false },
            title = { Text(tr("Cerrar sesión")) },
            text = { Text(tr("Se eliminarán las cookies guardadas de esta cuenta.")) },
            confirmButton = {
                TextButton(onClick = {
                    AccountManager.unlink()
                    showUnlink = false
                }) { Text(tr("Cerrar sesión"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showUnlink = false }) { Text(tr("Cancelar")) }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SettingsGroupCard(tr("Cuenta de YouTube")) {
                if (linked) {
                    SettingsInfoRow(
                        Icons.Filled.AccountCircle,
                        accountInfo?.name ?: tr("Cuenta vinculada"),
                        accountInfo?.email ?: (accountInfo?.channelHandle ?: ""),
                        MaterialTheme.colorScheme.primary
                    )
                    SettingsDestructiveRow(Icons.Filled.Logout, tr("Cerrar sesión"), MaterialTheme.colorScheme.error) { showUnlink = true }
                } else {
                    Text(
                        tr("Vincula tu cuenta de YouTube para importar y exportar playlists de YouTube Music."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    if (readingBrowser) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                        Text(
                            tr("Leyendo la sesión del navegador..."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    } else if (browserOpened) {
                        Text(
                            tr("Inicia sesión en music.youtube.com en la ventana que se ha abierto y vuelve aquí para continuar."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Button(
                            onClick = {
                                linkError = null
                                readingBrowser = true
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        runCatching {
                                            BrowserSessionReader.closeLoginWindow()
                                            val cookie = BrowserSessionReader.read().getOrThrow()
                                            cookie to AccountManager.link(cookie)
                                        }
                                    }
                                    readingBrowser = false
                                    result.onSuccess { (_, linkResult) ->
                                        linkResult.onSuccess {
                                            BrowserSessionReader.cleanup()
                                            browserOpened = false
                                        }.onFailure { e ->
                                            linkError = tr("No se pudo leer la sesión de Chrome o Edge. Inicia sesión en music.youtube.com e inténtalo de nuevo.") +
                                                (if (e.message != null) "\n${e.message}" else "")
                                        }
                                    }.onFailure { e ->
                                        linkError = tr("No se pudo leer la sesión de Chrome o Edge. Inicia sesión en music.youtube.com e inténtalo de nuevo.") +
                                            (if (e.message != null) "\n${e.message}" else "")
                                    }
                                }
                            },
                            enabled = !AccountManager.loading,
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        ) { Text(tr("Ya inicié sesión — Continuar")) }
                        TextButton(
                            onClick = { browserOpened = false; linkError = null },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        ) { Text(tr("Cancelar")) }
                    } else {
                        Button(
                            onClick = {
                                linkError = null
                                embeddedError = null
                                embeddedVisible = true
                                embeddedPhase = EmbeddedPhase.Initializing(tr("Preparando el navegador integrado..."))
                                scope.launch {
                                    EmbeddedBrowserLogin.ensureInitialized { msg ->
                                        embeddedPhase = EmbeddedPhase.Initializing(msg)
                                    }.onSuccess {
                                        runCatching {
                                            val (browser, component) = EmbeddedBrowserLogin.createBrowser()
                                            embeddedBrowser = browser
                                            embeddedComponent = component
                                        }.onSuccess {
                                            embeddedPhase = EmbeddedPhase.Ready
                                        }.onFailure { e ->
                                            embeddedPhase = EmbeddedPhase.Failed(
                                                e.message ?: tr("No se pudo iniciar el navegador integrado.")
                                            )
                                        }
                                    }.onFailure { e ->
                                        embeddedPhase = EmbeddedPhase.Failed(
                                            e.message ?: tr("No se pudo iniciar el navegador integrado.")
                                        )
                                    }
                                }
                            },
                            enabled = !AccountManager.loading,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        ) { Text(tr("Iniciar sesión con el navegador")) }
                        TextButton(
                            onClick = {
                                linkError = null
                                BrowserSessionReader.openLogin().onSuccess {
                                    browserOpened = true
                                }.onFailure { e ->
                                    linkError = e.message ?: tr("No se pudo abrir el navegador")
                                }
                            },
                            enabled = !AccountManager.loading,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        ) { Text(tr("Usar mi navegador")) }
                    }
                    if (linkError != null) {
                        Text(
                            linkError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                    TextButton(
                        onClick = { showManual = !showManual },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    ) { Text(tr("¿Problemas? Pega las cookies manualmente")) }
                    if (showManual) {
                        Text(
                            tr("1. Inicia sesión en music.youtube.com en tu navegador.") + "\n" +
                                tr("2. Abre las herramientas de desarrollador (F12) y ve a la pestaña \"Aplicación\" o \"Almacenamiento\" → \"Cookies\" → \"https://music.youtube.com\".") + "\n" +
                                tr("3. Copia todas las cookies (SAPISID, SID, HSID, etc.) y pégalas abajo."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        OutlinedTextField(
                            value = cookieText,
                            onValueChange = {
                                cookieText = it
                                linkError = null
                            },
                            label = { Text(tr("Pega aquí las cookies de music.youtube.com")) },
                            minLines = 4,
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        )
                        if (AccountManager.loading) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                        }
                        Button(
                            onClick = {
                                linkError = null
                                scope.launch {
                                    AccountManager.link(cookieText).onSuccess {
                                        cookieText = ""
                                        showManual = false
                                    }.onFailure { e ->
                                        linkError = tr("La cuenta no se pudo vincular. Comprueba las cookies.") +
                                            (if (e.message != null) "\n${e.message}" else "")
                                    }
                                }
                            },
                            enabled = cookieText.isNotBlank() && !AccountManager.loading,
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        ) { Text(tr("Vincular cuenta")) }
                    }
                }
            }
        }
    }
}

private fun uniqueLocalName(base: String): String {
    val trimmed = base.trim()
    var candidate = trimmed
    var n = 2
    while (PlaylistsManager.playlists.any { it.name == candidate }) {
        candidate = "$trimmed ($n)"
        n++
    }
    return candidate
}

@Composable
fun ImportFromYouTubeDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var phase by remember { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf("") }
    var playlists by remember { mutableStateOf<List<PlaylistItem>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var progress by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var resultMessage by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        phase = 0
        runCatching {
            val result = mutableListOf<PlaylistItem>()
            var continuation: String? = null
            val seen = mutableSetOf<String>()
            while (true) {
                val items: List<YTItem>
                val next: String?
                if (continuation == null) {
                    val page = YouTube.library("FEmusic_liked_playlists", 0).getOrThrow()
                    items = page.items
                    next = page.continuation
                } else {
                    val page = YouTube.libraryContinuation(continuation).getOrThrow()
                    items = page.items
                    next = page.continuation
                }
                result += items.filterIsInstance<PlaylistItem>()
                continuation = next?.takeUnless { it.isBlank() }
                if (continuation == null || !seen.add(continuation) || result.size > 2000) break
            }
            result
        }.onSuccess { items ->
            playlists = items
            selected = items.map { it.id }.toSet()
            phase = 1
        }.onFailure { e ->
            errorMessage = e.message ?: tr("Error desconocido")
            phase = 4
        }
    }

    fun importAll() {
        scope.launch {
            phase = 2
            val targets = playlists.filter { it.id in selected }
            total = targets.size
            progress = 0
            var imported = 0
            targets.forEach { yt ->
                runCatching {
                    val songs = mutableListOf<SongItem>()
                    val page = YouTube.playlist(yt.id).getOrThrow()
                    songs += page.songs
                    var cont = page.songsContinuation?.takeUnless { it.isBlank() } ?: page.continuation?.takeUnless { it.isBlank() }
                    var guard = 0
                    while (cont != null && guard++ < 500) {
                        val cp = YouTube.playlistContinuation(cont).getOrThrow()
                        songs += cp.songs
                        cont = cp.continuation?.takeUnless { it.isBlank() }
                    }
                    val localId = PlaylistsManager.create(uniqueLocalName(yt.title), yt.thumbnail)
                    songs.forEach { PlaylistsManager.addSong(localId, it) }
                    imported++
                }
                progress++
            }
            resultMessage = tr("{0} playlists importadas", imported)
            phase = 3
        }
    }

    Dialog(onDismissRequest = { if (phase != 2) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .width(480.dp)
                .heightIn(max = 560.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    tr("Importar de YouTube"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))
                when (phase) {
                    0 -> Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(tr("Cargando playlists..."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    1 -> {
                        if (playlists.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                Text(tr("No se encontraron playlists en tu cuenta"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = false)
                            ) {
                                items(playlists, key = { it.id }) { p ->
                                    val checked = p.id in selected
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { selected = if (checked) selected - p.id else selected + p.id }
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            if (checked) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                                            contentDescription = null,
                                            tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                p.title,
                                                style = MaterialTheme.typography.bodyLarge,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                p.songCountText ?: "",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = onDismiss) { Text(tr("Cancelar")) }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(onClick = { importAll() }, enabled = selected.isNotEmpty()) { Text(tr("Importar")) }
                        }
                    }

                    2 -> Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            LinearProgressIndicator(
                                progress = { if (total > 0) progress.toFloat() / total else 0f },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(tr("Importando {0} de {1}...", progress, total))
                        }
                    }

                    3 -> Column {
                        Text(resultMessage)
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Button(onClick = onDismiss) { Text(tr("Cerrar")) }
                        }
                    }

                    4 -> Column {
                        Text(errorMessage, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Button(onClick = onDismiss) { Text(tr("Cerrar")) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExportToYouTubeDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val playlists = PlaylistsManager.playlists
    var selected by remember { mutableStateOf(playlists.map { it.id }.toSet()) }
    var phase by remember { mutableIntStateOf(1) }
    var progress by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var resultMessage by remember { mutableStateOf("") }

    fun exportAll() {
        scope.launch {
            phase = 2
            val targets = playlists.filter { it.id in selected }
            total = targets.size
            progress = 0
            var exported = 0
            val failedSongs = mutableListOf<String>()
            targets.forEach { pl ->
                runCatching {
                    val ytId = YouTube.createPlaylist(pl.name).getOrThrow()
                    pl.songs.forEach { song ->
                        if (song.id.startsWith("local:")) return@forEach
                        if (YouTube.addToPlaylist(ytId, song.id).isFailure) {
                            failedSongs += song.title
                        }
                        delay(250)
                    }
                    exported++
                }
                progress++
            }
            resultMessage = tr("{0} playlists exportadas", exported)
            if (failedSongs.isNotEmpty()) {
                resultMessage += "\n" + tr("Algunas canciones no se pudieron exportar:") + "\n" +
                    failedSongs.distinct().take(8).joinToString(", ")
            }
            phase = 3
        }
    }

    Dialog(onDismissRequest = { if (phase != 2) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .width(480.dp)
                .heightIn(max = 560.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    tr("Exportar a YouTube"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))
                when (phase) {
                    1 -> {
                        if (playlists.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                Text(tr("Aún no hay listas. Crea la primera."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = false)
                            ) {
                                items(playlists, key = { it.id }) { p ->
                                    val checked = p.id in selected
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { selected = if (checked) selected - p.id else selected + p.id }
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            if (checked) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                                            contentDescription = null,
                                            tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                p.name,
                                                style = MaterialTheme.typography.bodyLarge,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                tr("{0} canciones", p.songs.size),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = onDismiss) { Text(tr("Cancelar")) }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(onClick = { exportAll() }, enabled = selected.isNotEmpty()) { Text(tr("Exportar")) }
                        }
                    }

                    2 -> Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            LinearProgressIndicator(
                                progress = { if (total > 0) progress.toFloat() / total else 0f },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(tr("Exportando {0} de {1}...", progress, total))
                        }
                    }

                    3 -> Column {
                        Text(resultMessage)
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Button(onClick = onDismiss) { Text(tr("Cerrar")) }
                        }
                    }
                }
            }
        }
    }
}

// ===================== SETTINGS MAIN =====================
@Composable
fun SettingsScreen(onNavigate: (SettingsSubScreen) -> Unit) {
    val scrollState = rememberScrollState()
    val cacheDir = remember { File(System.getProperty("user.home"), ".opentune/cache") }
    var showAccountDialog by remember { mutableStateOf(false) }

    if (showAccountDialog) {
        AccountDialog(onDismiss = { showAccountDialog = false })
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                tr("Ajustes"),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickActionCard(tr("Apariencia"), Icons.Filled.Palette, MaterialTheme.colorScheme.primary, Modifier.weight(1f)) { onNavigate(SettingsSubScreen.Appearance) }
                QuickActionCard(tr("Reproductor"), Icons.Filled.PlayArrow, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f)) { onNavigate(SettingsSubScreen.PlayerAudio) }
                QuickActionCard(tr("Almacenamiento"), Icons.Filled.Storage, MaterialTheme.colorScheme.secondary, Modifier.weight(1f)) { onNavigate(SettingsSubScreen.Storage) }
                QuickActionCard(tr("Privacidad"), Icons.Filled.Security, MaterialTheme.colorScheme.error, Modifier.weight(1f)) { onNavigate(SettingsSubScreen.Privacy) }
            }
        }

        item {
            SettingsGroupCard(tr("Reproductor y contenido")) {
                SettingsNavRow(Icons.Filled.PlayArrow, tr("Reproductor y audio"), tr("WAV PCM (vía ffmpeg)"), MaterialTheme.colorScheme.tertiary) { onNavigate(SettingsSubScreen.PlayerAudio) }
                SettingsNavRow(Icons.Filled.Language, tr("Contenido"), tr("Idioma y región"), MaterialTheme.colorScheme.secondary) { onNavigate(SettingsSubScreen.Content) }
                val cacheMB = cacheDir.listFiles()?.filter { it.name.endsWith(".webm") }?.sumOf { it.length() }?.let { it / (1024 * 1024) } ?: 0
                SettingsInfoRow(Icons.Filled.Storage, tr("Caché"), "$cacheMB MB", MaterialTheme.colorScheme.secondary)
                SettingsDestructiveRow(Icons.Filled.Delete, tr("Borrar caché de canciones"), MaterialTheme.colorScheme.error) {
                    cacheDir.listFiles()?.filter { it.name.endsWith(".webm") }?.forEach { it.delete() }
                }
            }
        }

        item {
            SettingsGroupCard(tr("Cuenta")) {
                SettingsNavRow(
                    Icons.Filled.AccountCircle,
                    tr("Cuenta de YouTube"),
                    if (AccountManager.isLinked) tr("Vinculada") else tr("No vinculada"),
                    MaterialTheme.colorScheme.primary
                ) { showAccountDialog = true }
            }
        }

        item {
            SettingsGroupCard(tr("Sistema")) {
                SettingsNavRow(Icons.Filled.Science, tr("Ajustes experimentales"), tr("Varios"), MaterialTheme.colorScheme.tertiary) {}
                SettingsNavRow(Icons.Filled.Update, tr("Actualizaciones"), tr("Versión {0}", APP_VERSION), MaterialTheme.colorScheme.primary) {
                    try {
                        java.awt.Desktop.getDesktop().browse(java.net.URI("https://github.com/$GITHUB_REPO/releases"))
                    } catch (_: Exception) {}
                }
                SettingsNavRow(Icons.Filled.Info, tr("Acerca de"), "Luma Music", MaterialTheme.colorScheme.onSurfaceVariant) { onNavigate(SettingsSubScreen.About) }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

// ===================== APPEARANCE SETTINGS =====================
@Composable
fun AppearanceSettings(onBack: () -> Unit, onNavigate: (SettingsSubScreen) -> Unit) {
    val scrollState = rememberScrollState()
    val currentPalette = rememberCurrentPalette()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SettingsGroupCard(tr("Tema")) {
                SettingsNavRow(
                    Icons.Filled.Palette,
                    tr("Paleta de colores"),
                    tr("Actual: {0}", currentPalette.name),
                    MaterialTheme.colorScheme.primary
                ) { onNavigate(SettingsSubScreen.PalettePicker) }

                SettingsSwitchRow(
                    Icons.Filled.Contrast,
                    tr("Negro puro"),
                    tr("Fondo negro AMOLED"),
                    DesktopPreferences.pureBlack,
                    MaterialTheme.colorScheme.onSurfaceVariant
                ) { DesktopPreferences.updatePureBlack(it) }
            }
        }

        item {
            SettingsGroupCard(tr("Estilo del reproductor")) {
                SettingsSwitchRow(
                    Icons.Filled.Fullscreen,
                    tr("Reproductor a pantalla completa"),
                    tr("Abrir el reproductor a pantalla completa"),
                    DesktopPreferences.fullscreenPlayer,
                    MaterialTheme.colorScheme.primary
                ) { DesktopPreferences.updateFullscreenPlayer(it) }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

// ===================== PALETTE PICKER =====================
@Composable
fun PalettePickerScreen(onBack: () -> Unit) {
    val currentId = DesktopPreferences.themePaletteId

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = tr("Atrás"))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    tr("Paleta de colores"),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        items(DesktopPalettes.all) { palette ->
            val isSelected = palette.id == currentId
            Card(
                onClick = { DesktopPreferences.updateThemePalette(palette.id) },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = palette.primary
                    ) {}
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        palette.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (isSelected) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

// ===================== PLAYER & AUDIO SETTINGS =====================
@Composable
fun PlayerAudioSettings(onBack: () -> Unit) {
    val scrollState = rememberScrollState()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SettingsGroupCard(tr("Reproductor")) {
                SettingsInfoRow(Icons.Filled.GraphicEq, tr("Formato de audio"), tr("WAV PCM (vía ffmpeg)"), MaterialTheme.colorScheme.tertiary)

                SettingsSwitchRow(
                    Icons.Filled.SkipNext,
                    tr("Omitir en caso de error"),
                    tr("Saltar a la siguiente canción si falla la reproducción"),
                    DesktopPreferences.autoSkipOnError,
                    MaterialTheme.colorScheme.tertiary
                ) { DesktopPreferences.updateAutoSkipOnError(it) }

                SettingsSwitchRow(
                    Icons.Filled.Forward,
                    tr("Segundos extra al buscar"),
                    tr("Añadir segundos extra al retroceder o avanzar"),
                    DesktopPreferences.seekExtraSeconds,
                    MaterialTheme.colorScheme.primary
                ) { DesktopPreferences.updateSeekExtraSeconds(it) }

                SettingsInfoRow(Icons.Filled.Speed, tr("Motor de reproducción"), "ffmpeg + javax.sound", MaterialTheme.colorScheme.secondary)
            }
        }

        item {
            SettingsGroupCard(tr("Cola")) {
                SettingsSwitchRow(
                    Icons.Filled.QueueMusic,
                    tr("Cola persistente"),
                    tr("Guardar la cola entre sesiones"),
                    DesktopPreferences.persistentQueue,
                    MaterialTheme.colorScheme.primary
                ) { DesktopPreferences.updatePersistentQueue(it) }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

// ===================== STORAGE SETTINGS =====================
@Composable
fun StorageSettings(onBack: () -> Unit) {
    val cacheDir = remember { File(System.getProperty("user.home"), ".opentune/cache") }
    var cacheSize by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            cacheSize = cacheDir.listFiles()?.filter { it.name.endsWith(".webm") }?.sumOf { it.length() } ?: 0L
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SettingsGroupCard(tr("Caché")) {
                val cacheMB = cacheSize / (1024 * 1024)
                val maxSizeMB = DesktopPreferences.maxCacheSizeMB
                val progress = if (maxSizeMB > 0) (cacheMB.toFloat() / maxSizeMB).coerceIn(0f, 1f) else 0f

                SettingsInfoRow(Icons.Filled.Storage, tr("Caché de canciones"), "${cacheMB} MB / ${maxSizeMB} MB", MaterialTheme.colorScheme.secondary)

                if (maxSizeMB > 0) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(6.dp),
                        color = if (progress > 0.8f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }

                SettingsDestructiveRow(Icons.Filled.Delete, tr("Borrar caché de canciones"), MaterialTheme.colorScheme.error) {
                    cacheDir.listFiles()?.filter { it.name.endsWith(".webm") }?.forEach { it.delete() }
                }
            }
        }

        item {
            SettingsGroupCard(tr("Límites")) {
                val sizes = listOf(128L, 256L, 500L, 1024L, 2048L, -1L)
                val labels = listOf("128 MB", "256 MB", "500 MB", "1 GB", "2 GB", tr("Sin límite"))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sizes.forEachIndexed { index, size ->
                        val isSelected = DesktopPreferences.maxCacheSizeMB == size
                        FilterChip(
                            selected = isSelected,
                            onClick = { DesktopPreferences.updateMaxCacheSizeMB(size) },
                            label = { Text(labels[index], fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

// ===================== PRIVACY SETTINGS =====================
@Composable
fun PrivacySettings(onBack: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SettingsGroupCard(tr("Historial de escucha")) {
                SettingsSwitchRow(
                    Icons.Filled.History,
                    tr("Pausar historial de escucha"),
                    tr("No guardar historial de escucha"),
                    DesktopPreferences.pauseListenHistory,
                    MaterialTheme.colorScheme.error
                ) { DesktopPreferences.updatePauseListenHistory(it) }
            }
        }

        item {
            SettingsGroupCard(tr("Historial de búsqueda")) {
                SettingsSwitchRow(
                    Icons.Filled.SearchOff,
                    tr("Pausar historial de búsqueda"),
                    tr("No guardar historial de búsqueda"),
                    DesktopPreferences.pauseSearchHistory,
                    MaterialTheme.colorScheme.tertiary
                ) { DesktopPreferences.updatePauseSearchHistory(it) }
                SettingsDestructiveRow(
                    Icons.Filled.DeleteSweep,
                    tr("Borrar historial de búsqueda"),
                    MaterialTheme.colorScheme.error
                ) { SearchHistoryManager.clear() }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

// ===================== CONTENT SETTINGS =====================
@Composable
fun ContentSettings(onBack: () -> Unit) {
    val languages = listOf("system" to tr("Predeterminado del sistema"), "es" to "Español", "en" to "English", "pt" to "Português")
    val countries = listOf("system" to tr("Predeterminado del sistema"), "US" to tr("Estados Unidos"), "MX" to tr("México"), "ES" to tr("España"), "BR" to tr("Brasil"), "GB" to tr("Reino Unido"), "JP" to tr("Japón"), "KR" to tr("Corea del Sur"), "CN" to tr("China"), "DE" to tr("Alemania"), "FR" to tr("Francia"), "IT" to tr("Italia"), "RU" to tr("Rusia"), "IN" to tr("India"), "AU" to tr("Australia"), "CA" to tr("Canadá"), "AR" to tr("Argentina"), "CO" to tr("Colombia"))

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SettingsGroupCard(tr("General")) {
                val currentLang = I18n.current()
                val currentLangName = languages.find { it.first == currentLang }?.second ?: tr("Predeterminado del sistema")

                var showLangDialog by remember { mutableStateOf(false) }
                if (showLangDialog) {
                    AlertDialog(
                        onDismissRequest = { showLangDialog = false },
                        title = { Text(tr("Idioma del contenido")) },
                        text = {
                            LazyColumn {
                                items(languages) { (code, name) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).clickable {
                                            DesktopPreferences.updateContentLanguage(code)
                                            showLangDialog = false
                                        }.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(selected = code == currentLang, onClick = null)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(name, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        },
                        confirmButton = {}
                    )
                }

                SettingsClickableRow(
                    Icons.Filled.Language,
                    tr("Idioma del contenido"),
                    currentLangName,
                    MaterialTheme.colorScheme.secondary
                ) { showLangDialog = true }

                val currentCountry = DesktopPreferences.contentCountry
                val currentCountryName = countries.find { it.first == currentCountry }?.second ?: tr("Predeterminado del sistema")

                var showCountryDialog by remember { mutableStateOf(false) }
                if (showCountryDialog) {
                    AlertDialog(
                        onDismissRequest = { showCountryDialog = false },
                        title = { Text(tr("País del contenido")) },
                        text = {
                            LazyColumn {
                                items(countries) { (code, name) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).clickable {
                                            DesktopPreferences.updateContentCountry(code)
                                            showCountryDialog = false
                                        }.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(selected = code == currentCountry, onClick = null)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(name, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        },
                        confirmButton = {}
                    )
                }

                SettingsClickableRow(
                    Icons.Filled.LocationOn,
                    tr("País del contenido"),
                    currentCountryName,
                    MaterialTheme.colorScheme.tertiary
                ) { showCountryDialog = true }

                SettingsSwitchRow(
                    Icons.Filled.Explicit,
                    tr("Ocultar contenido explícito"),
                    tr("Filtrar contenido explícito"),
                    DesktopPreferences.hideExplicit,
                    MaterialTheme.colorScheme.error
                ) { DesktopPreferences.updateHideExplicit(it) }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

// ===================== ABOUT SCREEN =====================
@Composable
fun AboutScreen(onBack: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SettingsGroupCard(tr("App")) {
                SettingsInfoRow(Icons.Filled.Info, tr("Versión"), APP_VERSION, MaterialTheme.colorScheme.primary)
                SettingsInfoRow(Icons.Filled.Code, tr("Motor"), "Compose Desktop + Skiko", MaterialTheme.colorScheme.secondary)
                SettingsInfoRow(Icons.Filled.PlayArrow, tr("Reproductor"), "yt-dlp + ffmpeg + javax.sound", MaterialTheme.colorScheme.tertiary)
                SettingsInfoRow(Icons.Filled.Storage, tr("Caché"), "~/.opentune/cache/", MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item {
            SettingsGroupCard(tr("Créditos")) {
                SettingsInfoRow(Icons.Filled.Person, tr("App original"), "Arturo254 (OpenTune)", MaterialTheme.colorScheme.primary)
                SettingsInfoRow(Icons.Filled.Code, tr("Port a escritorio"), "Luma Music", MaterialTheme.colorScheme.secondary)
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

// ===================== REUSABLE COMPONENTS =====================
@Composable
fun QuickActionCard(label: String, icon: ImageVector, accentColor: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(28.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun SettingsGroupCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            content()
        }
    }
}

@Composable
fun SettingsNavRow(icon: ImageVector, title: String, subtitle: String, accentColor: Color, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), color = Color.Transparent) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsIcon(icon, accentColor)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun SettingsInfoRow(icon: ImageVector, title: String, subtitle: String, accentColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIcon(icon, accentColor)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SettingsClickableRow(icon: ImageVector, title: String, subtitle: String, accentColor: Color, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), color = Color.Transparent) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsIcon(icon, accentColor)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun SettingsSwitchRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, accentColor: Color, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIcon(icon, accentColor)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
            )
        )
    }
}

@Composable
fun SettingsDestructiveRow(icon: ImageVector, title: String, accentColor: Color, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), color = Color.Transparent) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsIcon(icon, accentColor)
            Spacer(modifier = Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge, color = accentColor, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun SettingsIcon(icon: ImageVector, accentColor: Color) {
    Surface(
        modifier = Modifier.size(40.dp),
        shape = RoundedCornerShape(10.dp),
        color = accentColor.copy(alpha = 0.15f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
        }
    }
}

// ===================== SHARED COMPONENTS =====================
private val MediaCardWidth = 160.dp

@OptIn(ExperimentalTextApi::class)
@Composable
private fun AdaptiveText(
    text: String,
    style: TextStyle,
    color: Color,
    maxLines: Int
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    var widthPx by remember { mutableStateOf(0) }
    val overflows = remember(text, widthPx, style, maxLines) {
        if (widthPx > 0) {
            val result = textMeasurer.measure(
                text = text,
                style = style,
                overflow = TextOverflow.Clip,
                softWrap = true,
                maxLines = maxLines,
                constraints = Constraints(maxWidth = widthPx)
            )
            result.didOverflowHeight || result.lineCount > maxLines
        } else {
            false
        }
    }
    val lineHeightPx = remember(style, textMeasurer) {
        textMeasurer.measure(text = "Wg", style = style, softWrap = false, maxLines = 1).size.height
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(with(density) { (lineHeightPx * maxLines).toDp() })
            .onSizeChanged { widthPx = it.width },
        contentAlignment = Alignment.CenterStart
    ) {
        if (overflows) {
            MarqueeLine(text = text, style = style, color = color)
        } else {
            Text(
                text = text,
                style = style,
                color = color,
                minLines = maxLines,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun MarqueeLine(text: String, style: TextStyle, color: Color) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val textWidthPx = remember(text, style) {
        textMeasurer.measure(text = text, style = style, softWrap = false, maxLines = 1).size.width.toInt()
    }
    val gap = 32.dp
    val loopDistance = textWidthPx + with(density) { gap.toPx() }.toInt()
    val duration = (textWidthPx * 17.4).toInt().coerceIn(2500, 24000)
    val transition = rememberInfiniteTransition(label = "marquee")
    val offsetX by transition.animateFloat(
        initialValue = 0f,
        targetValue = -loopDistance.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = duration, easing = LinearEasing),
            repeatMode = InfiniteRepeatMode.Restart
        ),
        label = "marqueeOffset"
    )
    Box(
        modifier = Modifier.fillMaxWidth().clipToBounds(),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier
                .wrapContentWidth(unbounded = true)
                .offset { IntOffset(offsetX.roundToInt(), 0) }
        ) {
            Text(text = text, style = style, color = color, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
            Spacer(modifier = Modifier.width(gap))
            Text(text = text, style = style, color = color, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
        }
    }
}

@Composable
fun SongCard(song: SongItem, onClick: () -> Unit) {
    val isCurrent = NowPlayingState.currentSongId.value == song.id
    val isCurrentPlaying = isCurrent && NowPlayingState.isPlaying.value

    Card(
        onClick = onClick,
        modifier = Modifier.width(MediaCardWidth),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp))) {
                AsyncImage(
                    model = song.thumbnail,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                if (isCurrent) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    )
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EqualizerBars(
                            color = MaterialTheme.colorScheme.primary,
                            animated = isCurrentPlaying,
                            barWidth = 4.dp,
                            barSpacing = 3.dp,
                            maxHeight = 28.dp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            AdaptiveText(
                text = song.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(2.dp))
            AdaptiveText(
                text = song.artists.joinToString { it.name },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
fun MediaCard(
    title: String,
    subtitle: String,
    thumbnail: String?,
    onClick: () -> Unit,
    circle: Boolean = false
) {
    val imageShape = if (circle) CircleShape else RoundedCornerShape(8.dp)
    Card(
        onClick = onClick,
        modifier = Modifier.width(MediaCardWidth),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(imageShape),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnail.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Icon(
                            Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center).size(40.dp)
                        )
                    }
                } else {
                    AsyncImage(
                        model = thumbnail,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            AdaptiveText(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(2.dp))
            AdaptiveText(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
fun MediaRow(items: List<YTItem>, onOpenDetail: (DetailScreen) -> Unit) {
    val songs = items.filterIsInstance<SongItem>()
    val listState = rememberLazyListState()
    val scrollScope = rememberCoroutineScope()
    Box {
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.pointerInput(listState) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var totalDrag = 0f
                    var dragging = false
                    drag(down.id) {
                        val dx = it.position.x - it.previousPosition.x
                        totalDrag += dx
                        if (!dragging && abs(totalDrag) > viewConfiguration.touchSlop) {
                            dragging = true
                        }
                        if (dragging) {
                            it.consume()
                            scrollScope.launch { listState.scrollBy(-dx) }
                        }
                    }
                }
            }
        ) {
            items(items.take(12)) { item ->
            when (item) {
                is SongItem -> SongCard(
                    song = item,
                    onClick = { PlayerManager.playSong(item, songs) }
                )
                is AlbumItem -> MediaCard(
                    title = item.title,
                    subtitle = item.artists?.joinToString { it.name } ?: (item.year?.toString() ?: tr("Álbum")),
                    thumbnail = item.thumbnail,
                    onClick = {
                        onOpenDetail(
                            DetailScreen.Album(
                                browseId = item.browseId,
                                title = item.title,
                                thumbnail = item.thumbnail,
                                artists = item.artists
                            )
                        )
                    }
                )
                is PlaylistItem -> MediaCard(
                    title = item.title,
                    subtitle = item.author?.name ?: tr("Lista de reproducción"),
                    thumbnail = item.thumbnail,
                    onClick = {
                        onOpenDetail(
                            DetailScreen.Playlist(
                                playlistId = item.id,
                                title = item.title,
                                thumbnail = item.thumbnail
                            )
                        )
                    }
                )
                is ArtistItem -> MediaCard(
                    title = item.title,
                    subtitle = tr("Artista"),
                    thumbnail = item.thumbnail,
                    circle = true,
                    onClick = {
                        onOpenDetail(
                            DetailScreen.Artist(
                                browseId = item.id,
                                title = item.title,
                                thumbnail = item.thumbnail
                            )
                        )
                    }
                )
            }
        }
        }
        HorizontalScrollbar(
            adapter = rememberScrollbarAdapter(listState),
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
            style = LocalScrollbarStyle.current.copy(
                minimalHeight = 44.dp,
                thickness = 10.dp,
                shape = RoundedCornerShape(5.dp),
                hoverDurationMillis = 100
            )
        )
    }
}
@Composable
fun SongListItem(
    song: SongItem,
    onClick: () -> Unit,
    onLike: (() -> Unit)? = null,
    isLiked: Boolean = false,
    onDelete: (() -> Unit)? = null
) {
    var showAddToPlaylist by remember { mutableStateOf(false) }
    if (showAddToPlaylist) {
        AddToPlaylistDialog(song = song, onDismiss = { showAddToPlaylist = false })
    }
    val isCurrent = NowPlayingState.currentSongId.value == song.id
    val isCurrentPlaying = isCurrent && NowPlayingState.isPlaying.value
    val itemColor = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(64.dp),
        color = itemColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (isCurrent) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    )
                    EqualizerBars(color = MaterialTheme.colorScheme.primary, animated = isCurrentPlaying)
                } else if (song.thumbnail.isBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    AsyncImage(
                        model = song.thumbnail,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    song.artists.joinToString { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (onLike != null) {
                IconButton(onClick = onLike) {
                    Icon(
                        if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (isLiked) tr("Quitar Me gusta") else tr("Me gusta"),
                        tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = { showAddToPlaylist = true }) {
                Icon(
                    Icons.Outlined.PlaylistAdd,
                    contentDescription = tr("Añadir a lista"),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = tr("Quitar"),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun MoodChip(mood: com.arturo254.opentune.innertube.pages.MoodAndGenres.Item, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(60.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Text(mood.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun ErrorScreen(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(tr("Error: {0}", message), color = MaterialTheme.colorScheme.error)
    }
}

@Composable
fun EmptyScreen(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
