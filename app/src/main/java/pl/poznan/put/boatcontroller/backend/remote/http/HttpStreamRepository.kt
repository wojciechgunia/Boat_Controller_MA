package pl.poznan.put.boatcontroller.backend.remote.http

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import android.view.View
import android.view.Window
import android.webkit.WebView
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import pl.poznan.put.boatcontroller.domain.enums.ConnectionState
import pl.poznan.put.boatcontroller.domain.enums.ControllerTab
import java.lang.ref.WeakReference
import kotlin.coroutines.resume

/**
 * Repository do zarządzania połączeniami HTTP stream.
 */
object HttpStreamRepository {
    private var streamService: HttpStreamService? = null
    private var currentActiveTab: ControllerTab = ControllerTab.NONE
    private var lastActiveTabBeforePause: ControllerTab = ControllerTab.NONE

    private var activeWebViewRef: WeakReference<WebView>? = null
    private var activeConfig: HttpStreamConfig? = null

    private val _isWebViewReady = MutableStateFlow(false)
    val isWebViewReady = _isWebViewReady.asStateFlow()

    private val activeWebView: WebView?
        get() = activeWebViewRef?.get()
    private var requestCount = 0
    private var lastRequestTime = 0L

    private val _connectionState = MutableStateFlow(ConnectionState.Disconnected)
    val connectionState = _connectionState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _isSnapshotCapturing = MutableStateFlow(false)
    val isSnapshotCapturing = _isSnapshotCapturing.asStateFlow()

    private var isObservingConnection = false

//    init {
//        CoroutineScope(Dispatchers.IO).launch {
//            while (true) {
//                delay(2000)
//                logStreamStatus()
//            }
//        }
//    }

    fun getUrlForTab(tab: ControllerTab): String? {
        val config = HttpStreamConfigs.getConfigForTab(tab)
        return config?.getUrl()
    }

    /**
     * Ustawia aktywny tab - CENTRALNY STAN.
     *
     * @param tab Tab który ma być aktywny (ControllerTab.NONE = żaden aktywny)
     */
    fun setActiveTab(tab: ControllerTab) {
        val previousTab = currentActiveTab
        currentActiveTab = tab

        if (previousTab != tab && previousTab != ControllerTab.NONE) {
            destroyWebView()
        }

        if (tab == ControllerTab.SONAR || tab == ControllerTab.CAMERA) {
            val config = HttpStreamConfigs.getConfigForTab(tab)
            if (config != null) {
                streamService?.stop()
                resetObservation()

                streamService = HttpStreamService(config) {
                    currentActiveTab
                }
                streamService?.startConnectionLoop()
                observeConnectionState()
            }
        } else {
            streamService?.stop()
            streamService = null
            resetObservation()
            destroyWebView()
        }

        if (previousTab != tab) {
            Log.d("ControllerActivity", "Actual tab: ${tab.name}")
        }
    }

    fun getActiveTab(): ControllerTab = currentActiveTab

    fun registerWebView(webView: WebView?, config: HttpStreamConfig?) {
        activeWebViewRef = webView?.let { WeakReference(it) }
        activeConfig = config
        _isWebViewReady.value = webView != null
    }

    /**
     * Bezpieczne wyrejestrowanie WebView.
     * Czyści referencję TYLKO jeśli przekazany webView to ten aktualnie aktywny.
     * Zapobiega sytuacji, gdzie stary WebView (przy zmianie taba) czyści referencję do nowego.
     */
    fun unregisterWebView(webView: WebView?) {
        val current = activeWebViewRef?.get()
        if (current == webView || webView == null) {
            activeWebViewRef = null
            activeConfig = null
            _isWebViewReady.value = false
        } else {
            Log.d("HttpStreamRepository", "Ignoring unregister for inactive WebView")
        }
    }

    fun registerRequest() {
        requestCount++
        lastRequestTime = System.currentTimeMillis()
    }

    fun isStreamActive(): Boolean {
        val hasWebView = activeWebView != null
        val hasRecentRequests = (System.currentTimeMillis() - lastRequestTime) < 2000
        val isStreamTab = currentActiveTab == ControllerTab.SONAR || currentActiveTab == ControllerTab.CAMERA
        return isStreamTab && hasWebView && hasRecentRequests
    }

    fun getActiveStreamName(): String? {
        return activeConfig?.name
    }

    fun destroyWebView() {
        val webView = activeWebView
        activeWebViewRef = null
        activeConfig = null
        requestCount = 0
        _isWebViewReady.value = false

        webView?.let { view ->
            try {
                view.onPause()
                view.stopLoading()
                view.clearHistory()
                view.clearCache(true)
                view.loadUrl("about:blank")
                view.destroy()
            } catch (_: Exception) {}
        }
    }

    /**
     * Loguje podsumowanie stanu streamów co 2 sekundy.
     */
    private fun logStreamStatus() {
        val isActive = isStreamActive()
        val streamName = getActiveStreamName() ?: "none"
        val tabName = currentActiveTab.name

        val status = buildString {
            append("Stream status: ")
            append("tab=$tabName, ")
            append("stream=$streamName, ")
            append("active=$isActive")
            if (isActive) {
                append(" (requests: $requestCount)")
            }
        }

        Log.d("HttpStreamRepository", status)
    }

    /**
     * Wywoływane gdy aplikacja jest minimalizowana - niszczy wszystkie WebView.
     */
    fun onAppPaused() {
        lastActiveTabBeforePause = currentActiveTab
        destroyWebView()
        streamService?.stop()
        streamService = null
        resetObservation()
        setActiveTab(ControllerTab.NONE)
    }

    /**
     * Wywoływane gdy aplikacja wraca na ekran - przywraca ostatni aktywny tab.
     */
    fun onAppResumed(): ControllerTab? {
        val tabToRestore = lastActiveTabBeforePause
        if (tabToRestore == ControllerTab.SONAR || tabToRestore == ControllerTab.CAMERA) {
            setActiveTab(tabToRestore)
            return tabToRestore
        }
        return null
    }

    /**
     * Zmienia stan na Reconnecting - używane gdy wykrywamy utratę połączenia.
     */
    fun setReconnecting() {
        _connectionState.value = ConnectionState.Reconnecting
        _errorMessage.value = null
    }

    /**
     * Zmienia stan na Connected - używane gdy WebView pomyślnie załadował stream.
     */
    fun setConnected() {
        _connectionState.value = ConnectionState.Connected
        _errorMessage.value = null
    }

    /**
     * Wymusza ponowne połączenie - resetuje stan i wymusza reconnect.
     */
    fun forceReconnect(tab: ControllerTab) {
        destroyWebView()
        _connectionState.value = ConnectionState.Reconnecting
        _errorMessage.value = null
        lastRequestTime = 0L

        streamService?.stop()
        resetObservation()
        streamService = null

        if (currentActiveTab != tab) {
            currentActiveTab = tab
        }

        if (tab == ControllerTab.SONAR || tab == ControllerTab.CAMERA) {
            val config = HttpStreamConfigs.getConfigForTab(tab)
            if (config != null) {
                streamService = HttpStreamService(config) {
                    currentActiveTab
                }
                streamService?.startConnectionLoop()
                observeConnectionState()
            }
        }
    }

    /**
     * Obserwuje stan połączenia z serwisu i przekazuje do publicznego flow
     */
    private fun observeConnectionState() {
        if (isObservingConnection) return

        isObservingConnection = true
        CoroutineScope(Dispatchers.IO).launch {
            streamService?.connectionState?.collectLatest { state ->
                _connectionState.value = state
            }
        }
        CoroutineScope(Dispatchers.IO).launch {
            streamService?.errorMessage?.collectLatest { error ->
                _errorMessage.value = error
            }
        }
    }

    /**
     * Resetuje flagę obserwacji - wywoływane gdy serwis jest zatrzymywany.
     */
    private fun resetObservation() {
        isObservingConnection = false
    }

    /**
     * Przechwytuje bitmapę z aktywnego WebView używając PixelCopy.
     * Pozwala to na przechwycenie zawartości renderowanej sprzętowo (Canvas/WebGL),
     * co nie jest możliwe przy użyciu zwykłego draw().
     *
     * Funkcja jest suspendowana i ustawia flagę isSnapshotCapturing,
     * co pozwala UI na ukrycie elementów nakładkowych przed wykonaniem zrzutu.
     *
     * @param window Okno aplikacji (potrzebne do PixelCopy)
     * @return Bitmapa z WebView lub null jeśli wystąpił błąd
     */
    suspend fun captureSnapshot(window: Window): Bitmap? {
        val webView = activeWebView ?: return null

        if (webView.width <= 0 || webView.height <= 0) {
            Log.w("HttpStreamRepository", "WebView has invalid size: ${webView.width}x${webView.height}")
            return null
        }

        _isSnapshotCapturing.value = true
        // Daj czas UI na ukrycie elementów (Compose potrzebuje klatki na przerysowanie)
        delay(100)

        return try {
            suspendCancellableCoroutine { continuation ->
                try {
                    val bitmap = Bitmap.createBitmap(webView.width, webView.height, Bitmap.Config.ARGB_8888)
                    val location = IntArray(2)
                    webView.getLocationInWindow(location)

                    // Uwzględnij pozycję WebView w oknie
                    val rect = Rect(
                        location[0],
                        location[1],
                        location[0] + webView.width,
                        location[1] + webView.height
                    )

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        PixelCopy.request(
                            window,
                            rect,
                            bitmap,
                            { result ->
                                if (result == PixelCopy.SUCCESS) {
                                    continuation.resume(bitmap)
                                } else {
                                    Log.e("HttpStreamRepository", "PixelCopy failed: $result")
                                    continuation.resume(null)
                                }
                            },
                            Handler(Looper.getMainLooper())
                        )
                    } else {
                        // Fallback dla starszych Androidów (API < 26)
                        Log.w("HttpStreamRepository", "PixelCopy not supported (API < 26), using fallback draw()")
                        val canvas = Canvas(bitmap)
                        val originalLayerType = webView.layerType
                        try {
                            // Próba wymuszenia software renderowania (może nie zadziałać dla Canvas/WebGL)
                            webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                            webView.draw(canvas)
                        } finally {
                            webView.setLayerType(originalLayerType, null)
                        }
                        continuation.resume(bitmap)
                    }
                } catch (e: Exception) {
                    Log.e("HttpStreamRepository", "Error capturing snapshot", e)
                    continuation.resume(null)
                }
            }
        } finally {
            _isSnapshotCapturing.value = false
        }
    }
}