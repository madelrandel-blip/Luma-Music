package com.arturo254.opentune

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.arturo254.opentune.innertube.PlaybackAuthState
import com.arturo254.opentune.innertube.YouTube
import com.arturo254.opentune.innertube.models.AccountInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class AccountData(
    val cookie: String = "",
    val visitorData: String = "",
    val dataSyncId: String = "",
    val poToken: String = "",
    val poTokenGvs: String = "",
    val poTokenPlayer: String = "",
    val webClientPoTokenEnabled: Boolean = false,
)

/**
 * Links the app to a YouTube/YouTube Music account using the cookies of a
 * logged-in session (same approach as OpenTune: there is no OAuth client in
 * the innertube layer). The cookie must contain SAPISID for an authenticated
 * session. Everything is persisted in ~/.opentune/account.json.
 */
object AccountManager {
    private val file = File(System.getProperty("user.home"), ".opentune/account.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private var data = AccountData()

    var accountInfo by mutableStateOf<AccountInfo?>(null)
        private set
    var loading by mutableStateOf(false)
        private set

    val isLinked: Boolean get() = data.cookie.isNotBlank()

    init {
        load()
        applyToYouTube()
    }

    /** Triggers the object initializer (load + apply persisted auth) at startup. */
    fun initialize() {}

    /**
     * Sets the cookies, makes sure a visitor data is available and fetches the
     * account info to confirm the session works. Returns the account info.
     */
    suspend fun link(cookie: String): Result<AccountInfo> = withContext(Dispatchers.IO) {
        loading = true
        try {
            runCatching {
                val trimmed = cookie.trim()
                if (trimmed.isEmpty() || !trimmed.contains("SAPISID")) {
                    throw IllegalStateException(tr("La cookie debe contener SAPISID"))
                }
                data = data.copy(cookie = trimmed)
                applyToYouTube()

                if (YouTube.visitorData.isNullOrBlank()) {
                    YouTube.visitorData().getOrNull()?.let { vd ->
                        data = data.copy(visitorData = vd)
                        applyToYouTube()
                    }
                }

                val info = YouTube.accountInfo().getOrThrow()
                data = data.copy(visitorData = YouTube.visitorData ?: data.visitorData)
                save()
                accountInfo = info
                info
            }
        } finally {
            loading = false
        }
    }

    fun unlink() {
        data = AccountData()
        accountInfo = null
        applyToYouTube()
        save()
    }

    private fun applyToYouTube() {
        YouTube.authState = PlaybackAuthState(
            cookie = data.cookie.ifBlank { null },
            visitorData = data.visitorData.ifBlank { null },
            dataSyncId = data.dataSyncId.ifBlank { null },
            poToken = data.poToken.ifBlank { null },
            poTokenGvs = data.poTokenGvs.ifBlank { null },
            poTokenPlayer = data.poTokenPlayer.ifBlank { null },
            webClientPoTokenEnabled = data.webClientPoTokenEnabled,
        )
    }

    private fun save() {
        try {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(data))
        } catch (e: Exception) {
            println("[AccountManager] Save error: ${e.message}")
        }
    }

    private fun load() {
        try {
            if (file.exists() && file.length() > 0) {
                data = json.decodeFromString<AccountData>(file.readText())
            }
        } catch (e: Exception) {
            println("[AccountManager] Load error: ${e.message}")
        }
    }
}
