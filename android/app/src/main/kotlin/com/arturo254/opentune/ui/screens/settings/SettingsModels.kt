/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.arturo254.opentune.ui.screens.settings

import androidx.compose.ui.graphics.painter.Painter

data class SettingsQuickToggle(
    val icon: Painter,
    val label: String,
    val state: String,
    val isActive: Boolean,
    val onToggle: () -> Unit,
)

data class SettingsItem(
    val icon: Painter,
    val title: String,
    val subtitle: String? = null,
    val showUpdateIndicator: Boolean = false,
    val keywords: List<String> = emptyList(),
    val onClick: () -> Unit,
)
