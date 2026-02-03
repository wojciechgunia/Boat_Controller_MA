package pl.poznan.put.boatcontroller.socket

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import android.webkit.WebView
import java.lang.ref.WeakReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import pl.poznan.put.boatcontroller.ConnectionState
import pl.poznan.put.boatcontroller.enums.ControllerTab
import androidx.core.graphics.createBitmap

/**
 * Repository do zarządzania połączeniami HTTP stream.
 * CENTRALNY STAN - single source of truth dla widoczności tabów i aktywnych streamów.
 * 
 * Używa jednego serwisu dla wszystkich streamów - różnią się tylko configiem.
 */
object HttpStreamRepository {
    private var streamService: HttpStreamService? = null
    private var currentActiveTab: ControllerTab = ControllerTab.NONE
    private var lastActiveTabBeforePause: ControllerTab = ControllerTab.NONE

    private var activeWebViewRef: WeakReference<WebView>? = null
    private var activeConfig: HttpStreamConfig? = null

    private val activeWebView: WebView?
        get() = activeWebViewRef?.get()
    private var requestCount = 0
    private var lastRequestTime = 0L

    val connectionState = MutableSharedFlow<ConnectionState>(replay = 1)
    val errorMessage = MutableSharedFlow<String?>(replay = 1)
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
                
                // Uruchom nowy serwis z odpowiednim configiem i callbackiem do pobierania aktywnego taba
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
        
        // Loguj zmianę tylko gdy się zmienił
        if (previousTab != tab) {
            Log.d("ControllerActivity", "Actual tab: ${tab.name}")
        }
    }

    fun getActiveTab(): ControllerTab = currentActiveTab

    fun registerWebView(webView: WebView?, config: HttpStreamConfig?) {
        activeWebViewRef = webView?.let { WeakReference(it) }
        activeConfig = config
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
        // Ustaw null natychmiast, aby uniknąć wielokrotnych wywołań
        activeWebViewRef = null
        activeConfig = null
        requestCount = 0
        
        webView?.let { view ->
            try {
                view.onPause()
                view.stopLoading()
                view.clearHistory()
                view.clearCache(true)
                view.loadUrl("about:blank")
                view.destroy()
            } catch (_: Exception) {
                // Ignoruj błędy przy niszczeniu - WebView może być już zniszczony
            }
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
        // Zapamiętaj ostatni aktywny tab przed zniszczeniem
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
        // Przywróć ostatni aktywny tab (jeśli był Sonar lub Camera)
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
        connectionState.tryEmit(ConnectionState.Reconnecting)
        errorMessage.tryEmit(null)
    }
    
    /**
     * Zmienia stan na Connected - używane gdy WebView pomyślnie załadował stream.
     */
    fun setConnected() {
        connectionState.tryEmit(ConnectionState.Connected)
        errorMessage.tryEmit(null)
    }
    
    /**
     * Wymusza ponowne połączenie - resetuje stan i wymusza reconnect.
     */
    fun forceReconnect(tab: ControllerTab) {
        destroyWebView()
        // Resetuj stan połączenia - rozpocznij próbę połączenia
        connectionState.tryEmit(ConnectionState.Reconnecting)
        errorMessage.tryEmit(null)
        // Resetuj czas ostatniego requestu
        lastRequestTime = 0L
        
        // WAŻNE: Zatrzymaj i ponownie utwórz serwis, aby zresetować jego stan i uniknąć cache'owanych połączeń
        streamService?.stop()
        resetObservation() // Resetuj obserwację przed utworzeniem nowego serwisu
        streamService = null
        
        // Ustaw aktywny tab PRZED utworzeniem serwisu (tylko jeśli się zmienił)
        if (currentActiveTab != tab) {
            currentActiveTab = tab
        }
        
        // Upewnij się że tab jest aktywny przed utworzeniem serwisu
        if (tab == ControllerTab.SONAR || tab == ControllerTab.CAMERA) {
            val config = HttpStreamConfigs.getConfigForTab(tab)
            if (config != null) {
                streamService = HttpStreamService(config) { 
                    // Callback do pobierania aktywnego taba
                    currentActiveTab
                }
                streamService?.startConnectionLoop()
                observeConnectionState()
            }
        }
    }
    
    /**
     * Obserwuje stan połączenia z serwisu i przekazuje do publicznego flow.
     * Wywoływane tylko raz dla każdego serwisu.
     */
    private fun observeConnectionState() {
        if (isObservingConnection) return // Już obserwujemy
        
        isObservingConnection = true
        CoroutineScope(Dispatchers.IO).launch {
            streamService?.connectionState?.collectLatest { state ->
                connectionState.emit(state)
            }
        }
        CoroutineScope(Dispatchers.IO).launch {
            streamService?.errorMessage?.collectLatest { error ->
                errorMessage.emit(error)
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
     * @return Bitmapa z WebView lub null jeśli WebView nie istnieje lub nie jest widoczne
     */
    fun captureWebViewBitmap(): Bitmap? {
        val webView = activeWebView ?: return null
        
        return try {
            if (webView.width <= 0 || webView.height <= 0) {
                Log.w("HttpStreamRepository", "WebView has invalid size: ${webView.width}x${webView.height}")
                return null
            }

            val bitmap = createBitmap(webView.width, webView.height)
            val canvas = Canvas(bitmap)

            webView.draw(canvas)
            
            Log.d("HttpStreamRepository", "✅ Captured bitmap: ${bitmap.width}x${bitmap.height}")
            bitmap
        } catch (e: Exception) {
            Log.e("HttpStreamRepository", "❌ Error capturing bitmap from WebView", e)
            null
        }
    }
}
