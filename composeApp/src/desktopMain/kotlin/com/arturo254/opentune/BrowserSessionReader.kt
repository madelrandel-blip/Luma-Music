package com.arturo254.opentune

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.Crypt32Util
import com.sun.jna.platform.win32.WinReg
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.sql.DriverManager
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Lets the user sign in in their own browser instead of pasting cookies.
 *
 * The browser stores the session cookies in a SQLite database whose values are
 * AES-256-GCM encrypted with a key protected by Windows DPAPI. Windows does not
 * allow reading the database of a browser that is running (it is locked), so we
 * launch a temporary window of the user's default browser (Brave/Chrome/Edge)
 * with its own user-data dir; once the user signs in and we close that window,
 * its cookies can be read freely.
 *
 * As a fallback, existing browser profiles are also checked (useful when the
 * browser is closed and already has a YouTube session).
 */
object BrowserSessionReader {

    private const val TAG = "[BrowserSession]"
    private const val LOGIN_URL = "https://music.youtube.com"

    private var tempUserData: File? = null
    private var tempProcessPid: Long? = null

    /** Launches a temporary window of the user's default browser to sign in. */
    fun openLogin(): Result<Unit> = runCatching {
        cleanup()
        val exe = findBrowserExe() ?: throw IllegalStateException(tr("No se encontró un navegador compatible (Brave, Chrome o Edge)"))
        val dir = Files.createTempDirectory("opentune-login").toFile()
        tempUserData = dir
        val proc = ProcessBuilder(
            exe,
            "--user-data-dir=${dir.absolutePath}",
            "--no-first-run",
            "--no-default-browser-check",
            "--no-ms-welcome",
            LOGIN_URL
        ).redirectErrorStream(true).start()
        tempProcessPid = proc.pid()
    }

    /** Closes the temporary browser instance so its cookies can be read. */
    fun closeLoginWindow() {
        val pid = tempProcessPid ?: return
        tempProcessPid = null
        runCatching { Runtime.getRuntime().exec("taskkill /PID $pid /T /F") }
        runCatching { Thread.sleep(800) }
    }

    /** Closes the temporary browser and deletes its temporary profile (and any stale ones). */
    fun cleanup() {
        closeLoginWindow()
        val dir = tempUserData
        tempUserData = null
        val stale = (if (dir != null) listOf(dir) else emptyList()) + staleTempDirs()
        stale.distinct().forEach { runCatching { it.deleteRecursively() } }
    }

    private fun staleTempDirs(): List<File> = runCatching {
        val tmp = System.getProperty("java.io.tmpdir")
        File(tmp).listFiles { f -> f.isDirectory && f.name.startsWith("opentune-login") }?.toList() ?: emptyList()
    }.getOrDefault(emptyList())

    /**
     * Returns the full session cookie string for music.youtube.com, or throws.
     * Tries existing Chrome/Edge profiles first, then the temporary instance.
     */
    fun read(): Result<String> = runCatching {
        val localAppData = System.getenv("LOCALAPPDATA")
            ?: throw IllegalStateException(tr("LOCALAPPDATA no está definida"))
        val candidates = listOf(
            File(localAppData, "Google\\Chrome\\User Data"),
            File(localAppData, "Microsoft\\Edge\\User Data"),
        ) + listOfNotNull(tempUserData)
        var lastError: Exception? = null
        for (userData in candidates) {
            if (!userData.isDirectory) continue
            try {
                readFromUserData(userData)?.let { return@runCatching it }
            } catch (e: Exception) {
                lastError = e
                println("$TAG ${userData.name}: ${e.message}")
            }
        }
        throw lastError ?: IllegalStateException(tr("No se encontró una sesión de YouTube iniciada en Chrome o Edge"))
    }

    /** Reads the session from a specific profile directory (used by tests). */
    fun readFrom(userData: File): Result<String> = runCatching {
        readFromUserData(userData)
            ?: throw IllegalStateException(tr("No se encontró una sesión de YouTube iniciada en Chrome o Edge"))
    }

    /**
     * Finds the browser to use for the temporary sign-in window. Prefers the
     * user's default browser (registry ProgId), then falls back to any
     * installed Chromium-based browser.
     */
    private fun findBrowserExe(): String? {
        val progId = runCatching {
            Advapi32Util.registryGetStringValue(
                WinReg.HKEY_CURRENT_USER,
                "Software\\Microsoft\\Windows\\Shell\\Associations\\UrlAssociations\\http\\UserChoice",
                "ProgId"
            )
        }.getOrNull()
        val preferred = when {
            progId == null -> null
            progId.contains("Brave", ignoreCase = true) -> braveExe()
            progId.contains("Chrome", ignoreCase = true) -> chromeExe()
            progId.contains("Edge", ignoreCase = true) -> edgeExe()
            else -> null
        }
        return listOfNotNull(preferred, braveExe(), chromeExe(), edgeExe())
            .distinct()
            .firstOrNull { File(it).isFile }
    }

    private fun braveExe(): String? = exeIn(listOf(
        System.getenv("LOCALAPPDATA") + "\\BraveSoftware\\Brave-Browser\\Application\\brave.exe",
        System.getenv("ProgramFiles") + "\\BraveSoftware\\Brave-Browser\\Application\\brave.exe",
        System.getenv("ProgramFiles(x86)") + "\\BraveSoftware\\Brave-Browser\\Application\\brave.exe",
    ))

    private fun chromeExe(): String? = exeIn(listOf(
        System.getenv("ProgramFiles") + "\\Google\\Chrome\\Application\\chrome.exe",
        System.getenv("ProgramFiles(x86)") + "\\Google\\Chrome\\Application\\chrome.exe",
    ))

    private fun edgeExe(): String? = exeIn(listOf(
        System.getenv("ProgramFiles(x86)") + "\\Microsoft\\Edge\\Application\\msedge.exe",
        System.getenv("ProgramFiles") + "\\Microsoft\\Edge\\Application\\msedge.exe",
    ))

    private fun exeIn(paths: List<String>): String? = paths.firstOrNull { File(it).isFile }

    private fun readFromUserData(userData: File): String? {
        val aesKey = readAesKey(File(userData, "Local State"))
        for (profile in findProfiles(userData)) {
            val db = listOf(
                File(profile, "Network\\Cookies"),
                File(profile, "Cookies"),
            ).firstOrNull { it.isFile } ?: continue
            try {
                val cookie = readCookies(db, aesKey)
                if (cookie.contains("SAPISID")) return cookie
            } catch (e: Exception) {
                println("$TAG perfil ${profile.name}: ${e.message}")
            }
        }
        return null
    }

    private fun findProfiles(userData: File): List<File> {
        val profiles = mutableListOf(File(userData, "Default"))
        for (i in 1..20) {
            val p = File(userData, "Profile $i")
            if (p.isDirectory) profiles += p
        }
        return profiles
    }

    private fun readAesKey(localState: File): ByteArray {
        if (!localState.isFile) throw IllegalStateException(tr("No se encontró el archivo Local State"))
        val text = localState.readText(StandardCharsets.UTF_8)
        val keyField = "\"encrypted_key\":\""
        val start = text.indexOf(keyField)
        if (start < 0) throw IllegalStateException(tr("Local State no contiene encrypted_key"))
        val b64 = text.substring(start + keyField.length).substringBefore("\"")
        val encrypted = Base64.getDecoder().decode(b64)
        if (encrypted.size <= 5) throw IllegalStateException(tr("encrypted_key inválido"))
        val key = Crypt32Util.cryptUnprotectData(encrypted.copyOfRange(5, encrypted.size))
        return when {
            key.size == 32 -> key
            key.size >= 35 && key[0] == 'v'.code.toByte() && key[1] == '1'.code.toByte() && key[2] == '0'.code.toByte() ->
                key.copyOfRange(3, 35)
            else -> throw IllegalStateException(tr("Clave de cookies no reconocida"))
        }
    }

    private fun readCookies(db: File, aesKey: ByteArray): String {
        var lastError: Exception? = null
        repeat(4) { attempt ->
            try {
                return readCookiesOnce(db, aesKey)
            } catch (e: Exception) {
                lastError = e
                Thread.sleep(400L * (attempt + 1))
            }
        }
        throw lastError ?: IllegalStateException(tr("No se pudo leer la base de datos de cookies"))
    }

    private fun readCookiesOnce(db: File, aesKey: ByteArray): String {
        val tempDir = Files.createTempDirectory("opentune-cookies").toFile()
        try {
            listOf("", "-wal", "-shm").forEach { suffix ->
                val src = File(db.parentFile, db.name + suffix)
                if (src.isFile) {
                    Files.copy(
                        src.toPath(),
                        File(tempDir, db.name + suffix).toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                    )
                }
            }
            val cookies = mutableListOf<Triple<String, String, String>>()
            Class.forName("org.sqlite.JDBC")
            DriverManager.getConnection("jdbc:sqlite:" + File(tempDir, db.name).absolutePath).use { conn ->
                conn.createStatement().use { st ->
                    st.executeQuery(
                        "SELECT host_key, name, value FROM cookies " +
                            "WHERE host_key LIKE '%.youtube.com' OR host_key = 'youtube.com'"
                    ).use { rs ->
                        while (rs.next()) {
                            val host = rs.getString("host_key")
                            val name = rs.getString("name")
                            val value = decryptValue(rs.getBytes("value"), aesKey)
                            cookies += Triple(host, name, value)
                        }
                    }
                }
            }
            if (cookies.none { it.second == "SAPISID" }) {
                throw IllegalStateException(tr("La sesión no contiene SAPISID"))
            }
            return cookies.sortedWith(compareBy(
                { if (it.first.startsWith("music.youtube.com")) 0 else if (it.first.startsWith(".")) 1 else 2 },
                { it.second }
            )).joinToString("; ") { "${it.second}=${it.third}" }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun decryptValue(blob: ByteArray?, key: ByteArray): String {
        if (blob == null || blob.isEmpty()) return ""
        if (blob.size >= 3 && blob[0] == 'v'.code.toByte() && blob[1] == '1'.code.toByte() && blob[2] == '0'.code.toByte()) {
            val nonce = blob.copyOfRange(3, 15)
            val ciphertext = blob.copyOfRange(15, blob.size)
            return try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
                String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
            } catch (e: Exception) {
                ""
            }
        }
        return String(blob, StandardCharsets.UTF_8)
    }
}
