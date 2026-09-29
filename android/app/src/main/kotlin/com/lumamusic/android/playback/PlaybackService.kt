/*
 * Luma Music
 * A minimal Media3 playback service: plays YouTube Music videoIds through ExoPlayer by
 * resolving each one's real stream URL lazily (via YTPlayerUtils) right before ExoPlayer opens
 * the connection. No database, no downloads, no Hilt — just enough to validate that Luma's own
 * Android UI can drive real playback end to end.
 */

package com.lumamusic.android.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.lumamusic.android.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap

@UnstableApi
class PlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null

    // videoId -> (streamUrl, expiresAtMs). Avoids re-resolving the same song's stream on every
    // seek/retry within its ~url's lifetime (YouTube stream URLs expire after a few hours).
    private val streamUrlCache = ConcurrentHashMap<String, Pair<String, Long>>()

    override fun onCreate() {
        super.onCreate()

        val connectivityManager = getSystemService(ConnectivityManager::class.java)

        val dataSourceFactory = ResolvingDataSource.Factory(
            DefaultDataSource.Factory(this, OkHttpDataSource.Factory(OkHttpClient()))
        ) { dataSpec ->
            val videoId = dataSpec.uri.toString()

            val cached = streamUrlCache[videoId]
            if (cached != null && cached.second > System.currentTimeMillis()) {
                return@Factory dataSpec.withUri(cached.first.toUri())
            }

            val playbackData = runBlocking(Dispatchers.IO) {
                YTPlayerUtils.playerResponseForPlayback(
                    videoId = videoId,
                    audioQuality = AudioQuality.AUTO,
                    connectivityManager = connectivityManager,
                )
            }.getOrThrow()

            streamUrlCache[videoId] =
                playbackData.streamUrl to (System.currentTimeMillis() + playbackData.streamExpiresInSeconds * 1000L)

            dataSpec.withUri(playbackData.streamUrl.toUri())
        }

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        val sessionActivityIntent = Intent(this, MainActivity::class.java)
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            sessionActivityIntent,
            PendingIntent.FLAG_IMMUTABLE,
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityPendingIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val session = mediaSession ?: return
        if (!session.player.playWhenReady || session.player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}

/** Builds a playable [MediaItem] for a YouTube Music video, keyed by its videoId. */
fun videoIdToMediaItem(
    videoId: String,
    title: String,
    artist: String,
    artworkUri: Uri?,
): MediaItem =
    MediaItem.Builder()
        .setMediaId(videoId)
        .setUri(videoId)
        .setMediaMetadata(
            androidx.media3.common.MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setArtworkUri(artworkUri)
                .build()
        )
        .build()
