package com.arturo254.opentune

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode as InfiniteRepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.PopupProperties
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
import com.arturo254.opentune.library.FollowedArtistsManager
import com.arturo254.opentune.library.LikedSongsManager
import com.arturo254.opentune.library.ListenHistoryManager
import com.arturo254.opentune.library.LocalSongsManager
import com.arturo254.opentune.library.Playlist
import com.arturo254.opentune.library.PlaylistsManager
import com.arturo254.opentune.library.SearchHistoryManager
import com.arturo254.opentune.party.PartyModeManager
import com.arturo254.opentune.player.PlayerManager
import com.arturo254.opentune.player.RepeatMode
import com.arturo254.opentune.ui.EqualizerBars
import com.arturo254.opentune.ui.NowPlayingState
import com.arturo254.opentune.ui.theme.LumaMusicTheme
import java.awt.Component
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.util.Base64
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

/**
 * Lets a composable anywhere in the tree (e.g. a song's right-click context menu, several
 * levels away from App()'s navigation state) request a detail-screen navigation without
 * threading an onOpenDetail callback through every intermediate composable's signature.
 * App() observes pendingDetail and clears it once handled.
 */
object SongContextNav {
    var pendingDetail by mutableStateOf<DetailScreen?>(null)

    fun openArtist(browseId: String?, title: String, thumbnail: String?) {
        if (browseId.isNullOrBlank()) return
        pendingDetail = DetailScreen.Artist(browseId = browseId, title = title, thumbnail = thumbnail)
    }
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
    data object Experimental : SettingsSubScreen("Ajustes experimentales")
    data object About : SettingsSubScreen("Acerca de")
}

/**
 * Snapshot of "where the app is" for browser-style back/forward history. Plain data
 * class comparison (no extra composition), so tracking it costs nothing at runtime.
 */
private data class AppLocation(
    val screen: Screen,
    val detail: DetailScreen?,
    val settingsSub: SettingsSubScreen
)

@Composable
fun App() {
    LumaMusicTheme {
        var updateAvailable by remember { mutableStateOf<LatestRelease?>(null) }
        var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
        var settingsSubScreen by remember { mutableStateOf<SettingsSubScreen>(SettingsSubScreen.Main) }
        var detailScreen by remember { mutableStateOf<DetailScreen?>(null) }
        LaunchedEffect(SongContextNav.pendingDetail) {
            SongContextNav.pendingDetail?.let {
                detailScreen = it
                SongContextNav.pendingDetail = null
            }
        }
        var searchQuery by remember { mutableStateOf("") }
        var searchFieldFocused by remember { mutableStateOf(false) }
        var queueVisible by remember { mutableStateOf(false) }
        var lyricsVisible by remember { mutableStateOf(false) }
        var queuePanelTab by remember { mutableStateOf(0) }

        // Browser-style back/forward navigation history. Just two small lists of cheap
        // data-class snapshots — negligible memory, and pushing/popping is O(1), so this
        // adds no measurable overhead to navigation or RAM.
        val backStack = remember { mutableStateListOf<AppLocation>() }
        val forwardStack = remember { mutableStateListOf<AppLocation>() }
        var lastLocation by remember { mutableStateOf(AppLocation(currentScreen, detailScreen, settingsSubScreen)) }
        var isNavigatingHistory by remember { mutableStateOf(false) }

        LaunchedEffect(currentScreen, detailScreen, settingsSubScreen) {
            val newLocation = AppLocation(currentScreen, detailScreen, settingsSubScreen)
            if (newLocation != lastLocation) {
                if (!isNavigatingHistory) {
                    backStack.add(lastLocation)
                    forwardStack.clear()
                }
                lastLocation = newLocation
                isNavigatingHistory = false
            }
        }

        val canGoBack = backStack.isNotEmpty()
        val canGoForward = forwardStack.isNotEmpty()

        fun goBack() {
            if (backStack.isEmpty()) return
            val target = backStack.removeAt(backStack.lastIndex)
            forwardStack.add(lastLocation)
            isNavigatingHistory = true
            currentScreen = target.screen
            detailScreen = target.detail
            settingsSubScreen = target.settingsSub
        }

        fun goForward() {
            if (forwardStack.isEmpty()) return
            val target = forwardStack.removeAt(forwardStack.lastIndex)
            backStack.add(lastLocation)
            isNavigatingHistory = true
            currentScreen = target.screen
            detailScreen = target.detail
            settingsSubScreen = target.settingsSub
        }

        // Hoisted search state — survives navigation
        var searchResults by remember { mutableStateOf<SearchSummaryPage?>(null) }
        var searchSongs by remember { mutableStateOf<List<SongItem>>(emptyList()) }
        // Community/featured playlists matching the query - the default "top results" tab is
        // heavily biased toward official catalog songs, so a fan-made "complete OST" playlist
        // (which is exactly what shows up first on youtube.com/music.youtube.com) often never
        // surfaces there. Fetched explicitly so those results show up here too.
        var searchPlaylists by remember { mutableStateOf<List<PlaylistItem>>(emptyList()) }
        var searchLoading by remember { mutableStateOf(false) }
        var searchError by remember { mutableStateOf<String?>(null) }
        var searchHasQuery by remember { mutableStateOf(false) }
        var lastSearchedQuery by remember { mutableStateOf("") }
        var searchSuggestionsList by remember { mutableStateOf<List<String>>(emptyList()) }
        val searchScrollState = rememberLazyListState()

        // Real photos for followed artists, shared by the left rail and Home's "Más de
        // tus artistas" row - fetched once here instead of separately in each, so opening
        // both doesn't double the network calls. Backed by FollowedArtistsManager (artists
        // the user explicitly followed/subscribed to), not listening history, so this stays
        // under the user's control instead of surfacing anyone they've merely played.
        val followedArtistsData = FollowedArtistsManager.artists
        val recentArtistsBase = remember(followedArtistsData) {
            followedArtistsData.map { Artist(name = it.name, id = it.id) }
        }
        var recentArtists by remember(followedArtistsData) {
            mutableStateOf(
                followedArtistsData.map { fa ->
                    ArtistItem(
                        id = fa.id,
                        title = fa.name,
                        thumbnail = fa.thumbnail,
                        shuffleEndpoint = null,
                        radioEndpoint = null
                    )
                }
            )
        }
        var recentArtistSongs by remember { mutableStateOf<List<SongItem>>(emptyList()) }
        val recentArtistsKey = remember(recentArtistsBase) { recentArtistsBase.take(8).joinToString(",") { it.id.orEmpty() } }
        LaunchedEffect(recentArtistsKey) {
            if (recentArtistsBase.isEmpty()) { recentArtistSongs = emptyList(); return@LaunchedEffect }
            val fetchedArtists = mutableListOf<ArtistItem>()
            val fetchedSongs = mutableListOf<SongItem>()
            coroutineScope {
                val deferred = recentArtistsBase.take(8).map { artist -> async { YouTube.artist(artist.id!!) } }
                deferred.forEach { d ->
                    d.await().onSuccess { page ->
                        fetchedArtists += page.artist
                        page.sections.firstOrNull { s -> s.items.any { it is SongItem } }
                            ?.items?.filterIsInstance<SongItem>()?.let { fetchedSongs += it }
                    }
                }
            }
            if (fetchedArtists.isNotEmpty()) {
                recentArtists = fetchedArtists.distinctBy { it.id }
            }
            recentArtistSongs = fetchedSongs.distinctBy { it.id }
        }

        // Lightweight, faster-than-search suggestions (e.g. "juice" -> "Juice WRLD") for
        // the dropdown under the search field - a separate, shorter debounce so they feel
        // instant without adding load to the heavier full-results search below.
        LaunchedEffect(searchQuery) {
            if (searchQuery.isBlank()) {
                searchSuggestionsList = emptyList()
                return@LaunchedEffect
            }
            kotlinx.coroutines.delay(200)
            YouTube.searchSuggestions(searchQuery).onSuccess { s ->
                if (searchQuery.isNotBlank()) searchSuggestionsList = s.queries.take(6)
            }
        }

        // Debounced auto-search: waits 400ms after last keystroke
        LaunchedEffect(searchQuery) {
            if (searchQuery.isBlank()) {
                searchHasQuery = false
                searchResults = null
                searchSongs = emptyList()
                searchPlaylists = emptyList()
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
                    // Community playlists explicitly, in parallel - these are where fan-made
                    // "full game soundtrack" compilations live, and the algorithmic top-results
                    // tab above rarely surfaces them on its own.
                    val playlistsDeferred = async { YouTube.search(searchQuery, YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST) }
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
                    playlistsDeferred.await().onSuccess { result ->
                        if (searchQuery == lastSearchedQuery || result.items.isNotEmpty()) {
                            searchPlaylists = result.items.filterIsInstance<PlaylistItem>()
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
            val detail = detailScreen
            val inSettingsSub = currentScreen is Screen.Settings && settingsSubScreen !is SettingsSubScreen.Main
            val showBack = inSettingsSub || detail != null
            val backTitle = when {
                inSettingsSub -> (settingsSubScreen as SettingsSubScreen).label
                detail != null -> detail.title
                else -> null
            }

            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            RecentArtistsRail(
                artists = recentArtists,
                librarySelected = !showBack && currentScreen == Screen.Library,
                onLibraryClick = {
                    currentScreen = Screen.Library
                    detailScreen = null
                },
                onOpenDetail = { detailScreen = it }
            )
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                AppTopNav(
                    currentScreen = currentScreen,
                    onScreenSelected = {
                        currentScreen = it
                        detailScreen = null
                        if (it is Screen.Settings) settingsSubScreen = SettingsSubScreen.Main
                    },
                    showBack = showBack,
                    backTitle = backTitle,
                    onBack = {
                        if (canGoBack) goBack()
                        else if (inSettingsSub) settingsSubScreen = SettingsSubScreen.Main
                        else detailScreen = null
                    },
                    canGoBack = canGoBack,
                    canGoForward = canGoForward,
                    onNavigateBack = { goBack() },
                    onNavigateForward = { goForward() },
                    searchQuery = searchQuery,
                    onSearchQueryChange = {
                        searchQuery = it
                        // The search icon nav button is gone - the field itself is what
                        // takes you to the results screen now, the moment there's text.
                        if (it.isNotBlank() && currentScreen !is Screen.Search) {
                            currentScreen = Screen.Search
                            detailScreen = null
                        }
                    },
                    onSearch = {
                        if (searchQuery.isNotBlank()) SearchHistoryManager.add(searchQuery)
                    },
                    onSearchFieldFocusChange = {
                        searchFieldFocused = it
                        // Focusing the field also opens the search screen (search history
                        // etc), same as clicking Spotify's search bar before typing.
                        if (it && currentScreen !is Screen.Search) {
                            currentScreen = Screen.Search
                            detailScreen = null
                        }
                    },
                    onAccountClick = {
                        currentScreen = Screen.Settings
                        settingsSubScreen = SettingsSubScreen.Account
                        detailScreen = null
                    },
                    searchSuggestions = searchSuggestionsList,
                    onSuggestionClick = { suggestion ->
                        searchQuery = suggestion
                        searchSuggestionsList = emptyList()
                        SearchHistoryManager.add(suggestion)
                        currentScreen = Screen.Search
                        detailScreen = null
                    }
                )

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    // Smooth cross-fade between screens/detail pages instead of an instant
                    // cut. Only one screen's content is actually composed/laid out at a
                    // time (the other is fading in/out of a cheap alpha animation), so this
                    // doesn't keep extra screens around in memory.
                    Crossfade(
                        targetState = Triple(detail, currentScreen, settingsSubScreen),
                        animationSpec = tween(200)
                    ) { (crossDetail, crossScreen, crossSettingsSub) ->
                        if (crossDetail != null) {
                            when (crossDetail) {
                                is DetailScreen.Album -> AlbumScreen(
                                    browseId = crossDetail.browseId,
                                    fallbackTitle = crossDetail.title,
                                    fallbackThumbnail = crossDetail.thumbnail,
                                    fallbackArtists = crossDetail.artists
                                )
                                is DetailScreen.Playlist -> PlaylistScreen(
                                    playlistId = crossDetail.playlistId,
                                    fallbackTitle = crossDetail.title,
                                    fallbackThumbnail = crossDetail.thumbnail
                                )
                                is DetailScreen.Artist -> ArtistScreen(
                                    browseId = crossDetail.browseId,
                                    fallbackTitle = crossDetail.title,
                                    fallbackThumbnail = crossDetail.thumbnail,
                                    onOpenDetail = { detailScreen = it }
                                )
                                is DetailScreen.LocalPlaylist -> LocalPlaylistScreen(
                                    playlistId = crossDetail.playlistId,
                                    onBack = { detailScreen = null }
                                )
                            }
                        } else {
                            when (crossScreen) {
                            is Screen.Home -> HomeScreen(
                                onOpenDetail = { detailScreen = it },
                                recentArtists = recentArtists,
                                recentArtistSongs = recentArtistSongs
                            )
                            is Screen.Search -> SearchScreen(
                                query = searchQuery,
                                results = searchResults,
                                songs = searchSongs,
                                playlists = searchPlaylists,
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
                                when (crossSettingsSub) {
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
                                    is SettingsSubScreen.Experimental -> ExperimentalSettings(onBack = { settingsSubScreen = SettingsSubScreen.Main })
                                    is SettingsSubScreen.About -> AboutScreen(onBack = { settingsSubScreen = SettingsSubScreen.Main })
                                }
                            }
                        }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = queueVisible,
                enter = fadeIn(tween(180)) + slideInHorizontally(tween(220)) { it / 3 },
                exit = fadeOut(tween(150)) + slideOutHorizontally(tween(200)) { it / 3 }
            ) {
                QueuePanel(
                    selectedTab = queuePanelTab,
                    onTabSelected = { queuePanelTab = it },
                    onClose = { queueVisible = false },
                    modifier = Modifier.width(340.dp).fillMaxHeight()
                )
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

        if (lyricsVisible) {
            LyricsScreen(onDismiss = { lyricsVisible = false })
        }
    }
}
}

/**
 * Single persistent top bar, Spotify-style: icon-only navigation (with hover tooltips)
 * always visible, a back button + title when viewing a detail/settings sub-screen, and
 * the search field on the right when not. Replaces the old left NavigationRail + the
 * separate per-screen top bars, so there's one Surface/one composable instead of three -
 * fewer nodes in the tree, not more, so this is neutral to slightly cheaper at runtime.
 */
@Composable
fun AppTopNav(
    currentScreen: Screen,
    onScreenSelected: (Screen) -> Unit,
    showBack: Boolean,
    backTitle: String?,
    onBack: () -> Unit,
    canGoBack: Boolean = false,
    canGoForward: Boolean = false,
    onNavigateBack: () -> Unit = onBack,
    onNavigateForward: () -> Unit = {},
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearch: () -> Unit = {},
    onSearchFieldFocusChange: (Boolean) -> Unit = {},
    onAccountClick: () -> Unit = {},
    searchSuggestions: List<String> = emptyList(),
    onSuggestionClick: (String) -> Unit = {}
) {
    var fieldFocused by remember { mutableStateOf(false) }
    // Library now lives at the top of the left artists rail instead of up here - Home
    // sits right beside the search field, Explore right on its other corner, and
    // Settings is paired with the account icon on the far right.

    Surface(
        modifier = Modifier.fillMaxWidth().height(68.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Browser-style back/forward, Spotify-style: two small round buttons, dimmed
            // when there's nowhere to go. Cheap state reads, no extra composition cost.
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(onClick = onNavigateBack, enabled = canGoBack) {
                    Icon(
                        Icons.Filled.ArrowBack,
                        contentDescription = tr("Atrás"),
                        tint = if (canGoBack) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    )
                }
                IconButton(onClick = onNavigateForward, enabled = canGoForward) {
                    Icon(
                        Icons.Filled.ArrowForward,
                        contentDescription = tr("Adelante"),
                        tint = if (canGoForward) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // When viewing a detail/settings page, the page title takes the wordmark's
            // spot instead of blocking the search field - the search bar now stays
            // usable everywhere (fixes not being able to search from an artist page).
            if (showBack) {
                Text(
                    backTitle.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 220.dp).padding(end = 20.dp)
                )
            } else {
                Text(
                    "Luma",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(end = 20.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Home sits right on the search field's left corner...
            NavIconButton(
                screen = Screen.Home,
                selected = !showBack && currentScreen == Screen.Home,
                onClick = { onScreenSelected(Screen.Home) }
            )
            Spacer(modifier = Modifier.width(8.dp))

            Box(modifier = Modifier.weight(2f), contentAlignment = Alignment.Center) {
                // Spotify-style pill search: filled background, no border, rounded full,
                // centered in the remaining space rather than pinned right. Always
                // visible/functional, even while viewing a detail page.
                OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = {
                            Text(
                                tr("¿Qué quieres reproducir?"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(Icons.Filled.Close, contentDescription = tr("Limpiar"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                        shape = RoundedCornerShape(50),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            cursorColor = MaterialTheme.colorScheme.primary,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        ),
                        modifier = Modifier
                            .widthIn(min = 120.dp, max = 420.dp)
                            .fillMaxWidth()
                            .onFocusChanged {
                                fieldFocused = it.isFocused
                                onSearchFieldFocusChange(it.isFocused)
                            }
                    )

                    // Lightweight suggestions dropdown ("juice" -> "Juice WRLD"...) so
                    // finding an artist/song doesn't require typing the full name.
                    DropdownMenu(
                        expanded = fieldFocused && searchQuery.isNotBlank() && searchSuggestions.isNotEmpty(),
                        onDismissRequest = {},
                        properties = PopupProperties(focusable = false),
                        modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth()
                    ) {
                        searchSuggestions.forEach { suggestion ->
                            DropdownMenuItem(
                                text = { Text(suggestion, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                leadingIcon = {
                                    Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                },
                                onClick = { onSuggestionClick(suggestion) }
                            )
                        }
                    }
            }

            // ...and Explore sits right on its right corner.
            Spacer(modifier = Modifier.width(8.dp))
            NavIconButton(
                screen = Screen.Explore,
                selected = !showBack && currentScreen == Screen.Explore,
                onClick = { onScreenSelected(Screen.Explore) }
            )

            Spacer(modifier = Modifier.weight(1f))

            NavIconButton(
                screen = Screen.Settings,
                selected = !showBack && currentScreen == Screen.Settings,
                onClick = { onScreenSelected(Screen.Settings) }
            )
            Spacer(modifier = Modifier.width(4.dp))
            val accountThumbnail = AccountManager.accountInfo?.thumbnailUrl
            // With no YouTube account linked, the local guest profile's photo stands in for it,
            // so whoever is using the app sees themselves up here either way.
            val guestAvatar = GuestProfileManager.avatarFile
            IconButton(onClick = onAccountClick) {
                when {
                    accountThumbnail != null -> AsyncImage(
                        model = accountThumbnail,
                        contentDescription = tr("Cuenta"),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(30.dp).clip(CircleShape)
                    )
                    guestAvatar != null -> AsyncImage(
                        model = guestAvatar,
                        contentDescription = tr("Cuenta"),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(30.dp).clip(CircleShape)
                    )
                    // A named guest with no photo still gets their initial instead of a generic icon.
                    GuestProfileManager.exists -> Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            GuestProfileManager.name.take(1).uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    else -> Icon(
                        Icons.Filled.AccountCircle,
                        contentDescription = tr("Cuenta"),
                        modifier = Modifier.size(30.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun NavIconButton(screen: Screen, selected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    // Smooth, cheap animations: just interpolating a couple of colors and a tiny scale
    // factor - no extra composables, no layout passes, negligible cost either way.
    val background by animateColorAsState(
        targetValue = when {
            selected -> MaterialTheme.colorScheme.primaryContainer
            hovered -> MaterialTheme.colorScheme.surfaceContainerHigh
            else -> Color.Transparent
        },
        animationSpec = tween(160)
    )
    val tint by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(160)
    )
    val scale by animateFloatAsState(
        targetValue = if (hovered && !selected) 1.04f else 1f,
        animationSpec = tween(120)
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .hoverable(interactionSource)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Icon(screen.icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(
            screen.label,
            color = tint,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/**
 * Thin left rail: the Library button pinned at the top, then recently-played artists
 * below it, Spotify-style shortcut column. Reuses the same fetched artist photos as
 * Home's "Más de tus artistas" row (passed in from App()), so having both on screen at
 * once doesn't double the network calls.
 */
@Composable
private fun RecentArtistsRail(
    artists: List<ArtistItem>,
    librarySelected: Boolean,
    onLibraryClick: () -> Unit,
    onOpenDetail: (DetailScreen) -> Unit
) {
    Column(
        modifier = Modifier.width(80.dp).fillMaxHeight().padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val libraryTint = if (librarySelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(72.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (librarySelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                .clickable(onClick = onLibraryClick)
                .padding(vertical = 8.dp, horizontal = 4.dp)
        ) {
            Icon(Screen.Library.icon, contentDescription = null, tint = libraryTint, modifier = Modifier.size(26.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                Screen.Library.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = libraryTint,
                fontWeight = if (librarySelected) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (artists.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(modifier = Modifier.width(44.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items(artists, key = { it.id }) { artist ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        onOpenDetail(DetailScreen.Artist(browseId = artist.id, title = artist.title, thumbnail = artist.thumbnail))
                    }
                    .padding(vertical = 6.dp, horizontal = 4.dp)
            ) {
                Box(modifier = Modifier.size(52.dp).clip(CircleShape)) {
                    if (!artist.thumbnail.isNullOrBlank()) {
                        AsyncImage(
                            model = artist.thumbnail,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    artist.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
                }
            }
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
    // Reflects DownloadsManager.downloadingIds (cleared automatically when the download
    // finishes or fails) instead of a fragile local flag that never reset itself.
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
            // While the room is holding a song for somebody who is still loading it, this is a
            // wait, not a pause - show it as loading so nobody thinks the player got stuck.
            isLoading = PlayerManager.isLoading || PartyModeManager.waitingForOthers
            position = PlayerManager.smoothPosition()
            duration = PlayerManager.duration
            repeatMode = PlayerManager.repeatMode
            isLiked = s?.let { LikedSongsManager.isLiked(it.id) } == true
            isDownloaded = s?.let { DownloadsManager.isDownloaded(it.id) } == true
            isDownloading = s?.let { DownloadsManager.downloadingIds.contains(it.id) } == true
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
                    Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        val artworkSize = if (isDownloading) 40.dp else 48.dp
                        if (current.thumbnail.isBlank()) {
                            Box(
                                modifier = Modifier
                                    .size(artworkSize)
                                    .clip(MaterialTheme.shapes.small)
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
                                modifier = Modifier.size(artworkSize).clip(MaterialTheme.shapes.small),
                                contentScale = ContentScale.Crop
                            )
                        }
                        if (isDownloading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
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
                    if (song?.id?.startsWith("local:") != true) {
                        IconButton(onClick = {
                            song?.let { s ->
                                runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(s.shareLink)) }
                            }
                        }) {
                            Icon(
                                Icons.Filled.SmartDisplay,
                                contentDescription = tr("Ver video"),
                                tint = MaterialTheme.colorScheme.onSurface
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

/**
 * Persistent right-side panel (Spotify-style), replacing the old modal queue dialog.
 * It's a plain Surface embedded as a Row sibling next to the main content - no Dialog
 * window, no extra overlay layer - so it's actually cheaper than the popup it replaces.
 * Two tabs: current queue, and recently played (from ListenHistoryManager).
 */
@Composable
fun QueuePanel(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
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

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 3.dp,
        modifier = modifier.onPreviewKeyEvent { keyEvent ->
            if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Escape) {
                onClose(); true
            } else false
        }
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    tr("Cola de reproducción"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = tr("Cerrar"))
                }
            }

            if (DesktopPreferences.partyModeEnabled) {
                PartyModeBar()
            }

            Spacer(modifier = Modifier.height(4.dp))

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { onTabSelected(0) },
                    text = { Text(tr("Cola ({0})", queue.size), fontSize = 13.sp) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { onTabSelected(1) },
                    text = { Text(tr("Escuchado recientemente"), fontSize = 13.sp) }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (selectedTab == 0) {
                if (queue.isNotEmpty()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { PlayerManager.clearQueue() }) {
                            Text(tr("Vaciar"), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                    }
                }
                if (queue.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            tr("La cola está vacía"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        itemsIndexed(queue) { index, song ->
                            val isCurrent = index == currentIndex
                            val nowPlayingId = PlayerManager.currentSong?.id
                            QueuePanelRow(
                                song = song,
                                highlighted = isCurrent,
                                showEqualizer = song.id == nowPlayingId,
                                equalizerAnimated = isPlaying,
                                onClick = { PlayerManager.jumpToIndex(index) },
                                trailing = {
                                    IconButton(onClick = { PlayerManager.removeFromQueue(index) }) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = tr("Quitar de la cola"),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            } else {
                val history = remember { ListenHistoryManager.entries.asReversed() }
                val nowPlayingId = PlayerManager.currentSong?.id
                if (history.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            tr("Aún no has escuchado nada"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(history) { song ->
                            QueuePanelRow(
                                song = song,
                                highlighted = song.id == nowPlayingId,
                                showEqualizer = song.id == nowPlayingId,
                                equalizerAnimated = isPlaying,
                                onClick = { PlayerManager.playSong(song, history) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueuePanelRow(
    song: SongItem,
    highlighted: Boolean,
    showEqualizer: Boolean,
    equalizerAnimated: Boolean,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    var showAddToPlaylist by remember { mutableStateOf(false) }
    if (showAddToPlaylist) {
        AddToPlaylistDialog(song = song, onDismiss = { showAddToPlaylist = false })
    }
    var showContextMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (highlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .onRightClick(song.id) { showContextMenu = true }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Downloading state is read straight from DownloadsManager's Compose state, so this
        // row recomposes on its own when a download starts/finishes.
        val isDownloading = DownloadsManager.downloadingIds.contains(song.id)
        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            val artworkSize = if (isDownloading) 33.dp else 40.dp
            if (song.thumbnail.isNotBlank()) {
                AsyncImage(
                    model = song.thumbnail,
                    contentDescription = null,
                    modifier = Modifier
                        .size(artworkSize)
                        .clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(artworkSize)
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
            if (isDownloading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(40.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (showEqualizer) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(3.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 3.dp, vertical = 2.dp)
                ) {
                    EqualizerBars(
                        color = MaterialTheme.colorScheme.primary,
                        animated = equalizerAnimated,
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
                fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal
            )
            Text(
                song.artists.joinToString { it.name },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (trailing != null) trailing()
        SongContextMenu(
            song = song,
            expanded = showContextMenu,
            onDismiss = { showContextMenu = false },
            onAddToPlaylist = { showAddToPlaylist = true }
        )
    }
}

// ===================== PARTY MODE =====================

@Composable
private fun PartyAvatar(participant: com.arturo254.opentune.party.PartyParticipantDto, size: androidx.compose.ui.unit.Dp = 28.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .border(2.dp, MaterialTheme.colorScheme.surfaceContainerLow, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        val bitmap = remember(participant.avatarBase64) {
            participant.avatarBase64?.let { b64 ->
                runCatching {
                    val bytes = Base64.getDecoder().decode(b64)
                    org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
                }.getOrNull()
            }
        }
        when {
            bitmap != null -> Image(
                bitmap = bitmap,
                contentDescription = participant.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            !participant.avatarUrl.isNullOrBlank() -> AsyncImage(
                model = participant.avatarUrl,
                contentDescription = participant.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            else -> Text(
                participant.name.take(1).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** The row shown at the top of [QueuePanel] when Party Mode is on: either "Crear sala" /
 * "Unirse a sala" buttons, or - once in a room - everyone currently listening together. */
@Composable
private fun PartyModeBar() {
    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    if (showCreateDialog) PartyCreateDialog(onDismiss = { showCreateDialog = false })
    if (showJoinDialog) PartyJoinDialog(onDismiss = { showJoinDialog = false })

    // PartyModeManager's state is backed by mutableStateOf, so reading it here is enough for
    // Compose to recompose on its own - no polling loop needed (unlike PlayerManager's plain
    // vars elsewhere in this file, which do need one).
    val participants = PartyModeManager.participants
    val inRoom = PartyModeManager.inRoom

    Column {
        if (inRoom) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy((-8).dp)
                ) {
                    participants.take(5).forEach { p -> PartyAvatar(p) }
                }
                if (participants.size > 5) {
                    Text(
                        "+${participants.size - 5}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                }
                if (PartyModeManager.role == PartyModeManager.Role.HOST) {
                    TextButton(onClick = { showCreateDialog = true }) {
                        Text(tr("Datos de la sala"), fontSize = 12.sp)
                    }
                }
                TextButton(onClick = { PartyModeManager.leaveRoom() }) {
                    Text(tr("Salir de la sala"), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { showCreateDialog = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Celebration, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(tr("Crear sala"), fontSize = 12.sp)
                }
                OutlinedButton(onClick = { showJoinDialog = true }, modifier = Modifier.weight(1f)) {
                    Text(tr("Unirse a sala"), fontSize = 12.sp)
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(bottom = 4.dp))
    }
}

@Composable
private fun PartyCreateDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var created by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var copiedField by remember { mutableStateOf<String?>(null) }

    fun copyToClipboard(text: String, field: String) {
        scope.launch {
            withContext(Dispatchers.IO) {
                val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                clipboard.setContents(java.awt.datatransfer.StringSelection(text), null)
            }
            copiedField = field
        }
    }

    LaunchedEffect(Unit) {
        // Reopened while already hosting: just show the existing room's details again.
        if (PartyModeManager.role == PartyModeManager.Role.HOST) {
            created = true
            loading = false
            return@LaunchedEffect
        }
        PartyModeManager.createRoom().onSuccess {
            created = true
        }.onFailure {
            error = it.message
        }
        loading = false
    }

    Dialog(onDismissRequest = { if (!loading) onDismiss() }) {
        Surface(modifier = Modifier.width(400.dp), shape = MaterialTheme.shapes.extraLarge) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(tr("Crear sala"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(16.dp))
                when {
                    loading -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                tr("Conectando con el servidor de salas..."),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    error != null -> {
                        Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                    created -> {
                        Text(
                            tr("Comparte el código y el PIN con tu amigo (por chat, WhatsApp, etc.). Funciona desde cualquier internet, sin configurar nada en el router."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                            OutlinedTextField(
                                value = PartyModeManager.roomCode,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(tr("Código de sala")) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = { copyToClipboard(PartyModeManager.roomCode, "code") }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = tr("Copiar código"))
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                            OutlinedTextField(
                                value = PartyModeManager.roomPin,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(tr("PIN")) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = { copyToClipboard(PartyModeManager.roomPin, "pin") }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = tr("Copiar PIN"))
                            }
                        }
                        if (copiedField != null) {
                            Text(
                                tr("¡Copiado!"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = {
                            // "Listo" only closes this window - the room stays open so the friend
                            // can join. Leaving is done with "Salir de la sala" in the queue panel.
                            if (error != null) PartyModeManager.leaveRoom()
                            onDismiss()
                        },
                        enabled = !loading
                    ) { Text(if (created) tr("Listo") else tr("Cerrar")) }
                }
            }
        }
    }
}

@Composable
private fun PartyJoinDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var codeText by remember { mutableStateOf("") }
    var pinText by remember { mutableStateOf("") }
    var joining by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = { if (!joining) onDismiss() }) {
        Surface(modifier = Modifier.width(400.dp), shape = MaterialTheme.shapes.extraLarge) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(tr("Unirse a sala"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    tr("Pega el código de sala y el PIN que te compartió tu amigo."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                OutlinedTextField(
                    value = codeText,
                    onValueChange = { new -> if (new.length <= 12) { codeText = new.uppercase(); error = null } },
                    label = { Text(tr("Código de sala")) },
                    singleLine = true,
                    enabled = !joining,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = pinText,
                    onValueChange = { new -> if (new.length <= 4 && new.all { it.isDigit() }) { pinText = new; error = null } },
                    label = { Text(tr("PIN")) },
                    singleLine = true,
                    enabled = !joining,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (joining) {
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !joining) { Text(tr("Cancelar")) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            joining = true
                            error = null
                            scope.launch {
                                PartyModeManager.joinRoom(codeText, pinText).onSuccess {
                                    onDismiss()
                                }.onFailure {
                                    error = it.message ?: tr("No se pudo unir a la sala")
                                }
                                joining = false
                            }
                        },
                        enabled = codeText.count { it.isLetterOrDigit() } == 8 && pinText.length == 4 && !joining
                    ) { Text(tr("Unirse")) }
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

    // Ticks fast and on its own so the highlighted line follows the audio closely: the fetch
    // below can block for seconds, and PlayerManager.position only refreshes a few times a
    // second, which together left the lyrics visibly trailing the song.
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60)
            position = PlayerManager.smoothPosition()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(250)
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
fun HomeScreen(
    onOpenDetail: (DetailScreen) -> Unit,
    recentArtists: List<ArtistItem> = emptyList(),
    recentArtistSongs: List<SongItem> = emptyList()
) {
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
            if (recentArtistSongs.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            tr("Más de tus artistas"),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        MediaRow(items = recentArtistSongs, onOpenDetail = onOpenDetail)
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
            // Recently-played artists now live in the left rail (RecentArtistsRail)
            // instead of a row at the bottom of Home, using the same recentArtists data.
        }
    }
}

// ===================== SEARCH =====================
@Composable
fun SearchScreen(
    query: String,
    results: SearchSummaryPage?,
    songs: List<SongItem>,
    playlists: List<PlaylistItem>,
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
            // Community playlists, e.g. a fan-made "Super Mario Galaxy - Complete OST" -
            // fetched explicitly above since the algorithmic top-results tab favors official
            // catalog songs and often leaves these out entirely. Deduplicated against whatever
            // the summaries above already showed so the same playlist isn't rendered twice.
            run {
                val alreadyShownIds = summaries.flatMap { it.items }.map { it.id }.toSet()
                val extraPlaylists = playlists.filterNot { it.id in alreadyShownIds }
                if (extraPlaylists.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                tr("Listas de reproducción"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            MediaRow(items = extraPlaylists, onOpenDetail = onOpenDetail)
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
    val scope = rememberCoroutineScope()
    // "Mostrar todo" state: the artist page itself only carries the first handful of popular
    // songs, so the full list is fetched on demand from the shelf's own endpoint (same thing
    // YouTube Music does when you press its "Mostrar todo").
    var allSongs by remember(browseId) { mutableStateOf<List<SongItem>>(emptyList()) }
    var showingAll by remember(browseId) { mutableStateOf(false) }
    var loadingAll by remember(browseId) { mutableStateOf(false) }
    var allSongsError by remember(browseId) { mutableStateOf<String?>(null) }

    LaunchedEffect(browseId) {
        loading = true
        error = null
        YouTube.artist(browseId).onSuccess { p ->
            page = p
            loading = false
            // If the linked YouTube account is already subscribed to this artist (from
            // YouTube Music itself, not from tapping "Seguir" here), pick that up so it
            // shows in the followed-artists rail without the user having to re-follow it.
            if (p.artist.subscribed && !FollowedArtistsManager.isFollowing(p.artist.id)) {
                FollowedArtistsManager.follow(p.artist.id, p.artist.title.ifBlank { fallbackTitle }, p.artist.thumbnail ?: fallbackThumbnail)
            }
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
            // Spotify-style artist page: the first section with actual songs becomes the
            // numbered "Populares" list under the hero banner; everything else (albums,
            // singles, related artists, etc.) keeps the horizontal card rows below it.
            val topSongsSection = remember(page) { page!!.sections.firstOrNull { s -> s.items.any { it is SongItem } } }
            val topSongs = remember(topSongsSection) {
                topSongsSection?.items?.filterIsInstance<SongItem>()?.take(10) ?: emptyList()
            }
            val otherSections = remember(page, topSongsSection) {
                page!!.sections.filter { it !== topSongsSection }
            }
            // What the list actually shows: the short list, or everything once it's loaded.
            val shownSongs = if (showingAll && allSongs.isNotEmpty()) allSongs else topSongs
            val sectionSongCount = topSongsSection?.items?.count { it is SongItem } ?: 0
            val canShowAll = topSongsSection != null &&
                (topSongsSection.moreEndpoint != null || sectionSongCount > topSongs.size)
            // Whether one of this artist's popular songs is the one actually loaded/playing
            // right now, so the hero button shows a pause icon (and toggles pause on click)
            // instead of always looking like it's paused.
            val isCurrentArtistSong = topSongs.any { it.id == NowPlayingState.currentSongId.value }
            val isArtistPlaying = isCurrentArtistSong && NowPlayingState.isPlaying.value
            val isFollowing = FollowedArtistsManager.isFollowing(artist.id)

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    ArtistHero(
                        artist = artist,
                        fallbackTitle = fallbackTitle,
                        fallbackThumbnail = fallbackThumbnail,
                        description = page!!.description,
                        isPlaying = isArtistPlaying,
                        onPlayPause = {
                            if (isCurrentArtistSong) {
                                PlayerManager.playPause()
                            } else if (topSongs.isNotEmpty()) {
                                PlayerManager.playSong(topSongs.first(), topSongs)
                            }
                        },
                        onShuffle = {
                            if (topSongs.isNotEmpty()) {
                                val shuffled = topSongs.shuffled()
                                PlayerManager.playSong(shuffled.first(), shuffled)
                            }
                        },
                        isFollowing = isFollowing,
                        onToggleFollow = {
                            val channelId = artist.channelId ?: artist.id
                            val wasFollowing = isFollowing
                            FollowedArtistsManager.toggle(
                                artist.id,
                                artist.title.ifBlank { fallbackTitle },
                                artist.thumbnail ?: fallbackThumbnail
                            )
                            if (AccountManager.isLinked) {
                                scope.launch {
                                    YouTube.subscribeChannel(channelId, !wasFollowing)
                                }
                            }
                        }
                    )
                }

                if (topSongs.isNotEmpty()) {
                    item {
                        Text(
                            tr("Populares"),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                        )
                    }
                    itemsIndexed(shownSongs, key = { _, song -> song.id }) { index, song ->
                        ArtistTopSongRow(
                            index = index + 1,
                            song = song,
                            onClick = { PlayerManager.playSong(song, shownSongs) }
                        )
                    }

                    if (canShowAll) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (showingAll) {
                                            showingAll = false
                                        } else if (allSongs.isNotEmpty()) {
                                            showingAll = true
                                        } else {
                                            scope.launch {
                                                loadingAll = true
                                                allSongsError = null
                                                val loaded = loadAllArtistSongs(topSongsSection)
                                                loaded.onSuccess { songs ->
                                                    allSongs = songs
                                                    showingAll = songs.isNotEmpty()
                                                    if (songs.isEmpty()) allSongsError = tr("No se pudieron cargar más canciones")
                                                }.onFailure {
                                                    allSongsError = tr("No se pudieron cargar más canciones")
                                                }
                                                loadingAll = false
                                            }
                                        }
                                    },
                                    enabled = !loadingAll
                                ) {
                                    if (loadingAll) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text(if (showingAll) tr("Mostrar menos") else tr("Mostrar todo"), fontSize = 13.sp)
                                }
                                if (showingAll && allSongs.isNotEmpty()) {
                                    Text(
                                        tr("{0} canciones", allSongs.size.toString()),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 12.dp)
                                    )
                                }
                                allSongsError?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(start = 12.dp)
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }

                otherSections.forEach { section ->
                    if (section.items.isNotEmpty()) {
                        item(key = "section-${section.title}") {
                            ArtistSectionRow(section = section, onOpenDetail = onOpenDetail)
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

/** One card for any kind of item an artist/home shelf can hold - shared by the horizontal rows
 * and by the grid a shelf expands into, so both always look and behave the same. */
@Composable
private fun MediaItemCard(item: YTItem, songs: List<SongItem>, onOpenDetail: (DetailScreen) -> Unit) {
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

/**
 * One shelf of an artist's page (albums, singles and EPs, appearances...). Like YouTube Music,
 * a shelf that has more than the handful shown gets a "Más" button that loads the rest and lays
 * them out in a grid, instead of leaving them unreachable behind a horizontal row.
 */
@Composable
private fun ArtistSectionRow(
    section: com.arturo254.opentune.innertube.pages.ArtistSection,
    onOpenDetail: (DetailScreen) -> Unit
) {
    val scope = rememberCoroutineScope()
    var allItems by remember(section) { mutableStateOf<List<YTItem>>(emptyList()) }
    var expanded by remember(section) { mutableStateOf(false) }
    var loading by remember(section) { mutableStateOf(false) }
    var failed by remember(section) { mutableStateOf(false) }

    Column(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                section.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            if (section.moreEndpoint != null) {
                TextButton(
                    onClick = {
                        if (expanded) {
                            expanded = false
                        } else if (allItems.isNotEmpty()) {
                            expanded = true
                        } else {
                            scope.launch {
                                loading = true
                                failed = false
                                loadAllArtistItems(section)
                                    .onSuccess { items ->
                                        allItems = items
                                        expanded = items.isNotEmpty()
                                        failed = items.isEmpty()
                                    }
                                    .onFailure { failed = true }
                                loading = false
                            }
                        }
                    },
                    enabled = !loading
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (expanded) tr("Mostrar menos") else tr("Más"), fontSize = 13.sp)
                }
            }
        }

        if (failed) {
            Text(
                tr("No se pudo cargar el resto"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (expanded && allItems.isNotEmpty()) {
            // Everything at once, in as many columns as the window currently fits.
            val sectionSongs = allItems.filterIsInstance<SongItem>()
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val columns = ((maxWidth + 12.dp) / (MediaCardWidth + 12.dp)).toInt().coerceAtLeast(1)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    allItems.chunked(columns).forEach { rowItems ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            rowItems.forEach { item ->
                                MediaItemCard(item = item, songs = sectionSongs, onOpenDetail = onOpenDetail)
                            }
                        }
                    }
                }
            }
        } else {
            MediaRow(items = section.items, onOpenDetail = onOpenDetail)
        }
    }
}

/** Every album/single/etc. behind a shelf's "Más", following YouTube's own paging. */
private suspend fun loadAllArtistItems(section: com.arturo254.opentune.innertube.pages.ArtistSection): Result<List<YTItem>> {
    val endpoint = section.moreEndpoint ?: return Result.success(section.items)
    return runCatching {
        val first = YouTube.artistItems(endpoint).getOrThrow()
        val items = mutableListOf<YTItem>()
        items += first.items
        var continuation = first.continuation
        var pages = 0
        while (continuation != null && pages < 20) {
            val next = YouTube.artistItemsContinuation(continuation).getOrNull() ?: break
            items += next.items
            continuation = next.continuation
            pages++
        }
        items.distinctBy { it.id }
    }
}

/**
 * Loads every song behind an artist's "popular songs" shelf: its own endpoint returns the full
 * list one page at a time, so the continuations are followed until YouTube stops handing out
 * more (bounded, so a huge discography can't spin forever). Falls back to whatever the shelf
 * already carried when it has no endpoint of its own.
 */
private suspend fun loadAllArtistSongs(section: com.arturo254.opentune.innertube.pages.ArtistSection?): Result<List<SongItem>> {
    if (section == null) return Result.success(emptyList())
    val endpoint = section.moreEndpoint
        ?: return Result.success(section.items.filterIsInstance<SongItem>())

    return runCatching {
        val first = YouTube.artistItems(endpoint).getOrThrow()
        val songs = mutableListOf<SongItem>()
        songs += first.items.filterIsInstance<SongItem>()
        var continuation = first.continuation
        var pages = 0
        while (continuation != null && pages < 20) {
            val next = YouTube.artistItemsContinuation(continuation).getOrNull() ?: break
            songs += next.items.filterIsInstance<SongItem>()
            continuation = next.continuation
            pages++
        }
        // Keys in the list must be unique, and continuations can repeat an item.
        songs.distinctBy { it.id }
    }
}

/**
 * Big banner hero like Spotify's artist page: artist photo full-bleed with a bottom
 * gradient, name overlaid large/bold, monthly listeners under it, then a play/shuffle
 * row. Same AsyncImage/gradient primitives used elsewhere - no new heavy composables.
 */
@Composable
private fun ArtistHero(
    artist: ArtistItem,
    fallbackTitle: String,
    fallbackThumbnail: String?,
    description: String?,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onShuffle: () -> Unit,
    isFollowing: Boolean,
    onToggleFollow: () -> Unit
) {
    val backgroundColor = MaterialTheme.colorScheme.background
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(340.dp)) {
            val thumb = artist.thumbnail ?: fallbackThumbnail
            if (thumb.isNullOrBlank()) {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(72.dp)
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
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.5f to Color.Black.copy(alpha = 0.25f),
                        1f to backgroundColor
                    )
                )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Text(
                    artist.title.ifBlank { fallbackTitle },
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                artist.monthlyListenerCountText?.let {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledIconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(56.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) tr("Pausa") else tr("Reproducir"),
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            IconButton(onClick = onShuffle) {
                Icon(
                    Icons.Filled.Shuffle,
                    contentDescription = tr("Aleatorio"),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            OutlinedButton(onClick = onToggleFollow) {
                Icon(
                    if (isFollowing) Icons.Filled.Check else Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isFollowing) tr("Siguiendo") else tr("Seguir"))
            }
        }

        description?.let { d ->
            Text(
                d,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun ArtistTopSongRow(index: Int, song: SongItem, onClick: () -> Unit) {
    val liked = LikedSongsManager.isLiked(song.id)
    val isCurrent = NowPlayingState.currentSongId.value == song.id
    val isCurrentPlaying = isCurrent && NowPlayingState.isPlaying.value
    var showAddToPlaylist by remember { mutableStateOf(false) }
    if (showAddToPlaylist) {
        AddToPlaylistDialog(song = song, onDismiss = { showAddToPlaylist = false })
    }
    var showContextMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent)
            .clickable(onClick = onClick)
            .onRightClick(song.id) { showContextMenu = true }
            .padding(horizontal = 24.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(28.dp), contentAlignment = Alignment.Center) {
            if (isCurrent) {
                EqualizerBars(
                    color = MaterialTheme.colorScheme.primary,
                    animated = isCurrentPlaying,
                    maxHeight = 14.dp
                )
            } else {
                Text(
                    index.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp))) {
            if (song.thumbnail.isNotBlank()) {
                AsyncImage(
                    model = song.thumbnail,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            song.album?.name?.let {
                Text(
                    it,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(onClick = { LikedSongsManager.toggleLike(song) }) {
            Icon(
                if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = null,
                tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        SongContextMenu(
            song = song,
            expanded = showContextMenu,
            onDismiss = { showContextMenu = false },
            onAddToPlaylist = { showAddToPlaylist = true }
        )
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
    val playlistCount = PlaylistsManager.playlists.size

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

    // Icon + label + live count per tab, instead of plain text - makes it obvious at a
    // glance how much is in each section without having to click into it.
    data class LibraryTab(val icon: ImageVector, val label: String, val count: Int)
    val tabsData = remember(likedSongs, downloadedSongs, cachedSongs, localSongs, playlistCount) {
        listOf(
            LibraryTab(Icons.Filled.Favorite, tr("Favoritas"), likedSongs.size),
            LibraryTab(Icons.Filled.Download, tr("Descargadas"), downloadedSongs.size),
            LibraryTab(Icons.Filled.Storage, tr("En caché"), cachedSongs.size),
            LibraryTab(Icons.Filled.FolderOpen, tr("Locales"), localSongs.size),
            LibraryTab(Icons.Filled.QueueMusic, tr("Listas de reproducción"), playlistCount)
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            edgePadding = 24.dp
        ) {
            tabsData.forEachIndexed { index, tabInfo ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    icon = { Icon(tabInfo.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    text = {
                        Text(
                            "${tabInfo.label} (${tabInfo.count})",
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
                },
                headerExtra = { DownloadsFolderRow() }
            )
            2 -> LibrarySongList(
                songs = cachedSongs,
                emptyMessage = tr("No hay canciones en caché"),
                onPlayAll = { if (cachedSongs.isNotEmpty()) PlayerManager.playSong(cachedSongs.first(), cachedSongs) },
                onLike = { song -> LikedSongsManager.toggleLike(song) },
                onDelete = { song ->
                    CacheMetadataManager.deleteSong(song.id)
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
    onDelete: ((SongItem) -> Unit)? = null,
    headerExtra: (@Composable () -> Unit)? = null
) {
    if (songs.isEmpty()) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            if (headerExtra != null) {
                headerExtra()
                Spacer(modifier = Modifier.height(12.dp))
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(emptyMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    var filter by remember { mutableStateOf("") }
    val filteredSongs = remember(songs, filter) {
        if (filter.isBlank()) songs
        else songs.filter {
            it.title.contains(filter, ignoreCase = true) ||
                it.artists.any { a -> a.name.contains(filter, ignoreCase = true) }
        }
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
                    tr("{0} canciones", songs.size),
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
        if (headerExtra != null) {
            item {
                headerExtra()
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
        if (songs.size > 6) {
            item {
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    placeholder = { Text(tr("Filtrar por título o artista..."), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingIcon = {
                        if (filter.isNotEmpty()) {
                            IconButton(onClick = { filter = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = tr("Limpiar"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                )
            }
        }
        if (filteredSongs.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(tr("Ningún resultado para \"{0}\"", filter), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(filteredSongs, key = { it.id }) { song ->
                SongListItem(
                    song = song,
                    onClick = { PlayerManager.playSong(song, filteredSongs) },
                    onLike = if (onLike != null) {{ onLike(song) }} else null,
                    isLiked = if (onLike != null) LikedSongsManager.isLiked(song.id) else false,
                    onDelete = if (onDelete != null) {{ onDelete(song) }} else null
                )
            }
        }
    }
}

// Lets the user choose (and later change) the folder on disk where downloaded songs are
// stored, instead of always using the fixed ~/.opentune/downloads folder. Shown at the top
// of the "Descargadas" library tab.
@Composable
fun DownloadsFolderRow() {
    val scope = rememberCoroutineScope()
    var currentDir by remember { mutableStateOf(DownloadsManager.getDownloadsDir()) }
    var moving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val isDefault = currentDir.absolutePath == DownloadsManager.defaultDownloadsDirPath()

    fun applyNewDir(path: String) {
        moving = true
        error = null
        scope.launch {
            val ok = withContext(Dispatchers.IO) { DownloadsManager.setDownloadsDir(path) }
            moving = false
            if (ok) {
                currentDir = DownloadsManager.getDownloadsDir()
            } else {
                error = tr("No se pudo cambiar la carpeta de descargas")
            }
        }
    }

    fun chooseFolder() {
        val chooser = JFileChooser(currentDir).apply {
            dialogTitle = tr("Elige dónde guardar las descargas")
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isAcceptAllFileFilterUsed = false
        }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile?.let { dir -> applyNewDir(dir.absolutePath) }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        tr("Carpeta de descargas"),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        currentDir.absolutePath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (moving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    if (!isDefault) {
                        TextButton(onClick = { applyNewDir(DownloadsManager.defaultDownloadsDirPath()) }) {
                            Text(tr("Restablecer"))
                        }
                    }
                    TextButton(onClick = { chooseFolder() }) {
                        Text(tr("Cambiar"))
                    }
                }
            }
        }
        if (error != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(error ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
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
    var showImportFromLink by remember { mutableStateOf(false) }
    val linked = AccountManager.isLinked

    // Rename/delete now work right from the list - no need to open a playlist first
    // just to fix its name or get rid of it.
    var renameTarget by remember { mutableStateOf<Playlist?>(null) }
    var deleteTarget by remember { mutableStateOf<Playlist?>(null) }
    var filter by remember { mutableStateOf("") }

    val filteredPlaylists = remember(playlists, filter) {
        if (filter.isBlank()) playlists else playlists.filter { it.name.contains(filter, ignoreCase = true) }
    }

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
    if (showImportFromLink) {
        ImportPlaylistFromLinkDialog(onDismiss = { showImportFromLink = false })
    }
    renameTarget?.let { target ->
        PlaylistNameDialog(
            title = tr("Renombrar"),
            initialName = target.name,
            confirmLabel = tr("Renombrar"),
            onConfirm = { name -> PlaylistsManager.rename(target.id, name) },
            onDismiss = { renameTarget = null }
        )
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(tr("Eliminar")) },
            text = { Text(tr("¿Eliminar la lista \"{0}\"? Se eliminará de forma permanente.", target.name)) },
            confirmButton = {
                TextButton(onClick = {
                    PlaylistsManager.delete(target.id)
                    deleteTarget = null
                }) { Text(tr("Eliminar"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(tr("Cancelar")) }
            }
        )
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
                OutlinedButton(
                    onClick = { showImportFromLink = true }
                ) {
                    Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(tr("Cargar playlist desde un enlace"))
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
            if (playlists.size > 6) {
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    placeholder = { Text(tr("Filtrar listas..."), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingIcon = {
                        if (filter.isNotEmpty()) {
                            IconButton(onClick = { filter = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = tr("Limpiar"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                )
            }

            if (filteredPlaylists.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(tr("Ningún resultado para \"{0}\"", filter), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredPlaylists, key = { it.id }) { playlist ->
                        PlaylistCard(
                            playlist = playlist,
                            onClick = { onOpenDetail(DetailScreen.LocalPlaylist(playlist.id, playlist.name)) },
                            onPlay = { if (playlist.songs.isNotEmpty()) PlayerManager.playSong(playlist.songs.first(), playlist.songs) },
                            onRename = { renameTarget = playlist },
                            onDuplicate = { PlaylistsManager.duplicate(playlist.id) },
                            onDelete = { deleteTarget = playlist }
                        )
                    }
                }
            }
        }
    }
}

/** A playlist row with a quick-actions overflow menu (play/rename/duplicate/delete) so
 * managing a list doesn't require opening it first. */
@Composable
private fun PlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, hoveredElevation = 4.dp),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.small)
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
            IconButton(onClick = onPlay, enabled = playlist.songs.isNotEmpty()) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = tr("Reproducir"),
                    tint = if (playlist.songs.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = tr("Más opciones"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(tr("Renombrar")) },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = { menuOpen = false; onRename() }
                    )
                    DropdownMenuItem(
                        text = { Text(tr("Duplicar")) },
                        leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                        onClick = { menuOpen = false; onDuplicate() }
                    )
                    DropdownMenuItem(
                        text = { Text(tr("Eliminar"), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menuOpen = false; onDelete() }
                    )
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
    var duplicatedNotice by remember { mutableStateOf(false) }

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
                        OutlinedButton(onClick = {
                            PlaylistsManager.duplicate(playlist.id)
                            duplicatedNotice = true
                        }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(tr("Duplicar"))
                        }
                        OutlinedButton(onClick = { showDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(tr("Eliminar"), color = MaterialTheme.colorScheme.error)
                        }
                    }
                    if (duplicatedNotice) {
                        LaunchedEffect(duplicatedNotice) {
                            kotlinx.coroutines.delay(2500)
                            duplicatedNotice = false
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            tr("Lista duplicada en tu biblioteca"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
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
            itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                SongListItem(
                    song = song,
                    onClick = { PlayerManager.playSong(song, songs) },
                    onLike = { LikedSongsManager.toggleLike(song) },
                    isLiked = LikedSongsManager.isLiked(song.id),
                    onDelete = { PlaylistsManager.removeSong(playlist.id, song.id) },
                    leadingExtra = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = { PlaylistsManager.moveSong(playlist.id, index, index - 1) },
                                enabled = index > 0,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Filled.KeyboardArrowUp,
                                    contentDescription = tr("Subir"),
                                    tint = if (index > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = { PlaylistsManager.moveSong(playlist.id, index, index + 1) },
                                enabled = index < songs.size - 1,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Filled.KeyboardArrowDown,
                                    contentDescription = tr("Bajar"),
                                    tint = if (index < songs.size - 1) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
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

        item {
            var showGuestDialog by remember { mutableStateOf(false) }
            if (showGuestDialog) {
                GuestProfileDialog(onDismiss = { showGuestDialog = false })
            }
            SettingsGroupCard(tr("Perfil de invitado")) {
                if (GuestProfileManager.exists) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center
                        ) {
                            val avatar = GuestProfileManager.avatarFile
                            if (avatar != null) {
                                AsyncImage(model = avatar, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            } else {
                                Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(GuestProfileManager.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { showGuestDialog = true }) {
                            Icon(Icons.Filled.Edit, contentDescription = tr("Cambiar"))
                        }
                    }
                    SettingsDestructiveRow(Icons.Filled.DeleteSweep, tr("Eliminar perfil de invitado"), MaterialTheme.colorScheme.error) {
                        GuestProfileManager.clear()
                    }
                } else {
                    Text(
                        tr("Crea un nombre y una foto para usar como invitado, sin necesidad de tu cuenta de YouTube (por ejemplo, para el Modo Fiesta)."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    Button(
                        onClick = { showGuestDialog = true },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    ) { Text(tr("Crear perfil de invitado")) }
                }
                if (linked && GuestProfileManager.exists) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        tr("Identidad para Modo Fiesta"),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = DesktopPreferences.partyIdentity == "youtube",
                            onClick = { DesktopPreferences.updatePartyIdentity("youtube") },
                            label = { Text(tr("Cuenta de YouTube")) }
                        )
                        FilterChip(
                            selected = DesktopPreferences.partyIdentity == "guest",
                            onClick = { DesktopPreferences.updatePartyIdentity("guest") },
                            label = { Text(tr("Perfil de invitado")) }
                        )
                    }
                }
            }
        }
    }
}

/** Lets the user set (or edit) their local guest name + photo - see [GuestProfileManager]. */
@Composable
fun GuestProfileDialog(onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(GuestProfileManager.name) }
    var pickedPhoto by remember { mutableStateOf<File?>(null) }

    fun choosePhoto() {
        val chooser = JFileChooser().apply {
            dialogTitle = tr("Elegir una foto")
            fileSelectionMode = JFileChooser.FILES_ONLY
            fileFilter = FileNameExtensionFilter(tr("Imágenes"), "jpg", "jpeg", "png", "webp", "bmp")
        }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile?.let { pickedPhoto = it }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(420.dp),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(tr("Perfil de invitado"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .align(Alignment.CenterHorizontally)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .clickable { choosePhoto() },
                    contentAlignment = Alignment.Center
                ) {
                    val preview = pickedPhoto ?: GuestProfileManager.avatarFile
                    if (preview != null) {
                        AsyncImage(model = preview, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Icon(Icons.Filled.AddAPhoto, contentDescription = tr("Elegir una foto"), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(tr("Nombre")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(tr("Cancelar")) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            GuestProfileManager.save(name, pickedPhoto)
                            onDismiss()
                        },
                        enabled = name.isNotBlank()
                    ) { Text(tr("Crear")) }
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

/** Extracts a YouTube Music playlist id from a pasted link or accepts a raw id/list value
 * directly. Strips a leading "VL" if present, since [YouTube.playlist] adds that prefix
 * itself when building the browse request. */
private fun extractPlaylistId(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    val listParamRegex = Regex("[?&]list=([a-zA-Z0-9_-]+)")
    val raw = listParamRegex.find(trimmed)?.groupValues?.get(1) ?: trimmed
    val cleaned = raw.trim().removePrefix("VL")
    return cleaned.takeIf { it.isNotBlank() }
}

@Composable
fun ImportPlaylistFromLinkDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var phase by remember { mutableIntStateOf(0) }
    var linkInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var resultMessage by remember { mutableStateOf("") }

    fun load() {
        val playlistId = extractPlaylistId(linkInput)
        if (playlistId.isNullOrBlank()) {
            errorMessage = tr("Enlace o ID de playlist no válido")
            phase = 3
            return
        }
        scope.launch {
            phase = 1
            runCatching {
                YouTube.playlist(playlistId).completed().getOrThrow()
            }.onSuccess { page ->
                val localId = PlaylistsManager.create(uniqueLocalName(page.playlist.title), page.playlist.thumbnail)
                page.songs.forEach { PlaylistsManager.addSong(localId, it) }
                resultMessage = tr("Playlist \"{0}\" cargada con {1} canciones", page.playlist.title, page.songs.size)
                phase = 2
            }.onFailure { e ->
                errorMessage = mapError(e)
                phase = 3
            }
        }
    }

    Dialog(onDismissRequest = { if (phase != 1) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.width(480.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    tr("Cargar playlist desde un enlace"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))
                when (phase) {
                    0 -> {
                        Text(
                            tr("Pega el enlace de una playlist pública de YouTube Music o YouTube (o su ID)."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = linkInput,
                            onValueChange = { linkInput = it },
                            placeholder = { Text("https://music.youtube.com/playlist?list=...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = onDismiss) { Text(tr("Cancelar")) }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(onClick = { load() }, enabled = linkInput.isNotBlank()) { Text(tr("Cargar")) }
                        }
                    }

                    1 -> Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(tr("Cargando playlist..."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    2 -> Column {
                        Text(resultMessage)
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Button(onClick = onDismiss) { Text(tr("Cerrar")) }
                        }
                    }

                    3 -> Column {
                        Text(errorMessage, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { phase = 0 }) { Text(tr("Reintentar")) }
                            Spacer(modifier = Modifier.width(8.dp))
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
    var showAccountDialog by remember { mutableStateOf(false) }
    // Recomputed whenever this screen recomposes; counts every container the cache can hold.
    var cacheBytes by remember { mutableLongStateOf(CacheMetadataManager.cacheSizeBytes()) }

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
                SettingsInfoRow(Icons.Filled.Storage, tr("Caché"), "${cacheBytes / (1024 * 1024)} MB", MaterialTheme.colorScheme.secondary)
                SettingsDestructiveRow(Icons.Filled.Delete, tr("Borrar caché de canciones"), MaterialTheme.colorScheme.error) {
                    CacheMetadataManager.clearAll()
                    cacheBytes = CacheMetadataManager.cacheSizeBytes()
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
                SettingsNavRow(Icons.Filled.Science, tr("Ajustes experimentales"), tr("Varios"), MaterialTheme.colorScheme.tertiary) {
                    onNavigate(SettingsSubScreen.Experimental)
                }
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

                // Measured, not guessed: how long the last song took to start, and how much of
                // that was spent finding the audio versus buffering it.
                var timings by remember { mutableStateOf(PlayerManager.lastStartup) }
                LaunchedEffect(Unit) {
                    while (true) {
                        timings = PlayerManager.lastStartup
                        kotlinx.coroutines.delay(1000)
                    }
                }
                timings?.let { t ->
                    val total = "%.1f".format(t.firstAudioMs / 1000.0)
                    val resolve = "%.1f".format(t.resolveMs / 1000.0)
                    val source = if (t.fromCache) tr("desde caché") else tr("desde internet")
                    SettingsInfoRow(
                        Icons.Filled.Timer,
                        tr("Última canción: tiempo hasta sonar"),
                        tr("{0} s en total ({1} s buscando el audio, {2})", total, resolve, source),
                        MaterialTheme.colorScheme.primary
                    )
                }
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
    var cacheSize by remember { mutableLongStateOf(0L) }
    var clearFailed by remember { mutableStateOf(false) }

    // Counts every cached container (.m4a, .webm, ...) plus interrupted-download leftovers, so
    // the number matches what the folder really holds - this used to count only .webm, which is
    // not what yt-dlp saves any more, so a full cache showed up as 0 MB.
    LaunchedEffect(Unit) {
        while (true) {
            cacheSize = CacheMetadataManager.cacheSizeBytes()
            kotlinx.coroutines.delay(1000)
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
                    clearFailed = !CacheMetadataManager.clearAll()
                    cacheSize = CacheMetadataManager.cacheSizeBytes()
                }

                if (clearFailed) {
                    Text(
                        tr("Algunos archivos no se pudieron borrar porque se están usando. Detén la reproducción e inténtalo de nuevo."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
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

// ===================== EXPERIMENTAL SETTINGS =====================
@Composable
fun ExperimentalSettings(onBack: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SettingsGroupCard(tr("Modo Fiesta")) {
                SettingsSwitchRow(
                    Icons.Filled.Celebration,
                    tr("Modo Fiesta"),
                    tr("Escucha música en tiempo real con un amigo: cola y reproducción compartidas"),
                    DesktopPreferences.partyModeEnabled,
                    MaterialTheme.colorScheme.primary
                ) { DesktopPreferences.updatePartyModeEnabled(it) }
                Text(
                    tr("Función experimental: crea una sala desde el panel de la cola y compártele el código a un amigo para escuchar música juntos en tiempo real."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
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

/** The small floating green play button that fades in over a song/album/artist thumbnail on
 * hover - shared by [SongCard] and [MediaCard] so every kind of card gets the same look. */
@Composable
private fun HoverPlayButton() {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 6.dp,
        modifier = Modifier.size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
fun SongCard(song: SongItem, onClick: () -> Unit) {
    val isCurrent = NowPlayingState.currentSongId.value == song.id
    val isCurrentPlaying = isCurrent && NowPlayingState.isPlaying.value
    var showAddToPlaylist by remember { mutableStateOf(false) }
    if (showAddToPlaylist) {
        AddToPlaylistDialog(song = song, onDismiss = { showAddToPlaylist = false })
    }
    var showContextMenu by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Card(
        onClick = onClick,
        modifier = Modifier.width(MediaCardWidth).onRightClick(song.id) { showContextMenu = true },
        colors = CardDefaults.cardColors(
            containerColor = when {
                isCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                isHovered -> MaterialTheme.colorScheme.surfaceContainerHigh
                else -> MaterialTheme.colorScheme.surfaceContainer
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, hoveredElevation = 6.dp, pressedElevation = 1.dp),
        shape = MaterialTheme.shapes.medium,
        interactionSource = interactionSource
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                Box(modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.small)) {
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
                // The floating play button only makes sense for a song that isn't already the one
                // playing - `isCurrent` already shows the equalizer above instead.
                // Fully qualified: inside this Box, Kotlin's overload resolution otherwise reaches
                // for the ColumnScope-only overload (found via the outer Column) instead of this
                // plain, receiver-less one, and refuses to compile with "cannot be called in this
                // context with an implicit receiver".
                androidx.compose.animation.AnimatedVisibility(
                    visible = isHovered && !isCurrent,
                    enter = fadeIn(tween(150)),
                    exit = fadeOut(tween(150)),
                    modifier = Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 6.dp)
                ) {
                    HoverPlayButton()
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
        SongContextMenu(
            song = song,
            expanded = showContextMenu,
            onDismiss = { showContextMenu = false },
            onAddToPlaylist = { showAddToPlaylist = true }
        )
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
    val imageShape = if (circle) CircleShape else MaterialTheme.shapes.small
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Card(
        onClick = onClick,
        modifier = Modifier.width(MediaCardWidth),
        colors = CardDefaults.cardColors(
            containerColor = if (isHovered) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, hoveredElevation = 6.dp, pressedElevation = 1.dp),
        shape = MaterialTheme.shapes.medium,
        interactionSource = interactionSource
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                Box(
                    modifier = Modifier.fillMaxSize().clip(imageShape),
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
                androidx.compose.animation.AnimatedVisibility(
                    visible = isHovered,
                    enter = fadeIn(tween(150)),
                    exit = fadeOut(tween(150)),
                    modifier = Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 6.dp)
                ) {
                    HoverPlayButton()
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
                MediaItemCard(item = item, songs = songs, onOpenDetail = onOpenDetail)
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

/** Invokes [onRightClick] on a right mouse-button press, independent of any primary-click
 * handling (clickable/onClick) already on the same node - used by every place a song is shown
 * to open its context menu regardless of what else the row already does on left-click. */
private fun Modifier.onRightClick(key: Any?, onRightClick: () -> Unit): Modifier =
    this.pointerInput(key) {
        awaitEachGesture {
            val event = awaitPointerEvent()
            if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                onRightClick()
                event.changes.forEach { it.consume() }
            }
        }
    }

/** The right-click context menu shared by every song row in the app: queue, download, add to
 * playlist, go to artist, share link. */
@Composable
private fun SongContextMenu(
    song: SongItem,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAddToPlaylist: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(tr("Agregar a la cola")) },
            leadingIcon = { Icon(Icons.Filled.QueueMusic, contentDescription = null) },
            onClick = {
                onDismiss()
                PlayerManager.addToQueue(song)
            }
        )
        DropdownMenuItem(
            text = { Text(tr("Descargar")) },
            leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
            onClick = {
                onDismiss()
                PlayerManager.downloadSong(song)
            }
        )
        DropdownMenuItem(
            text = { Text(tr("Agregar a una playlist")) },
            leadingIcon = { Icon(Icons.Outlined.PlaylistAdd, contentDescription = null) },
            onClick = {
                onDismiss()
                onAddToPlaylist()
            }
        )
        val firstArtist = song.artists.firstOrNull()
        DropdownMenuItem(
            text = { Text(tr("Ir al artista")) },
            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
            enabled = !firstArtist?.id.isNullOrBlank(),
            onClick = {
                onDismiss()
                SongContextNav.openArtist(firstArtist?.id, firstArtist?.name ?: "", null)
            }
        )
        DropdownMenuItem(
            text = { Text(tr("Compartir enlace")) },
            leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
            onClick = {
                onDismiss()
                runCatching {
                    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(song.shareLink), null)
                }
            }
        )
    }
}

@Composable
fun SongListItem(
    song: SongItem,
    onClick: () -> Unit,
    onLike: (() -> Unit)? = null,
    isLiked: Boolean = false,
    onDelete: (() -> Unit)? = null,
    leadingExtra: (@Composable () -> Unit)? = null
) {
    var showAddToPlaylist by remember { mutableStateOf(false) }
    if (showAddToPlaylist) {
        AddToPlaylistDialog(song = song, onDismiss = { showAddToPlaylist = false })
    }
    var showContextMenu by remember { mutableStateOf(false) }
    val isCurrent = NowPlayingState.currentSongId.value == song.id
    val isCurrentPlaying = isCurrent && NowPlayingState.isPlaying.value
    val rowInteractionSource = remember { MutableInteractionSource() }
    val rowHovered by rowInteractionSource.collectIsHoveredAsState()
    val itemColor = when {
        isCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        rowHovered -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> Color.Transparent
    }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            // Right-click opens the context menu (queue/download/playlist/artist/share),
            // independent of the Surface's own primary-click handling above.
            .onRightClick(song.id) { showContextMenu = true },
        color = itemColor,
        interactionSource = rowInteractionSource,
        shape = MaterialTheme.shapes.small
    ) {
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (leadingExtra != null) {
                leadingExtra()
            }
            // Downloading state comes straight from DownloadsManager's Compose state, so this
            // recomposes on its own when a download starts/finishes - no polling needed here.
            val isDownloading = DownloadsManager.downloadingIds.contains(song.id)
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(if (isDownloading) 40.dp else 48.dp)
                        .clip(MaterialTheme.shapes.small),
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
                if (isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
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

        SongContextMenu(
            song = song,
            expanded = showContextMenu,
            onDismiss = { showContextMenu = false },
            onAddToPlaylist = { showAddToPlaylist = true }
        )
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
