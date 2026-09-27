package com.arturo254.opentune.party

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Symmetric encryption for Party Mode. Messages travel through a free public MQTT broker (see
 * [PartyBrokers]), which is treated as untrusted: anyone could be listening on it. What keeps
 * the room private is the room code + PIN, shared with friends out of band (chat, WhatsApp) and
 * never sent through the broker. Every message is AES-GCM encrypted with a key derived from them,
 * so without both, messages can't be read, and anything forged fails to authenticate and is
 * ignored - see [PartyModeManager].
 */
object PartyCrypto {
    private const val GCM_IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private val secureRandom = SecureRandom()

    fun newSecret(): ByteArray = ByteArray(32).also { secureRandom.nextBytes(it) }

    /**
     * Derives a fixed-size AES-256 key from the room secret. The secret already has full
     * 256-bit entropy (freshly random, not a user-chosen password), so a plain hash is enough -
     * there's no low-entropy input here that needs stretching against brute-forcing.
     */
    fun deriveKey(secret: ByteArray): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256").digest(secret)
        return SecretKeySpec(digest, "AES")
    }

    fun encrypt(key: SecretKeySpec, plaintext: ByteArray): ByteArray {
        val iv = ByteArray(GCM_IV_BYTES).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext)
        return iv + ciphertext
    }

    /**
     * Returns null (instead of throwing) on any failure - wrong key, tampered/corrupted bytes,
     * or plain garbage from something that isn't a real Luma Music peer at all - so the caller
     * can drop the connection uniformly without ever branching on *why* it failed.
     */
    fun decrypt(key: SecretKeySpec, data: ByteArray): ByteArray? {
        return try {
            if (data.size <= GCM_IV_BYTES) return null
            val iv = data.copyOfRange(0, GCM_IV_BYTES)
            val ciphertext = data.copyOfRange(GCM_IV_BYTES, data.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(ciphertext)
        } catch (_: Exception) {
            null
        }
    }

    fun bytesToBase64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    fun base64UrlToBytes(s: String): ByteArray? = try { Base64.getUrlDecoder().decode(s) } catch (_: Exception) { null }
}
