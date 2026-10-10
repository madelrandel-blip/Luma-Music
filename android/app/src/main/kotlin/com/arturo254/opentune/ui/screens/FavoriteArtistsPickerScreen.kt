/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.arturo254.opentune.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import com.arturo254.opentune.LocalPlayerAwareWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.arturo254.opentune.R
import com.arturo254.opentune.constants.FavoriteArtist
import com.arturo254.opentune.constants.FavoriteArtistsKey
import com.arturo254.opentune.constants.FavoriteArtistsPromptShownKey
import com.arturo254.opentune.constants.FavoriteArtistsRequired
import com.arturo254.opentune.constants.decodeFavoriteArtists
import com.arturo254.opentune.constants.encodeFavoriteArtists
import com.arturo254.opentune.innertube.YouTube
import com.arturo254.opentune.innertube.models.ArtistItem
import com.arturo254.opentune.utils.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.datastore.preferences.core.edit

/**
 * Onboarding (route `onboarding/artists`): the user picks exactly
 * [FavoriteArtistsRequired] artists, which Home then uses for all its
 * recommendations. Also reachable later from Settings > Favorite artists.
 */
@Composable
fun FavoriteArtistsPickerScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val selected = remember { mutableStateListOf<FavoriteArtist>() }
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ArtistItem>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    val defaultQuery = stringResource(R.string.onboarding_default_query)

    LaunchedEffect(Unit) {
        // Pre-select what was saved before, so "change artists" starts from the current picks.
        val saved = context.dataStore.data.first()[FavoriteArtistsKey].decodeFavoriteArtists()
        if (selected.isEmpty()) selected.addAll(saved.take(FavoriteArtistsRequired))
        context.dataStore.edit { it[FavoriteArtistsPromptShownKey] = true }
    }

    LaunchedEffect(query) {
        delay(if (query.isBlank()) 0 else 400)
        isSearching = true
        results = YouTube.search(query.ifBlank { defaultQuery }, YouTube.SearchFilter.FILTER_ARTIST)
            .getOrNull()
            ?.items
            ?.filterIsInstance<ArtistItem>()
            .orEmpty()
        isSearching = false
    }

    // Selected artists first, then search results that aren't selected yet.
    val shown = remember(results, selected.toList()) {
        selected.map { ArtistChoice(it.id, it.name, it.thumbnail) } +
            results.filter { r -> selected.none { it.id == r.id } }
                .map { ArtistChoice(it.id, it.title, it.thumbnail) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { navController.navigateUp() }) {
                Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
            }
        }

        Text(
            text = stringResource(R.string.onboarding_pick_artists_title),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.onboarding_pick_artists_desc),
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.search)) },
            leadingIcon = { Icon(painterResource(R.drawable.search), contentDescription = null) },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(shown, key = { it.id }) { choice ->
                    val isSelected = selected.any { it.id == choice.id }
                    ArtistChoiceCell(
                        choice = choice,
                        selected = isSelected,
                        onClick = {
                            if (isSelected) {
                                selected.removeAll { it.id == choice.id }
                            } else if (selected.size < FavoriteArtistsRequired) {
                                selected.add(FavoriteArtist(choice.id, choice.name, choice.thumbnail))
                            }
                        },
                    )
                }
            }
            if (isSearching && results.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${selected.size}/$FavoriteArtistsRequired",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Button(
                enabled = selected.size == FavoriteArtistsRequired,
                shape = CircleShape,
                onClick = {
                    coroutineScope.launch {
                        context.dataStore.edit {
                            it[FavoriteArtistsKey] = selected.toList().encodeFavoriteArtists()
                        }
                        navController.navigateUp()
                    }
                },
            ) {
                Text(
                    text = stringResource(R.string.onboarding_done),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }
    }
}

private data class ArtistChoice(
    val id: String,
    val name: String,
    val thumbnail: String?,
)

@Composable
private fun ArtistChoiceCell(
    choice: ArtistChoice,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            AsyncImage(
                model = choice.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .then(
                        if (selected) {
                            Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        } else {
                            Modifier
                        }
                    ),
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.check),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = choice.name,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
