package com.arturo254.opentune.party

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * One free, public MQTT broker Party Mode can meet through. No account or API key is needed for
 * any of these. Every payload sent through them is already AES-GCM encrypted with a key derived
 * from the room code + PIN (see [PartyCrypto]), so the broker only ever relays opaque bytes - it
 * can't read or forge anything in the room.
 *
 * [tag] is the first character of every room code, so a guest knows which broker the host picked
 * without typing anything extra. Tags must never be reused for a different broker.
 */
data class PartyBroker(val tag: Char, val host: String, val port: Int, val tls: Boolean)

object PartyBrokers {
    val all = listOf(
        PartyBroker('E', "broker.emqx.io", 8883, tls = true),
        PartyBroker('H', "broker.hivemq.com", 8883, tls = true),
        PartyBroker('M', "test.mosquitto.org", 1883, tls = false),
        PartyBroker('Q', "broker.emqx.io", 1883, tls = false),
    )

    fun byTag(tag: Char): PartyBroker? = all.firstOrNull { it.tag == tag.uppercaseChar() }
}

/**
 * A deliberately tiny MQTT 3.1.1 client: CONNECT, SUBSCRIBE, PUBLISH (QoS 0 only), keep-alive
 * pings and DISCONNECT, which is everything Party Mode needs. Written by hand instead of pulling
 * in a library, so there's no new Gradle dependency.
 *
 * Both the host and every guest open an *outgoing* connection to the same broker, which is why
 * nobody has to open or forward anything in their router: routers always allow outgoing
 * connections.
 */
class MiniMqttClient(
    private val broker: PartyBroker,
    private val clientId: String,
    private val onMessage: (topic: String, payload: ByteArray) -> Unit,
    private val onConnectionLost: () -> Unit,
) {
    private var socket: Socket? = null
    private var output: OutputStream? = null
    private var readerThread: Thread? = null
    private var pingThread: Thread? = null
    private val writeLock = Any()
    @Volatile private var closedByUs = false
    private var nextPacketId = 1

    val isConnected: Boolean get() = socket?.isClosed == false && !closedByUs

    /** Connects and waits for the broker's CONNACK. Throws on any failure (unreachable, blocked
     * port, TLS problem, broker refusal). */
    fun connect(timeoutMs: Int = CONNECT_TIMEOUT_MS) {
        closedByUs = false
        val raw = Socket()
        try {
            raw.connect(InetSocketAddress(broker.host, broker.port), timeoutMs)
            raw.soTimeout = timeoutMs
            raw.tcpNoDelay = true
            val s: Socket = if (broker.tls) {
                val ssl = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                    .createSocket(raw, broker.host, broker.port, true) as SSLSocket
                ssl.soTimeout = timeoutMs
                ssl.startHandshake()
                ssl
            } else raw
            val out = s.getOutputStream()
            val input = s.getInputStream()

            // CONNECT
            val body = ByteArrayBuilder()
            body.string("MQTT")
            body.byte(4)          // protocol level 3.1.1
            body.byte(0x02)       // clean session, no will, no credentials
            body.short(KEEP_ALIVE_SEC)
            body.string(clientId)
            writePacket(out, 0x10, body.toByteArray())

            // CONNACK
            val header = readByte(input)
            val len = readRemainingLength(input)
            val ack = readExactly(input, len)
            if ((header and 0xF0) != 0x20 || ack.size < 2 || ack[1].toInt() != 0) {
                throw IllegalStateException("El servidor de la sala rechazó la conexión")
            }

            s.soTimeout = 0
            socket = s
            output = out
            readerThread = Thread({ readLoop(input) }, "party-mqtt-reader").apply { isDaemon = true; start() }
            pingThread = Thread({ pingLoop() }, "party-mqtt-ping").apply { isDaemon = true; start() }
        } catch (e: Exception) {
            runCatching { raw.close() }
            throw e
        }
    }

    fun subscribe(topic: String) {
        val body = ByteArrayBuilder()
        val id = synchronized(writeLock) { nextPacketId.also { nextPacketId = if (it >= 65535) 1 else it + 1 } }
        body.short(id)
        body.string(topic)
        body.byte(0) // QoS 0
        send(0x82, body.toByteArray())
    }

    fun publish(topic: String, payload: ByteArray) {
        val body = ByteArrayBuilder()
        body.string(topic)
        body.bytes(payload)
        send(0x30, body.toByteArray())
    }

    fun close() {
        closedByUs = true
        runCatching { send(0xE0, ByteArray(0)) }
        runCatching { socket?.close() }
        pingThread?.interrupt()
        socket = null
        output = null
    }

    private fun send(header: Int, body: ByteArray) {
        val out = output ?: throw IllegalStateException("Sin conexión con la sala")
        writePacket(out, header, body)
    }

    private fun writePacket(out: OutputStream, header: Int, body: ByteArray) {
        val packet = ByteArrayBuilder()
        packet.byte(header)
        var remaining = body.size
        do {
            var digit = remaining % 128
            remaining /= 128
            if (remaining > 0) digit = digit or 0x80
            packet.byte(digit)
        } while (remaining > 0)
        packet.bytes(body)
        synchronized(writeLock) {
            out.write(packet.toByteArray())
            out.flush()
        }
    }

    private fun readLoop(input: InputStream) {
        try {
            while (!closedByUs) {
                val header = readByte(input)
                val len = readRemainingLength(input)
                if (len > MAX_PACKET_BYTES) throw IllegalStateException("packet too large")
                val body = readExactly(input, len)
                if ((header and 0xF0) == 0x30) {
                    val qos = (header shr 1) and 0x03
                    if (body.size < 2) continue
                    val topicLen = ((body[0].toInt() and 0xFF) shl 8) or (body[1].toInt() and 0xFF)
                    var offset = 2 + topicLen
                    if (offset > body.size) continue
                    val topic = String(body, 2, topicLen, Charsets.UTF_8)
                    if (qos > 0) offset += 2
                    if (offset > body.size) continue
                    val payload = body.copyOfRange(offset, body.size)
                    runCatching { onMessage(topic, payload) }
                }
                // SUBACK / PINGRESP / anything else: nothing to do.
            }
        } catch (_: Exception) {
        } finally {
            runCatching { socket?.close() }
            pingThread?.interrupt()
            if (!closedByUs) onConnectionLost()
        }
    }

    private fun pingLoop() {
        try {
            while (!closedByUs) {
                Thread.sleep(KEEP_ALIVE_SEC * 1000L / 2)
                if (closedByUs) break
                send(0xC0, ByteArray(0))
            }
        } catch (_: InterruptedException) {
        } catch (_: Exception) {
            runCatching { socket?.close() } // makes readLoop notice and report the loss
        }
    }

    private fun readByte(input: InputStream): Int {
        val b = input.read()
        if (b < 0) throw EOFException()
        return b
    }

    private fun readRemainingLength(input: InputStream): Int {
        var multiplier = 1
        var value = 0
        var count = 0
        while (true) {
            val digit = readByte(input)
            value += (digit and 0x7F) * multiplier
            if ((digit and 0x80) == 0) break
            multiplier *= 128
            if (++count >= 4) throw IllegalStateException("bad remaining length")
        }
        return value
    }

    private fun readExactly(input: InputStream, len: Int): ByteArray {
        val buf = ByteArray(len)
        var off = 0
        while (off < len) {
            val r = input.read(buf, off, len - off)
            if (r < 0) throw EOFException()
            off += r
        }
        return buf
    }

    private class ByteArrayBuilder {
        private val out = java.io.ByteArrayOutputStream()
        fun byte(v: Int) = out.write(v and 0xFF)
        fun short(v: Int) { out.write((v shr 8) and 0xFF); out.write(v and 0xFF) }
        fun bytes(b: ByteArray) = out.write(b)
        fun string(s: String) { val b = s.toByteArray(Charsets.UTF_8); short(b.size); bytes(b) }
        fun toByteArray(): ByteArray = out.toByteArray()
    }

    companion object {
        const val CONNECT_TIMEOUT_MS = 7000
        const val KEEP_ALIVE_SEC = 30
        const val MAX_PACKET_BYTES = 2 * 1024 * 1024
    }
}
