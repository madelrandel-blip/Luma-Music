/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.arturo254.opentune.ui.screens.library

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.arturo254.opentune.LocalDatabase
import com.arturo254.opentune.LocalPlayerAwareWindowInsets
import com.arturo254.opentune.R
import com.arturo254.opentune.constants.AppBarHeight
import com.arturo254.opentune.constants.LibraryFilter
import com.arturo254.opentune.constants.LibraryViewType
import com.arturo254.opentune.constants.MixSortType
import com.arturo254.opentune.constants.MixSortTypeKey
import com.arturo254.opentune.constants.PlaylistViewTypeKey
import com.arturo254.opentune.ui.component.CreatePlaylistDialog
import com.arturo254.opentune.ui.component.LocalMainUiActions
import com.arturo254.opentune.ui.component.ProfileAvatarButton
import com.arturo254.opentune.utils.LocalMediaScanner
import com.arturo254.opentune.utils.rememberEnumPreference
import com.arturo254.opentune.viewmodels.LibraryMixViewModel
import kotlinx.coroutines.launch
import java.text.Collator
import java.time.LocalDateTime
import java.util.Locale

/** One row of the mixed "Your Library" list. */
private data class LibraryEntry(
    val key: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
    val circle: Boolean,
    val sortDate: LocalDateTime?,
    val updated: LocalDateTime?,
    val onClick: () -> Unit,
)

/**
 * Spotify-style header for the library tab: profile avatar, bold title,
 * search and a "+" menu (create playlist / add local music).
 */
@Composable
fun YourLibraryHeader(
    onCreatePlaylist: () -> Unit,
    onAddLocalMusic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val actions = LocalMainUiActions.current
    var showAddMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(AppBarHeight)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfileAvatarButton()
        Spacer(Modifier.width(14.dp))
        Text(
            text = stringResource(R.string.your_library),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = actions.openSearch) {
            Icon(
                painter = painterResource(R.drawable.search),
                contentDescription = stringResource(R.string.search),
                modifier = Modifier.size(28.dp),
            )
        }
        Box {
            IconButton(onClick = { showAddMenu = true }) {
                Icon(
                    painter = painterResource(R.drawable.add),
                    contentDescription = stringResource(R.string.create_playlist),
                    modifier = Modifier.size(30.dp),
                )
            }
            DropdownMenu(
                expanded = showAddMenu,
                onDismissRequest = { showAddMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.create_playlist)) },
                    leadingIcon = { Icon(painterResource(R.drawable.playlist_add), null) },
                    onClick = {
                        showAddMenu = false
                        onCreatePlaylist()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.add_local_music)) },
                    leadingIcon = { Icon(painterResource(R.drawable.folder), null) },
                    onClick = {
                        showAddMenu = false
                        onAddLocalMusic()
                    },
                )
            }
        }
    }
}

/** Filter chips under the header; tapping the selected chip clears it. */
@Composable
fun YourLibraryChips(
    current: LibraryFilter,
    onSelect: (LibraryFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chips = listOf(
        LibraryFilter.PLAYLISTS to stringResource(R.string.filter_playlists),
        LibraryFilter.ARTISTS to stringResource(R.string.filter_artists),
        LibraryFilter.ALBUMS to stringResource(R.string.filter_albums),
        LibraryFilter.SONGS to stringResource(R.string.filter_songs),
        LibraryFilter.ON_DEVICE to stringResource(R.string.filter_on_device),
        LibraryFilter.SPOTIFY to stringResource(R.string.spotify),
    )
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(chips, key = { it.first.name }) { (filter, label) ->
            val selected = filter == current
            Surface(
                shape = CircleShape,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                onClick = { onSelect(if (selected) LibraryFilter.LIBRARY else filter) },
            ) {
                Text(
                    text = label,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/**
 * Mixed list of liked songs, playlists, artists and albums, styled like
 * Spotify's "Your Library". [filter] is LIBRARY (everything), PLAYLISTS,
 * ARTISTS or ALBUMS.
 */
@Composable
fun YourLibraryList(
    navController: NavController,
    filter: LibraryFilter,
    onFilterChange: (LibraryFilter) -> Unit,
    onAddLocalMusic: () -> Unit,
    viewModel: LibraryMixViewModel = hiltViewModel(),
) {
    val database = LocalDatabase.current
    val actions = LocalMainUiActions.current

    val (sortType, onSortTypeChange) = rememberEnumPreference(MixSortTypeKey, MixSortType.CREATE_DATE)
    val (viewType, onViewTypeChange) = rememberEnumPreference(PlaylistViewTypeKey, LibraryViewType.LIST)

    val playlists by viewModel.playlists.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val likedCount by database.likedSongsCount().collectAsState(initial = 0)

    val playlistLabel = stringResource(R.string.library_type_playlist)
    val artistLabel = stringResource(R.string.library_type_artist)
    val albumLabel = stringResource(R.string.library_type_album)
    val songsLabel = stringResource(R.string.filter_songs)

    val collator = remember {
        Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY }
    }

    val entries = remember(playlists, artists, albums, filter, sortType, playlistLabel) {
        buildList {
            if (filter == LibraryFilter.LIBRARY || filter == LibraryFilter.PLAYLISTS) {
                playlists.forEach { playlist ->
                    add(
                        LibraryEntry(
                            key = "playlist_${playlist.id}",
                            title = playlist.playlist.name,
                            subtitle = "$playlistLabel • ${playlist.songCount} $songsLabel".lowercase()
                                .replaceFirstChar { it.uppercase() },
                            thumbnailUrl = playlist.thumbnails.firstOrNull(),
                            circle = false,
                            sortDate = playlist.playlist.bookmarkedAt ?: playlist.playlist.lastUpdateTime,
                            updated = playlist.playlist.lastUpdateTime,
                            onClick = {
                                if (!playlist.playlist.isEditable && playlist.songCount == 0 &&
                                    playlist.playlist.remoteSongCount != 0
                                ) {
                                    navController.navigate("online_playlist/${playlist.playlist.browseId}")
                                } else {
                                    navController.navigate("local_playlist/${playlist.id}")
                                }
                            },
                        )
                    )
                }
            }
            if (filter == LibraryFilter.LIBRARY || filter == LibraryFilter.ARTISTS) {
                artists.forEach { artist ->
                    add(
                        LibraryEntry(
                            key = "artist_${artist.id}",
                            title = artist.artist.name,
                            subtitle = artistLabel,
                            thumbnailUrl = artist.artist.thumbnailUrl,
                            circle = true,
                            sortDate = artist.artist.bookmarkedAt,
                            updated = artist.artist.lastUpdateTime,
                            onClick = { navController.navigate("artist/${artist.id}") },
                        )
                    )
                }
            }
            if (filter == LibraryFilter.LIBRARY || filter == LibraryFilter.ALBUMS) {
                albums.forEach { album ->
                    val artistNames = album.artists.joinToString { it.name }
                    add(
                        LibraryEntry(
                            key = "album_${album.id}",
                            title = album.album.title,
                            subtitle = if (artistNames.isBlank()) albumLabel else "$albumLabel • $artistNames",
                            thumbnailUrl = album.album.thumbnailUrl,
                            circle = false,
                            sortDate = album.album.bookmarkedAt,
                            updated = album.album.lastUpdateTime,
                            onClick = { navController.navigate("album/${album.id}") },
                        )
                    )
                }
            }
        }.let { list ->
            when (sortType) {
                MixSortType.CREATE_DATE -> list.sortedByDescending { it.sortDate }
                MixSortType.NAME -> list.sortedWith(compareBy(collator) { it.title })
                MixSortType.LAST_UPDATED -> list.sortedByDescending { it.updated }
            }
        }
    }

    val showLiked = filter == LibraryFilter.LIBRARY || filter == LibraryFilter.PLAYLISTS
    val sortLabel = when (sortType) {
        MixSortType.CREATE_DATE -> stringResource(R.string.library_sort_recents)
        MixSortType.NAME -> stringResource(R.string.library_sort_alphabetical)
        MixSortType.LAST_UPDATED -> stringResource(R.string.sort_by_last_updated)
    }
    val nextSort = {
        onSortTypeChange(
            when (sortType) {
                MixSortType.CREATE_DATE -> MixSortType.NAME
                MixSortType.NAME -> MixSortType.LAST_UPDATED
                MixSortType.LAST_UPDATED -> MixSortType.CREATE_DATE
            }
        )
    }

    val insets = LocalPlayerAwareWindowInsets.current
    val contentPadding = insets.only(WindowInsetsSides.Top + WindowInsetsSides.Bottom).asPaddingValues()
    val likedEntry = LibraryEntry(
        key = "liked",
        title = stringResource(R.string.library_liked_songs),
        subtitle = "$playlistLabel • $likedCount $songsLabel".lowercase().replaceFirstChar { it.uppercase() },
        thumbnailUrl = null,
        circle = false,
        sortDate = null,
        updated = null,
        onClick = { navController.navigate("auto_playlist/liked") },
    )

    val header: @Composable () -> Unit = {
        Column {
            YourLibraryChips(current = filter, onSelect = onFilterChange)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 4.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = nextSort)
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.swap_vert),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(text = sortLabel, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onViewTypeChange(viewType.toggle()) }) {
                    Icon(
                        painter = painterResource(
                            if (viewType == LibraryViewType.LIST) R.drawable.grid_view else R.drawable.list
                        ),
                        contentDescription = null,
                    )
                }
            }
        }
    }

    val addArtistsLabel = stringResource(R.string.library_add_artists)
    val addLocalLabel = stringResource(R.string.add_local_music)

    if (viewType == LibraryViewType.LIST) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            item(key = "header") { header() }
            if (showLiked) {
                item(key = likedEntry.key) { LibraryListRow(likedEntry, liked = true) }
            }
            items(entries, key = { it.key }) { entry -> LibraryListRow(entry) }
            item(key = "add_artists") {
                LibraryActionRow(
                    label = addArtistsLabel,
                    iconRes = R.drawable.add,
                    shape = CircleShape,
                    onClick = actions.openSearch,
                )
            }
            item(key = "add_local") {
                LibraryActionRow(
                    label = addLocalLabel,
                    iconRes = R.drawable.folder,
                    shape = RoundedCornerShape(6.dp),
                    onClick = onAddLocalMusic,
                )
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            item(key = "header", span = { GridItemSpan(maxLineSpan) }) { header() }
            if (showLiked) {
                item(key = likedEntry.key) { LibraryGridCell(likedEntry, liked = true) }
            }
            items(entries, key = { it.key }) { entry -> LibraryGridCell(entry) }
            item(key = "add_artists") {
                LibraryGridAction(addArtistsLabel, R.drawable.add, CircleShape, actions.openSearch)
            }
            item(key = "add_local") {
                LibraryGridAction(addLocalLabel, R.drawable.folder, RoundedCornerShape(6.dp), onAddLocalMusic)
            }
        }
    }
}

@Composable
private fun LibraryThumbnail(
    entry: LibraryEntry,
    liked: Boolean,
    size: Dp?,
) {
    val shape = if (entry.circle) CircleShape else RoundedCornerShape(4.dp)
    val base = if (size != null) Modifier.size(size) else Modifier.fillMaxWidth().aspectRatio(1f)
    when {
        liked -> Box(
            modifier = base
                .clip(shape)
                .background(Brush.linearGradient(listOf(Color(0xFF450AF5), Color(0xFF8E8EE5), Color(0xFFC4EFD9)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.favorite),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
        }
        entry.thumbnailUrl != null -> AsyncImage(
            model = entry.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = base
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
        else -> Box(
            modifier = base
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(if (entry.circle) R.drawable.person else R.drawable.queue_music),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun LibraryListRow(
    entry: LibraryEntry,
    liked: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = entry.onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LibraryThumbnail(entry, liked, size = 68.dp)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (liked) {
                    Icon(
                        painter = painterResource(R.drawable.bookmark_filled),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(14.dp)
                            .padding(end = 2.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = entry.subtitle,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun LibraryGridCell(
    entry: LibraryEntry,
    liked: Boolean = false,
) {
    Column(
        modifier = Modifier
            .clickable(onClick = entry.onClick)
            .padding(8.dp),
    ) {
        LibraryThumbnail(entry, liked, size = null)
        Spacer(Modifier.height(6.dp))
        Text(
            text = entry.title,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = entry.subtitle,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LibraryActionRow(
    label: String,
    iconRes: Int,
    shape: Shape,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(30.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            fontSize = 17.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun LibraryGridAction(
    label: String,
    iconRes: Int,
    shape: Shape,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(30.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(text = label, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Launcher for "Add local music": lets the user pick audio files with the
 * system picker, keeps read access to them and imports each one through
 * [LocalMediaScanner.scanUri] so they show up under "On device".
 */
@Composable
fun rememberAddLocalMusicLauncher(onImported: () -> Unit = {}): () -> Unit {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            var imported = 0
            uris.forEach { uri ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
                if (runCatching { LocalMediaScanner.scanUri(context, database, uri) }.getOrNull() != null) {
                    imported++
                }
            }
            Toast.makeText(
                context,
                context.getString(R.string.local_music_imported, imported),
                Toast.LENGTH_SHORT,
            ).show()
            if (imported > 0) onImported()
        }
    }

    return remember(launcher) { { launcher.launch(arrayOf("audio/*")) } }
}

/** Shows [CreatePlaylistDialog] while [visible]. */
@Composable
fun YourLibraryCreatePlaylist(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    if (visible) {
        CreatePlaylistDialog(onDismiss = onDismiss)
    }
}
