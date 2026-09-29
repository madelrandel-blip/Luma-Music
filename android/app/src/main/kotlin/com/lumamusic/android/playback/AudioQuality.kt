/*
 * Luma Music
 * Minimal playback-engine support types, adapted from the OpenTune project
 * (github.com/Erorr40/OpenTune, GPL-3.0) for reuse without its database/UI layers.
 */

package com.lumamusic.android.playback

enum class AudioQuality {
    AUTO,
    HIGH,
    HIGHEST,
    LOW,
}

enum class PlayerStreamClient {
    ANDROID_VR,
    WEB_REMIX,
    IOS,
    TVHTML5,
    ANDROID_MUSIC,
}
