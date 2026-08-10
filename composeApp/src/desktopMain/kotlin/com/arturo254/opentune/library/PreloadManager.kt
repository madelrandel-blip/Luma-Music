/*
 * OpenTune Desktop Port - Preload Manager
 * Downloads audio in background for instant playback
 * Licensed Under GPL-3.0
 */

package com.arturo254.opentune.library

import com.arturo254.opentune.innertube.models.SongItem
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

object PreloadManager {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val preloadDir = File(System.getProperty("user.home"), ".opentune/preload").also { it.mkdirs() }

    private val preloadedIds = mutableSetOf<String>()
    private val activeJobs = mutableMapOf<String, Job>()

    private var ytDlpPath: String? = null
    private var ffmpegPath: String? = null
    private var currentSearchIds = listOf<String>()

    fun init(ytDlp: String?, ffmpeg: String?) {
        ytDlpPath = ytDlp
        ffmpegPath = ffmpeg
        println("[PreloadManager] init: yt-dlp=$ytDlp, ffmpeg=$ffmpeg")
    }

    fun preloadSongs(songs: List<SongItem>, maxPreload: Int = 5) {
        cleanupOldPreloads()

        currentSearchIds = songs.map { it.id }

        val toPreload = songs.take(maxPreload).filter { song ->
            song.id !in preloadedIds && !getPreloadFile(song.id).exists()
        }

        println("[PreloadManager] Preloading ${toPreload.size} songs")

        for (song in toPreload) {
            val job = scope.launch {
                try {
                    preloadSong(song)
                } catch (e: Exception) {
                    println("[PreloadManager] Failed to preload ${song.id}: ${e.message}")
                }
            }
            activeJobs[song.id] = job
        }
    }

    fun getPreloadFile(videoId: String): File {
        return File(preloadDir, "${videoId}.webm")
    }

    fun isPreloaded(videoId: String): Boolean {
        return getPreloadFile(videoId).let { it.exists() && it.length() > 0 }
    }

    fun cancelAll() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
    }

    private suspend fun preloadSong(song: SongItem) {
        val ytDlp = ytDlpPath ?: return
        val ffmpeg = ffmpegPath ?: return

        val videoUrl = "https://www.youtube.com/watch?v=${song.id}"
        val outFile = getPreloadFile(song.id)

        if (outFile.exists() && outFile.length() > 0) {
            preloadedIds.add(song.id)
            return
        }

        val tempFile = File(preloadDir, "tmp_${song.id}.webm")

        try {
            // Download full audio in background
            val cmd = listOf(
                ytDlp, "--no-warnings",
                "-f", "bestaudio",
                "--no-playlist",
                "-o", tempFile.absolutePath.replace("\\", "/"),
                videoUrl
            )
            val process = ProcessBuilder(cmd).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode == 0 && tempFile.exists() && tempFile.length() > 0) {
                // Move temp to final
                if (outFile.exists()) outFile.delete()
                tempFile.renameTo(outFile)
                preloadedIds.add(song.id)
                println("[PreloadManager] Preloaded: ${song.title} (${outFile.length()} bytes)")
            } else {
                println("[PreloadManager] yt-dlp failed for ${song.id}: ${output.take(150)}")
                tempFile.delete()
            }
        } catch (e: Exception) {
            println("[PreloadManager] Error preloading ${song.id}: ${e.message}")
            tempFile.delete()
        } finally {
            activeJobs.remove(song.id)
        }
    }

    fun cleanupOldPreloads() {
        val files = preloadDir.listFiles()?.filter { it.name.endsWith(".webm") && !it.name.startsWith("tmp_") } ?: return

        for (file in files) {
            val fileId = file.nameWithoutExtension
            if (fileId !in currentSearchIds) {
                if (file.delete()) {
                    preloadedIds.remove(fileId)
                    println("[PreloadManager] Cleaned: ${fileId}")
                }
            }
        }

        // Also clean up stale temp files
        preloadDir.listFiles()?.filter { it.name.startsWith("tmp_") && it.lastModified() < System.currentTimeMillis() - 60_000 }?.forEach {
            it.delete()
        }
    }

    fun cleanupAll() {
        preloadDir.listFiles()?.forEach { it.delete() }
        preloadedIds.clear()
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
    }
}
