package com.arturo254.opentune

import com.arturo254.opentune.party.PartyModeManager
import com.arturo254.opentune.player.DiscordRpcManager
import com.arturo254.opentune.player.PlayerManager

/**
 * Everything that has to stop when Luma Music closes.
 *
 * Closing the window is not enough on its own: the app starts helper *processes* of its own
 * (ffmpeg to decode audio, yt-dlp to resolve and download it, and the embedded browser's own
 * child processes when the YouTube sign-in has been opened). On Windows a child process is not
 * killed when its parent goes away, so anything still running at that moment stays behind in the
 * Task Manager - burning CPU, holding the cache files open, and keeping the installation folder
 * locked ("in use by another program") when trying to update or uninstall.
 *
 * [shutdownEverything] therefore does three things, in order: it asks each part of the app to
 * stop cleanly, it disposes the embedded browser (whose native side has processes of its own),
 * and finally it kills whatever child processes are *still* alive - found through this process's
 * own descendants, so only things this app started are ever touched.
 *
 * [installShutdownHook] wires the same child-process cleanup into the JVM's shutdown hooks, so
 * an exit that doesn't go through the close button (a crash, a "close window" from the taskbar,
 * a kill from outside) still doesn't leave orphans behind.
 */
object AppShutdown {
    @Volatile private var alreadyRan = false

    fun shutdownEverything() {
        if (alreadyRan) return
        alreadyRan = true

        // Politely first: each of these can refuse to finish (a stuck socket, a native call that
        // never returns), so every one is isolated and none of them can block the rest.
        runCatching { PartyModeManager.leaveRoom() }
        runCatching { PlayerManager.shutdown() }
        runCatching { DiscordRpcManager.stop() }
        runCatching { EmbeddedBrowserLogin.shutdown() }

        killChildProcesses()
    }

    /**
     * Kills every process this one started that is still alive - ffmpeg, yt-dlp, the embedded
     * browser's helpers. Asks them to quit first and only then forces it, so a download being
     * written to disk gets the chance to close its file handle cleanly.
     */
    fun killChildProcesses() {
        runCatching {
            val children = ProcessHandle.current().descendants().toList()
            if (children.isEmpty()) return@runCatching
            children.forEach { runCatching { it.destroy() } }
            // Brief grace period, then no more Mr. Nice Guy.
            Thread.sleep(200)
            ProcessHandle.current().descendants().forEach { runCatching { it.destroyForcibly() } }
        }
    }

    fun installShutdownHook() {
        runCatching {
            Runtime.getRuntime().addShutdownHook(
                Thread({ killChildProcesses() }, "luma-shutdown-hook")
            )
        }
    }

    /**
     * Last resort for the close button: if any of the cleanup above hangs, the process is ended
     * anyway after [timeoutMs] instead of sitting in the Task Manager with no window - which is
     * exactly the symptom this whole file exists to prevent.
     */
    fun startExitWatchdog(timeoutMs: Long = 4000) {
        Thread({
            try {
                Thread.sleep(timeoutMs)
            } catch (_: InterruptedException) {
                return@Thread
            }
            runCatching { killChildProcesses() }
            // halt, not exit: shutdown hooks have had their chance by now, and one of them
            // hanging is a reason this watchdog is needed in the first place.
            Runtime.getRuntime().halt(0)
        }, "luma-exit-watchdog").apply { isDaemon = true }.start()
    }
}
