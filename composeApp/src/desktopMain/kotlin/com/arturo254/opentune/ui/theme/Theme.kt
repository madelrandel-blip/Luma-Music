package com.arturo254.opentune.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.arturo254.opentune.DesktopPreferences
import com.arturo254.opentune.rememberCurrentPalette
import kotlin.math.roundToInt

private fun Color.desaturate(factor: Float): Color {
    val r = red; val g = green; val b = blue
    val gray = 0.299f * r + 0.587f * g + 0.114f * b
    return Color(
        red = (gray + factor * (r - gray)).coerceIn(0f, 1f),
        green = (gray + factor * (g - gray)).coerceIn(0f, 1f),
        blue = (gray + factor * (b - gray)).coerceIn(0f, 1f),
        alpha = alpha
    )
}

fun generateDarkColorScheme(seed: Color, pureBlack: Boolean): ColorScheme {
    val primary = seed
    val onPrimary = if (seed.luminance() > 0.5f) Color.Black else Color.White
    val primaryContainer = seed.desaturate(0.7f).copy(alpha = 0.35f)
    val onPrimaryContainer = seed.copy(alpha = 0.9f)

    val secondary = seed.desaturate(0.5f).copy(alpha = 0.85f)
    val onSecondary = if (secondary.luminance() > 0.4f) Color.Black else Color.White
    val secondaryContainer = seed.desaturate(0.6f).copy(alpha = 0.25f)
    val onSecondaryContainer = secondary.copy(alpha = 0.9f)

    val tertiary = seed.desaturate(0.3f).copy(alpha = 0.9f)
    val onTertiary = if (tertiary.luminance() > 0.4f) Color.Black else Color.White
    val tertiaryContainer = seed.desaturate(0.5f).copy(alpha = 0.2f)
    val onTertiaryContainer = tertiary.copy(alpha = 0.9f)

    val black = Color.Black
    val background = if (pureBlack) black else Color(0xFF1A1A1A)
    val onBackground = Color(0xFFE6E1E5)
    val surface = if (pureBlack) black else Color(0xFF1A1A1A)
    val onSurface = Color(0xFFE6E1E5)
    val surfaceVariant = if (pureBlack) Color(0xFF1C1C1C) else Color(0xFF49454F)
    val onSurfaceVariant = Color(0xFFCAC4D0)
    val surfaceTint = primary

    return darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceTint = surfaceTint,
        surfaceBright = if (pureBlack) Color(0xFF1C1C1C) else Color(0xFF383838),
        surfaceDim = if (pureBlack) black else Color(0xFF141218),
        surfaceContainer = if (pureBlack) Color(0xFF111111) else Color(0xFF211F26),
        surfaceContainerHigh = if (pureBlack) Color(0xFF1A1A1A) else Color(0xFF2B2930),
        surfaceContainerHighest = if (pureBlack) Color(0xFF222222) else Color(0xFF36343B),
        surfaceContainerLow = if (pureBlack) Color(0xFF0E0E0E) else Color(0xFF1D1B20),
        surfaceContainerLowest = if (pureBlack) black else Color(0xFF0F0D13),
        inversePrimary = primary.copy(alpha = 0.8f),
        inverseSurface = Color(0xFFE6E1E5),
        inverseOnSurface = Color(0xFF322F35),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        outline = Color(0xFF938F99),
        outlineVariant = if (pureBlack) Color(0xFF2A2A2A) else Color(0xFF49454F),
        scrim = Color.Black,
    )
}

@Composable
fun LumaMusicTheme(content: @Composable () -> Unit) {
    val palette = rememberCurrentPalette()
    val pureBlack = DesktopPreferences.pureBlack
    val colorScheme = remember(palette.primary, pureBlack) {
        generateDarkColorScheme(palette.primary, pureBlack)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
