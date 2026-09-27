package com.arturo254.opentune

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.imageio.ImageIO

@Serializable
private data class GuestProfileData(val name: String = "", val avatarFileName: String = "")

/**
 * A local "guest" identity - just a name and a picture, with no YouTube account behind it -
 * for people who want to show up as themselves in things like Party Mode without linking a
 * real account. Independent of [AccountManager]: both can exist at once, and whichever one is
 * used to represent the user in a given feature is that feature's own choice (see
 * [DesktopPreferences.partyIdentity]), not something this object decides.
 */
object GuestProfileManager {
    private val dir = File(System.getProperty("user.home"), ".opentune")
    private val file = File(dir, "guest_profile.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    var name by mutableStateOf(""); private set
    var avatarFile by mutableStateOf<File?>(null); private set

    val exists: Boolean get() = name.isNotBlank()

    init { load() }

    /** Saves the guest name and, if provided, copies [sourceImage] into our own data folder
     * (so the profile survives even if the original file is later moved/deleted) resized down
     * to a small square - this same file is what gets base64-embedded in a Party Mode handshake,
     * so keeping it small keeps that message small too. */
    fun save(newName: String, sourceImage: File?) {
        name = newName.trim()
        if (sourceImage != null && sourceImage.exists()) {
            try {
                val image = ImageIO.read(sourceImage)
                if (image != null) {
                    dir.mkdirs()
                    val target = File(dir, "guest_avatar.jpg")
                    val size = 256
                    val scaled = java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_RGB)
                    val g = scaled.createGraphics()
                    try {
                        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                        // Center-crop to a square first so non-square photos aren't squashed.
                        val srcSize = minOf(image.width, image.height)
                        val srcX = (image.width - srcSize) / 2
                        val srcY = (image.height - srcSize) / 2
                        g.drawImage(image, 0, 0, size, size, srcX, srcY, srcX + srcSize, srcY + srcSize, null)
                    } finally {
                        g.dispose()
                    }
                    ImageIO.write(scaled, "jpg", target)
                    avatarFile = target
                }
            } catch (e: Exception) {
                println("[GuestProfileManager] Avatar copy error: ${e.message}")
            }
        }
        persist()
    }

    fun clear() {
        name = ""
        avatarFile?.delete()
        avatarFile = null
        file.delete()
    }

    private fun persist() {
        try {
            dir.mkdirs()
            file.writeText(json.encodeToString(GuestProfileData(name, avatarFile?.name.orEmpty())))
        } catch (e: Exception) {
            println("[GuestProfileManager] Save error: ${e.message}")
        }
    }

    private fun load() {
        try {
            if (file.exists() && file.length() > 0) {
                val data = json.decodeFromString<GuestProfileData>(file.readText())
                name = data.name
                if (data.avatarFileName.isNotBlank()) {
                    val f = File(dir, data.avatarFileName)
                    if (f.exists()) avatarFile = f
                }
            }
        } catch (e: Exception) {
            println("[GuestProfileManager] Load error: ${e.message}")
        }
    }
}
