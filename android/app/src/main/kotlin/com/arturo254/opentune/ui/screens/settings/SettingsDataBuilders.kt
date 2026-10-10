/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.arturo254.opentune.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.arturo254.opentune.BuildConfig
import com.arturo254.opentune.R
import com.arturo254.opentune.constants.AudioQuality
import com.arturo254.opentune.constants.AudioQualityKey
import com.arturo254.opentune.constants.PauseListenHistoryKey
import com.arturo254.opentune.constants.PauseSearchHistoryKey
import com.arturo254.opentune.utils.rememberEnumPreference
import com.arturo254.opentune.utils.rememberPreference

private fun subtitleOf(vararg parts: String) = parts.joinToString(" • ")

/**
 * The three tiles at the top of the screen, mirroring the
 * "Data saver / Offline mode / Private session" toggles with the closest
 * preferences this app actually has.
 */
@Composable
fun buildQuickToggles(): List<SettingsQuickToggle> {
    val (audioQuality, onAudioQualityChange) = rememberEnumPreference(AudioQualityKey, AudioQuality.AUTO)
    val (pauseListenHistory, onPauseListenHistoryChange) = rememberPreference(PauseListenHistoryKey, false)
    val (pauseSearchHistory, onPauseSearchHistoryChange) = rememberPreference(PauseSearchHistoryKey, false)

    val enabled = stringResource(R.string.enabled)
    val disabled = stringResource(R.string.disabled)
    val dataSaverOn = audioQuality == AudioQuality.LOW

    return listOf(
        SettingsQuickToggle(
            icon = painterResource(if (dataSaverOn) R.drawable.speed else R.drawable.graphic_eq),
            label = stringResource(R.string.settings_data_saver),
            state = when (audioQuality) {
                AudioQuality.LOW -> enabled
                AudioQuality.AUTO -> stringResource(R.string.audio_quality_auto)
                AudioQuality.HIGH -> stringResource(R.string.audio_quality_high)
                AudioQuality.HIGHEST -> stringResource(R.string.audio_quality_max)
            },
            isActive = dataSaverOn,
            onToggle = { onAudioQualityChange(if (dataSaverOn) AudioQuality.AUTO else AudioQuality.LOW) },
        ),
        SettingsQuickToggle(
            icon = painterResource(if (pauseSearchHistory) R.drawable.search_off else R.drawable.search),
            label = stringResource(R.string.settings_private_search),
            state = if (pauseSearchHistory) enabled else disabled,
            isActive = pauseSearchHistory,
            onToggle = { onPauseSearchHistoryChange(!pauseSearchHistory) },
        ),
        SettingsQuickToggle(
            icon = painterResource(if (pauseListenHistory) R.drawable.visibility_off else R.drawable.visibility),
            label = stringResource(R.string.settings_private_session),
            state = if (pauseListenHistory) enabled else disabled,
            isActive = pauseListenHistory,
            onToggle = { onPauseListenHistoryChange(!pauseListenHistory) },
        ),
    )
}

/**
 * Flat category list in the order Account, Content & display, Privacy,
 * Playback, Apps & devices, Data saving & offline, About, with the
 * app-specific screens slotted in next to their closest category.
 */
@Composable
fun buildSettingsRows(
    navController: NavController,
    isAndroid12OrLater: Boolean,
    hasUpdate: Boolean,
    isLoggedIn: Boolean,
    context: Context,
    onOpenAccount: () -> Unit,
    resetSearch: () -> Unit,
): List<SettingsItem> {
    fun go(route: String): () -> Unit = { resetSearch(); navController.navigate(route) }

    return buildList {
        add(
            SettingsItem(
                icon = painterResource(R.drawable.account),
                title = stringResource(R.string.account),
                subtitle = subtitleOf(
                    stringResource(R.string.username),
                    stringResource(if (isLoggedIn) R.string.action_logout else R.string.action_login),
                ),
                keywords = listOf("account", "login", "logout", "guest", "youtube", "cuenta"),
                onClick = { resetSearch(); onOpenAccount() },
            ),
        )
        add(
            SettingsItem(
                icon = painterResource(R.drawable.login),
                title = stringResource(R.string.account_options_title),
                subtitle = stringResource(R.string.account_options_desc),
                keywords = listOf("login", "logout", "guest", "token", "cookie", "sync", "youtube", "invitado"),
                onClick = go("settings/account_options"),
            ),
        )
        add(
            SettingsItem(
                icon = painterResource(R.drawable.language),
                title = stringResource(R.string.content),
                subtitle = subtitleOf(
                    stringResource(R.string.content_language),
                    stringResource(R.string.hide_explicit),
                    stringResource(R.string.lyrics),
                ),
                keywords = listOf("language", "content", "lyrics", "translation", "region", "proxy"),
                onClick = go("settings/content"),
            ),
        )
        add(
            SettingsItem(
                icon = painterResource(R.drawable.palette),
                title = stringResource(R.string.appearance),
                subtitle = subtitleOf(
                    stringResource(R.string.dark_theme),
                    stringResource(R.string.color_palette),
                    stringResource(R.string.default_open_tab),
                ),
                keywords = listOf("theme", "palette", "material you", "dynamic color", "font", "ui", "display"),
                onClick = go("settings/appearance"),
            ),
        )
        add(
            SettingsItem(
                icon = painterResource(R.drawable.security),
                title = stringResource(R.string.privacy),
                subtitle = subtitleOf(
                    stringResource(R.string.pause_listen_history),
                    stringResource(R.string.pause_search_history),
                    stringResource(R.string.disable_screenshot),
                ),
                keywords = listOf("privacy", "history", "tracking", "security", "private session"),
                onClick = go("settings/privacy"),
            ),
        )
        add(
            SettingsItem(
                icon = painterResource(R.drawable.play),
                title = stringResource(R.string.player_and_audio),
                subtitle = subtitleOf(
                    stringResource(R.string.audio_quality),
                    stringResource(R.string.skip_silence),
                    stringResource(R.string.persistent_queue),
                ),
                keywords = listOf("audio", "playback", "volume", "quality", "equalizer", "crossfade", "media quality"),
                onClick = go("settings/player"),
            ),
        )
        add(
            SettingsItem(
                icon = painterResource(R.drawable.integration),
                title = stringResource(R.string.integrations),
                subtitle = subtitleOf(stringResource(R.string.discord), "Last.fm", "ListenBrainz"),
                keywords = listOf("discord", "rpc", "lastfm", "last.fm", "scrobble", "listenbrainz", "apps", "devices"),
                onClick = go("settings/integration"),
            ),
        )
        add(
            SettingsItem(
                icon = painterResource(R.drawable.android_auto),
                title = stringResource(R.string.android_auto),
                subtitle = subtitleOf(
                    stringResource(R.string.android_auto_enable),
                    stringResource(R.string.android_auto_library),
                ),
                keywords = listOf("android auto", "car", "automotive", "vehicle", "driving"),
                onClick = go("settings/android_auto"),
            ),
        )
        add(
            SettingsItem(
                icon = painterResource(R.drawable.bedtime),
                title = stringResource(R.string.aod_screen_title),
                subtitle = stringResource(R.string.always_on_display_description),
                keywords = listOf("AOD", "Always On Display", "Display"),
                onClick = go("settings/appearance/always_on_display"),
            ),
        )
        add(
            SettingsItem(
                icon = painterResource(R.drawable.fire),
                title = stringResource(R.string.music_together),
                subtitle = subtitleOf(
                    stringResource(R.string.together_host_section),
                    stringResource(R.string.together_join_section),
                ),
                keywords = listOf("together", "session", "sync", "party", "join", "host"),
                onClick = go("settings/music_together"),
            ),
        )
        add(
            SettingsItem(
                icon = painterResource(R.drawable.storage),
                title = stringResource(R.string.storage),
                subtitle = subtitleOf(
                    stringResource(R.string.song_cache),
                    stringResource(R.string.image_cache),
                    stringResource(R.string.smart_trimmer),
                ),
                keywords = listOf("storage", "cache", "offline", "downloads", "cleanup", "data saver"),
                onClick = go("settings/storage"),
            ),
        )
        add(
            SettingsItem(
                icon = painterResource(R.drawable.restore),
                title = stringResource(R.string.backup_restore),
                subtitle = subtitleOf(
                    stringResource(R.string.action_backup),
                    stringResource(R.string.action_restore),
                    stringResource(R.string.import_playlist),
                ),
                keywords = listOf("backup", "restore", "import", "export", "migration"),
                onClick = go("settings/backup_restore"),
            ),
        )
        add(
            SettingsItem(
                icon = painterResource(R.drawable.token),
                title = stringResource(R.string.po_token_generation),
                subtitle = stringResource(R.string.po_token_generation_subtitle),
                keywords = listOf("po token", "token", "web client", "visitor data", "gvs", "player"),
                onClick = go("settings/po_token"),
            ),
        )
        if (isAndroid12OrLater) {
            add(
                SettingsItem(
                    icon = painterResource(R.drawable.link),
                    title = stringResource(R.string.default_links),
                    subtitle = stringResource(R.string.open_supported_links),
                    keywords = listOf("links", "deeplink", "default", "supported links"),
                    onClick = {
                        resetSearch()
                        try {
                            val intent = Intent(
                                Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
                                Uri.parse("package:${context.packageName}")
                            )
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                R.string.open_app_settings_error,
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    },
                ),
            )
        }
        add(
            SettingsItem(
                icon = painterResource(R.drawable.experiment),
                title = stringResource(R.string.experiment_settings),
                subtitle = subtitleOf(
                    stringResource(R.string.nerd_stats),
                    stringResource(R.string.debug_logs),
                ),
                keywords = listOf("experimental", "debug", "developer", "labs", "internal"),
                onClick = go("settings/misc"),
            ),
        )
        add(
            SettingsItem(
                icon = painterResource(R.drawable.update),
                title = stringResource(R.string.updates),
                subtitle = if (hasUpdate) {
                    stringResource(R.string.new_version_available)
                } else {
                    subtitleOf(BuildConfig.VERSION_NAME, stringResource(R.string.update_channel))
                },
                showUpdateIndicator = hasUpdate,
                keywords = listOf("update", "version", "release", "changelog"),
                onClick = go("settings/update"),
            ),
        )
        add(
            SettingsItem(
                icon = painterResource(R.drawable.info),
                title = stringResource(R.string.about),
                subtitle = subtitleOf(
                    stringResource(R.string.current_version),
                    stringResource(R.string.contributors),
                    stringResource(R.string.view_license),
                ),
                keywords = listOf("about", "app info", "license", "contributors", "support"),
                onClick = go("settings/about"),
            ),
        )
    }
}

/** Deeper screens that only show up as search results. */
@Composable
fun buildInternalItems(
    navController: NavController,
    resetSearch: () -> Unit,
): List<SettingsItem> {
    fun go(route: String): () -> Unit = { resetSearch(); navController.navigate(route) }

    return listOf(
        SettingsItem(
            icon = painterResource(R.drawable.palette),
            title = stringResource(R.string.theme_creator_title),
            subtitle = stringResource(R.string.theme_creator_subtitle),
            keywords = listOf("theme", "creator", "seed", "material", "palette", "import", "export"),
            onClick = go("settings/appearance/theme_creator"),
        ),
        SettingsItem(
            icon = painterResource(R.drawable.palette),
            title = stringResource(R.string.customize_colors),
            subtitle = stringResource(R.string.appearance),
            keywords = listOf("palette", "color", "accent", "tone", "dynamic color"),
            onClick = go("settings/appearance/palette_picker"),
        ),
        SettingsItem(
            icon = painterResource(R.drawable.image),
            title = stringResource(R.string.customize_background_title),
            subtitle = stringResource(R.string.appearance),
            keywords = listOf("background", "wallpaper", "image", "blur", "gradient"),
            onClick = go("customize_background"),
        ),
        SettingsItem(
            icon = painterResource(R.drawable.discord),
            title = stringResource(R.string.discord_integration),
            subtitle = stringResource(R.string.integration),
            keywords = listOf("discord", "rpc", "rich presence", "status", "activity"),
            onClick = go("settings/discord"),
        ),
        SettingsItem(
            icon = painterResource(R.drawable.security),
            title = stringResource(R.string.advanced_login),
            subtitle = stringResource(R.string.discord),
            keywords = listOf("token", "login", "authentication", "discord login"),
            onClick = go("settings/discord/login"),
        ),
        SettingsItem(
            icon = painterResource(R.drawable.experiment),
            title = stringResource(R.string.experimental_features),
            subtitle = stringResource(R.string.experimental_features_description),
            keywords = listOf("experimental", "labs", "advanced", "discord experimental", "internal"),
            onClick = go("settings/discord/experimental"),
        ),
        SettingsItem(
            icon = painterResource(R.drawable.integration),
            title = stringResource(R.string.lastfm_integration),
            subtitle = stringResource(R.string.integration),
            keywords = listOf("lastfm", "last.fm", "scrobble", "listening history"),
            onClick = go("settings/lastfm"),
        ),
    )
}
