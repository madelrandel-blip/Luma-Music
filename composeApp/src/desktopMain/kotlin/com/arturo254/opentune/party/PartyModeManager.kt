package com.arturo254.opentune.party

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.arturo254.opentune.AccountManager
import com.arturo254.opentune.DesktopPreferences
import com.arturo254.opentune.GuestProfileManager
import com.arturo254.opentune.player.PlayerManager
import com.arturo254.opentune.player.TransportAction
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

/**
 * Party Mode: two (or a few more) Luma Music instances sharing one listening session.
 *
 * Connection: nobody has to touch their router. The host and every guest each open an
 * *outgoing* connection to the same free public MQTT broker (see [PartyBrokers] and
 * [MiniMqttClient]) and exchange messages through it - routers always allow outgoing
 * connections, so this works on basically any home internet, including ones without UPnP or
 * behind carrier-grade NAT. Only small control messages travel (play/pause, queue, position);
 * the music itself is streamed by each computer on its own.
 *
 * Privacy: the broker is a third party, so it's treated as untrusted. Every message is AES-GCM
 * encrypted (see [PartyCrypto]) with a key derived from the room code + PIN, which are shared out
 * of band (chat, WhatsApp) and never sent through the broker. The broker - or anyone else
 * listening on it - only sees opaque bytes on a topic named after a hash of the code, and can't
 * read, forge or inject anything into the room.
 *
 * Room logic is unchanged from the direct-connection version: the host is the room's single
 * source of truth for playback. Guests never mutate their own queue/playback directly (see
 * [PlayerManager.transportInterceptor]) - every button press becomes a request to the host, which
 * applies it and broadcasts the resulting state to everyone, so nobody's queue drifts out of sync.
 *
 * Topics, under `lumamusic/party/v1/<hash of code>`:
 *  - `/h`       guests -> host (Hello, requests, Ping, Leave)
 *  - `/g`       host -> every guest (State, Presence, Ping, Leave)
 *  - `/g/<id>`  host -> one guest (Welcome)
 * Since there are no sockets per guest any more, liveness is tracked with pings: a guest the host
 * hasn't heard from in [PEER_TIMEOUT_MS] is dropped, and a guest that hasn't heard from the host
 * in that long treats the room as gone.
 */
object PartyModeManager {
    enum class Role { NONE, HOST, GUEST }
    enum class ConnectionState { IDLE, CONNECTING, CONNECTED, FAILED }

    var role by mutableStateOf(Role.NONE); private set
    var connectionState by mutableStateOf(ConnectionState.IDLE); private set
    var participants by mutableStateOf<List<PartyParticipantDto>>(emptyList()); private set
    var lastError by mutableStateOf<String?>(null); private set
    /** The code to share with friends, formatted for reading (e.g. "EK7Q-M2PX"). */
    var roomCode by mutableStateOf(""); private set
    /** A 4-digit PIN generated fresh for each room, shared together with [roomCode]. */
    var roomPin by mutableStateOf(""); private set
    /** True while the room is held because somebody is still loading the song - the UI shows a
     * loading indicator instead of a paused player, since nobody actually paused anything. */
    var waitingForOthers by mutableStateOf(false); private set

    val inRoom: Boolean get() = role != Role.NONE

    private const val MAX_PARTICIPANTS = 8
    private const val JOIN_TIMEOUT_MS = 15_000L
    private const val HELLO_RETRY_MS = 3_000L
    private const val PING_INTERVAL_MS = 4_000L
    private const val PEER_TIMEOUT_MS = 25_000L
    private const val RECONNECT_ATTEMPTS = 5
    /** How long the host waits for everyone to load a new song before starting without them. */
    private const val READY_TIMEOUT_MS = 15_000L
    private const val CODE_LENGTH = 8
    private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private const val HOST_ID = "host"

    private val protocolJson = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val secureRandom = SecureRandom()
    private val sendExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "party-send").apply { isDaemon = true }
    }

    private var key: SecretKeySpec? = null
    private var myId: String = ""
    private var client: MiniMqttClient? = null
    private var broker: PartyBroker? = null
    private var topicBase: String = ""
    private var playerListener: (() -> Unit)? = null
    private var heartbeatJob: Job? = null
    /** Bumped on every create/join/leave so callbacks from an old connection are ignored. */
    @Volatile private var session = 0

    // Host-only: guest id -> last time we heard from it.
    private val guestLastSeen = ConcurrentHashMap<String, Long>()
    private val participantsLock = Any()
    private var lastBroadcastSongId: String? = null
    private var lastBroadcastPlaying: Boolean = false
    private var lastBroadcastIndex: Int = -1
    private var lastQueueSignature: String? = null
    /** Position and wall clock of the last [State] sent, to notice a seek (a jump the elapsed
     * time can't explain) without broadcasting on every position tick. */
    private var lastBroadcastPositionMs: Long = 0L
    private var lastBroadcastAtMs: Long = 0L

    // Host-only: who has reported the current song as loaded, and which song that is.
    private val readyGuests = java.util.Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    @Volatile private var waitingSongId: String? = null
    /** The last song this device got ready to play - so the room is held once per song, and not
     * again every time that song is mentioned in a later update. */
    @Volatile private var coordinatedSongId: String? = null
    private var readyTimeoutJob: Job? = null

    // Guest-only.
    private var clockOffsetMs: Long = 0L
    @Volatile private var lastHostMessageAt: Long = 0L
    @Volatile private var pendingWelcome: CompletableDeferred<PartyMessage.Welcome>? = null

    private val toHostTopic get() = "$topicBase/h"
    private val toGuestsTopic get() = "$topicBase/g"
    private fun toGuestTopic(id: String) = "$topicBase/g/$id"

    // ---------------------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------------------

    /** Connects to the first reachable public broker, then generates the room code and PIN. */
    suspend fun createRoom(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            closeClient()
            reset()
            val mySession = ++session
            connectionState = ConnectionState.CONNECTING
            myId = HOST_ID

            val code = newCodeFor(connectFirstAvailableBroker(mySession))
            val pin = secureRandom.nextInt(10000).toString().padStart(4, '0')
            topicBase = topicBaseFor(code)
            key = keyFor(code, pin)
            client!!.subscribe(toHostTopic)

            roomCode = formatCode(code)
            roomPin = pin

            val (name, avatarUrl, avatarBase64) = currentIdentity()
            participants = listOf(PartyParticipantDto(id = myId, name = name, isHost = true, avatarUrl = avatarUrl, avatarBase64 = avatarBase64))

            role = Role.HOST
            connectionState = ConnectionState.CONNECTED
            lastQueueSignature = queueSignature()
            installHostBroadcaster()
            startHostHeartbeat(mySession)
        }.onFailure {
            lastError = it.message
            closeClient()
            cleanupHostResources()
            reset()
            connectionState = ConnectionState.FAILED
        }
    }

    /** Joins with the [code] and [pin] the host shared - dashes, spaces and case don't matter. */
    suspend fun joinRoom(code: String, pin: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            closeClient()
            reset()
            val mySession = ++session
            connectionState = ConnectionState.CONNECTING

            val normalized = normalizeCode(code)
            val trimmedPin = pin.trim()
            if (normalized.length != CODE_LENGTH) throw IllegalStateException("El código de sala debe tener $CODE_LENGTH caracteres")
            if (!trimmedPin.matches(Regex("^\\d{4}$"))) throw IllegalStateException("El PIN debe tener 4 dígitos")
            val chosen = PartyBrokers.byTag(normalized[0]) ?: throw IllegalStateException("El código de sala no es válido")

            myId = UUID.randomUUID().toString().replace("-", "")
            topicBase = topicBaseFor(normalized)
            key = keyFor(normalized, trimmedPin)

            val welcomeDeferred = CompletableDeferred<PartyMessage.Welcome>()
            pendingWelcome = welcomeDeferred
            connectTo(chosen, mySession)
            client!!.subscribe(toGuestsTopic)
            client!!.subscribe(toGuestTopic(myId))

            val (name, avatarUrl, avatarBase64) = currentIdentity()
            val welcome = withTimeoutOrNull(JOIN_TIMEOUT_MS) {
                val retry = launch {
                    while (true) {
                        send(toHostTopic, PartyMessage.Hello(name = name, avatarUrl = avatarUrl, avatarBase64 = avatarBase64, ts = System.currentTimeMillis()))
                        delay(HELLO_RETRY_MS)
                    }
                }
                try { welcomeDeferred.await() } finally { retry.cancel() }
            } ?: throw IllegalStateException("No se encontró la sala. Revisa el código y el PIN, y que tu amigo tenga la sala abierta.")
            pendingWelcome = null

            clockOffsetMs = welcome.serverTimeMs - System.currentTimeMillis()
            lastHostMessageAt = System.currentTimeMillis()
            participants = welcome.participants

            PlayerManager.applyRemoteState {
                val songs = welcome.queue.map { it.toSongItem() }
                PlayerManager.queue.clear()
                PlayerManager.queue.addAll(songs)
                val target = songs.getOrNull(welcome.currentIndex)
                if (target != null) {
                    PlayerManager.playSong(target, songs)
                    if (!welcome.isPlaying) PlayerManager.pausePlayback()
                }
            }
            scope.launch { catchUpPosition(welcome.positionMs, welcome.isPlaying, welcome.serverTimeMs, startAt = null) }

            PlayerManager.transportInterceptor = ::onLocalGuestAction
            roomCode = formatCode(normalized)
            roomPin = trimmedPin
            role = Role.GUEST
            connectionState = ConnectionState.CONNECTED
            startGuestHeartbeat(mySession)
            Unit
        }.onFailure {
            lastError = it.message
            pendingWelcome = null
            closeClient()
            cleanupGuestResources()
            reset()
            connectionState = ConnectionState.FAILED
        }
    }

    fun leaveRoom() {
        when (role) {
            Role.HOST -> runCatching { send(toGuestsTopic, PartyMessage.Leave) }
            Role.GUEST -> runCatching { send(toHostTopic, PartyMessage.Leave) }
            Role.NONE -> {}
        }
        session++
        closeClient()
        cleanupHostResources()
        cleanupGuestResources()
        reset()
    }

    // ---------------------------------------------------------------------------------------
    // Connection
    // ---------------------------------------------------------------------------------------

    private fun connectFirstAvailableBroker(mySession: Int): PartyBroker {
        for (candidate in PartyBrokers.all) {
            try {
                connectTo(candidate, mySession)
                return candidate
            } catch (_: Exception) {
                // Try the next one - a network may block one port or one broker may be down.
            }
        }
        throw IllegalStateException("No se pudo conectar al servidor de salas. Revisa tu conexión a internet e inténtalo de nuevo.")
    }

    private fun connectTo(target: PartyBroker, mySession: Int) {
        val c = MiniMqttClient(
            broker = target,
            clientId = "luma-" + UUID.randomUUID().toString().replace("-", "").take(20),
            onMessage = { topic, payload -> if (session == mySession) onRelayMessage(topic, payload) },
            onConnectionLost = { if (session == mySession) scope.launch { reconnectOrGiveUp(mySession) } },
        )
        c.connect()
        client = c
        broker = target
    }

    /** The broker dropped us (network blip, broker restart). Try to get back into the same room
     * a few times before giving up - the room's state lives on the host, not on the broker, so a
     * reconnect loses nothing. */
    private suspend fun reconnectOrGiveUp(mySession: Int) {
        val target = broker ?: return
        repeat(RECONNECT_ATTEMPTS) {
            delay(3000)
            if (session != mySession || role == Role.NONE) return
            try {
                connectTo(target, mySession)
                when (role) {
                    Role.HOST -> client?.subscribe(toHostTopic)
                    Role.GUEST -> {
                        client?.subscribe(toGuestsTopic)
                        client?.subscribe(toGuestTopic(myId))
                        send(toHostTopic, PartyMessage.Ping)
                    }
                    Role.NONE -> {}
                }
                return
            } catch (_: Exception) {
            }
        }
        if (session != mySession) return
        lastError = "Se perdió la conexión con la sala"
        leaveRoom()
        connectionState = ConnectionState.FAILED
    }

    private fun closeClient() {
        runCatching { client?.close() }
        client = null
    }

    // ---------------------------------------------------------------------------------------
    // Identity
    // ---------------------------------------------------------------------------------------

    /** Resolves (name, avatarUrl, avatarBase64) from whichever identity the user picked in
     * Settings > Cuenta (see [DesktopPreferences.partyIdentity]), falling back sensibly if the
     * preferred one isn't actually available. */
    private fun currentIdentity(): Triple<String, String?, String?> {
        fun guestTriple(): Triple<String, String?, String?> {
            val avatarB64 = GuestProfileManager.avatarFile?.let { f ->
                runCatching { Base64.getEncoder().encodeToString(f.readBytes()) }.getOrNull()
            }
            return Triple(GuestProfileManager.name, null, avatarB64)
        }
        fun youtubeTriple(): Triple<String, String?, String?>? {
            val info = AccountManager.accountInfo ?: return null
            return Triple(info.name, info.thumbnailUrl, null)
        }
        val preferGuest = DesktopPreferences.partyIdentity == "guest"
        if (preferGuest && GuestProfileManager.exists) return guestTriple()
        youtubeTriple()?.let { return it }
        if (GuestProfileManager.exists) return guestTriple()
        return Triple(System.getProperty("user.name") ?: "Invitado", null, null)
    }

    // ---------------------------------------------------------------------------------------
    // Incoming messages
    // ---------------------------------------------------------------------------------------

    private fun onRelayMessage(topic: String, payload: ByteArray) {
        val k = key ?: return
        val plain = PartyCrypto.decrypt(k, payload) ?: return // wrong PIN, junk, or not for us
        val envelope = try {
            protocolJson.decodeFromString<PartyEnvelope>(String(plain, Charsets.UTF_8))
        } catch (_: Exception) {
            return
        }
        when {
            role == Role.HOST && topic == toHostTopic -> onGuestMessage(envelope.from, envelope.msg)
            role != Role.HOST && envelope.from == HOST_ID && (topic == toGuestsTopic || topic == toGuestTopic(myId)) ->
                onHostMessage(envelope.msg)
        }
    }

    // ---------------------------------------------------------------------------------------
    // Host side
    // ---------------------------------------------------------------------------------------

    private fun onGuestMessage(from: String, msg: PartyMessage) {
        if (!from.matches(Regex("^[A-Za-z0-9]{8,64}$"))) return
        val known = guestLastSeen.containsKey(from)
        when (msg) {
            is PartyMessage.Hello -> {
                if (abs(System.currentTimeMillis() - msg.ts) > 120_000) return
                if (!known && guestLastSeen.size >= MAX_PARTICIPANTS - 1) return
                guestLastSeen[from] = System.currentTimeMillis()
                if (!known) {
                    val participant = PartyParticipantDto(id = from, name = msg.name.ifBlank { "Invitado" }, isHost = false, avatarUrl = msg.avatarUrl, avatarBase64 = msg.avatarBase64)
                    synchronized(participantsLock) { participants = participants.filter { it.id != from } + participant }
                }
                // Sent again for a repeated Hello too - the guest retries until one arrives.
                send(toGuestTopic(from), PartyMessage.Welcome(
                    yourId = from,
                    participants = participants,
                    queue = PlayerManager.queue.map { it.toPartyDto() },
                    currentIndex = PlayerManager.currentIndex,
                    positionMs = PlayerManager.position,
                    isPlaying = PlayerManager.isPlaying,
                    serverTimeMs = System.currentTimeMillis(),
                ))
                lastQueueSignature = queueSignature() // the Welcome above carried the full queue
                if (!known) send(toGuestsTopic, PartyMessage.Presence(participants))
            }
            is PartyMessage.Ready -> {
                if (!known) return
                guestLastSeen[from] = System.currentTimeMillis()
                if (msg.songId == waitingSongId) {
                    readyGuests.add(from)
                    if (readyGuests.containsAll(guestLastSeen.keys)) startTogether()
                }
            }
            is PartyMessage.Leave -> if (known) removeGuest(from)
            else -> {
                if (!known) return // requests only count from someone who joined properly
                guestLastSeen[from] = System.currentTimeMillis()
                handleGuestRequest(msg)
            }
        }
    }

    private fun removeGuest(id: String) {
        guestLastSeen.remove(id)
        readyGuests.remove(id)
        // Don't keep the room waiting on somebody who just left.
        if (waitingSongId != null && (guestLastSeen.isEmpty() || readyGuests.containsAll(guestLastSeen.keys))) startTogether()
        synchronized(participantsLock) { participants = participants.filter { it.id != id } }
        runCatching { send(toGuestsTopic, PartyMessage.Presence(participants)) }
    }

    private fun handleGuestRequest(msg: PartyMessage) {
        when (msg) {
            is PartyMessage.RequestPause -> PlayerManager.pausePlayback()
            is PartyMessage.RequestResume -> PlayerManager.resumePlayback()
            is PartyMessage.RequestNext -> PlayerManager.next()
            is PartyMessage.RequestPrevious -> PlayerManager.previous()
            is PartyMessage.RequestSeek -> PlayerManager.seekTo(msg.positionMs)
            is PartyMessage.RequestJump -> PlayerManager.jumpToIndex(msg.index)
            is PartyMessage.RequestQueueAdd -> PlayerManager.addToQueue(msg.song.toSongItem())
            is PartyMessage.RequestQueueRemove -> {
                val idx = PlayerManager.queue.indexOfFirst { it.id == msg.songId }
                if (idx >= 0) PlayerManager.removeFromQueue(idx)
            }
            is PartyMessage.RequestPlaySong -> PlayerManager.playSong(msg.song.toSongItem(), msg.queue.map { it.toSongItem() })
            else -> {}
        }
    }

    /** Host only: mirrors every local playback change (however it happened - the host's own
     * clicks, or a guest's request applied above, both end up here through the same
     * [PlayerManager] calls) out to every guest. */
    private fun installHostBroadcaster() {
        val listener = {
            val songId = PlayerManager.currentSong?.id
            val playing = PlayerManager.isPlaying
            val index = PlayerManager.currentIndex
            val queueSig = queueSignature()
            val now = System.currentTimeMillis()

            // PlayerManager notifies on every position tick while a song plays. Broadcasting all
            // of those would mean several messages per second through the relay (and the whole
            // queue with each one), which gets the connection throttled and makes guests' own
            // requests go missing. So only actual changes go out here; plain position drift is
            // handled by the much lighter heartbeat in startHostHeartbeat.
            val songOrPlayingChanged = songId != lastBroadcastSongId || playing != lastBroadcastPlaying
            val queueChanged = queueSig != lastQueueSignature
            val expectedPosition = lastBroadcastPositionMs + if (lastBroadcastPlaying) now - lastBroadcastAtMs else 0L
            val seeked = abs(PlayerManager.position - expectedPosition) > 1500

            // A new song just became ready to play here: hold it until everyone else has it
            // loaded too, so nobody ends up listening seconds ahead of the rest.
            // The moment a new song becomes playable *here* (it is announced earlier than that,
            // while still loading, hence tracking this separately from lastBroadcastSongId).
            val becamePlayableHere = songId != null && playing && !PlayerManager.isLoading &&
                songId != coordinatedSongId
            if (becamePlayableHere) coordinatedSongId = songId

            if (becamePlayableHere && guestLastSeen.isNotEmpty()) {
                startWaitingForGuests(songId!!)
            } else if (songOrPlayingChanged || queueChanged || index != lastBroadcastIndex || seeked) {
                lastBroadcastSongId = songId
                lastBroadcastPlaying = playing
                lastBroadcastIndex = index
                broadcastState(
                    startAtServerTimeMs = if (songOrPlayingChanged) now + 350 else null,
                    includeQueue = queueChanged,
                )
            }
        }
        playerListener = listener
        PlayerManager.addListener(listener)
    }

    /** While playing, sends the state every 2s for drift correction; otherwise a light ping, so
     * guests know the room is still there. Also drops guests that went quiet. */
    private fun startHostHeartbeat(mySession: Int) {
        heartbeatJob = scope.launch {
            var tick = 0L
            while (role == Role.HOST && session == mySession) {
                delay(1000)
                tick++
                if (guestLastSeen.isEmpty()) continue
                if (PlayerManager.isPlaying) {
                    if (tick % 2 == 0L) broadcastState(startAtServerTimeMs = null, includeQueue = queueSignature() != lastQueueSignature)
                } else if (tick % (PING_INTERVAL_MS / 1000) == 0L) {
                    runCatching { send(toGuestsTopic, PartyMessage.Ping) }
                }
                val now = System.currentTimeMillis()
                guestLastSeen.filterValues { now - it > PEER_TIMEOUT_MS }.keys.forEach { removeGuest(it) }
            }
        }
    }

    /**
     * Holds the song here until every guest says it has it loaded (or [READY_TIMEOUT_MS] passes),
     * then starts everyone at the same instant. Without this, whoever finished loading first
     * played alone while the others were still buffering, and their player looked paused for no
     * visible reason instead of simply loading.
     */
    private fun startWaitingForGuests(songId: String) {
        waitingSongId = songId
        readyGuests.clear()
        waitingForOthers = true
        lastBroadcastSongId = songId
        lastBroadcastPlaying = false
        lastBroadcastIndex = PlayerManager.currentIndex
        PlayerManager.applyRemoteState { PlayerManager.pausePlayback() }
        broadcastState(startAtServerTimeMs = null, includeQueue = queueSignature() != lastQueueSignature)
        readyTimeoutJob?.cancel()
        readyTimeoutJob = scope.launch {
            delay(READY_TIMEOUT_MS)
            // Somebody never reported back (slow connection, closed app) - start anyway.
            if (waitingSongId == songId) startTogether()
        }
    }

    private fun startTogether() {
        readyTimeoutJob?.cancel(); readyTimeoutJob = null
        waitingSongId = null
        readyGuests.clear()
        waitingForOthers = false
        PlayerManager.applyRemoteState { PlayerManager.resumePlayback() }
        lastBroadcastPlaying = true
        broadcastState(startAtServerTimeMs = System.currentTimeMillis() + 400, includeQueue = false)
    }

    /** [includeQueue] false sends everything except the song list, which guests then keep as-is -
     * the queue is by far the biggest part of this message and rarely changes. */
    private fun broadcastState(startAtServerTimeMs: Long?, includeQueue: Boolean) {
        if (role != Role.HOST || guestLastSeen.isEmpty()) return
        lastBroadcastPositionMs = PlayerManager.position
        lastBroadcastAtMs = System.currentTimeMillis()
        if (includeQueue) lastQueueSignature = queueSignature()
        runCatching {
            send(toGuestsTopic, PartyMessage.State(
                queue = if (includeQueue) PlayerManager.queue.map { it.toPartyDto() } else null,
                currentIndex = PlayerManager.currentIndex,
                positionMs = PlayerManager.position,
                isPlaying = PlayerManager.isPlaying,
                serverTimeMs = System.currentTimeMillis(),
                startAtServerTimeMs = startAtServerTimeMs,
                waitingForGuests = waitingForOthers,
            ))
        }
    }

    // ---------------------------------------------------------------------------------------
    // Guest side
    // ---------------------------------------------------------------------------------------

    private fun onHostMessage(msg: PartyMessage) {
        lastHostMessageAt = System.currentTimeMillis()
        when (msg) {
            is PartyMessage.Welcome -> pendingWelcome?.complete(msg)
            is PartyMessage.Presence -> if (role == Role.GUEST) participants = msg.participants
            is PartyMessage.State -> if (role == Role.GUEST) {
                waitingForOthers = msg.waitingForGuests
                scope.launch { applyIncomingState(msg) }
            }
            is PartyMessage.Leave -> if (role == Role.GUEST) {
                lastError = "El anfitrión cerró la sala"
                leaveRoom()
            }
            else -> {}
        }
    }

    private fun startGuestHeartbeat(mySession: Int) {
        heartbeatJob = scope.launch {
            while (role == Role.GUEST && session == mySession) {
                delay(PING_INTERVAL_MS)
                runCatching { send(toHostTopic, PartyMessage.Ping) }
                if (System.currentTimeMillis() - lastHostMessageAt > PEER_TIMEOUT_MS) {
                    lastError = "Se perdió la conexión con la sala"
                    leaveRoom()
                    connectionState = ConnectionState.FAILED
                    break
                }
            }
        }
    }

    /** Installed as [PlayerManager.transportInterceptor] while we're a guest: turns a local
     * button press into a request to the host instead of ever mutating our own queue/playback
     * directly - see the class doc above for why. */
    private fun onLocalGuestAction(action: TransportAction): Boolean {
        if (client == null || key == null) return false
        val message: PartyMessage = when (action) {
            TransportAction.Pause -> PartyMessage.RequestPause
            TransportAction.Resume -> PartyMessage.RequestResume
            TransportAction.Next -> PartyMessage.RequestNext
            TransportAction.Previous -> PartyMessage.RequestPrevious
            is TransportAction.Seek -> PartyMessage.RequestSeek(action.ms)
            is TransportAction.Jump -> PartyMessage.RequestJump(action.index)
            is TransportAction.AddToQueue -> PartyMessage.RequestQueueAdd(action.song.toPartyDto())
            is TransportAction.RemoveFromQueue -> {
                val song = PlayerManager.queue.getOrNull(action.index) ?: return true
                PartyMessage.RequestQueueRemove(song.id)
            }
            is TransportAction.PlaySong -> PartyMessage.RequestPlaySong(action.song.toPartyDto(), action.queueSongs.map { it.toPartyDto() })
        }
        // Best effort; the host's next state broadcast resyncs us either way.
        runCatching { send(toHostTopic, message) }
        return true
    }

    private suspend fun applyIncomingState(state: PartyMessage.State) {
        // A null queue means "unchanged" (see PartyMessage.State), so keep the one we have.
        val newQueue = state.queue?.map { it.toSongItem() } ?: PlayerManager.queue.toList()
        val target = newQueue.getOrNull(state.currentIndex)
        val songChanged = target != null && target.id != PlayerManager.currentSong?.id

        PlayerManager.applyRemoteState {
            val currentIds = PlayerManager.queue.map { it.id }
            if (newQueue.map { it.id } != currentIds && !songChanged) {
                PlayerManager.queue.clear()
                PlayerManager.queue.addAll(newQueue)
            }
            if (songChanged && target != null) {
                PlayerManager.playSong(target, newQueue)
            } else {
                if (state.isPlaying && !PlayerManager.isPlaying) PlayerManager.resumePlayback()
                if (!state.isPlaying && PlayerManager.isPlaying) PlayerManager.pausePlayback()
            }
        }
        if (songChanged) reportReadyWhenLoaded(target?.id)
        catchUpPosition(state.positionMs, state.isPlaying, state.serverTimeMs, state.startAtServerTimeMs)
    }

    /** Tells the host this guest has the song loaded, so it can start the room together. */
    private fun reportReadyWhenLoaded(songId: String?) {
        if (songId == null) return
        scope.launch {
            var waited = 0
            while (PlayerManager.isLoading && waited < 30_000) { delay(150); waited += 150 }
            if (role != Role.GUEST || PlayerManager.currentSong?.id != songId) return@launch
            runCatching { send(toHostTopic, PartyMessage.Ready(songId)) }
        }
    }

    /** Waits for a newly-started song to finish resolving its stream (if needed), then jumps
     * to wherever the room actually is right now - either "start together at this shared-clock
     * instant" ([startAt], for a fresh play/resume) or a plain drift correction otherwise. This
     * is intentionally approximate (no RTT compensation, ~1s correction threshold) - plenty for
     * people singing along together, not meant to be sample-accurate. */
    private suspend fun catchUpPosition(basePositionMs: Long, wasPlaying: Boolean, atServerTimeMs: Long, startAt: Long?) {
        if (startAt != null) {
            val localTarget = startAt - clockOffsetMs
            val wait = localTarget - System.currentTimeMillis()
            if (wait > 0) delay(wait)
        }
        var waited = 0
        while (PlayerManager.isLoading && waited < 15000) { delay(150); waited += 150 }
        val hostNow = System.currentTimeMillis() + clockOffsetMs
        val elapsed = if (wasPlaying) (hostNow - atServerTimeMs).coerceAtLeast(0) else 0
        val expected = (basePositionMs + elapsed).coerceAtLeast(0)
        if (abs(PlayerManager.position - expected) > 1500) {
            PlayerManager.applyRemoteState { PlayerManager.seekTo(expected) }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Sending, codes and keys
    // ---------------------------------------------------------------------------------------

    private fun queueSignature(): String =
        PlayerManager.queue.joinToString(",") { it.id }

    /**
     * Encrypts and hands the message to [sendExecutor] rather than publishing inline: callers
     * include the audio thread (through PlayerManager's listener) and the UI thread (a button
     * press as a guest), and a slow or stalled relay connection must never block either of those.
     * The single thread also keeps messages in the order they were produced.
     */
    private fun send(topic: String, message: PartyMessage) {
        val c = client ?: throw IllegalStateException("Sin conexión con la sala")
        val k = key ?: throw IllegalStateException("Sin conexión con la sala")
        val payload = PartyCrypto.encrypt(k, protocolJson.encodeToString(PartyEnvelope(from = myId, msg = message)).toByteArray(Charsets.UTF_8))
        sendExecutor.execute {
            // If publishing fails the client tears the connection down itself, which triggers the
            // reconnect in connectTo's onConnectionLost - nothing useful to do here.
            runCatching { c.publish(topic, payload) }
        }
    }

    private fun newCodeFor(target: PartyBroker): String {
        val sb = StringBuilder().append(target.tag)
        repeat(CODE_LENGTH - 1) { sb.append(CODE_ALPHABET[secureRandom.nextInt(CODE_ALPHABET.length)]) }
        return sb.toString()
    }

    private fun normalizeCode(input: String): String =
        input.uppercase().filter { it.isLetterOrDigit() }

    private fun formatCode(code: String): String =
        if (code.length == CODE_LENGTH) code.substring(0, 4) + "-" + code.substring(4) else code

    private fun topicBaseFor(code: String): String {
        val hash = MessageDigest.getInstance("SHA-256").digest("luma-party-topic:$code".toByteArray(Charsets.UTF_8))
        return "lumamusic/party/v1/" + hash.joinToString("") { "%02x".format(it) }.take(32)
    }

    private fun keyFor(code: String, pin: String): SecretKeySpec =
        PartyCrypto.deriveKey("luma-party-key:$code:$pin".toByteArray(Charsets.UTF_8))

    // ---------------------------------------------------------------------------------------
    // Cleanup
    // ---------------------------------------------------------------------------------------

    private fun cleanupHostResources() {
        heartbeatJob?.cancel(); heartbeatJob = null
        playerListener?.let { PlayerManager.removeListener(it) }; playerListener = null
        guestLastSeen.clear()
    }

    private fun cleanupGuestResources() {
        heartbeatJob?.cancel(); heartbeatJob = null
        PlayerManager.transportInterceptor = null
    }

    private fun reset() {
        role = Role.NONE
        connectionState = ConnectionState.IDLE
        participants = emptyList()
        roomCode = ""
        roomPin = ""
        waitingForOthers = false
        waitingSongId = null
        coordinatedSongId = null
        readyGuests.clear()
        readyTimeoutJob?.cancel(); readyTimeoutJob = null
        key = null
        myId = ""
        topicBase = ""
        broker = null
        lastBroadcastSongId = null
        lastBroadcastPlaying = false
        lastBroadcastIndex = -1
        lastQueueSignature = null
        lastBroadcastPositionMs = 0L
        lastBroadcastAtMs = 0L
    }
}
