/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.arturo254.opentune.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.arturo254.opentune.R
import com.arturo254.opentune.constants.GuestAvatarUriKey
import com.arturo254.opentune.constants.GuestModeEnabledKey
import com.arturo254.opentune.constants.GuestNameKey
import com.arturo254.opentune.constants.InnerTubeCookieKey
import com.arturo254.opentune.innertube.utils.parseCookieString
import com.arturo254.opentune.utils.rememberPreference
import com.arturo254.opentune.viewmodels.HomeViewModel
import kotlin.math.roundToInt

private const val DrawerWidthFraction = 0.88f
private const val DrawerEnterDuration = 300
private const val DrawerExitDuration = 250
private val DrawerDismissDistance = 80.dp

/**
 * Left side panel opened from the profile avatar, in the style of
 * Spotify's account drawer: a profile card ("View profile"), then a list
 * of shortcuts ending in "Settings and privacy".
 */
@Composable
fun ProfileDrawer(
    visible: Boolean,
    onDismiss: () -> Unit,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = visible, onBack = onDismiss)

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(DrawerEnterDuration)),
            exit = fadeOut(tween(DrawerExitDuration)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    )
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally(tween(DrawerEnterDuration, easing = FastOutSlowInEasing)) { -it },
            exit = slideOutHorizontally(tween(DrawerExitDuration, easing = FastOutSlowInEasing)) { -it },
        ) {
            val dismissDistancePx = with(LocalDensity.current) { DrawerDismissDistance.toPx() }
            var dragOffset by remember { mutableFloatStateOf(0f) }

            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(DrawerWidthFraction)
                    .offset { IntOffset(dragOffset.roundToInt(), 0) }
                    .draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { delta ->
                            dragOffset = (dragOffset + delta).coerceAtMost(0f)
                        },
                        onDragStopped = { velocity ->
                            if (dragOffset < -dismissDistancePx || velocity < -1000f) {
                                onDismiss()
                            } else {
                                dragOffset = 0f
                            }
                        },
                    ),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                ProfileDrawerContent(
                    navController = navController,
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun ProfileDrawerContent(
    navController: NavController,
    onDismiss: () -> Unit,
) {
    val (innerTubeCookie, _) = rememberPreference(InnerTubeCookieKey, "")
    val (guestModeEnabled, _) = rememberPreference(GuestModeEnabledKey, false)
    val (guestName, _) = rememberPreference(GuestNameKey, "")
    val (guestAvatarUri, _) = rememberPreference(GuestAvatarUriKey, "")
    val isLoggedIn = remember(innerTubeCookie) {
        "SAPISID" in parseCookieString(innerTubeCookie)
    }
    val isGuest = guestModeEnabled && !isLoggedIn

    val viewModel: HomeViewModel = hiltViewModel()
    val accountName by viewModel.accountName.collectAsState()
    val accountImageUrl by viewModel.accountImageUrl.collectAsState()

    val name = when {
        isLoggedIn -> accountName
        isGuest -> guestName.ifBlank { stringResource(R.string.guest_default_name) }
        else -> stringResource(R.string.not_logged_in)
    }
    val avatarUrl = when {
        isLoggedIn -> accountImageUrl
        isGuest -> guestAvatarUri.ifBlank { null }
        else -> null
    }

    val go: (String) -> Unit = { route ->
        onDismiss()
        navController.navigate(route)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { go("settings/account") }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (avatarUrl != null) {
                            AsyncImage(
                                model = avatarUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape),
                            )
                        } else {
                            Icon(
                                painter = painterResource(if (isGuest) R.drawable.person else R.drawable.account),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }

                    Spacer(Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = name,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(R.string.view_profile),
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (!isLoggedIn) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { go("settings/account_options") }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.add),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.drawer_add_account),
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource(R.string.drawer_add_account_desc),
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        DrawerRow(
            icon = painterResource(R.drawable.stats),
            label = stringResource(R.string.drawer_listening_stats),
            onClick = { go("stats") },
        )
        DrawerRow(
            icon = painterResource(R.drawable.history),
            label = stringResource(R.string.drawer_recents),
            onClick = { go("history") },
        )
        DrawerRow(
            icon = painterResource(R.drawable.auto_awesome),
            label = stringResource(R.string.year_in_music),
            onClick = { go("year_in_music") },
        )
        DrawerRow(
            icon = painterResource(R.drawable.fire),
            label = stringResource(R.string.music_together),
            onClick = { go("settings/music_together") },
        )
        DrawerRow(
            icon = painterResource(R.drawable.settings),
            label = stringResource(R.string.drawer_settings_privacy),
            onClick = { go("settings") },
        )
    }
}

@Composable
private fun DrawerRow(
    icon: Painter,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(20.dp))
        Text(
            text = label,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
