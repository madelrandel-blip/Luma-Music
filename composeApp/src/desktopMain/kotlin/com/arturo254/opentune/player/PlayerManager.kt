package com.arturo254.opentune.player

import com.arturo254.opentune.DesktopPreferences
import com.arturo254.opentune.tr
import com.arturo254.opentune.innertube.models.SongItem
import com.arturo254.opentune.library.CacheMetadataManager
import com.arturo254.opentune.library.DownloadsManager
import com.arturo254.opentune.library.ListenHistoryManager
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.FloatControl
import javax.sound.sampled.SourceDataLine
import kotlin.random.Random

enum class RepeatMode { SEQUENTIAL, SHUFFLE, LOOP }

object PlayerManager {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val cacheDir = File(System.getProperty("user.home"), ".opentune/cache").also { it.mkdirs() }
    private var ytDlpPath: String? = null
    private var ffmpegPath: String? = null

    private const val MAX_CACHE_SIZE_MB = 500L
    private const val MAX_CACHE_FILES = 50

    private val generationCounter = AtomicInteger(0)

    var currentSong: SongItem? = null; private set
    var isPlaying: Boolean = false; private set
    var position: Long = 0L; private set
    var duration: Long = 0L; private set
    var isLoading: Boolean = false; private set
    var error: String? = null; private set
    var repeatMode: RepeatMode = RepeatMode.SEQUENTIAL; private set
    var volume: Float = 1.0f; private set
    var isMuted: Boolean = false; private set

    val queue = CopyOnWriteArrayList<SongItem>()
    var currentIndex: Int = -1; private set

    @Volatile private var activeThread: FfmpegThread? = null
    @Volatile private var currentAudioFile: File? = null
    @Volatile private var currentGeneration = 0
    @Volatile private var preloadedVideoId: String? = null
    private const val MAX_RETRIES = 3
    private const val PRELOAD_THRESHOLD_MS = 10_000L

    private val listeners = mutableListOf<() -> Unit>()

    fun addListener(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }

    private fun notifyChange() {
        DiscordRpcManager.onSongChanged(currentSong, isPlaying)
        listeners.forEach { it() }
    }

    fun playSong(song: SongItem, queueSongs: List<SongItem> = emptyList()) {
        val gen = generationCounter.incrementAndGet()
        currentGeneration = gen

        if (queueSongs.isNotEmpty()) {
            queue.clear()
            queue.addAll(queueSongs)
            currentIndex = queue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        } else if (currentSong != null) {
            val existingIndex = queue.indexOfFirst { it.id == song.id }
            if (existingIndex >= 0) currentIndex = existingIndex
            else {
                queue.add(song)
                currentIndex = queue.size - 1
            }
        } else {
            queue.clear()
            queue.add(song)
            currentIndex = 0
        }

        currentSong = song
        isPlaying = false
        isLoading = true
        error = null
        position = 0L
        duration = 0L
        preloadedVideoId = null
        notifyChange()

        val old = activeThread
        activeThread = null
        old?.abandon()

        scope.launch {
            try {
                if (currentGeneration != gen) return@launch

                val isLocal = song.id.startsWith("local:")
                if (!isLocal) {
                    if (ytDlpPath == null) { error = tr("yt-dlp no encontrado"); isLoading = false; notifyChange(); return@launch }
                    if (ffmpegPath == null) { error = tr("ffmpeg no encontrado"); isLoading = false; notifyChange(); return@launch }
                }
                if (currentGeneration != gen) return@launch

                val audioFile = if (isLocal) {
                    val f = File(song.id.removePrefix("local:"))
                    if (!f.exists() || !f.isFile) {
                        error = tr("Archivo no encontrado")
                        isLoading = false
                        notifyChange()
                        return@launch
                    }
                    f
                } else {
                    resolveAudio(song.id, gen) ?: return@launch
                }
                if (currentGeneration != gen) return@launch

                if (!isLocal) {
                    // Save metadata for cached tab
                    CacheMetadataManager.saveMetadata(song)
                }

                currentAudioFile = audioFile
                isLoading = false
                isPlaying = true
                notifyChange()
                startThread(audioFile, 0L, gen)
                ListenHistoryManager.record(song)

                scope.launch { cleanupCache() }
            } catch (e: Exception) {
                if (currentGeneration == gen) {
                    isLoading = false
                    error = e.message
                    isPlaying = false
                    notifyChange()
                }
            }
        }
    }

    private suspend fun resolveAudio(videoId: String, gen: Int): File? {
        val cached = getCachedFile(videoId)
        if (cached != null && cached.exists() && cached.length() > 0) return cached

        val videoUrl = "https://www.youtube.com/watch?v=$videoId"
        var lastError: String? = null
        for (attempt in 1..MAX_RETRIES) {
            if (currentGeneration != gen) return null
            try {
                val f = downloadAudio(videoId, videoUrl)
                if (f != null) return f
                lastError = tr("Descarga fallida")
                if (attempt < MAX_RETRIES) delay(500)
            } catch (e: Exception) {
                lastError = e.message
                if (attempt < MAX_RETRIES) delay(500)
            }
        }
        if (currentGeneration == gen && lastError != null) {
            error = tr("Error de audio: {0}", lastError)
            isLoading = false
            notifyChange()
        }
        return null
    }

    fun playPause() {
        if (isPlaying) pausePlayback() else if (currentSong != null) resumePlayback()
    }

    fun pausePlayback() { isPlaying = false; activeThread?.doPause(); notifyChange() }

    fun resumePlayback() {
        if (currentSong == null) return
        isPlaying = true
        activeThread?.doResume()
        notifyChange()
    }

    fun seekTo(ms: Long) {
        val file = currentAudioFile ?: return
        val gen = currentGeneration
        val clampedMs = ms.coerceIn(0L, duration.coerceAtLeast(0L))
        position = clampedMs

        val old = activeThread
        activeThread = null
        old?.abandon()

        isPlaying = true
        startThread(file, clampedMs, gen)
        notifyChange()
    }

    fun next() {
        when (repeatMode) {
            RepeatMode.LOOP -> {
                // Replay current song from beginning
                val song = currentSong ?: return
                playSong(song)
            }
            RepeatMode.SHUFFLE -> {
                if (queue.size <= 1) return
                if (queue.size == 2) {
                    // If only 2 songs, pick the other one
                    currentIndex = if (currentIndex == 0) 1 else 0
                } else {
                    var next: Int
                    do { next = Random.nextInt(queue.size) } while (next == currentIndex)
                    currentIndex = next
                }
                playSong(queue[currentIndex])
            }
            RepeatMode.SEQUENTIAL -> {
                if (currentIndex < queue.size - 1) {
                    currentIndex++
                    playSong(queue[currentIndex])
                }
            }
        }
    }

    fun previous() {
        if (position > 3000) seekTo(0L)
        else if (currentIndex > 0) { currentIndex--; playSong(queue[currentIndex]) }
    }

    fun toggleRepeatMode() {
        repeatMode = when (repeatMode) {
            RepeatMode.SEQUENTIAL -> RepeatMode.SHUFFLE
            RepeatMode.SHUFFLE -> RepeatMode.LOOP
            RepeatMode.LOOP -> RepeatMode.SEQUENTIAL
        }
        notifyChange()
    }

    private var volumeBeforeMute: Float = 1.0f

    fun setVolume(v: Float) {
        volume = v.coerceIn(0f, 1f)
        if (isMuted && volume > 0f) isMuted = false
        activeThread?.applyVolume()
        notifyChange()
    }

    fun persistVolume() {
        DesktopPreferences.updateVolume(volume)
    }

    fun toggleMute() {
        isMuted = !isMuted
        if (isMuted) volumeBeforeMute = volume
        activeThread?.applyVolume()
        notifyChange()
    }

    fun effectiveVolume(): Float = if (isMuted) 0f else volume

    fun jumpToIndex(index: Int) {
        if (index < 0 || index >= queue.size) return
        playSong(queue[index])
    }

    fun removeFromQueue(index: Int) {
        if (queue.isEmpty() || index < 0 || index >= queue.size) return
        queue.removeAt(index)
        if (index < currentIndex) currentIndex--
        notifyChange()
    }

    fun clearQueue() {
        if (queue.isEmpty()) return
        queue.clear()
        currentIndex = -1
        notifyChange()
    }

    fun preloadNext() {
        if (queue.isEmpty() || repeatMode == RepeatMode.LOOP) return
        val nextIndex = when (repeatMode) {
            RepeatMode.SHUFFLE -> {
                if (queue.size <= 1) return
                var idx: Int
                do { idx = Random.nextInt(queue.size) } while (idx == currentIndex)
                idx
            }
            RepeatMode.SEQUENTIAL -> {
                if (currentIndex >= queue.size - 1) return
                currentIndex + 1
            }
            else -> return
        }
        val nextSong = queue[nextIndex]
        if (nextSong.id == preloadedVideoId) return
        if (nextSong.id.startsWith("local:")) return // local files need no preload
        if (getCachedFile(nextSong.id) != null) return // already cached

        preloadedVideoId = nextSong.id
        scope.launch {
            try {
                if (ytDlpPath == null || ffmpegPath == null) return@launch
                val videoUrl = "https://www.youtube.com/watch?v=${nextSong.id}"
                downloadAudio(nextSong.id, videoUrl)
            } catch (_: Exception) {}
        }
    }

    fun downloadSong(song: SongItem) {
        if (song.id.startsWith("local:")) return // already a local file
        if (DownloadsManager.isDownloaded(song.id)) return
        scope.launch(Dispatchers.IO) {
            try {
                if (ytDlpPath == null || ffmpegPath == null) return@launch
                // Ensure song is in cache first
                var audioFile = getCachedFile(song.id)
                if (audioFile == null) {
                    val videoUrl = "https://www.youtube.com/watch?v=${song.id}"
                    audioFile = downloadAudio(song.id, videoUrl)
                }
                if (audioFile == null) return@launch

                // Copy to downloads folder
                val downloadsDir = DownloadsManager.getDownloadsDir()
                val destFile = File(downloadsDir, "${song.id}.webm")
                if (!destFile.exists()) {
                    audioFile.copyTo(destFile, overwrite = true)
                }
                DownloadsManager.addDownload(song)
            } catch (e: Exception) {
                println("[PlayerManager] Download error: ${e.message}")
            }
        }
    }

    fun stop() {
        val gen = generationCounter.incrementAndGet()
        currentGeneration = gen
        val old = activeThread
        activeThread = null
        currentAudioFile = null
        isPlaying = false
        position = 0L
        duration = 0L
        old?.abandon()
        notifyChange()
    }

    private fun startThread(audioFile: File, seekMs: Long, generation: Int) {
        val thread = FfmpegThread(audioFile, seekMs, generation)
        activeThread = thread
        thread.start()
    }

    private fun getCachedFile(videoId: String): File? {
        return cacheDir.listFiles()?.filter {
            it.name.startsWith("${videoId}.") && it.length() > 0
        }?.sortedByDescending { it.lastModified() }?.firstOrNull()
    }

    private fun cacheFile(videoId: String) = File(cacheDir, "$videoId.webm")

    private fun downloadAudio(videoId: String, videoUrl: String): File? {
        val outFile = cacheFile(videoId)
        if (outFile.exists() && outFile.length() > 0) return outFile

        val cmd = listOf(
            ytDlpPath!!, "--no-warnings",
            "-f", "ba[ext=webm]/ba",
            "--no-playlist",
            "--concurrent-fragments", "4",
            "-o", outFile.absolutePath.replace("\\", "/"),
            videoUrl
        )
        val process = ProcessBuilder(cmd).redirectErrorStream(true).start()
        val drainThread = Thread {
            try { val buf = ByteArray(4096); while (process.inputStream.read(buf) != -1) {} } catch (_: Exception) {}
        }
        drainThread.isDaemon = true
        drainThread.start()
        val exitCode = process.waitFor()
        drainThread.join(2000)

        return if (exitCode == 0 && outFile.exists() && outFile.length() > 0) outFile else null
    }

    private fun cleanupCache() {
        val files = cacheDir.listFiles()?.filter { it.name.endsWith(".webm") }
            ?.sortedByDescending { it.lastModified() } ?: return
        var totalSize = files.sumOf { it.length() }
        val maxSizeBytes = MAX_CACHE_SIZE_MB * 1024 * 1024
        for (file in files) {
            if (files.indexOf(file) < MAX_CACHE_FILES && totalSize <= maxSizeBytes) break
            if (file.delete()) totalSize -= file.length()
        }
    }

    init {
        volume = DesktopPreferences.volume
        ytDlpPath = findYtDlp()
        ffmpegPath = findFfmpeg()
        DiscordRpcManager.start()
    }

    private fun bundledExe(name: String): String? {
        return runCatching {
            val codeSource = DesktopPreferences::class.java.protectionDomain.codeSource
            val jarFile = codeSource?.location?.let { File(it.toURI()) } ?: return null
            val appDir = jarFile.parentFile ?: return null
            val exe = File(File(appDir, "resources"), name)
            if (exe.exists()) exe.absolutePath else null
        }.getOrNull()
    }

    private fun findYtDlp(): String? {
        bundledExe("bin/yt-dlp.exe")?.let { return it }
        val user = System.getProperty("user.name")
        val winGetDir = File("C:\\Users\\$user\\AppData\\Local\\Microsoft\\WinGet\\Packages")
        if (winGetDir.exists()) {
            winGetDir.listFiles()?.filter { it.name.contains("yt-dlp") }?.forEach { pkgDir ->
                val exe = File(pkgDir, "yt-dlp.exe")
                if (exe.exists()) return exe.absolutePath
            }
        }
        val candidates = listOf("C:\\Users\\$user\\AppData\\Local\\Microsoft\\WinGet\\Links\\yt-dlp.exe")
        for (path in candidates) { if (File(path).exists()) return path }
        try {
            val p = ProcessBuilder(listOf("yt-dlp", "--version")).redirectErrorStream(true).start()
            if (p.waitFor() == 0) return "yt-dlp"
        } catch (_: Exception) {}
        return null
    }

    private fun findFfmpeg(): String? {
        bundledExe("bin/ffmpeg.exe")?.let { return it }
        val user = System.getProperty("user.name")
        val candidates = listOf("C:\\Program Files\\Krita (x64)\\bin\\ffmpeg.exe")
        for (path in candidates) { if (File(path).exists()) return path }
        val winGetDir = File("C:\\Users\\$user\\AppData\\Local\\Microsoft\\WinGet\\Packages")
        if (winGetDir.exists()) {
            winGetDir.listFiles()?.filter { it.name.contains("ffmpeg") }?.forEach { pkgDir ->
                val exe = File(pkgDir, "ffmpeg.exe")
                if (exe.exists()) return exe.absolutePath
            }
        }
        try {
            val p = ProcessBuilder(listOf("ffmpeg", "-version")).redirectErrorStream(true).start()
            if (p.waitFor() == 0) return "ffmpeg"
        } catch (_: Exception) {}
        return null
    }

    private class FfmpegThread(
        private val audioFile: File,
        private val seekMs: Long,
        private val generation: Int
    ) : Thread("FfmpegPlayer") {
        @Volatile private var paused = false
        @Volatile var abandoned = false; private set
        private var process: Process? = null
        private var gainControl: FloatControl? = null

        fun doPause() { paused = true }
        fun doResume() { paused = false }
        fun abandon() { abandoned = true; process?.destroyForcibly() }

        fun applyVolume() {
            val g = gainControl ?: return
            val v = PlayerManager.effectiveVolume()
            if (v <= 0.001f) {
                runCatching { g.value = g.minimum }.getOrNull()
                return
            }
            // Perceptual curve so mid-position is actually medium loudness
            val actual = (v * v).coerceIn(0.001f, 1f)
            runCatching {
                if (g.type == FloatControl.Type.VOLUME) {
                    g.value = actual.coerceIn(g.minimum, g.maximum)
                } else {
                    val dB = 20f * kotlin.math.log10(actual.toDouble()).toFloat()
                    g.value = dB.coerceIn(g.minimum, g.maximum)
                }
            }.getOrNull()
        }

        private fun isActive(): Boolean = !abandoned && PlayerManager.currentGeneration == generation

        override fun run() {
            var line: SourceDataLine? = null
            try {
                if (!isActive()) return
                val cmd = mutableListOf(PlayerManager.ffmpegPath!!, "-y")
                if (seekMs > 0) cmd.addAll(listOf("-ss", String.format("%.3f", seekMs / 1000.0)))
                cmd.addAll(listOf("-i", audioFile.absolutePath, "-af", "volume=1.0", "-f", "wav", "-acodec", "pcm_s16le", "-ac", "2", "pipe:1"))

                val proc = ProcessBuilder(cmd).redirectErrorStream(false).start()
                process = proc

                val stderrThread = Thread {
                    try {
                        val reader = BufferedReader(InputStreamReader(proc.errorStream))
                        var l: String?
                        while (reader.readLine().also { l = it } != null) {
                            if (!isActive()) break
                            val ln = l ?: continue
                            if (ln.contains("Duration:")) {
                                Regex("Duration: (\\d+):(\\d+):(\\d+\\.\\d+)").find(ln)?.let { m ->
                                    val (h, mi, s) = m.destructured
                                    PlayerManager.duration = ((h.toLong() * 3600 + mi.toLong() * 60) * 1000 + (s.toDouble() * 1000).toLong())
                                    PlayerManager.notifyChange()
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
                stderrThread.isDaemon = true
                stderrThread.start()

                val pcmInput = proc.inputStream ?: throw Exception("Cannot open ffmpeg output")
                line = playPcmStream(pcmInput, seekMs)

                proc.destroyForcibly()
                stderrThread.join(1000)

                if (isActive()) {
                    PlayerManager.isPlaying = false
                    PlayerManager.position = 0L
                    PlayerManager.notifyChange()
                    sleep(500)
                    if (isActive()) PlayerManager.next()
                }
            } catch (e: InterruptedException) { process?.destroyForcibly() }
            catch (e: Exception) {
                process?.destroyForcibly()
                if (isActive()) { PlayerManager.error = tr("Error de reproducción: {0}", e.message); PlayerManager.isPlaying = false; PlayerManager.notifyChange() }
            } finally {
                try { line?.drain() } catch (_: Exception) {}
                try { line?.close() } catch (_: Exception) {}
            }
        }

        private fun playPcmStream(input: InputStream, seekMs: Long): SourceDataLine? {
            val riffHeader = ByteArray(12)
            var offset = 0
            while (offset < 12) { val r = input.read(riffHeader, offset, 12 - offset); if (r == -1) throw Exception("Unexpected end of WAV"); offset += r }

            val fmtChunk = ByteArray(8)
            offset = 0
            while (offset < 8) { val r = input.read(fmtChunk, offset, 8 - offset); if (r == -1) throw Exception("Unexpected end of WAV"); offset += r }

            val fmtSize = (fmtChunk[4].toInt() and 0xFF) or ((fmtChunk[5].toInt() and 0xFF) shl 8) or ((fmtChunk[6].toInt() and 0xFF) shl 16) or ((fmtChunk[7].toInt() and 0xFF) shl 24)
            val fmtData = ByteArray(fmtSize)
            offset = 0
            while (offset < fmtSize) { val r = input.read(fmtData, offset, fmtSize - offset); if (r == -1) throw Exception("Unexpected end of WAV"); offset += r }

            val channels = (fmtData[2].toInt() and 0xFF) or ((fmtData[3].toInt() and 0xFF) shl 8)
            val sampleRate = (fmtData[4].toInt() and 0xFF) or ((fmtData[5].toInt() and 0xFF) shl 8) or ((fmtData[6].toInt() and 0xFF) shl 16) or ((fmtData[7].toInt() and 0xFF) shl 24)
            val bitsPerSample = (fmtData[14].toInt() and 0xFF) or ((fmtData[15].toInt() and 0xFF) shl 8)

            while (true) {
                val chunkId = ByteArray(4); var r = 0
                while (r < 4) { val rd = input.read(chunkId, r, 4 - r); if (rd == -1) throw Exception("Unexpected end of WAV"); r += rd }
                val chunkSizeBuf = ByteArray(4); r = 0
                while (r < 4) { val rd = input.read(chunkSizeBuf, r, 4 - r); if (rd == -1) throw Exception("Unexpected end of WAV"); r += rd }
                val chunkSize = (chunkSizeBuf[0].toInt() and 0xFF) or ((chunkSizeBuf[1].toInt() and 0xFF) shl 8) or ((chunkSizeBuf[2].toInt() and 0xFF) shl 16) or ((chunkSizeBuf[3].toInt() and 0xFF) shl 24)
                if (String(chunkId) == "data") break
                var skipped = 0L
                while (skipped < chunkSize) { val toSkip = minOf(8192L, chunkSize - skipped); val buf = ByteArray(toSkip.toInt()); var sr = 0; while (sr < toSkip) { val rd = input.read(buf, sr, (toSkip - sr).toInt()); if (rd == -1) throw Exception("Unexpected end of WAV"); sr += rd }; skipped += sr }
            }

            val audioFormat = AudioFormat(AudioFormat.Encoding.PCM_SIGNED, sampleRate.toFloat(), bitsPerSample, channels, channels * bitsPerSample / 8, sampleRate.toFloat(), false)
            val info = DataLine.Info(SourceDataLine::class.java, audioFormat)
            if (!AudioSystem.isLineSupported(info)) throw Exception("Audio format not supported")

            val line = AudioSystem.getLine(info) as SourceDataLine
            line.open(audioFormat, 16384)
            line.start()
            gainControl = runCatching { line.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl }
                .getOrNull()
                ?: runCatching { line.getControl(FloatControl.Type.VOLUME) as FloatControl }.getOrNull()
            applyVolume()

            val frameSize = channels * bitsPerSample / 8
            val bytesPerSecond = sampleRate * frameSize
            val buffer = ByteArray(8192)
            var totalBytes = 0L
            var carryBytes = 0
            var lastUpdate = 0L

            while (isActive() && !Thread.currentThread().isInterrupted) {
                if (paused) { sleep(50); continue }
                val need = buffer.size - carryBytes
                val bytesRead = input.read(buffer, carryBytes, need)
                if (bytesRead == -1) {
                    if (carryBytes >= frameSize) { val w = carryBytes - carryBytes % frameSize; line.write(buffer, 0, w); totalBytes += w }
                    break
                }
                val totalRead = carryBytes + bytesRead
                val writable = totalRead - totalRead % frameSize
                line.write(buffer, 0, writable)
                totalBytes += writable
                carryBytes = totalRead - writable

                val now = System.currentTimeMillis()
                if (now - lastUpdate >= 250) {
                    PlayerManager.position = seekMs + (totalBytes * 1000L / bytesPerSecond)
                    PlayerManager.notifyChange()
                    lastUpdate = now

                    // Pre-download next song when 10 seconds remain
                    val remaining = PlayerManager.duration - PlayerManager.position
                    if (remaining in 1..PRELOAD_THRESHOLD_MS) {
                        PlayerManager.preloadNext()
                    }
                }
            }
            return line
        }
    }
}
