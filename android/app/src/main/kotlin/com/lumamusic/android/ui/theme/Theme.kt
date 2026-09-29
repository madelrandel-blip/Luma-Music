/*
 * Luma Music
 * Dark theme using the same default "Rose" accent as the desktop app
 * (DesktopPalette("default", "Default (Rose)", Color(0xFFED5564)) in DesktopPreferences.kt).
 */

package com.lumamusic.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val LumaRose = Color(0xFFED5564)

private val LumaDarkColors = darkColorScheme(
    primary = LumaRose,
    onPrimary = Color.Black,
    secondary = LumaRose,
    background = Color(0xFF121212),
    surface = Color(0xFF121212),
    surfaceContainer = Color(0xFF1C1C1E),
    surfaceContainerHigh = Color(0xFF262628),
    onBackground = Color(0xFFEDEDED),
    onSurface = Color(0xFFEDEDED),
)

private val LumaLightColors = lightColorScheme(
    primary = LumaRose,
    onPrimary = Color.White,
    secondary = LumaRose,
)

@Composable
fun LumaMusicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) LumaDarkColors else LumaLightColors,
        content = content,
    )
}
