package com.arturo254.opentune

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.friwi.jcefmaven.CefAppBuilder
import me.friwi.jcefmaven.EnumProgress
import me.friwi.jcefmaven.IProgressHandler
import org.cef.CefApp
import org.cef.CefClient
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefContextMenuParams
import org.cef.callback.CefCookieVisitor
import org.cef.callback.CefMenuModel
import org.cef.handler.CefContextMenuHandler
import org.cef.misc.BoolRef
import org.cef.network.CefCookie
import org.cef.network.CefCookieManager
import java.awt.Component
import java.awt.event.MouseWheelEvent
import java.io.File
import java.lang.reflect.Method
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * In-app login: embeds a Chromium browser (JCEF) inside the application so the
 * user can sign in without leaving the app or pasting cookies. On the first use
 * the natives (~100 MB) are downloaded to ~/.opentune/jcef-bundle.
 *
 * The session cookies live in CEF's in-memory cookie store, so once the user
 * signs in we can read them through [CefCookieManager] (same SAPISID-based
 * auth that [AccountManager] expects, no files, no locks, no DPAPI).
 */
object EmbeddedBrowserLogin {

    private const val TAG = "[EmbeddedBrowser]"
    private const val LOGIN_URL = "https://music.youtube.com"

    /**
     * JCEF's OSR layer maps one AWT wheel rotation unit to roughly 0.8 px, so a
     * normal wheel notch (rotation ±3) moves barely anything and feels "stiff".
     * We scale the rotation up (and flip its sign, which matches the direction
     * reported as inverted) before it reaches the native browser.
     */
    private const val WHEEL_SCALE = 40

    private val sendMouseWheelEvent: Method by lazy {
        Class.forName("org.cef.browser.CefBrowser_N")
            .getDeclaredMethod("sendMouseWheelEvent", MouseWheelEvent::class.java)
            .apply { isAccessible = true }
    }

    @Volatile
    private var app: CefApp? = null

    private val installDir: File
        get() = File(System.getProperty("user.home"), ".opentune/jcef-bundle")

    fun isInitialized(): Boolean = app != null

    /**
     * Builds the shared CefApp. Safe to call many times (idempotent). Downloads
     * and extracts the natives on first run. Must be called from a background
     * coroutine; [onProgress] may be invoked from the installer thread.
     */
    suspend fun ensureInitialized(onProgress: (String) -> Unit): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (app != null) return@runCatching
                val builder = CefAppBuilder()
                builder.setInstallDir(installDir)
                builder.setProgressHandler(object : IProgressHandler {
                    override fun handleProgress(state: EnumProgress, percent: Float) {
                        onProgress(when (state) {
                            EnumProgress.LOCATING -> tr("Preparando el navegador integrado...")
                            EnumProgress.DOWNLOADING -> tr("Descargando el navegador integrado (primera vez, puede tardar)...")
                            EnumProgress.EXTRACTING, EnumProgress.INSTALL -> tr("Extrayendo el navegador integrado...")
                            EnumProgress.INITIALIZING -> tr("Iniciando el navegador integrado...")
                            EnumProgress.INITIALIZED -> tr("Listo")
                        })
                    }
                })
                builder.addJcefArgs("--disable-gpu")
                builder.getCefSettings().windowless_rendering_enabled = true
                val cef = builder.build()
                if (cef == null) {
                    throw IllegalStateException(tr("No se pudo iniciar el navegador integrado."))
                }
                app = cef
            }
        }

    /**
     * Creates an off-screen browser that loads the YouTube Music sign-in page.
     * Must be called on the AWT event thread. Returns the browser and the
     * [Component] to embed in the UI.
     */
    fun createBrowser(): Pair<CefBrowser, Component> {
        val cef = app ?: throw IllegalStateException(tr("El navegador integrado no está inicializado"))
        val client: CefClient = cef.createClient()

        // Right-clicking opens CEF's native context menu, which in OSR mode is
        // rendered as a popup inside the GL frame and can crash the process.
        // Clearing the menu in onBeforeContextMenu prevents the popup entirely.
        client.addContextMenuHandler(object : CefContextMenuHandler {
            override fun onBeforeContextMenu(
                browser: CefBrowser,
                frame: CefFrame,
                params: CefContextMenuParams,
                model: CefMenuModel
            ) {
                model.clear()
            }

            override fun onContextMenuCommand(
                browser: CefBrowser,
                frame: CefFrame,
                params: CefContextMenuParams,
                commandId: Int,
                eventFlags: Int
            ) = false

            override fun onContextMenuDismissed(browser: CefBrowser, frame: CefFrame) = Unit
        })

        val browser = client.createBrowser(LOGIN_URL, true, false)
        val component = browser.getUIComponent()
        installScrollFix(browser, component)
        return browser to component
    }

    /**
     * Replaces the browser canvas' wheel handling. JCEF's default mapping is
     * far too slow and the direction was reported as inverted, so we intercept
     * the raw wheel event, build a corrected synthetic one (inverted + scaled)
     * and forward it to the native browser through reflection.
     */
    private fun installScrollFix(browser: CefBrowser, component: Component) {
        component.mouseWheelListeners.forEach { component.removeMouseWheelListener(it) }
        component.addMouseWheelListener { e ->
            val corrected = MouseWheelEvent(
                e.component,
                MouseWheelEvent.MOUSE_WHEEL,
                e.`when`,
                e.modifiersEx,
                e.x,
                e.y,
                e.xOnScreen,
                e.yOnScreen,
                e.clickCount,
                e.isPopupTrigger,
                e.scrollType,
                e.scrollAmount,
                -e.wheelRotation * WHEEL_SCALE,
                -e.preciseWheelRotation * WHEEL_SCALE
            )
            runCatching { sendMouseWheelEvent.invoke(browser, corrected) }
        }
    }

    /** Closes the browser and its client. Must be called on the AWT event thread. */
    fun disposeBrowser(browser: CefBrowser?) {
        if (browser == null) return
        runCatching { browser.close(true) }
        runCatching { browser.client.dispose() }
    }

    /**
     * Reads the session cookies of the embedded browser as a single cookie
     * string (SAPISID + SID/HSID/SSID/APISID, ...), or fails if no authenticated
     * session is present.
     */
    suspend fun readCookies(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val manager = CefCookieManager.getGlobalManager()
            val collected = Collections.synchronizedList(mutableListOf<Pair<String, String>>())
            val done = CountDownLatch(1)
            val ok = manager.visitUrlCookies(
                LOGIN_URL,
                true,
                object : CefCookieVisitor {
                    override fun visit(cookie: CefCookie, count: Int, total: Int, delete: BoolRef): Boolean {
                        val domain = cookie.domain ?: ""
                        if (domain.contains("youtube.com", ignoreCase = true) &&
                            cookie.name.isNotBlank() && cookie.value.isNotBlank()
                        ) {
                            collected += cookie.name to cookie.value
                        }
                        if (total > 0 && count >= total - 1) {
                            done.countDown()
                            return false
                        }
                        return true
                    }
                }
            )
            if (!ok) {
                throw IllegalStateException(tr("No se pudo acceder a las cookies del navegador integrado"))
            }
            if (!done.await(8, TimeUnit.SECONDS)) {
                println("$TAG timeout esperando cookies")
            }
            if (collected.none { it.first == "SAPISID" }) {
                throw IllegalStateException(
                    tr("No hay una sesión de YouTube iniciada en la ventana. Inicia sesión e inténtalo de nuevo.")
                )
            }
            collected.joinToString("; ") { "${it.first}=${it.second}" }
        }
    }
}
