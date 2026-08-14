package com.arturo254.opentune

import androidx.compose.runtime.*
import java.io.File
import java.util.Properties

object DesktopPreferences {
    private val file = File(System.getProperty("user.home"), ".opentune/settings.properties")
    private val props = Properties()

    private var _themePaletteId = "default"
    private var _pureBlack = false
    private var _contentLanguage = "system"
    private var _contentCountry = "system"
    private var _hideExplicit = false
    private var _pauseListenHistory = false
    private var _pauseSearchHistory = false
    private var _audioQuality = "auto"
    private var _autoSkipOnError = false
    private var _seekExtraSeconds = false
    private var _persistentQueue = true
    private var _maxCacheSizeMB = 500L
    private var _fullscreenPlayer = false
    private var _volume = 1.0f

    var themePaletteId by mutableStateOf("default"); private set
    var pureBlack by mutableStateOf(false); private set
    var contentLanguage by mutableStateOf("system"); private set
    var contentCountry by mutableStateOf("system"); private set
    var hideExplicit by mutableStateOf(false); private set
    var pauseListenHistory by mutableStateOf(false); private set
    var pauseSearchHistory by mutableStateOf(false); private set
    var audioQuality by mutableStateOf("auto"); private set
    var autoSkipOnError by mutableStateOf(false); private set
    var seekExtraSeconds by mutableStateOf(false); private set
    var persistentQueue by mutableStateOf(true); private set
    var maxCacheSizeMB by mutableStateOf(500L); private set
    var fullscreenPlayer by mutableStateOf(false); private set
    var volume by mutableStateOf(1.0f); private set

    init { load() }

    private fun load() {
        try {
            if (file.exists()) file.inputStream().use { props.load(it) }
            themePaletteId = props.getProperty("themePaletteId", "default")
            pureBlack = props.getProperty("pureBlack", "false").toBoolean()
            contentLanguage = props.getProperty("contentLanguage", "system")
            contentCountry = props.getProperty("contentCountry", "system")
            hideExplicit = props.getProperty("hideExplicit", "false").toBoolean()
            pauseListenHistory = props.getProperty("pauseListenHistory", "false").toBoolean()
            pauseSearchHistory = props.getProperty("pauseSearchHistory", "false").toBoolean()
            audioQuality = props.getProperty("audioQuality", "auto")
            autoSkipOnError = props.getProperty("autoSkipOnError", "false").toBoolean()
            seekExtraSeconds = props.getProperty("seekExtraSeconds", "false").toBoolean()
            persistentQueue = props.getProperty("persistentQueue", "true").toBoolean()
            maxCacheSizeMB = props.getProperty("maxCacheSizeMB", "500").toLongOrNull() ?: 500
            fullscreenPlayer = props.getProperty("fullscreenPlayer", "false").toBoolean()
            volume = props.getProperty("volume", "1.0").toFloatOrNull()?.coerceIn(0f, 1f) ?: 1.0f
        } catch (_: Exception) {}
    }

    private fun save() {
        try {
            file.parentFile?.mkdirs()
            props.setProperty("themePaletteId", themePaletteId)
            props.setProperty("pureBlack", pureBlack.toString())
            props.setProperty("contentLanguage", contentLanguage)
            props.setProperty("contentCountry", contentCountry)
            props.setProperty("hideExplicit", hideExplicit.toString())
            props.setProperty("pauseListenHistory", pauseListenHistory.toString())
            props.setProperty("pauseSearchHistory", pauseSearchHistory.toString())
            props.setProperty("audioQuality", audioQuality)
            props.setProperty("autoSkipOnError", autoSkipOnError.toString())
            props.setProperty("seekExtraSeconds", seekExtraSeconds.toString())
            props.setProperty("persistentQueue", persistentQueue.toString())
            props.setProperty("maxCacheSizeMB", maxCacheSizeMB.toString())
            props.setProperty("fullscreenPlayer", fullscreenPlayer.toString())
            props.setProperty("volume", volume.toString())
            file.outputStream().use { props.store(it, null) }
        } catch (_: Exception) {}
    }

    fun updateThemePalette(v: String) { themePaletteId = v; save() }
    fun updatePureBlack(v: Boolean) { pureBlack = v; save() }
    fun updateContentLanguage(v: String) { contentLanguage = v; save() }
    fun updateContentCountry(v: String) { contentCountry = v; save() }
    fun updateHideExplicit(v: Boolean) { hideExplicit = v; save() }
    fun updatePauseListenHistory(v: Boolean) { pauseListenHistory = v; save() }
    fun updatePauseSearchHistory(v: Boolean) { pauseSearchHistory = v; save() }
    fun updateAudioQuality(v: String) { audioQuality = v; save() }
    fun updateAutoSkipOnError(v: Boolean) { autoSkipOnError = v; save() }
    fun updateSeekExtraSeconds(v: Boolean) { seekExtraSeconds = v; save() }
    fun updatePersistentQueue(v: Boolean) { persistentQueue = v; save() }
    fun updateMaxCacheSizeMB(v: Long) { maxCacheSizeMB = v; save() }
    fun updateFullscreenPlayer(v: Boolean) { fullscreenPlayer = v; save() }
    fun updateVolume(v: Float) { volume = v.coerceIn(0f, 1f); save() }
}

data class DesktopPalette(
    val id: String,
    val name: String,
    val primary: androidx.compose.ui.graphics.Color,
)

object DesktopPalettes {
    val all = listOf(
        DesktopPalette("default", "Default (Rose)", androidx.compose.ui.graphics.Color(0xFFED5564)),
        DesktopPalette("ocean_blue", "Ocean Blue", androidx.compose.ui.graphics.Color(0xFF4A90D9)),
        DesktopPalette("arctic_blue", "Arctic Blue", androidx.compose.ui.graphics.Color(0xFF00BFFF)),
        DesktopPalette("cobalt_blue", "Cobalt Blue", androidx.compose.ui.graphics.Color(0xFF0047AB)),
        DesktopPalette("midnight_navy", "Midnight Navy", androidx.compose.ui.graphics.Color(0xFF2C3E50)),
        DesktopPalette("emerald_green", "Emerald Green", androidx.compose.ui.graphics.Color(0xFF2ECC71)),
        DesktopPalette("teal_wave", "Teal Wave", androidx.compose.ui.graphics.Color(0xFF1ABC9C)),
        DesktopPalette("spotify_green", "Spotify Green", androidx.compose.ui.graphics.Color(0xFF1DB954)),
        DesktopPalette("forest_green", "Forest Green", androidx.compose.ui.graphics.Color(0xFF228B22)),
        DesktopPalette("sunset_orange", "Sunset Orange", androidx.compose.ui.graphics.Color(0xFFE67E22)),
        DesktopPalette("golden_hour", "Golden Hour", androidx.compose.ui.graphics.Color(0xFFF39C12)),
        DesktopPalette("tangerine", "Tangerine", androidx.compose.ui.graphics.Color(0xFFFF9800)),
        DesktopPalette("royal_purple", "Royal Purple", androidx.compose.ui.graphics.Color(0xFF7B1FA2)),
        DesktopPalette("lavender_dream", "Lavender Dream", androidx.compose.ui.graphics.Color(0xFF9C27B0)),
        DesktopPalette("deep_violet", "Deep Violet", androidx.compose.ui.graphics.Color(0xFF6A1B9A)),
        DesktopPalette("crimson_red", "Crimson Red", androidx.compose.ui.graphics.Color(0xFFC62828)),
        DesktopPalette("ruby", "Ruby", androidx.compose.ui.graphics.Color(0xFFE91E63)),
        DesktopPalette("cherry_blossom", "Cherry Blossom", androidx.compose.ui.graphics.Color(0xFFEC407A)),
        DesktopPalette("hot_pink", "Hot Pink", androidx.compose.ui.graphics.Color(0xFFFF4081)),
        DesktopPalette("carbon", "Carbon", androidx.compose.ui.graphics.Color(0xFF424242)),
        DesktopPalette("steel_grey", "Steel Grey", androidx.compose.ui.graphics.Color(0xFF607D8B)),
    )

    fun byId(id: String) = all.find { it.id == id } ?: all.first()
}

@Composable
fun rememberCurrentPalette(): DesktopPalette {
    val paletteId = DesktopPreferences.themePaletteId
    return remember(paletteId) { DesktopPalettes.byId(paletteId) }
}
