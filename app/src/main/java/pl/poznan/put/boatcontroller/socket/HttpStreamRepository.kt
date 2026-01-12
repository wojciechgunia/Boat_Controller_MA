package pl.poznan.put.boatcontroller.socket

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

/**
 * Repository do zarządzania połączeniami HTTP stream.
 * CENTRALNY STAN - single source of truth dla widoczności tabów i aktywnych streamów.
 * 
 * Używa jednego serwisu dla wszystkich streamów - różnią się tylko configiem.
 */
object HttpStreamRepository {
    // Jeden serwis dla wszystkich streamów - zmienia config w zależności od aktywnego taba
    private var streamService: HttpStreamService? = null
    
    // CENTRALNY STAN - aktualny aktywny tab
    private var currentActiveTab: ControllerTab = ControllerTab.NONE
    
    // Zapamiętaj ostatni aktywny tab przed minimalizacją (dla przywrócenia po onResume)
    private var lastActiveTabBeforePause: ControllerTab = ControllerTab.NONE
    
    // Śledzenie WebView - faktyczne istnienie WebView i requesty
    // Używamy WeakReference aby uniknąć memory leak
    private var activeWebViewRef: WeakReference<WebView>? = null
    private var activeConfig: HttpStreamConfig? = null
    
    // Helper do pobierania WebView z WeakReference
    private val activeWebView: WebView?
        get() = activeWebViewRef?.get()
    private var requestCount = 0
    private var lastRequestTime = 0L
    
    // Jeden stan połączenia dla aktywnego streamu
    val connectionState = MutableSharedFlow<ConnectionState>(replay = 1)
    val errorMessage = MutableSharedFlow<String?>(replay = 1)
    
    // Flagi do śledzenia czy obserwujemy już stan
    private var isObservingConnection = false
    
    init {
        // Logowanie podsumowania co 2 sekundy
        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                delay(2000)
                logStreamStatus()
            }
        }
    }
    
    /**
     * Uruchamia wszystkie skonfigurowane streamy (serwisy sprawdzające dostępność).
     */
    fun startAll() {
        // Nie uruchamiamy serwisów z góry - uruchamiamy tylko gdy tab jest aktywny
        // To oszczędza zasoby i dane
    }
    
    /**
     * Zwraca URL dla danego taba.
     */
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
        
        // Jeśli tab się zmienił, zniszcz stare WebView
        if (previousTab != tab && previousTab != ControllerTab.NONE) {
            destroyWebView()
        }
        
        // Jeśli nowy tab wymaga streamu, uruchom serwis
        if (tab == ControllerTab.SONAR || tab == ControllerTab.CAMERA) {
            val config = HttpStreamConfigs.getConfigForTab(tab)
            if (config != null) {
                // Zatrzymaj poprzedni serwis jeśli był
                streamService?.stop()
                
                // Uruchom nowy serwis z odpowiednim configiem
                streamService = HttpStreamService(config)
                streamService?.startConnectionLoop()
                
                // Obserwuj stan połączenia
                observeConnectionState()
            }
        } else {
            // Tab nie wymaga streamu - zatrzymaj serwis i zniszcz WebView
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
    
    /**
     * Zwraca aktualny aktywny tab.
     */
    fun getActiveTab(): ControllerTab = currentActiveTab
    
    /**
     * Rejestruje WebView - wywoływane gdy WebView jest tworzony.
     */
    fun registerWebView(webView: WebView?, config: HttpStreamConfig?) {
        activeWebViewRef = webView?.let { WeakReference(it) }
        activeConfig = config
    }
    
    /**
     * Rejestruje request do streamu.
     */
    fun registerRequest() {
        requestCount++
        lastRequestTime = System.currentTimeMillis()
    }
    
    /**
     * Sprawdza czy stream jest aktywny - FAKTYCZNIE działa (WebView istnieje + były requesty w ostatnich 2s).
     */
    fun isStreamActive(): Boolean {
        val hasWebView = activeWebView != null
        val hasRecentRequests = (System.currentTimeMillis() - lastRequestTime) < 2000
        val isStreamTab = currentActiveTab == ControllerTab.SONAR || currentActiveTab == ControllerTab.CAMERA
        return isStreamTab && hasWebView && hasRecentRequests
    }
    
    /**
     * Zwraca nazwę aktywnego streamu (dla logowania).
     */
    fun getActiveStreamName(): String? {
        return activeConfig?.name
    }
    
    /**
     * Niszczy WebView - wywoływane gdy tab traci widoczność.
     */
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
            } catch (e: Exception) {
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
     * Wymusza ponowne połączenie - resetuje stan i wymusza reconnect.
     */
    fun forceReconnect(tab: ControllerTab) {
        destroyWebView()
        // Resetuj stan połączenia
        connectionState.tryEmit(ConnectionState.Connecting)
        errorMessage.tryEmit(null)
        setActiveTab(tab)
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
}
