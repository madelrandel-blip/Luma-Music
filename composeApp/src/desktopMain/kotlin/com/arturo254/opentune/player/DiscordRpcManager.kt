package com.arturo254.opentune.player

import club.minnced.discord.rpc.DiscordEventHandlers
import club.minnced.discord.rpc.DiscordRPC
import club.minnced.discord.rpc.DiscordRichPresence
import com.arturo254.opentune.innertube.models.SongItem
import java.io.File
import kotlin.concurrent.thread

object DiscordRpcManager {

    // Crea tu app en https://discord.com/developers/applications, ponle "Luma Music"
    // y copia aqui el Application ID (columna de la izquierda -> General Information).
    private const val APPLICATION_ID = "1537163940079996938"

    private val lib: DiscordRPC by lazy { DiscordRPC.INSTANCE }

    private var initialized = false
    private var lastSongId: String? = null
    private var lastState = STATE_CLEARED
    private var startTs = 0L

    private const val STATE_CLEARED = 0
    private const val STATE_PLAYING = 1
    private const val STATE_PAUSED = 2

    fun start() {
        if (initialized || APPLICATION_ID.isBlank() || APPLICATION_ID.startsWith("AQUI_")) return
        try {
            bundledBinDir()?.let { System.setProperty("jna.library.path", it) }
            val rpc = lib
            rpc.Discord_Initialize(APPLICATION_ID, DiscordEventHandlers(), true, null)
            initialized = true
            thread(name = "discord-rpc-callbacks", isDaemon = true) {
                try {
                    while (true) {
                        rpc.Discord_RunCallbacks()
                        Thread.sleep(500)
                    }
                } catch (e: InterruptedException) {
                    rpc.Discord_Shutdown()
                }
            }
            println("[DiscordRPC] Rich Presence iniciado")
        } catch (e: Throwable) {
            initialized = false
            println("[DiscordRPC] No se pudo iniciar: ${e.message}")
        }
    }

    fun onSongChanged(song: SongItem?, playing: Boolean) {
        if (!initialized) return
        val state = when {
            song == null -> STATE_CLEARED
            playing -> STATE_PLAYING
            else -> STATE_PAUSED
        }
        if (song?.id == lastSongId && state == lastState) return
        val isNewSong = song?.id != lastSongId
        lastSongId = song?.id
        lastState = state
        try {
            if (song == null) {
                lib.Discord_ClearPresence()
                return
            }
            if (isNewSong || startTs == 0L) startTs = System.currentTimeMillis() / 1000

            val presence = DiscordRichPresence()
            presence.details = song.title
            presence.state = song.artists.joinToString(", ") { it.name }
            if (song.thumbnail.isNotBlank()) {
                presence.largeImageKey = highResThumb(song.thumbnail)
                presence.largeImageText = song.title
            }
            presence.startTimestamp = startTs
            presence.instance = 1
            lib.Discord_UpdatePresence(presence)
        } catch (e: Throwable) {
            println("[DiscordRPC] Error actualizando presencia: ${e.message}")
        }
    }

    // Convierte la miniatura de YouTube a una version cuadrada de mayor resolucion
    private fun highResThumb(url: String): String {
        val base = url.substringBefore('=')
        return if (base != url) "$base=w512-h512-l90-rj" else url
    }

    // Ubica la carpeta resources/bin de la app empaquetada (donde esta discord-rpc.dll)
    private fun bundledBinDir(): String? {
        return runCatching {
            val codeSource = com.arturo254.opentune.DesktopPreferences::class.java.protectionDomain.codeSource
            val jarFile = codeSource?.location?.let { File(it.toURI()) } ?: return null
            val appDir = jarFile.parentFile ?: return null
            val binDir = File(File(appDir, "resources"), "bin")
            if (File(binDir, "discord-rpc.dll").exists()) binDir.absolutePath else null
        }.getOrNull()
    }
}
