/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.arturo254.opentune.ui.screens.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arturo254.opentune.LocalPlayerAwareWindowInsets

data class SettingsContentState(
    val quickToggles: List<SettingsQuickToggle>,
    val rows: List<SettingsItem>,
    val showPermissionBanner: Boolean,
    val showUpdateBanner: Boolean,
    val latestVersion: String,
    val showLogout: Boolean,
    val isSearchActive: Boolean,
    val onRequestPermission: () -> Unit,
    val onUpdateClick: () -> Unit,
    val onLogoutClick: () -> Unit,
)

/**
 * Drives the one-shot entrance of the settings list. The returned clock runs
 * from 0 to [SettingsAnimations.totalEntranceDuration] milliseconds; every
 * item derives its own progress from it, so rows that are scrolled into view
 * later (and get recomposed by the LazyColumn) don't replay the animation.
 */
@Composable
fun rememberSettingsEntranceClock(animate: Boolean): Animatable<Float, AnimationVector1D> {
    val total = SettingsAnimations.totalEntranceDuration.toFloat()
    val clock = remember { Animatable(if (animate) 0f else total) }
    LaunchedEffect(Unit) {
        if (clock.value < total) {
            clock.animateTo(
                targetValue = total,
                animationSpec = tween(total.toInt(), easing = LinearEasing),
            )
        }
    }
    return clock
}

fun Modifier.settingsEntrance(
    clock: Animatable<Float, AnimationVector1D>,
    index: Int,
): Modifier = graphicsLayer {
    val start = SettingsAnimations.ContentStartDelay +
        index.coerceAtMost(SettingsAnimations.MaxStaggerIndex) * SettingsAnimations.StaggerDelayPerItem
    val raw = ((clock.value - start) / SettingsAnimations.EntranceItemDuration).coerceIn(0f, 1f)
    val progress = FastOutSlowInEasing.transform(raw)
    alpha = progress
    translationY = (1f - progress) * SettingsAnimations.EntranceOffsetY.toPx()
    translationX = (1f - progress) * -SettingsAnimations.EntranceOffsetX.toPx()
}

@Composable
fun SettingsList(
    state: SettingsContentState,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    topPadding: Dp = 0.dp,
    animateEntrance: Boolean = true,
) {
    val clock = rememberSettingsEntranceClock(animateEntrance)
    val pad = SettingsDimensions.ScreenHorizontalPadding
    val spacing = SettingsDimensions.SectionSpacing

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .widthIn(max = SettingsDimensions.MaxContentWidth)
                .fillMaxSize()
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                    )
                ),
            contentPadding = PaddingValues(top = topPadding + 8.dp, bottom = 32.dp),
        ) {
            if (!state.isSearchActive) {
                if (state.showPermissionBanner) {
                    item(key = "permission") {
                        SettingsPermissionBanner(
                            onRequestPermission = state.onRequestPermission,
                            modifier = Modifier
                                .settingsEntrance(clock, 0)
                                .padding(horizontal = pad)
                                .padding(bottom = spacing),
                        )
                    }
                }

                if (state.showUpdateBanner) {
                    item(key = "update") {
                        SettingsUpdateBanner(
                            latestVersion = state.latestVersion,
                            onClick = state.onUpdateClick,
                            modifier = Modifier
                                .settingsEntrance(clock, 0)
                                .padding(horizontal = pad)
                                .padding(bottom = spacing),
                        )
                    }
                }
            }

            if (state.quickToggles.isNotEmpty()) {
                item(key = "quickToggles") {
                    SettingsQuickTogglesRow(
                        toggles = state.quickToggles,
                        modifier = Modifier
                            .settingsEntrance(clock, 0)
                            .padding(horizontal = pad)
                            .padding(bottom = 12.dp),
                    )
                }
            }

            if (state.isSearchActive && state.quickToggles.isEmpty() && state.rows.isEmpty()) {
                item(key = "empty") {
                    SettingsSearchEmpty(
                        modifier = Modifier.padding(horizontal = pad, vertical = 24.dp),
                    )
                }
            }

            itemsIndexed(
                items = state.rows,
                key = { _, item -> "row_${item.title}" },
            ) { index, item ->
                SettingsCategoryRow(
                    item = item,
                    modifier = Modifier.settingsEntrance(clock, index + 1),
                )
            }

            if (state.showLogout && !state.isSearchActive) {
                item(key = "logout") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .settingsEntrance(clock, state.rows.size + 1)
                            .padding(top = 24.dp, bottom = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        SettingsLogoutPill(onClick = state.onLogoutClick)
                    }
                }
            }
        }
    }
}
