package com.arturo254.opentune.party

import com.arturo254.opentune.innertube.models.Artist
import com.arturo254.opentune.innertube.models.SongItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A stripped-down, network-friendly copy of a [SongItem] - just enough to reconstruct
 * something playable on the other end, without dragging the whole innertube model (watch
 * endpoints, album browse ids, etc.) through the wire. */
@Serializable
data class PartySongDto(
    val id: String,
    val title: String,
    val artist: String,
    val thumbnail: String,
    val durationSec: Int? = null,
)

fun SongItem.toPartyDto() = PartySongDto(
    id = id,
    title = title,
    artist = artists.joinToString(", ") { it.name },
    thumbnail = thumbnail,
    durationSec = duration,
)

fun PartySongDto.toSongItem() = SongItem(
    id = id,
    title = title,
    artists = if (artist.isBlank()) emptyList() else listOf(Artist(name = artist, id = null)),
    thumbnail = thumbnail,
    duration = durationSec,
)

@Serializable
data class PartyParticipantDto(
    val id: String,
    val name: String,
    val isHost: Boolean = false,
    /** Set only for a YouTube-linked identity - a remote URL both peers can load independently. */
    val avatarUrl: String? = null,
    /** Set only for a local guest identity - the other machine has no way to reach that file,
     * so a small (already downscaled) copy of the image travels with the handshake instead. */
    val avatarBase64: String? = null,
)

/**
 * The whole party-mode wire protocol. Every value of this sealed class is what actually crosses
 * the relay, always inside an AES-GCM encrypted [PartyEnvelope] (see [PartyModeManager]) -
 * nothing here is ever sent or accepted in the clear.
 */
@Serializable
sealed class PartyMessage {
    /** First message a guest must send right after connecting. Anything else as a first
     * message, or one that fails to decrypt/authenticate at all, gets the connection dropped. */
    @Serializable
    @SerialName("hello")
    data class Hello(
        val name: String,
        val avatarUrl: String? = null,
        val avatarBase64: String? = null,
        val ts: Long,
    ) : PartyMessage()

    /** Host's reply to a valid [Hello]: the room's full current state, so the guest can adopt
     * it in one shot instead of reconstructing it from a stream of incremental updates. */
    @Serializable
    @SerialName("welcome")
    data class Welcome(
        val yourId: String,
        val participants: List<PartyParticipantDto>,
        val queue: List<PartySongDto>,
        val currentIndex: Int,
        val positionMs: Long,
        val isPlaying: Boolean,
        val serverTimeMs: Long,
    ) : PartyMessage()

    /** Broadcast whenever someone joins or leaves. */
    @Serializable
    @SerialName("presence")
    data class Presence(val participants: List<PartyParticipantDto>) : PartyMessage()

    /**
     * The room's authoritative playback state, broadcast by the host any time it changes (a new
     * song, play/pause, a seek, the queue itself) and also on a plain ~1s heartbeat while
     * playing, purely for drift correction. [startAtServerTimeMs], when set, means "start (or
     * resume) exactly at this shared-clock instant" so everyone begins together instead of each
     * peer reacting the moment its own copy of the message happens to arrive.
     */
    @Serializable
    @SerialName("state")
    data class State(
        /** Null means "the queue didn't change, keep the one you have" - the queue is the big
         * part of this message, so it's only sent when it actually changed. */
        val queue: List<PartySongDto>? = null,
        val currentIndex: Int,
        val positionMs: Long,
        val isPlaying: Boolean,
        val serverTimeMs: Long,
        val startAtServerTimeMs: Long? = null,
        /** True while the host is holding playback because somebody in the room is still loading
         * the song - guests show that as "loading", not as a track someone paused. */
        val waitingForGuests: Boolean = false,
    ) : PartyMessage()

    // Anyone (host or guest) can add/pause/skip - see the class doc on PartyModeManager. Guests
    // never mutate playback locally: they send one of these to the host and wait for the [State]
    // it broadcasts back, so nobody's queue can ever drift out of sync with anyone else's.
    @Serializable
    @SerialName("request_pause")
    object RequestPause : PartyMessage()

    @Serializable
    @SerialName("request_resume")
    object RequestResume : PartyMessage()

    @Serializable
    @SerialName("request_next")
    object RequestNext : PartyMessage()

    @Serializable
    @SerialName("request_previous")
    object RequestPrevious : PartyMessage()

    @Serializable
    @SerialName("request_seek")
    data class RequestSeek(val positionMs: Long) : PartyMessage()

    @Serializable
    @SerialName("request_jump")
    data class RequestJump(val index: Int) : PartyMessage()

    @Serializable
    @SerialName("request_queue_add")
    data class RequestQueueAdd(val song: PartySongDto) : PartyMessage()

    @Serializable
    @SerialName("request_queue_remove")
    data class RequestQueueRemove(val songId: String) : PartyMessage()

    @Serializable
    @SerialName("request_play_song")
    data class RequestPlaySong(val song: PartySongDto, val queue: List<PartySongDto>) : PartyMessage()

    @Serializable
    @SerialName("leave")
    object Leave : PartyMessage()

    /** Sent by a guest once the current song has finished loading on its side, so the host knows
     * it can start everyone together instead of playing to people who are still buffering. */
    @Serializable
    @SerialName("ready")
    data class Ready(val songId: String) : PartyMessage()

    /** Keep-alive in both directions: there's no direct socket whose closing would tell either
     * side the other one is gone, so each side pings and times the other out if it goes quiet. */
    @Serializable
    @SerialName("ping")
    object Ping : PartyMessage()
}

/** What's actually encrypted and published through the relay: who sent it, plus the message. */
@Serializable
data class PartyEnvelope(val from: String, val msg: PartyMessage)
