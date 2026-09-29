/*
 * Luma Music
 * Minimal Application class: just enough setup for the innertube YouTube Music client to
 * work (locale + visitor data). No Hilt, no local database, no crash reporter — those belong
 * to the old OpenTune-based UI this app is replacing.
 */

package com.lumamusic.android

import android.app.Application
import com.arturo254.opentune.innertube.YouTube
import com.arturo254.opentune.innertube.models.YouTubeLocale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale

class App : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        val locale = Locale.getDefault()
        YouTube.locale = YouTubeLocale(
            gl = locale.country.takeIf { it.isNotBlank() } ?: "US",
            hl = locale.language.takeIf { it.isNotBlank() } ?: "en",
        )

        // Fetching visitorData isn't required for search/playback to work, but YouTube Music's
        // API is more reliable with one attached, so grab it once at startup.
        applicationScope.launch {
            YouTube.visitorData().onSuccess { YouTube.visitorData = it }
        }
    }
}
