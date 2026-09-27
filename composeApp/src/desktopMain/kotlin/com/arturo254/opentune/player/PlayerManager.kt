package com.arturo254.opentune.player

import com.arturo254.opentune.DesktopPreferences
import com.arturo254.opentune.tr
import com.arturo254.opentune.innertube.NewPipeUtils
import com.arturo254.opentune.innertube.YouTube
import com.arturo254.opentune.innertube.models.SongItem
import com.arturo254.opentune.innertube.models.YouTubeClient
import com.arturo254.opentune.innertube.models.response.PlayerResponse
import com.arturo254.opentune.library.CacheMetadataManager
import com.arturo254.opentune.library.DownloadsManager
import com.arturo254.opentune.library.ListenHistoryManager
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.FloatControl
import javax.sound.sampled.SourceDataLine
import kotlin.random.Random

enum class RepeatMode { SEQUENTIAL, SHUFFLE, LOOP }

/** Every user-triggered transport action [PlayerManager] can perform - see
 * [PlayerManager.transportInterceptor] for why this exists. */
sealed class TransportAction {
    object Pause : TransportAction()
    object Resume : TransportAction()
    object Next : TransportAction()
    object Previous : TransportAction()
    data class Seek(val ms: Long) : TransportAction()
    data class Jump(val index: Int) : TransportAction()
    data class AddToQueue(val song: SongItem) : TransportAction()
    data class RemoveFromQueue(val index: Int) : TransportAction()
    data class PlaySong(val song: SongItem, val queueSongs: List<SongItem>) : TransportAction()
}

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
    var position: Long = 0L
        private set(value) {
            field = value
            positionUpdatedAtMs = System.currentTimeMillis()
        }
    /** Wall-clock time [position] was last refreshed - see [smoothPosition]. */
    @Volatile private var positionUpdatedAtMs: Long = 0L
    var duration: Long = 0L; private set
    var isLoading: Boolean = false; private set
    var error: String? = null; private set
    var repeatMode: RepeatMode = RepeatMode.SEQUENTIAL; private set
    var volume: Float = 1.0f; private set
    var isMuted: Boolean = false; private set

    val queue = CopyOnWriteArrayList<SongItem>()
    var currentIndex: Int = -1; private set

    /** A saved queue+position to go back to when the user accidentally plays a song from a
     * different list (e.g. clicking a song in search results while an album/playlist was
     * playing). See [previous] and [playSong]. */
    private data class HistorySnapshot(val queue: List<SongItem>, val currentIndex: Int)
    private val backHistory = ArrayDeque<HistorySnapshot>()
    private const val MAX_HISTORY = 20
    /** True while [restoreFromHistory] is putting a saved queue back in place, so that swap
     * itself doesn't get pushed onto [backHistory] as if it were a brand new context. */
    @Volatile private var restoringFromHistory = false

    @Volatile private var activeThread: FfmpegThread? = null
    @Volatile private var currentAudioSource: String? = null
    @Volatile private var currentGeneration = 0
    @Volatile private var preloadedVideoId: String? = null
    private const val MAX_RETRIES = 3
    private const val PRELOAD_THRESHOLD_MS = 10_000L

    /**
     * [position] only refreshes a few times a second while the audio thread feeds the speakers,
     * so anything that has to line up with what is being heard right now - the synced lyrics
     * above all - would always run a fraction of a second behind it. This fills that gap in by
     * adding the time elapsed since the last refresh, capped so a stalled or paused thread can
     * never make it run away.
     */
    fun smoothPosition(): Long {
        if (!isPlaying) return position
        val elapsed = (System.currentTimeMillis() - positionUpdatedAtMs).coerceIn(0L, 1000L)
        val estimated = position + elapsed
        return if (duration > 0) estimated.coerceAtMost(duration) else estimated
    }

    /** How long the last song took to start, split by stage - shown in Settings > Reproductor
     * so a slow start can be told apart (resolving the audio vs. opening it vs. buffering). */
    data class StartupTimings(
        val title: String,
        val resolveMs: Long,
        val firstAudioMs: Long,
        val fromCache: Boolean,
    )
    @Volatile var lastStartup: StartupTimings? = null; private set

    /** Wall clock when the current song was asked for, to measure the stages above. */
    @Volatile private var startRequestedAtMs = 0L
    @Volatile private var resolvedAtMs = 0L
    @Volatile private var firstAudioReported = false

    /**
     * Audio URLs already resolved this session. Resolving is the slowest part of starting a song
     * (an InnerTube call, plus deciphering YouTube's throttling parameter), and the URLs stay
     * valid for hours - so replaying, going back a track, or playing something that was resolved
     * ahead of time starts without paying for it again.
     */
    private class ResolvedUrl(val url: String, val atMs: Long)
    private val resolvedUrls = ConcurrentHashMap<String, ResolvedUrl>()
    private const val URL_CACHE_TTL_MS = 3 * 60 * 60 * 1000L
    /** How long to let the stream settle before starting the background copy to the cache. */
    private const val BACKGROUND_CACHE_DELAY_MS = 6_000L
    /** Nothing playable after this long means something is wrong, not slow. */
    private const val RESOLVE_TIMEOUT_MS = 25_000L
    /** How many songs ahead in the queue get their URL resolved in advance. */
    private const val PREFETCH_AHEAD = 3

    /**
     * A plain-text record of how each song started, next to the cache. Start-up time depends on
     * the network and on which resolver wins, neither of which can be reproduced from the
     * outside, so the app writes down what actually happened instead of leaving it to guesswork.
     */
    private val startupLogFile = File(System.getProperty("user.home"), ".opentune/startup-log.txt")

    fun logStartup(line: String) {
        runCatching {
            startupLogFile.parentFile?.mkdirs()
            if (startupLogFile.length() > 300_000) startupLogFile.writeText("")
            val stamp = java.time.LocalTime.now().withNano(0).toString()
            startupLogFile.appendText("$stamp  $line\n")
        }
    }

    private fun rememberUrl(videoId: String, url: String) {
        resolvedUrls[videoId] = ResolvedUrl(url, System.currentTimeMillis())
    }

    private fun recallUrl(videoId: String): String? {
        val hit = resolvedUrls[videoId] ?: return null
        if (System.currentTimeMillis() - hit.atMs > URL_CACHE_TTL_MS) {
            resolvedUrls.remove(videoId)
            return null
        }
        return hit.url
    }

    /**
     * Downloading the whole track while the stream is still filling its buffer steals bandwidth
     * from the thing the user is waiting for, so the cache copy starts only once playback is
     * under way - and not at all if they have already skipped to something else.
     */
    private fun scheduleBackgroundCache(videoId: String, videoUrl: String, generation: Int) {
        scope.launch(Dispatchers.IO) {
            delay(BACKGROUND_CACHE_DELAY_MS)
            if (currentGeneration != generation) return@launch
            if (ytDlpPath == null) return@launch
            runCatching { downloadAudio(videoId, videoUrl) }
        }
    }

    /** Called by the playback thread the moment the first audio reaches the speakers. */
    internal fun reportFirstAudio(generation: Int) {
        if (currentGeneration != generation || firstAudioReported) return
        firstAudioReported = true
        val song = currentSong ?: return
        val now = System.currentTimeMillis()
        val timings = StartupTimings(
            title = song.title,
            resolveMs = (resolvedAtMs - startRequestedAtMs).coerceAtLeast(0),
            firstAudioMs = (now - startRequestedAtMs).coerceAtLeast(0),
            fromCache = currentAudioSource?.startsWith("http") == false,
        )
        lastStartup = timings
        logStartup("${song.id} TOTAL=${timings.firstAudioMs}ms (resolver=${timings.resolveMs}ms, ffmpeg+buffer=${timings.firstAudioMs - timings.resolveMs}ms, cache=${timings.fromCache})")
    }

    /**
     * Resolves (and remembers) a song's audio URL without playing it. Used to get the next song
     * in the queue ready while the current one plays, so pressing "next" starts right away.
     */
    fun prefetchStreamUrl(videoId: String) {
        if (videoId.startsWith("local:") || recallUrl(videoId) != null) return
        if (getCachedFile(videoId) != null) return
        if (!prefetching.add(videoId)) return
        scope.launch(Dispatchers.IO) {
            try {
                // Deliberately the same resolver playback uses (InnerTube first, yt-dlp as the
                // fallback that actually works right now) - a prefetch that only tried the fast
                // path would leave the slow one to be paid for at play time anyway.
                val url = runCatching { resolveForPrefetch(videoId) }.getOrNull()
                if (url != null) {
                    rememberUrl(videoId, url)
                    logStartup("$videoId prefetch listo")
                }
            } finally {
                prefetching.remove(videoId)
            }
        }
    }

    /** At most a couple of prefetches at a time: each one can mean a yt-dlp process. */
    private val prefetching = java.util.Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    private suspend fun resolveForPrefetch(videoId: String): String? {
        val videoUrl = "https://www.youtube.com/watch?v=$videoId"
        for (client in listOf(YouTubeClient.IOS_MUSIC, YouTubeClient.ANDROID_VR_NO_AUTH, YouTubeClient.WEB_REMIX)) {
            getNativeStreamUrl(videoId, client, "prefetch")?.let { return it }
        }
        return withContext(Dispatchers.IO) { getStreamUrl(videoUrl, preferFastClient = false) }
    }

    /**
     * Resolving the first song of a session is the slowest one: YouTube's player script has to be
     * fetched and evaluated before a URL can be deciphered. Doing that in the background right
     * after launch (using the last song listened to) means the user doesn't wait for it later.
     */
    fun warmUp() {
        scope.launch(Dispatchers.IO) {
            delay(1500) // let the window finish opening first
            val recent = runCatching { ListenHistoryManager.entries.firstOrNull() }.getOrNull() ?: return@launch
            if (recent.id.startsWith("local:") || getCachedFile(recent.id) != null) return@launch
            runCatching { getNativeStreamUrl(recent.id) }.getOrNull()?.let { rememberUrl(recent.id, it) }
        }
    }

    private val listeners = mutableListOf<() -> Unit>()

    fun addListener(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }

    private fun notifyChange() {
        DiscordRpcManager.onSongChanged(currentSong, isPlaying)
        listeners.forEach { it() }
    }

    /**
     * Installed by [com.arturo254.opentune.party.PartyModeManager] only while we're a *guest*
     * in a Party Mode room. When set, every transport action below is handed to this callback
     * first: returning true means it sent the action to the room's host as a request instead of
     * applying it here, so this guest's queue/playback can never drift out of sync with anyone
     * else's - it only actually changes once the host's confirmation comes back over the
     * socket (applied via [applyRemoteState]). Returning false (or leaving this null, which is
     * the case whenever we're not a guest - not in a room, or we ARE the host) means "apply it
     * locally as normal", so nothing here behaves any differently outside of Party Mode.
     */
    @Volatile var transportInterceptor: ((TransportAction) -> Boolean)? = null

    /** True while [applyRemoteState] is applying a host-confirmed change, so that call bypasses
     * [transportInterceptor] instead of turning right back into a new outgoing request. */
    @Volatile private var applyingRemoteState = false

    /** True while an already-intercepted outer call (e.g. [next]) is running one of the other
     * guarded methods internally (e.g. [playSong]) to actually change local playback - that
     * inner call must run for real, not be intercepted a second time as an unrelated action. */
    @Volatile private var suppressIntercept = false

    /** Used by PartyModeManager (guest role) to apply a host-confirmed state change - runs
     * [block] with interception fully bypassed, whichever of the methods below it calls. */
    fun applyRemoteState(block: () -> Unit) {
        val was = applyingRemoteState
        applyingRemoteState = true
        try { block() } finally { applyingRemoteState = was }
    }

    private inline fun withInterceptSuppressed(block: () -> Unit) {
        val was = suppressIntercept
        suppressIntercept = true
        try { block() } finally { suppressIntercept = was }
    }

    private fun intercepted(action: TransportAction): Boolean {
        if (applyingRemoteState || suppressIntercept) return false
        return transportInterceptor?.invoke(action) == true
    }

    fun playSong(song: SongItem, queueSongs: List<SongItem> = emptyList()) {
        if (intercepted(TransportAction.PlaySong(song, queueSongs))) return
        val gen = generationCounter.incrementAndGet()
        currentGeneration = gen

        if (queueSongs.isNotEmpty()) {
            // Only treat this as "the user jumped to a different list" (worth remembering so
            // `previous()` can return to it) when it genuinely replaces a different, non-empty
            // queue - not when it's just (re)loading the same list that's already playing.
            val isDifferentContext = !restoringFromHistory && currentSong != null &&
                queue.isNotEmpty() && queue.map { it.id } != queueSongs.map { it.id }
            if (isDifferentContext) {
                if (backHistory.size >= MAX_HISTORY) backHistory.removeFirst()
                backHistory.addLast(HistorySnapshot(queue.toList(), currentIndex))
            }
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

        startRequestedAtMs = System.currentTimeMillis()
        resolvedAtMs = 0L
        firstAudioReported = false
        logStartup("--- play \"${song.title}\" (${song.id})")
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

                val audioSource = if (isLocal) {
                    val f = File(song.id.removePrefix("local:"))
                    if (!f.exists() || !f.isFile) {
                        error = tr("Archivo no encontrado")
                        isLoading = false
                        notifyChange()
                        return@launch
                    }
                    f.absolutePath
                } else {
                    resolveAudio(song.id, gen) ?: return@launch
                }
                if (currentGeneration != gen) return@launch

                if (!isLocal) {
                    // Save metadata for cached tab
                    CacheMetadataManager.saveMetadata(song)
                }

                currentAudioSource = audioSource
                isLoading = false
                isPlaying = true
                notifyChange()
                startThread(audioSource, 0L, gen)
                ListenHistoryManager.record(song)
                // Resolving costs seconds, so the next few songs are resolved while this one
                // plays: by the time they're reached their URL is already in hand.
                for (ahead in 1..PREFETCH_AHEAD) {
                    queue.getOrNull(currentIndex + ahead)?.let { prefetchStreamUrl(it.id) }
                }

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

    /**
     * Finds something playable for [videoId] as fast as possible.
     *
     * The different ways of resolving a YouTube audio URL have wildly different costs, and which
     * one is quickest varies by video and by day: InnerTube usually answers in a few hundred ms,
     * but when YouTube hands back a URL with a throttling parameter it has to be deciphered with
     * YouTube's own player script, which can take seconds; yt-dlp is steadier but pays for
     * starting a process. Running them one after another means the slow case costs the sum of
     * all of them - which is what the wait before a song felt like.
     *
     * So they are raced instead, staggered so the cheap ones get a head start and the expensive
     * ones only ever run if the cheap ones haven't answered yet. The first URL to come back wins
     * and the rest are dropped (yt-dlp's process included, so nothing keeps running unnoticed).
     */
    private suspend fun resolveAudio(videoId: String, gen: Int): String? {
        // Already fully downloaded/cached from a previous play -> use it straight away.
        val cached = getCachedFile(videoId)
        if (cached != null && cached.exists() && cached.length() > 0) {
            resolvedAtMs = System.currentTimeMillis()
            logStartup("$videoId source=cache file=${cached.name}")
            return cached.absolutePath
        }

        val videoUrl = "https://www.youtube.com/watch?v=$videoId"

        // Resolved earlier in this session (a replay, or prefetched while the previous song was
        // playing): nothing to wait for.
        recallUrl(videoId)?.let {
            resolvedAtMs = System.currentTimeMillis()
            logStartup("$videoId source=memoria (url ya resuelta)")
            scheduleBackgroundCache(videoId, videoUrl, gen)
            return it
        }

        val startedAt = System.currentTimeMillis()
        val winner = CompletableDeferred<Pair<String, String>>()
        val decided = java.util.concurrent.atomic.AtomicBoolean(false)
        val spawned = java.util.Collections.synchronizedList(mutableListOf<Process>())

        // Deliberately launched on the manager's own scope rather than as children of this call:
        // yt-dlp is read with a blocking wait that cancellation can't interrupt, so waiting for
        // every racer to finish (which structured concurrency would do) would throw away exactly
        // the time this race is meant to save. Losers are stopped by killing their process below.
        fun race(name: String, headStartMs: Long, block: suspend (onProcess: (Process) -> Unit) -> String?) {
            scope.launch(Dispatchers.IO) {
                if (headStartMs > 0) delay(headStartMs)
                if (decided.get() || currentGeneration != gen) return@launch
                val t0 = System.currentTimeMillis()
                val outcome = runCatching {
                    block { process ->
                        // A process started after the race was already decided is useless.
                        if (decided.get()) runCatching { process.destroyForcibly() } else spawned.add(process)
                    }
                }
                val url = outcome.getOrNull()
                val took = System.currentTimeMillis() - t0
                if (url != null && winner.complete(url to name)) {
                    logStartup("$videoId ganador=$name en ${took}ms")
                } else {
                    val why = when {
                        url != null -> "llegó tarde"
                        outcome.exceptionOrNull() != null -> "error: ${outcome.exceptionOrNull()?.message}"
                        else -> "sin resultado"
                    }
                    logStartup("$videoId $name $why tras ${took}ms")
                }
            }
        }

        // Head starts come from what the startup log measured on a real connection: the InnerTube
        // clients answer (or fail) in under 200ms, so they all go first and cost almost nothing;
        // yt-dlp's full extraction is the one that reliably works but takes seconds, so it starts
        // early rather than being held back behind attempts that rarely win.
        race("innertube-ios-music", 0) { getNativeStreamUrl(videoId, YouTubeClient.IOS_MUSIC, "innertube-ios-music") }
        race("innertube-android-music", 0) { getNativeStreamUrl(videoId, YouTubeClient.ANDROID_MUSIC, "innertube-android-music") }
        race("innertube-android-vr", 120) { getNativeStreamUrl(videoId, YouTubeClient.ANDROID_VR_NO_AUTH, "innertube-android-vr") }
        race("innertube-ios", 120) { getNativeStreamUrl(videoId, YouTubeClient.IOS, "innertube-ios") }
        race("innertube-web-remix", 240) { getNativeStreamUrl(videoId, YouTubeClient.WEB_REMIX, "innertube-web-remix") }
        race("innertube-tv", 240) { getNativeStreamUrl(videoId, YouTubeClient.TVHTML5, "innertube-tv") }
        // Skips downloading the watch page and player JS, which is where yt-dlp spends most of
        // its time; if YouTube refuses that shortcut the full extraction below still covers it.
        race("yt-dlp-sin-web", 500) { onProcess -> getStreamUrl(videoUrl, preferFastClient = true, onProcess = onProcess) }
        race("yt-dlp-completo", 1200) { onProcess -> getStreamUrl(videoUrl, preferFastClient = false, onProcess = onProcess) }

        val result = withTimeoutOrNull(RESOLVE_TIMEOUT_MS) { winner.await() }
        decided.set(true)
        synchronized(spawned) { spawned.forEach { runCatching { it.destroyForcibly() } } }

        if (result == null) {
            logStartup("$videoId sin resultado tras ${System.currentTimeMillis() - startedAt}ms")
            if (currentGeneration == gen) {
                error = tr("No se pudo obtener el enlace de audio")
                isLoading = false
                notifyChange()
            }
            return null
        }

        resolvedAtMs = System.currentTimeMillis()
        rememberUrl(videoId, result.first)
        scheduleBackgroundCache(videoId, videoUrl, gen)
        return result.first
    }

    /** Resolves the direct, playable audio URL without downloading anything (fast). */
    private fun getStreamUrl(videoUrl: String, preferFastClient: Boolean, onProcess: (Process) -> Unit = {}): String? {
        val ytDlp = ytDlpPath ?: return null
        val cmd = mutableListOf(
            ytDlp, "--no-warnings", "--no-update", "-g",
            "-f", "ba[ext=m4a]/ba[ext=webm]/ba",
            "--no-playlist",
            "--socket-timeout", "10"
        )
        if (preferFastClient) {
            // Measured on a real connection: asking for the android client made yt-dlp fail after
            // 4-8 seconds every single time (YouTube rejects it), so that is gone. What is worth
            // trying is skipping the watch page and player-config downloads, which is where most
            // of yt-dlp's time goes; the full extraction runs in parallel as the safety net.
            cmd.addAll(listOf("--extractor-args", "youtube:player_skip=webpage,configs"))
        }
        cmd.add(videoUrl)
        val process = ProcessBuilder(cmd).start()
        onProcess(process)
        val errDrain = Thread {
            try { val buf = ByteArray(4096); while (process.errorStream.read(buf) != -1) {} } catch (_: Exception) {}
        }
        errDrain.isDaemon = true
        errDrain.start()
        val url = process.inputStream.bufferedReader().readLine()
        // Drain any remaining stdout so the process isn't blocked on a full pipe buffer.
        Thread {
            try { val buf = ByteArray(4096); while (process.inputStream.read(buf) != -1) {} } catch (_: Exception) {}
        }.apply { isDaemon = true; start() }
        val exitCode = process.waitFor()
        errDrain.join(2000)
        return if (exitCode == 0 && !url.isNullOrBlank()) url.trim() else null
    }

    /**
     * Resolves the direct audio URL entirely in-process (no subprocess): asks YouTube's
     * InnerTube "player" endpoint for the stream info via the IOS_MUSIC client (it doesn't
     * need a signature timestamp, so this skips fetching/parsing YouTube's player JS
     * entirely for most videos), then picks the best audio-only format - same preference
     * as the yt-dlp path: AAC/m4a first, otherwise highest-bitrate available (still "best
     * audio", same quality policy as before).
     */
    private suspend fun getNativeStreamUrl(
        videoId: String,
        client: YouTubeClient = YouTubeClient.IOS_MUSIC,
        clientName: String = "innertube",
    ): String? {
        val attempt = YouTube.player(videoId = videoId, client = client)
        val response = attempt.getOrNull()
        if (response == null) {
            logStartup("  $clientName: la petición falló (${attempt.exceptionOrNull()?.message})")
            return null
        }
        val status = response.playabilityStatus
        val audioFormats = response.streamingData?.adaptiveFormats?.filter { it.isAudio }
        if (audioFormats.isNullOrEmpty()) {
            logStartup("  $clientName: sin audio (status=${status.status}, motivo=${status.reason ?: "-"})")
            return null
        }

        val chosen = audioFormats
            .sortedWith(
                compareByDescending<PlayerResponse.StreamingData.Format> { it.mimeType.contains("mp4a") }
                    .thenByDescending { it.bitrate }
            )
            .firstOrNull() ?: return null

        val url = NewPipeUtils.getStreamUrl(chosen, videoId, client)
        if (url.isFailure) logStartup("  $clientName: no se pudo descifrar la URL (${url.exceptionOrNull()?.message})")
        return url.getOrNull()
    }

    fun playPause() {
        if (isPlaying) pausePlayback() else if (currentSong != null) resumePlayback()
    }

    fun pausePlayback() {
        if (intercepted(TransportAction.Pause)) return
        isPlaying = false; activeThread?.doPause(); notifyChange()
    }

    /**
     * Best-effort cleanup called right before the app process exits. [FfmpegThread] isn't a
     * daemon thread (it needs to survive brief generation swaps while seeking/skipping), so if
     * it's left running - e.g. blocked reading the ffmpeg subprocess's output - the JVM won't
     * actually terminate when the window closes; it lingers in the background, which is what
     * was leaving the installed files locked/"in use by another program" on uninstall. Killing
     * the ffmpeg subprocess here unblocks the thread so the process can exit cleanly.
     */
    fun shutdown() {
        activeThread?.abandon()
    }

    fun resumePlayback() {
        if (intercepted(TransportAction.Resume)) return
        if (currentSong == null) return
        isPlaying = true
        activeThread?.doResume()
        notifyChange()
    }

    fun seekTo(ms: Long) {
        if (intercepted(TransportAction.Seek(ms))) return
        val source = currentAudioSource ?: return
        val gen = currentGeneration
        val clampedMs = ms.coerceIn(0L, duration.coerceAtLeast(0L))
        position = clampedMs

        val old = activeThread
        activeThread = null
        old?.abandon()

        isPlaying = true
        startThread(source, clampedMs, gen)
        notifyChange()
    }

    fun next() {
        if (intercepted(TransportAction.Next)) return
        withInterceptSuppressed {
            when (repeatMode) {
                RepeatMode.LOOP -> {
                    // Replay current song from beginning
                    val song = currentSong ?: return@withInterceptSuppressed
                    playSong(song)
                }
                RepeatMode.SHUFFLE -> {
                    if (queue.size <= 1) return@withInterceptSuppressed
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
    }

    fun previous() {
        if (intercepted(TransportAction.Previous)) return
        withInterceptSuppressed {
            if (position > 3000) seekTo(0L)
            else if (backHistory.isNotEmpty()) restoreFromHistory()
            else if (currentIndex > 0) { currentIndex--; playSong(queue[currentIndex]) }
        }
    }

    /** Pops the most recent [HistorySnapshot] (the queue/song that was playing before the user
     * accidentally jumped into a different list) and resumes it, instead of continuing to
     * navigate inside the accidentally-played song's own list. */
    private fun restoreFromHistory() {
        val snapshot = backHistory.removeLastOrNull() ?: return
        restoringFromHistory = true
        try {
            val restoredIndex = snapshot.currentIndex.coerceIn(0, (snapshot.queue.size - 1).coerceAtLeast(0))
            if (snapshot.queue.isEmpty()) return
            playSong(snapshot.queue[restoredIndex], snapshot.queue)
        } finally {
            restoringFromHistory = false
        }
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
        if (intercepted(TransportAction.Jump(index))) return
        if (index < 0 || index >= queue.size) return
        withInterceptSuppressed { playSong(queue[index]) }
    }

    fun removeFromQueue(index: Int) {
        if (intercepted(TransportAction.RemoveFromQueue(index))) return
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

    /** Appends a song to the end of the queue without touching playback (e.g. from a song's
     * right-click menu, "Agregar a la cola"). No-op if it's already queued. */
    fun addToQueue(song: SongItem) {
        if (intercepted(TransportAction.AddToQueue(song))) return
        if (queue.none { it.id == song.id }) {
            queue.add(song)
            notifyChange()
        }
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
                // This can finish a full cache file for a song the user never actually presses
                // play on (they might skip past it instead) - without registering it here, that
                // file sits on disk invisible to the cache library tab until the next full scan.
                if (downloadAudio(nextSong.id, videoUrl) != null) {
                    CacheMetadataManager.saveMetadata(nextSong)
                }
            } catch (_: Exception) {}
        }
    }

    fun downloadSong(song: SongItem) {
        if (song.id.startsWith("local:")) return // already a local file
        if (DownloadsManager.isDownloaded(song.id)) return
        DownloadsManager.markDownloading(song.id)
        scope.launch(Dispatchers.IO) {
            try {
            try {
                val ffmpeg = ffmpegPath
                if (ytDlpPath == null || ffmpeg == null) return@launch
                // Ensure song is in cache first
                var audioFile = getCachedFile(song.id)
                if (audioFile == null) {
                    val videoUrl = "https://www.youtube.com/watch?v=${song.id}"
                    audioFile = downloadAudio(song.id, videoUrl)
                    if (audioFile != null) CacheMetadataManager.saveMetadata(song)
                }
                if (audioFile == null) return@launch

                val downloadsDir = DownloadsManager.getDownloadsDir()
                val artistName = song.artists.joinToString(", ") { it.name }
                val safeArtist = sanitizeForFileName(artistName)
                val safeTitle = sanitizeForFileName(song.title)
                val baseName = if (safeArtist.isNotBlank()) "$safeArtist - $safeTitle" else safeTitle
                val destFile = File(downloadsDir, "$baseName [${song.id}].mp3")

                if (destFile.exists()) {
                    DownloadsManager.addDownload(song)
                    return@launch
                }

                // Embed the cover art and title/artist tags into the file itself, instead of
                // just copying the raw, randomly-named cached stream.
                val coverFile = if (song.thumbnail.isNotBlank()) downloadThumbnail(song.thumbnail, song.id) else null
                val tagged = tagAndExport(ffmpeg, audioFile, coverFile, song, artistName, destFile)
                coverFile?.delete()

                if (!tagged) {
                    // Fall back to a plain, readably-named copy so the download isn't lost
                    // even if tagging/cover embedding failed (e.g. ffmpeg build without mp3 support).
                    destFile.delete()
                    val fallbackExt = audioFile.extension.takeIf { it.isNotBlank() } ?: "webm"
                    val fallbackFile = File(downloadsDir, "$baseName [${song.id}].$fallbackExt")
                    if (!fallbackFile.exists()) audioFile.copyTo(fallbackFile, overwrite = true)
                }

                DownloadsManager.addDownload(song)
            } catch (e: Exception) {
                println("[PlayerManager] Download error: ${e.message}")
            }
            } finally {
                DownloadsManager.clearDownloading(song.id)
            }
        }
    }

    private fun sanitizeForFileName(s: String): String {
        val cleaned = s.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        return cleaned.take(80)
    }

    private fun downloadThumbnail(url: String, videoId: String): File? {
        return try {
            val tempFile = File(cacheDir, "$videoId-cover.jpg")
            java.net.URL(url).openStream().use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
            if (tempFile.exists() && tempFile.length() > 0) tempFile else null
        } catch (e: Exception) {
            println("[PlayerManager] Cover download error: ${e.message}")
            null
        }
    }

    /** Re-muxes the cached audio into an mp3 with embedded cover art and title/artist/album tags. */
    private fun tagAndExport(
        ffmpeg: String,
        audioFile: File,
        coverFile: File?,
        song: SongItem,
        artistName: String,
        destFile: File
    ): Boolean {
        return try {
            val cmd = mutableListOf(ffmpeg, "-y", "-i", audioFile.absolutePath)
            if (coverFile != null) cmd += listOf("-i", coverFile.absolutePath)
            cmd += listOf("-map", "0:a")
            if (coverFile != null) cmd += listOf("-map", "1:0")
            cmd += listOf("-c:a", "libmp3lame", "-q:a", "2")
            if (coverFile != null) {
                cmd += listOf(
                    "-c:v", "copy",
                    "-disposition:v", "attached_pic",
                    "-metadata:s:v", "title=Album cover",
                    "-metadata:s:v", "comment=Cover (front)"
                )
            }
            cmd += listOf(
                "-id3v2_version", "3",
                "-metadata", "title=${song.title}",
                "-metadata", "artist=${artistName.ifBlank { tr("Artista desconocido") }}"
            )
            song.album?.name?.let { cmd += listOf("-metadata", "album=$it") }
            cmd += destFile.absolutePath

            val process = ProcessBuilder(cmd).redirectErrorStream(true).start()
            process.inputStream.bufferedReader().readText() // drain output so ffmpeg never blocks on a full pipe
            val exit = process.waitFor()
            exit == 0 && destFile.exists() && destFile.length() > 0
        } catch (e: Exception) {
            println("[PlayerManager] Tagging error: ${e.message}")
            false
        }
    }

    fun stop() {
        val gen = generationCounter.incrementAndGet()
        currentGeneration = gen
        val old = activeThread
        activeThread = null
        currentAudioSource = null
        isPlaying = false
        position = 0L
        duration = 0L
        old?.abandon()
        notifyChange()
    }

    /** Set while a fallback for [currentGeneration] is already running, so a source that keeps
     * failing can't bounce between resolvers forever. */
    @Volatile private var fallbackTriedForGeneration: Int = -1

    /**
     * Called when ffmpeg exited without ever producing a single byte of audio - the song never
     * actually started, however healthy it looked. Rather than silently skipping to the next
     * track (which just repeats the problem down the queue), this drops the file if a cached one
     * was at fault and resolves the audio again through yt-dlp, whose extraction is more robust
     * than the fast in-process one. Only one such retry per song, then the error is surfaced.
     */
    private fun onPlaybackProducedNothing(failedSource: String, seekMs: Long, generation: Int) {
        if (currentGeneration != generation) return
        val song = currentSong ?: return

        resolvedUrls.remove(song.id)
        val wasFile = !failedSource.startsWith("http://") && !failedSource.startsWith("https://")
        if (wasFile) {
            // Unplayable cached copy: get rid of it so it can't poison the next attempt either.
            runCatching {
                val f = File(failedSource)
                if (f.absolutePath.startsWith(cacheDir.absolutePath)) f.delete()
            }
        }
        if (song.id.startsWith("local:")) {
            isLoading = false; isPlaying = false
            error = tr("No se pudo reproducir este archivo")
            notifyChange()
            return
        }
        if (fallbackTriedForGeneration == generation) {
            isLoading = false; isPlaying = false
            error = tr("No se pudo reproducir esta canción")
            notifyChange()
            return
        }
        fallbackTriedForGeneration = generation

        scope.launch {
            isLoading = true
            notifyChange()
            val url = runCatching {
                getStreamUrl("https://www.youtube.com/watch?v=${song.id}", preferFastClient = false)
            }.getOrNull()
            if (currentGeneration != generation) return@launch
            if (url != null && url != failedSource) {
                currentAudioSource = url
                isLoading = false
                isPlaying = true
                notifyChange()
                startThread(url, seekMs, generation)
            } else {
                isLoading = false
                isPlaying = false
                error = tr("No se pudo reproducir esta canción")
                notifyChange()
            }
        }
    }

    private fun startThread(source: String, seekMs: Long, generation: Int) {
        val thread = FfmpegThread(source, seekMs, generation)
        activeThread = thread
        thread.start()
    }

    /**
     * The newest *finished* cached file for this video. An interrupted download leaves
     * `<id>.m4a.part` / `<id>.temp.m4a` behind, and those used to match here: ffmpeg would then
     * be pointed at half a file, produce nothing, and the song would sit at 0:00 forever - so
     * completeness is checked by [CacheMetadataManager.isCompleteCacheFile], never by size alone.
     */
    private fun getCachedFile(videoId: String): File? =
        CacheMetadataManager.filesFor(videoId).maxByOrNull { it.lastModified() }

    private fun cacheFile(videoId: String) = File(cacheDir, "$videoId.webm")

    private fun downloadAudio(videoId: String, videoUrl: String): File? {
        // Check for any cached variant of this video first
        getCachedFile(videoId)?.takeIf { it.exists() && it.length() > 0 }?.let { return it }

        val outBase = File(cacheDir, "$videoId.webm").absolutePath.replace("\\", "/").removeSuffix(".webm")
        val cmd = listOf(
            ytDlpPath!!, "--no-warnings", "--no-update",
            "-f", "ba[ext=m4a]/ba[ext=webm]/ba",
            "--no-playlist",
            "--concurrent-fragments", "4",
            "-o", outBase + ".%(ext)s",
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

        return getCachedFile(videoId)?.takeIf { it.exists() && it.length() > 0 }
    }

    private fun cleanupCache() {
        CacheMetadataManager.purgeStaleLeftovers()
        val files = cacheDir.listFiles()?.filter { CacheMetadataManager.isCompleteCacheFile(it) }
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
        // A deleted song's cache file is gone the moment CacheMetadataManager removes it, but its
        // resolved streaming URL could still be sitting in [resolvedUrls] for up to
        // URL_CACHE_TTL_MS - that alone was enough to keep "playing" a song whose cache had just
        // been cleared, straight from YouTube instead of the (deleted) local file. Forgetting it
        // here, right when the deletion actually happens, is what makes deleting the cache mean
        // "this won't just start again instantly".
        CacheMetadataManager.onSongDeleted = { videoId -> resolvedUrls.remove(videoId) }
        CacheMetadataManager.onAllCleared = { resolvedUrls.clear() }
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
        private val source: String,
        private val seekMs: Long,
        private val generation: Int
    ) : Thread("FfmpegPlayer") {
        @Volatile private var paused = false
        /** Bytes actually handed to the speakers - 0 means this source never played at all. */
        @Volatile private var producedBytes = 0L
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
                val isNetwork = source.startsWith("http://") || source.startsWith("https://")
                val cmd = mutableListOf(PlayerManager.ffmpegPath!!, "-y")
                if (isNetwork) {
                    // Ride out brief network hiccups while streaming instead of dying instantly.
                    cmd.addAll(listOf("-reconnect", "1", "-reconnect_streamed", "1", "-reconnect_delay_max", "5"))
                    // Don't wait to buffer/analyze ~5MB or 5s of data (ffmpeg's defaults)
                    // before starting to decode. YouTube audio streams are simple single
                    // audio-track containers with one stream and no variable codec, so a much
                    // smaller probe is still enough to identify the stream correctly - this
                    // only shortens ffmpeg's "figure out what I'm decoding" step, it doesn't
                    // touch decoding itself, so it starts playback faster with no change to
                    // audio quality/bitrate.
                    // 0.1s of analysis is plenty for a single-track audio stream (it was 0.5s),
                    // and it is time the user spends staring at a spinner.
                    cmd.addAll(listOf("-probesize", "32k", "-analyzeduration", "100000"))
                }
                if (seekMs > 0) cmd.addAll(listOf("-ss", String.format("%.3f", seekMs / 1000.0)))
                cmd.addAll(listOf("-i", source, "-af", "volume=1.0", "-f", "wav", "-acodec", "pcm_s16le", "-ac", "2", "pipe:1"))

                val ffmpegStartedAt = System.currentTimeMillis()
                val proc = ProcessBuilder(cmd).redirectErrorStream(false).start()
                process = proc
                PlayerManager.logStartup("ffmpeg lanzado en ${System.currentTimeMillis() - ffmpegStartedAt}ms (${if (isNetwork) "red" else "archivo"})")

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
                    if (producedBytes <= 0L) {
                        // Never produced audio: a broken/incomplete source, not a finished song -
                        // so retry it instead of skipping ahead as if it had played.
                        PlayerManager.onPlaybackProducedNothing(source, seekMs, generation)
                    } else {
                        PlayerManager.isPlaying = false
                        PlayerManager.position = 0L
                        PlayerManager.notifyChange()
                        sleep(500)
                        if (isActive()) PlayerManager.next()
                    }
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
            var lastPositionUpdate = 0L

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
                if (producedBytes == 0L && writable > 0) PlayerManager.reportFirstAudio(generation)
                totalBytes += writable
                producedBytes = totalBytes
                carryBytes = totalRead - writable

                val now = System.currentTimeMillis()
                if (now - lastPositionUpdate >= 100) {
                    // longFramePosition counts frames the sound card has actually played, so it
                    // tracks what is being *heard*; totalBytes counts what has been handed to the
                    // line, which is up to a buffer ahead of that (and is the fallback when the
                    // mixer doesn't report a frame position).
                    val playedFrames = runCatching { line.longFramePosition }.getOrDefault(0L)
                    PlayerManager.position = if (playedFrames > 0L) {
                        seekMs + playedFrames * 1000L / sampleRate
                    } else {
                        seekMs + (totalBytes * 1000L / bytesPerSecond)
                    }
                    lastPositionUpdate = now
                }
                if (now - lastUpdate >= 1000) {
                    // Listeners (Discord presence, Party Mode broadcasting) only need a slow tick;
                    // the UI reads position directly, several times a second.
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
