package com.arturo254.opentune

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.arturo254.opentune.innertube.YouTube
import com.arturo254.opentune.innertube.models.SongItem
import com.arturo254.opentune.innertube.models.YouTubeLocale
import com.arturo254.opentune.library.CacheMetadataManager
import com.arturo254.opentune.library.DownloadsManager
import com.arturo254.opentune.library.LikedSongsManager
import com.arturo254.opentune.library.LocalSongsManager
import com.arturo254.opentune.library.SearchHistoryManager
import com.arturo254.opentune.player.PlayerManager
import com.arturo254.opentune.player.RepeatMode
import com.arturo254.opentune.ui.EqualizerBars
import com.arturo254.opentune.ui.NowPlayingState
import com.arturo254.opentune.ui.theme.LumaMusicTheme
import java.io.File
import java.util.Locale
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class Screen(val label: String, val icon: ImageVector) {
    data object Home : Screen("Home", Icons.Filled.Home)
    data object Search : Screen("Search", Icons.Filled.Search)
    data object Explore : Screen("Explore", Icons.Filled.Explore)
    data object Library : Screen("Library", Icons.Filled.LibraryMusic)
    data object Settings : Screen("Settings", Icons.Filled.Settings)
}

sealed class SettingsSubScreen(val label: String) {
    data object Main : SettingsSubScreen("Settings")
    data object Appearance : SettingsSubScreen("Appearance")
    data object PalettePicker : SettingsSubScreen("Color Palette")
    data object PlayerAudio : SettingsSubScreen("Player & Audio")
    data object Storage : SettingsSubScreen("Storage")
    data object Privacy : SettingsSubScreen("Privacy")
    data object Content : SettingsSubScreen("Content")
    data object About : SettingsSubScreen("About")
}

@Composable
fun App() {
    LumaMusicTheme {
        var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
        var settingsSubScreen by remember { mutableStateOf<SettingsSubScreen>(SettingsSubScreen.Main) }
        var searchQuery by remember { mutableStateOf("") }

        // Hoisted search state — survives navigation
        val searchResults = remember { mutableStateListOf<SongItem>() }
        var searchLoading by remember { mutableStateOf(false) }
        var searchError by remember { mutableStateOf<String?>(null) }
        var searchHasQuery by remember { mutableStateOf(false) }
        var lastSearchedQuery by remember { mutableStateOf("") }
        val searchScrollState = rememberLazyListState()

        // Debounced auto-search: waits 400ms after last keystroke
        LaunchedEffect(searchQuery) {
            if (searchQuery.isBlank()) {
                searchHasQuery = false
                searchResults.clear()
                lastSearchedQuery = ""
                return@LaunchedEffect
            }
            searchHasQuery = true
            kotlinx.coroutines.delay(400)
            // Only fire if query actually changed since last search
            if (searchQuery != lastSearchedQuery) {
                searchLoading = true
                searchError = null
                YouTube.search(searchQuery, YouTube.SearchFilter.FILTER_SONG).onSuccess { result ->
                    // Only apply if still the latest query
                    if (searchQuery == lastSearchedQuery || result.items.isNotEmpty()) {
                        searchResults.clear()
                        searchResults.addAll(result.items.filterIsInstance<SongItem>())
                        lastSearchedQuery = searchQuery
                    }
                    searchLoading = false
                }.onFailure { e ->
                    searchError = mapError(e)
                    searchLoading = false
                }
            }
        }

        LaunchedEffect(Unit) {
            val locale = Locale.getDefault()
            YouTube.locale = YouTubeLocale(
                gl = locale.country.takeIf { it.length == 2 } ?: "US",
                hl = locale.language.takeIf { it.length == 2 } ?: "en"
            )
        }

        // Keep NowPlayingState in sync with PlayerManager
        LaunchedEffect(Unit) {
            NowPlayingState.update()
            PlayerManager.addListener { NowPlayingState.update() }
        }

        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Row(modifier = Modifier.weight(1f)) {
                AppNavigationRail(
                    currentScreen = currentScreen,
                    onScreenSelected = {
                        currentScreen = it
                        if (it is Screen.Settings) settingsSubScreen = SettingsSubScreen.Main
                    }
                )

                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (currentScreen is Screen.Settings && settingsSubScreen !is SettingsSubScreen.Main) {
                        SettingsTopBar(
                            title = (settingsSubScreen as SettingsSubScreen).label,
                            onBack = { settingsSubScreen = SettingsSubScreen.Main }
                        )
                    } else {
                        AppTopBar(
                            searchQuery = searchQuery,
                            onSearchQueryChange = { searchQuery = it },
                            onSearch = {
                                if (searchQuery.isNotBlank()) SearchHistoryManager.add(searchQuery)
                            }
                        )
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when (currentScreen) {
                            is Screen.Home -> HomeScreen()
                            is Screen.Search -> SearchScreen(
                                query = searchQuery,
                                results = searchResults,
                                loading = searchLoading,
                                error = searchError,
                                hasQuery = searchHasQuery,
                                history = SearchHistoryManager.entries,
                                onHistoryClick = { searchQuery = it },
                                onRemoveHistory = { SearchHistoryManager.remove(it) },
                                onClearHistory = { SearchHistoryManager.clear() },
                                scrollState = searchScrollState
                            )
                            is Screen.Explore -> ExploreScreen()
                            is Screen.Library -> LibraryScreen()
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
                                    is SettingsSubScreen.About -> AboutScreen(onBack = { settingsSubScreen = SettingsSubScreen.Main })
                                }
                            }
                        }
                    }
                }
            }

            PlayerBar()
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
    onSearch: () -> Unit = {}
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
                placeholder = { Text("Search music...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
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
                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()
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
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
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
fun PlayerBar() {
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
                        Icon(Icons.Filled.Replay10, contentDescription = "Rewind 10s", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { PlayerManager.previous() }) {
                        Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", tint = MaterialTheme.colorScheme.onSurface)
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
                                contentDescription = if (isPlaying) "Pause" else "Play"
                            )
                        }
                    }
                    IconButton(onClick = { PlayerManager.next() }) {
                        Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { PlayerManager.seekTo((position + 30000).coerceAtMost(duration)) }) {
                        Icon(Icons.Filled.Forward30, contentDescription = "Forward 30s", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { PlayerManager.toggleRepeatMode() }) {
                        Icon(
                            when (repeatMode) {
                                RepeatMode.SEQUENTIAL -> Icons.Filled.Repeat
                                RepeatMode.SHUFFLE -> Icons.Filled.Shuffle
                                RepeatMode.LOOP -> Icons.Filled.RepeatOne
                            },
                            contentDescription = when (repeatMode) {
                                RepeatMode.SEQUENTIAL -> "Sequential"
                                RepeatMode.SHUFFLE -> "Shuffle"
                                RepeatMode.LOOP -> "Loop"
                            },
                            tint = if (repeatMode == RepeatMode.SEQUENTIAL) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary
                        )
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
                            contentDescription = "Like",
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
                                contentDescription = "Download",
                                tint = when {
                                    isDownloaded -> MaterialTheme.colorScheme.primary
                                    isDownloading -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
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
    val msg = e?.message?.lowercase() ?: return "Error desconocido"
    return when {
        "timeout" in msg || "connect" in msg || "network" in msg || "unresolved" in msg ||
        "refused" in msg || "unknown host" in msg || "no route" in msg || "internet" in msg ->
            "No se pudo conectar a la red"
        else -> "No se pudo conectar a la red"
    }
}

// ===================== HOME =====================
@Composable
fun HomeScreen() {
    var sections by remember { mutableStateOf<List<com.arturo254.opentune.innertube.pages.HomePage.Section>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        loading = true
        YouTube.home().onSuccess { page ->
            sections = page.sections
            loading = false
        }.onFailure { e ->
            error = mapError(e)
            loading = false
        }
    }

    when {
        loading -> LoadingScreen()
        error != null -> ErrorScreen(error!!)
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            sections.forEach { section ->
                val songs = section.items.filterIsInstance<SongItem>()
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            section.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(songs.take(12)) { song ->
                                SongCard(
                                    song = song,
                                    onClick = { PlayerManager.playSong(song, songs) }
                                )
                            }
                        }
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
    results: List<SongItem>,
    loading: Boolean,
    error: String?,
    hasQuery: Boolean,
    history: List<String>,
    onHistoryClick: (String) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    scrollState: LazyListState
) {
    when {
        !hasQuery -> {
            if (history.isEmpty()) {
                EmptyScreen("Type to search...")
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
                                "Recent Searches",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            TextButton(onClick = onClearHistory) {
                                Text("Clear", color = MaterialTheme.colorScheme.error)
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
                                    contentDescription = "Remove from history",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        error != null && results.isEmpty() -> ErrorScreen(error)
        results.isEmpty() && loading -> LoadingScreen()
        else -> LazyColumn(
            state = scrollState,
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Text(
                        "Results for \"$query\" (${results.size})",
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
            items(results, key = { it.id }) { song ->
                SongListItem(
                    song = song,
                    onClick = { PlayerManager.playSong(song, results) },
                    onLike = { LikedSongsManager.toggleLike(song) },
                    isLiked = LikedSongsManager.isLiked(song.id)
                )
            }
        }
    }
}

// ===================== EXPLORE =====================
@Composable
fun ExploreScreen() {
    var moodItems by remember { mutableStateOf<List<com.arturo254.opentune.innertube.pages.MoodAndGenres.Item>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        loading = true
        YouTube.moodAndGenres().onSuccess { result ->
            moodItems = result.flatMap { it.items }
            loading = false
        }.onFailure { e ->
            error = mapError(e)
            loading = false
        }
    }

    when {
        loading -> LoadingScreen()
        error != null -> ErrorScreen(error!!)
        else -> LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(moodItems) { mood -> MoodChip(mood = mood) }
        }
    }
}

// ===================== LIBRARY =====================
@Composable
fun LibraryScreen() {
    var likedSongs by remember { mutableStateOf(LikedSongsManager.likedSongs) }
    var downloadedSongs by remember { mutableStateOf(DownloadsManager.downloadedSongs) }
    var cachedSongs by remember { mutableStateOf(CacheMetadataManager.getActualCachedSongs()) }
    var localSongs by remember { mutableStateOf(LocalSongsManager.songs) }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Liked", "Downloaded", "Cached", "Local")

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
                emptyMessage = "No liked songs yet",
                onPlayAll = { if (likedSongs.isNotEmpty()) PlayerManager.playSong(likedSongs.first(), likedSongs) },
                onLike = { song -> LikedSongsManager.toggleLike(song); likedSongs = LikedSongsManager.likedSongs }
            )
            1 -> LibrarySongList(
                songs = downloadedSongs,
                emptyMessage = "No downloaded songs",
                onPlayAll = { if (downloadedSongs.isNotEmpty()) PlayerManager.playSong(downloadedSongs.first(), downloadedSongs) },
                onLike = { song -> LikedSongsManager.toggleLike(song) },
                onDelete = { song ->
                    DownloadsManager.removeDownload(song)
                    downloadedSongs = DownloadsManager.downloadedSongs
                }
            )
            2 -> LibrarySongList(
                songs = cachedSongs,
                emptyMessage = "No cached songs",
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
                    Text("Play All")
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
            message = if (added > 0) "Added $added song(s)" else "No new songs found"
            onSongsChanged()
        }
    }

    fun chooseFolder() {
        val chooser = JFileChooser().apply {
            dialogTitle = "Choose a folder with songs"
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isAcceptAllFileFilterUsed = false
        }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile?.let { dir -> runImport { LocalSongsManager.addFolder(dir) } }
        }
    }

    fun chooseFiles() {
        val chooser = JFileChooser().apply {
            dialogTitle = "Choose song files"
            isMultiSelectionEnabled = true
            fileSelectionMode = JFileChooser.FILES_ONLY
            fileFilter = FileNameExtensionFilter(
                "Audio files",
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
                    "${songs.size} songs",
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
                    Text("Add Folder")
                }
                FilledTonalButton(onClick = { chooseFiles() }) {
                    Icon(Icons.Filled.LibraryMusic, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Files")
                }
            }
        }

        when {
            scanning -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Importing songs...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            songs.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No local songs yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))
                    FilledTonalButton(onClick = { chooseFolder() }) {
                        Icon(Icons.Filled.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Choose Folder")
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

// ===================== SETTINGS MAIN =====================
@Composable
fun SettingsScreen(onNavigate: (SettingsSubScreen) -> Unit) {
    val scrollState = rememberScrollState()
    val cacheDir = remember { File(System.getProperty("user.home"), ".opentune/cache") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickActionCard("Appearance", Icons.Filled.Palette, MaterialTheme.colorScheme.primary, Modifier.weight(1f)) { onNavigate(SettingsSubScreen.Appearance) }
                QuickActionCard("Player", Icons.Filled.PlayArrow, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f)) { onNavigate(SettingsSubScreen.PlayerAudio) }
                QuickActionCard("Storage", Icons.Filled.Storage, MaterialTheme.colorScheme.secondary, Modifier.weight(1f)) { onNavigate(SettingsSubScreen.Storage) }
                QuickActionCard("Privacy", Icons.Filled.Security, MaterialTheme.colorScheme.error, Modifier.weight(1f)) { onNavigate(SettingsSubScreen.Privacy) }
            }
        }

        item {
            SettingsGroupCard("Player & Content") {
                SettingsNavRow(Icons.Filled.PlayArrow, "Player & Audio", "WAV PCM via ffmpeg", MaterialTheme.colorScheme.tertiary) { onNavigate(SettingsSubScreen.PlayerAudio) }
                SettingsNavRow(Icons.Filled.Language, "Content", "Language & region", MaterialTheme.colorScheme.secondary) { onNavigate(SettingsSubScreen.Content) }
                val cacheMB = cacheDir.listFiles()?.filter { it.name.endsWith(".webm") }?.sumOf { it.length() }?.let { it / (1024 * 1024) } ?: 0
                SettingsInfoRow(Icons.Filled.Storage, "Cache", "$cacheMB MB", MaterialTheme.colorScheme.secondary)
                SettingsDestructiveRow(Icons.Filled.Delete, "Clear Song Cache", MaterialTheme.colorScheme.error) {
                    cacheDir.listFiles()?.filter { it.name.endsWith(".webm") }?.forEach { it.delete() }
                }
            }
        }

        item {
            SettingsGroupCard("Privacy") {
                SettingsNavRow(Icons.Filled.Security, "Privacy", if (DesktopPreferences.pauseListenHistory) "History paused" else "Pause listen history", MaterialTheme.colorScheme.error) { onNavigate(SettingsSubScreen.Privacy) }
            }
        }

        item {
            SettingsGroupCard("System") {
                SettingsNavRow(Icons.Filled.Science, "Experiment Settings", "Misc", MaterialTheme.colorScheme.tertiary) {}
                SettingsNavRow(Icons.Filled.Update, "Updates", "Version 1.0.0", MaterialTheme.colorScheme.primary) {}
                SettingsNavRow(Icons.Filled.Info, "About", "Luma Music", MaterialTheme.colorScheme.onSurfaceVariant) { onNavigate(SettingsSubScreen.About) }
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
            SettingsGroupCard("Theme") {
                SettingsNavRow(
                    Icons.Filled.Palette,
                    "Color Palette",
                    "Current: ${currentPalette.name}",
                    MaterialTheme.colorScheme.primary
                ) { onNavigate(SettingsSubScreen.PalettePicker) }

                SettingsSwitchRow(
                    Icons.Filled.Contrast,
                    "Pure Black",
                    "AMOLED black background",
                    DesktopPreferences.pureBlack,
                    MaterialTheme.colorScheme.onSurfaceVariant
                ) { DesktopPreferences.updatePureBlack(it) }
            }
        }

        item {
            SettingsGroupCard("Player Style") {
                SettingsSwitchRow(
                    Icons.Filled.Fullscreen,
                    "Fullscreen Player",
                    "Open player in fullscreen",
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
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Color Palette",
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
            SettingsGroupCard("Player") {
                SettingsInfoRow(Icons.Filled.GraphicEq, "Audio Format", "WAV PCM (via ffmpeg)", MaterialTheme.colorScheme.tertiary)

                SettingsSwitchRow(
                    Icons.Filled.SkipNext,
                    "Auto-skip on Error",
                    "Skip to next song if playback fails",
                    DesktopPreferences.autoSkipOnError,
                    MaterialTheme.colorScheme.tertiary
                ) { DesktopPreferences.updateAutoSkipOnError(it) }

                SettingsSwitchRow(
                    Icons.Filled.Forward,
                    "Seek Extra Seconds",
                    "Add extra seconds when seeking",
                    DesktopPreferences.seekExtraSeconds,
                    MaterialTheme.colorScheme.primary
                ) { DesktopPreferences.updateSeekExtraSeconds(it) }

                SettingsInfoRow(Icons.Filled.Speed, "Playback Engine", "ffmpeg + javax.sound", MaterialTheme.colorScheme.secondary)
            }
        }

        item {
            SettingsGroupCard("Queue") {
                SettingsSwitchRow(
                    Icons.Filled.QueueMusic,
                    "Persistent Queue",
                    "Save queue between sessions",
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
            SettingsGroupCard("Cache") {
                val cacheMB = cacheSize / (1024 * 1024)
                val maxSizeMB = DesktopPreferences.maxCacheSizeMB
                val progress = if (maxSizeMB > 0) (cacheMB.toFloat() / maxSizeMB).coerceIn(0f, 1f) else 0f

                SettingsInfoRow(Icons.Filled.Storage, "Song Cache", "${cacheMB} MB / ${maxSizeMB} MB", MaterialTheme.colorScheme.secondary)

                if (maxSizeMB > 0) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(6.dp),
                        color = if (progress > 0.8f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }

                SettingsDestructiveRow(Icons.Filled.Delete, "Clear Song Cache", MaterialTheme.colorScheme.error) {
                    cacheDir.listFiles()?.filter { it.name.endsWith(".webm") }?.forEach { it.delete() }
                }
            }
        }

        item {
            SettingsGroupCard("Limits") {
                val sizes = listOf(128L, 256L, 500L, 1024L, 2048L, -1L)
                val labels = listOf("128 MB", "256 MB", "500 MB", "1 GB", "2 GB", "Unlimited")

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
            SettingsGroupCard("Listen History") {
                SettingsSwitchRow(
                    Icons.Filled.History,
                    "Pause Listen History",
                    "Don't save listening history",
                    DesktopPreferences.pauseListenHistory,
                    MaterialTheme.colorScheme.error
                ) { DesktopPreferences.updatePauseListenHistory(it) }
            }
        }

        item {
            SettingsGroupCard("Search History") {
                SettingsSwitchRow(
                    Icons.Filled.SearchOff,
                    "Pause Search History",
                    "Don't save search history",
                    DesktopPreferences.pauseSearchHistory,
                    MaterialTheme.colorScheme.tertiary
                ) { DesktopPreferences.updatePauseSearchHistory(it) }
                SettingsDestructiveRow(
                    Icons.Filled.DeleteSweep,
                    "Clear Search History",
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
    val languages = listOf("system" to "System Default", "en" to "English", "es" to "Spanish", "pt" to "Portuguese", "fr" to "French", "de" to "German", "ja" to "Japanese", "ko" to "Korean", "zh" to "Chinese", "ru" to "Russian", "ar" to "Arabic", "hi" to "Hindi", "it" to "Italian", "tr" to "Turkish", "pl" to "Polish", "nl" to "Dutch", "vi" to "Vietnamese", "th" to "Thai", "id" to "Indonesian")
    val countries = listOf("system" to "System Default", "US" to "United States", "MX" to "Mexico", "ES" to "Spain", "BR" to "Brazil", "GB" to "United Kingdom", "JP" to "Japan", "KR" to "South Korea", "CN" to "China", "DE" to "Germany", "FR" to "France", "IT" to "Italy", "RU" to "Russia", "IN" to "India", "AU" to "Australia", "CA" to "Canada", "AR" to "Argentina", "CO" to "Colombia")

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SettingsGroupCard("General") {
                val currentLang = DesktopPreferences.contentLanguage
                val currentLangName = languages.find { it.first == currentLang }?.second ?: "System Default"

                var showLangDialog by remember { mutableStateOf(false) }
                if (showLangDialog) {
                    AlertDialog(
                        onDismissRequest = { showLangDialog = false },
                        title = { Text("Content Language") },
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
                    "Content Language",
                    currentLangName,
                    MaterialTheme.colorScheme.secondary
                ) { showLangDialog = true }

                val currentCountry = DesktopPreferences.contentCountry
                val currentCountryName = countries.find { it.first == currentCountry }?.second ?: "System Default"

                var showCountryDialog by remember { mutableStateOf(false) }
                if (showCountryDialog) {
                    AlertDialog(
                        onDismissRequest = { showCountryDialog = false },
                        title = { Text("Content Country") },
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
                    "Content Country",
                    currentCountryName,
                    MaterialTheme.colorScheme.tertiary
                ) { showCountryDialog = true }

                SettingsSwitchRow(
                    Icons.Filled.Explicit,
                    "Hide Explicit",
                    "Filter explicit content",
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
            SettingsGroupCard("App") {
                SettingsInfoRow(Icons.Filled.Info, "Version", "1.0.0", MaterialTheme.colorScheme.primary)
                SettingsInfoRow(Icons.Filled.Code, "Engine", "Compose Desktop + Skiko", MaterialTheme.colorScheme.secondary)
                SettingsInfoRow(Icons.Filled.PlayArrow, "Player", "yt-dlp + ffmpeg + javax.sound", MaterialTheme.colorScheme.tertiary)
                SettingsInfoRow(Icons.Filled.Storage, "Cache", "~/.opentune/cache/", MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item {
            SettingsGroupCard("Credits") {
                SettingsInfoRow(Icons.Filled.Person, "Original App", "Arturo254 (OpenTune)", MaterialTheme.colorScheme.primary)
                SettingsInfoRow(Icons.Filled.Code, "Desktop Port", "Luma Music", MaterialTheme.colorScheme.secondary)
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
@Composable
fun SongCard(song: SongItem, onClick: () -> Unit) {
    val isCurrent = NowPlayingState.currentSongId.value == song.id
    val isCurrentPlaying = isCurrent && NowPlayingState.isPlaying.value

    Card(
        onClick = onClick,
        modifier = Modifier.width(170.dp),
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
            Text(song.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Text(song.artists.joinToString { it.name }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
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
                        contentDescription = if (isLiked) "Unlike" else "Like",
                        tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun MoodChip(mood: com.arturo254.opentune.innertube.pages.MoodAndGenres.Item) {
    Card(
        modifier = Modifier.fillMaxWidth().height(60.dp),
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
        Text("Error: $message", color = MaterialTheme.colorScheme.error)
    }
}

@Composable
fun EmptyScreen(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
