/*
 * Luma Music
 * Thin wrapper around YouTube.home() -- YouTube Music's home-feed endpoint, used to power the
 * app's Inicio (home) tab. No database involved: results are fetched fresh each time loadHome()
 * runs, same reuse pattern as SearchRepository.
 */

package com.lumamusic.android.data

import com.arturo254.opentune.innertube.YouTube
import com.arturo254.opentune.innertube.pages.HomePage

object HomeRepository {
    suspend fun loadHome(): Result<HomePage> = YouTube.home()
}
