package com.arturo254.opentune

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toPainter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import javax.imageio.ImageIO

fun main() = application {
    AccountManager.initialize()
    val windowState = rememberWindowState(width = 1000.dp, height = 650.dp)
    var wasFullscreen = DesktopPreferences.fullscreenPlayer

    val windowIcon = remember {
        try {
            val stream = Thread.currentThread().contextClassLoader?.getResourceAsStream("icon.png")
            stream?.use { ImageIO.read(it)?.toPainter() }
        } catch (_: Exception) {
            null
        }
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "Luma Music",
        icon = windowIcon,
        state = windowState
    ) {
        LaunchedEffect(DesktopPreferences.fullscreenPlayer) {
            val isNow = DesktopPreferences.fullscreenPlayer
            if (isNow && !wasFullscreen) {
                windowState.placement = WindowPlacement.Fullscreen
            } else if (!isNow && wasFullscreen) {
                windowState.placement = WindowPlacement.Maximized
            }
            wasFullscreen = isNow
        }

        App()
    }
}
