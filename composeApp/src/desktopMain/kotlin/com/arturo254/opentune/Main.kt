package com.arturo254.opentune

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import com.arturo254.opentune.library.CacheMetadataManager
import com.arturo254.opentune.library.DownloadsManager
import com.arturo254.opentune.library.FollowedArtistsManager
import com.arturo254.opentune.library.ListenHistoryManager
import com.arturo254.opentune.library.PlaylistsManager
import com.arturo254.opentune.library.SearchHistoryManager
import com.arturo254.opentune.party.PartyModeManager
import com.arturo254.opentune.player.PlayerManager
import com.arturo254.opentune.ui.NowPlayingState
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.math.roundToInt

fun main() {
    // Covers exits that never reach the close button below (a crash, or the window being closed
    // from the taskbar): whatever the app started must not outlive it.
    AppShutdown.installShutdownHook()
    initializeAppState()
    // Gets the slow part of starting a song (YouTube's player script) ready in the background,
    // so the first song of the session doesn't have to wait for it.
    PlayerManager.warmUp()
    application { LumaApp() }
}

/**
 * Creates every app-wide object that holds Compose state, here on the main thread and before
 * any UI exists.
 *
 * These objects are created lazily, the first time something touches them. That used to happen
 * *inside* the `application { }` block - i.e. in the middle of a composition, inside Compose's
 * private snapshot - or on whichever background thread got there first (the account refresh,
 * the song resolver, the playback thread). A state created that way is invisible to everyone
 * else until that snapshot is applied, so a background thread (or the UI) reading it a moment
 * too early crashed with "Reading a state that was created after the snapshot was taken or in a
 * snapshot that has not yet been applied". Whether it happened depended on timing - e.g. on a
 * relaunch, when saved data (a linked account, history) made the background work finish
 * sooner - which is why it looked random. Created here, up front, they all exist in the global
 * snapshot before anything can race on them.
 */
private fun initializeAppState() {
    val singletons: List<Any> = listOf(
        DesktopPreferences,
        GuestProfileManager,
        ListenHistoryManager,
        SearchHistoryManager,
        PlaylistsManager,
        FollowedArtistsManager,
        DownloadsManager,
        CacheMetadataManager,
        NowPlayingState,
        SongContextNav,
        PartyModeManager,
    )
    check(singletons.isNotEmpty())
    AccountManager.initialize()
}

/** Loads an app resource as a Compose [androidx.compose.ui.graphics.painter.Painter], or null if
 * it's missing or unreadable - used for the [Window]'s baseline `icon` param (macOS dock, and the
 * fallback Windows uses before [BufferedImage]-based icons are applied below). */
private fun loadIconPainter(resourceName: String) =
    try {
        Thread.currentThread().contextClassLoader
            ?.getResourceAsStream(resourceName)?.use { ImageIO.read(it)?.toPainter() }
    } catch (_: Exception) {
        null
    }

/** Loads an app resource as a raw [BufferedImage] - used for [java.awt.Window.setIconImages],
 * which needs one bitmap per size so Windows never has to scale a single huge icon down for the
 * title bar/taskbar/Alt+Tab (that scaling is what made the icon look blurry before). */
private fun loadIconImage(resourceName: String): BufferedImage? =
    try {
        Thread.currentThread().contextClassLoader?.getResourceAsStream(resourceName)?.use { ImageIO.read(it) }
    } catch (_: Exception) {
        null
    }

@Composable
private fun ApplicationScope.LumaApp() {
    val windowState = rememberWindowState(width = 1000.dp, height = 650.dp)
    var wasFullscreen = DesktopPreferences.fullscreenPlayer

    val windowIcon = remember { loadIconPainter("icon.png") }
    val windowIcons = remember {
        listOf("icon-16.png", "icon-24.png", "icon-32.png", "icon-48.png", "icon-64.png", "icon-128.png", "icon-256.png")
            .mapNotNull { loadIconImage(it) }
    }

    // Shared by the close button below AND by this window's own taskbar/system close so both
    // paths always run the exact same shutdown sequence.
    fun closeApp() {
        // Closing the window alone doesn't end everything the app is running: besides its own
        // non-daemon threads, it has helper *processes* (ffmpeg, yt-dlp, the embedded browser's)
        // which Windows leaves running when their parent disappears - that's what shows up in
        // the Task Manager afterwards and keeps the installed files locked. AppShutdown stops
        // each part and then kills whatever child processes remain; the watchdog guarantees the
        // process ends even if one of them refuses to.
        AppShutdown.startExitWatchdog()
        AppShutdown.shutdownEverything()
        try {
            exitApplication()
        } finally {
            kotlin.system.exitProcess(0)
        }
    }

    Window(
        onCloseRequest = { closeApp() },
        // Still set for accessibility/screen readers and for the taskbar tooltip on hover, even
        // though there is no native title bar left to print it in.
        title = "Luma Music",
        icon = windowIcon,
        state = windowState,
        // No native title bar at all - the icon+text in the window's own top-left corner that
        // was asked to be removed lived there, and Windows has no way to keep the frame but drop
        // just that. Removing the frame also removes Windows' own minimize/maximize/close
        // buttons and drag handling, which the custom title bar below replaces.
        undecorated = true,
    ) {
        LaunchedEffect(Unit) {
            applyDarkTitleBar(window)
            // An undecorated AWT window keeps whatever icon `Window(icon = ...)` gave it, but
            // that's a single bitmap Windows then scales per context. Handing it the full size
            // ladder here (as setIconImages) is what keeps the taskbar button and Alt+Tab sharp.
            if (windowIcons.isNotEmpty()) {
                window.iconImages = windowIcons
            }
        }

        LaunchedEffect(DesktopPreferences.fullscreenPlayer) {
            val isNow = DesktopPreferences.fullscreenPlayer
            if (isNow && !wasFullscreen) {
                windowState.placement = WindowPlacement.Fullscreen
            } else if (!isNow && wasFullscreen) {
                windowState.placement = WindowPlacement.Maximized
            }
            wasFullscreen = isNow
        }

        Column(modifier = Modifier.fillMaxSize()) {
            CustomTitleBar(windowState = windowState, onClose = { closeApp() })
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                App()
            }
        }
    }
}

/**
 * Replaces the native Windows title bar we just removed above: a thin draggable strip with only
 * minimize/maximize-restore/close buttons - deliberately no app icon and no title text, since
 * hiding exactly those from this corner was the original request. The real icon still shows in
 * the taskbar and Alt+Tab because those are set separately via [java.awt.Window.setIconImages]
 * above, not by anything drawn here.
 *
 * Dragging is done by hand (moving the underlying AWT [window] on each pointer move) instead of
 * via Compose's `WindowDraggableArea`, which isn't resolvable with this project's Compose
 * Multiplatform version - this has the same effect without needing that symbol.
 *
 * Maximizing is also done by hand instead of via `windowState.placement = WindowPlacement.Maximized`:
 * on an undecorated AWT window, Windows treats a "maximized" state as covering the entire monitor,
 * taskbar included, which is what made the button look like it triggered fullscreen. Sizing the
 * window to `GraphicsEnvironment`'s maximum window bounds instead gives a normal, large window
 * that still leaves the taskbar visible, and restores back to its previous size/position.
 */
@Composable
private fun WindowScope.CustomTitleBar(windowState: WindowState, onClose: () -> Unit) {
    var isMaximized by remember { mutableStateOf(false) }
    var boundsBeforeMaximize by remember { mutableStateOf<Rectangle?>(null) }

    fun toggleMaximize() {
        if (isMaximized) {
            boundsBeforeMaximize?.let { window.bounds = it }
            isMaximized = false
        } else {
            boundsBeforeMaximize = window.bounds
            // Already excludes the taskbar/dock (and any other reserved screen space), unlike
            // `Frame.MAXIMIZED_BOTH` on an undecorated window.
            window.bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
            isMaximized = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(Color(0xFF0A0A0A))
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    // A maximized window doesn't support being repositioned by dragging - matches
                    // how a normal Windows title bar behaves (drag-from-maximized restores first
                    // instead of moving), so this is skipped rather than fighting the window
                    // manager.
                    if (!isMaximized) {
                        val current = window.location
                        window.setLocation(
                            current.x + dragAmount.x.roundToInt(),
                            current.y + dragAmount.y.roundToInt()
                        )
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { toggleMaximize() })
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TitleBarButton(symbol = "–", onClick = { windowState.isMinimized = true })
            TitleBarButton(symbol = if (isMaximized) "❒" else "□", onClick = { toggleMaximize() })
            TitleBarButton(symbol = "✕", closeStyle = true, onClick = onClose)
        }
    }
}

@Composable
private fun TitleBarButton(symbol: String, closeStyle: Boolean = false, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val backgroundColor = when {
        isHovered && closeStyle -> Color(0xFFE81123) // matches Windows' own close-hover red
        isHovered -> Color(0x1AFFFFFF) // a faint highlight, like the native minimize/maximize hover
        else -> Color.Transparent
    }
    val textColor = if (isHovered && closeStyle) Color.White else if (closeStyle) Color(0xFFB0B0B0) else Color(0xFFB0B0B0)

    Box(
        modifier = Modifier
            .width(46.dp)
            .fillMaxHeight()
            .background(backgroundColor)
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(text = symbol, color = textColor, fontSize = 13.sp)
    }
}

/**
 * Makes the native OS title bar match the app's dark theme instead of the default white/light
 * one Windows draws. There's no Swing/AWT API for this, so we call the DWM API directly.
 */
private fun applyDarkTitleBar(window: java.awt.Window) {
    if (!System.getProperty("os.name").orEmpty().contains("Windows", ignoreCase = true)) return
    try {
        val hwnd = WinDef.HWND(Native.getComponentPointer(window))

        // DWMWA_USE_IMMERSIVE_DARK_MODE: attribute id is 20 on Windows 10 20H1+/11, but was 19
        // on the earlier 20H1 preview builds - try the current one first, fall back if it fails.
        val trueValue = IntByReference(1)
        val darkModeResult = DwmApiExt.INSTANCE.DwmSetWindowAttribute(hwnd, 20, trueValue.pointer, 4)
        if (darkModeResult != 0) {
            DwmApiExt.INSTANCE.DwmSetWindowAttribute(hwnd, 19, trueValue.pointer, 4)
        }

        // DWMWA_CAPTION_COLOR (Windows 11 only): forces a true black title bar instead of the
        // dark gray that "dark mode" alone gives it. Harmlessly ignored on Windows 10.
        val black = IntByReference(0x00000000)
        DwmApiExt.INSTANCE.DwmSetWindowAttribute(hwnd, 35, black.pointer, 4)
    } catch (_: Throwable) {
        // Best effort - if DWM isn't available for any reason the window just keeps the
        // default title bar instead of crashing.
    }
}

private interface DwmApiExt : StdCallLibrary {
    fun DwmSetWindowAttribute(hwnd: WinDef.HWND, dwAttribute: Int, pvAttribute: Pointer, cbAttribute: Int): Int

    companion object {
        val INSTANCE: DwmApiExt = Native.load("dwmapi", DwmApiExt::class.java, W32APIOptions.DEFAULT_OPTIONS)
    }
}
