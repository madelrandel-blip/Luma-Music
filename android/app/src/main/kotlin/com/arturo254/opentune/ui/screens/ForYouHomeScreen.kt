/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.arturo254.opentune.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import coil3.compose.AsyncImage
import com.arturo254.opentune.LocalPlayerAwareWindowInsets
import com.arturo254.opentune.LocalPlayerConnection
import com.arturo254.opentune.R
import com.arturo254.opentune.constants.FavoriteArtistsPromptShownKey
import com.arturo254.opentune.ui.component.LocalMenuState
import com.arturo254.opentune.utils.dataStore
import com.arturo254.opentune.viewmodels.ForYouViewModel
import kotlinx.coroutines.flow.first

const val FavoriteArtistsRoute = "onboarding/artists"

/**
 * Home built only from the user's favorite artists (picked on first launch).
 * No generic recommendations, charts or preset playlists.
 */
@Composable
fun ForYouHomeScreen(
    navController: NavController,
    viewModel: ForYouViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val favorites by viewModel.favoriteArtists.collectAsState()
    val sections by viewModel.sections.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val hasError by viewModel.hasError.collectAsState()

    val listState = rememberLazyListState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop = backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsState()
    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            listState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    // First launch: open the picker automatically once.
    LaunchedEffect(favorites) {
        val current = favorites ?: return@LaunchedEffect
        if (current.isEmpty()) {
            val alreadyShown = context.dataStore.data.first()[FavoriteArtistsPromptShownKey] ?: false
            if (!alreadyShown) navController.navigate(FavoriteArtistsRoute)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = LocalPlayerAwareWindowInsets.current
            .only(WindowInsetsSides.Top + WindowInsetsSides.Bottom)
            .asPaddingValues(),
    ) {
        val artists = favorites ?: return@LazyColumn

        if (artists.isEmpty()) {
            item(key = "pick_prompt") {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 48.dp),
                ) {
                    Text(
                        text = stringResource(R.string.home_pick_artists_prompt),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { navController.navigate(FavoriteArtistsRoute) },
                        shape = CircleShape,
                    ) {
                        Text(stringResource(R.string.onboarding_pick_artists_title), fontWeight = FontWeight.Bold)
                    }
                }
            }
            return@LazyColumn
        }

        item(key = "favorite_artists") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(artists, key = { it.id }) { artist ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(76.dp)
                            .clickable { navController.navigate("artist/${artist.id}") },
                    ) {
                        AsyncImage(
                            model = artist.thumbnail,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = artist.name,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                item(key = "change") {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(76.dp)
                            .clickable { navController.navigate(FavoriteArtistsRoute) },
                    ) {
                        Column(
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.edit),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.home_change_artists),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (sections.isEmpty() && isLoading) {
            items(3) { HomeLoadingShimmer() }
        }

        if (sections.isEmpty() && hasError && !isLoading) {
            item(key = "error") {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                ) {
                    Text(
                        text = stringResource(R.string.home_load_error),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = viewModel::refresh) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
        }

        sections.forEach { section ->
            item(key = "title_${section.key}") {
                Text(
                    text = section.title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
                )
            }
            item(key = "row_${section.key}") {
                LazyRow(contentPadding = PaddingValues(horizontal = 4.dp)) {
                    items(section.items, key = { it.id }) { item ->
                        YouTubeGridItemWrapper(
                            item = item,
                            mediaMetadata = mediaMetadata,
                            isPlaying = isPlaying,
                            navController = navController,
                            playerConnection = playerConnection,
                            menuState = menuState,
                            haptic = haptic,
                            scope = scope,
                        )
                    }
                }
            }
        }

        item(key = "bottom_space") { Spacer(Modifier.height(16.dp)) }
    }
}
