/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.arturo254.opentune.ui.screens.settings

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object SettingsDimensions {
    val GroupCardCornerRadius = 16.dp
    val BannerCardCornerRadius = 20.dp

    val ScreenHorizontalPadding = 16.dp
    val SectionSpacing = 14.dp

    val BannerIconSize = 44.dp
    val BannerIconInnerSize = 22.dp

    val QuickToggleHeight = 92.dp
    val QuickToggleCornerRadius = 10.dp
    val QuickToggleSpacing = 8.dp
    val QuickToggleIconSize = 24.dp

    val CategoryRowVerticalPadding = 12.dp
    val CategoryIconSize = 24.dp
    val CategoryIconTextGap = 18.dp
    val CategoryTitleSize = 16.sp
    val CategorySubtitleSize = 13.sp

    val LogoutPillHorizontalPadding = 32.dp
    val LogoutPillVerticalPadding = 14.dp

    val MaxContentWidth = 640.dp
}

object SettingsAnimations {
    val PressScale = 0.97f
    val TilePressScale = 0.95f

    /** Delay before the content starts appearing, so the top bar shows first. */
    val ContentStartDelay = 120
    val StaggerDelayPerItem = 35
    val EntranceItemDuration = 250

    /** Items past this index share the last slot so long lists don't trail in. */
    val MaxStaggerIndex = 12

    val EntranceOffsetY = 14.dp
    val EntranceOffsetX = 6.dp

    val NavSlideDuration = 280
    val NavFadeDuration = 200

    val totalEntranceDuration: Int
        get() = ContentStartDelay + MaxStaggerIndex * StaggerDelayPerItem + EntranceItemDuration

    fun <T> pressSpring() = spring<T>(stiffness = Spring.StiffnessHigh)
}
