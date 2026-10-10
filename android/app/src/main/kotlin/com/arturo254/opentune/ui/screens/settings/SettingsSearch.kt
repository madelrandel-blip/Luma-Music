/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.arturo254.opentune.ui.screens.settings

fun filterQuickToggles(
    toggles: List<SettingsQuickToggle>,
    query: String,
): List<SettingsQuickToggle> {
    if (query.isBlank()) return toggles
    return toggles.filter { it.label.contains(query, ignoreCase = true) }
}

fun filterSettingsItems(
    items: List<SettingsItem>,
    query: String,
): List<SettingsItem> {
    if (query.isBlank()) return items
    return items.filter { matchesQuery(it, query) }
}

fun matchesQuery(
    item: SettingsItem,
    query: String,
): Boolean {
    if (item.title.contains(query, ignoreCase = true)) return true
    if (item.subtitle?.contains(query, ignoreCase = true) == true) return true
    return item.keywords.any { keyword ->
        keyword.contains(query, ignoreCase = true) ||
            query.contains(keyword, ignoreCase = true)
    }
}
