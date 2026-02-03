package pl.poznan.put.boatcontroller.backend.remote.http

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import android.view.View
import android.webkit.WebView
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import pl.poznan.put.boatcontroller.domain.enums.ConnectionState
import pl.poznan.put.boatcontroller.domain.enums.ControllerTab
import java.lang.ref.WeakReference

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
     * Przechwytuje bitmapę z aktywnego WebView.
     * Używa bezpośredniego rysowania na Canvas (z wymuszeniem LAYER_TYPE_SOFTWARE),
     * co pozwala na przechwycenie zawartości bez elementów nakładkowych interfejsu.
     *
     * @return Bitmapa z WebView lub null jeśli WebView nie istnieje lub wystąpił błąd
     */
    fun captureWebViewBitmap(): Bitmap? {
        val webView = activeWebView ?: return null

        if (webView.width <= 0 || webView.height <= 0) {
            Log.w("HttpStreamRepository", "WebView has invalid size: ${webView.width}x${webView.height}")
            return null
        }

        return try {
            val bitmap = createBitmap(webView.width, webView.height)
            val canvas = Canvas(bitmap)

            val originalLayerType = webView.layerType

            try {
                webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)

                val bg = webView.background
                if (bg != null) {
                    bg.draw(canvas)
                } else {
                    canvas.drawColor(Color.WHITE)
                }

                webView.draw(canvas)
            } finally {
                webView.setLayerType(originalLayerType, null)
            }

            Log.d("HttpStreamRepository", "Captured bitmap using direct draw: ${bitmap.width}x${bitmap.height}")
            bitmap
        } catch (e: Exception) {
            Log.e("HttpStreamRepository", "Error capturing bitmap from WebView", e)
            null
        }
    }
}