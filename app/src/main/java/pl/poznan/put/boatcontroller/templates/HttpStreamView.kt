package pl.poznan.put.boatcontroller.templates

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import pl.poznan.put.boatcontroller.ConnectionState
import pl.poznan.put.boatcontroller.enums.ControllerTab
import pl.poznan.put.boatcontroller.socket.HttpStreamConfig

/**
 * Reużywalny komponent do wyświetlania strumieni HTTP w WebView.
 * WebView jest tworzony lokalnie, ale stan połączenia jest zarządzany przez HttpStreamRepository.
 * 
 * @param streamUrl URL do załadowania
 * @param connectionState Stan połączenia (uspójniony z ConnectionStatusIndicator)
 * @param errorMessage Komunikat błędu (null gdy brak błędu)
 * @param isTabVisible Czy zakładka jest obecnie widoczna
 * @param label Etykieta wyświetlana w komunikatach (np. "kamera", "sonar")
 * @param config Konfiguracja streamu (używana do weryfikacji i rejestracji)
 * @param onShowErrorChange Callback do przekazania stanu showError do ConnectionStatusIndicator
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HttpStreamView(
    streamUrl: String?,
    connectionState: ConnectionState,
    errorMessage: String?,
    isTabVisible: Boolean,
    label: String = "urządzenie",
    config: HttpStreamConfig? = null,
    onShowErrorChange: ((Boolean) -> Unit)? = null
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    // Współdzielony stan showError - używany zarówno lokalnie jak i w callbacku
    val showErrorState = remember { mutableStateOf(false) }
    val showError = showErrorState.value
    var connectionStartTime by remember { mutableStateOf<Long?>(null) }
    
    // Zapamiętaj aktualne callbacki - używane w WebView callbacks
    val currentOnShowErrorChange = rememberUpdatedState(onShowErrorChange)
    
    // Funkcja do ustawiania showError - aktualizuje zarówno lokalny stan jak i callback
    val setShowErrorState: (Boolean) -> Unit = remember {
        { error ->
            showErrorState.value = error
            currentOnShowErrorChange.value?.invoke(error)
        }
    }
    
    // Pobierz stan z centralnego repozytorium - sprawdź czy tab z configu jest aktywny
    val activeTab = pl.poznan.put.boatcontroller.socket.HttpStreamRepository.getActiveTab()
    // Uproszczona logika - sprawdzamy tylko czy config tab jest aktywny i czy tab jest widoczny
    val isTabActive = config?.tab == activeTab
    val shouldShowWebView = isTabActive && isTabVisible
    
    // Uspójniony stan wyświetlania - używany zarówno dla Box jak i komunikatu
    // Ten sam stan co w ConnectionStatusIndicator
    // WAŻNE: Gdy showError == true, zawsze pokazuj "Brak połączenia" (nie czekaj na connectionState.Error)
    val displayState = when {
        connectionState == ConnectionState.Connected -> "Połączono"
        showError -> "Brak połączenia" // Natychmiast pokaż błąd gdy showError == true
        else -> "Łączenie..."
    }
    
    // Resetuj timeout gdy tab staje się aktywny lub gdy wymuszamy reconnect
    LaunchedEffect(isTabActive, isTabVisible) {
        if (isTabActive && isTabVisible) {
            connectionStartTime = System.currentTimeMillis()
            setShowErrorState(false)
        } else {
            connectionStartTime = null
            setShowErrorState(false)
        }
    }
    
    // Licznik odświeżeń - używany do wymuszenia ponownego utworzenia WebView
    var refreshCounter by remember { mutableIntStateOf(0) }
    
    // Timeout 5 sekund - pokaż błąd jeśli nie ma połączenia po 5 sekundach
    // WAŻNE: NIE dodajemy connectionState do dependencies, bo gdy forceReconnect zmienia stan,
    // LaunchedEffect się restartuje i przerywa delay. Sprawdzamy connectionState PO delay.
    // refreshCounter jest w dependencies, aby wymusić restart timeout przy odświeżaniu.
    LaunchedEffect(connectionStartTime, isTabActive, isTabVisible, refreshCounter) {
        if (isTabActive && isTabVisible && connectionStartTime != null) {
            // Czekaj 5 sekund - ten delay nie zostanie przerwany przez zmiany connectionState
            kotlinx.coroutines.delay(5000)
            // Sprawdź czy połączenie się udało (sprawdzamy aktualny connectionState po delay)
            if (connectionState != ConnectionState.Connected) {
                setShowErrorState(true)
            } else {
                setShowErrorState(false)
            }
        }
    }
    
    // Resetuj błąd gdy połączenie się uda
    LaunchedEffect(connectionState) {
        if (connectionState == ConnectionState.Connected) {
            setShowErrorState(false)
        }
    }
    
    // Funkcja do odświeżania połączenia - używa tej samej logiki co zmiana taba
    val onRefresh = {
        val tab = config?.tab
        if (tab != null && (tab == ControllerTab.SONAR || tab == ControllerTab.CAMERA)) {
            // Resetuj stany - ta sama logika co przy zmianie taba
            setShowErrorState(false)
            connectionStartTime = System.currentTimeMillis()
            refreshCounter++ // Zwiększ licznik aby wymusić ponowne utworzenie WebView
            
            // Wywołaj forceReconnect - to zniszczy stare WebView w repozytorium i utworzy nowe
            // Automatyczna logika timeout 5 sekund zadziała tak samo jak przy zmianie taba
            pl.poznan.put.boatcontroller.socket.HttpStreamRepository.forceReconnect(tab)
        }
    }

    // Funkcja pomocnicza do bezpiecznego niszczenia WebView
    val safeDestroyWebView: (WebView?) -> Unit = { webView ->
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
    
    // KLUCZOWE: DisposableEffect z kluczem shouldShowWebView - niszczy WebView gdy tab traci widoczność
    DisposableEffect(shouldShowWebView, streamUrl) {
        // Gdy tab traci widoczność, natychmiast niszczymy WebView i wyrejestrowujemy
        if (!shouldShowWebView && webViewRef != null) {
            val currentWebView = webViewRef
            webViewRef = null // Ustaw null natychmiast, aby uniknąć wielokrotnych wywołań
            safeDestroyWebView(currentWebView)
            // Wyrejestruj WebView w repozytorium
            pl.poznan.put.boatcontroller.socket.HttpStreamRepository.registerWebView(null, null)
        }
        
        onDispose {
            // Ostateczne czyszczenie - używamy bezpiecznej funkcji
            // WAŻNE: Ustaw webViewRef na null PRZED wywołaniem safeDestroyWebView,
            // aby uniknąć wielokrotnych wywołań gdy DisposableEffect jest wywoływany wielokrotnie
            val currentWebView = webViewRef
            webViewRef = null // Ustaw null natychmiast
            if (currentWebView != null) {
                safeDestroyWebView(currentWebView)
            }
            // Wyrejestruj WebView w repozytorium
            pl.poznan.put.boatcontroller.socket.HttpStreamRepository.registerWebView(null, null)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds() // Ważne: obetnij zawartość do granic Box
    ) {
        // KLUCZOWE: WebView jest tworzony TYLKO gdy:
        // 1. Tab jest faktycznie aktywny (shouldShowWebView == true)
        // 2. Mamy URL streamu
        // 3. Stan połączenia to Connected (TAK SAMO jak przy zmianie taba)
        // 4. Nie ma błędu połączenia
        // To gwarantuje zero pobierania danych gdy użytkownik nie jest w odpowiednim tabie
        // i że WebView jest wyświetlany TYLKO gdy faktycznie jest połączenie
        if (!shouldShowWebView) {
            // Tab nie jest aktywny - NIE tworzymy WebView = zero pobierania danych
            // Wyświetl informację że stream jest zatrzymany
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Stream $label zatrzymany (tab nieaktywny)",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }
        } else if (streamUrl != null && connectionState == ConnectionState.Connected && errorMessage == null && !showError) {
            // KLUCZOWE: key() z shouldShowWebView i refreshCounter wymusza całkowite usunięcie AndroidView gdy tab traci widoczność lub przy odświeżaniu
            key("$label-$shouldShowWebView-$streamUrl-$refreshCounter") {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                        webViewRef = this
                        
                        // Zarejestruj WebView w repozytorium z configiem
                        pl.poznan.put.boatcontroller.socket.HttpStreamRepository.registerWebView(this, config)

                        // Ustaw layoutParams z WRAP_CONTENT, aby WebView nie próbował się rozciągać poza granice
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        // Ważne: clipuj WebView do granic parenta
                        clipToOutline = true
                        // Upewnij się, że WebView nie próbuje się rozciągnąć poza granice
                        overScrollMode = android.view.View.OVER_SCROLL_NEVER
                        
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            loadWithOverviewMode = false
                            useWideViewPort = false
                            builtInZoomControls = false
                            displayZoomControls = false
                            setSupportZoom(true)
                            cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                            defaultTextEncodingName = "UTF-8"
                            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            standardFontFamily = "sans-serif"
                            // Zapobiega automatycznemu dopasowaniu szerokości
                            layoutAlgorithm = android.webkit.WebSettings.LayoutAlgorithm.NORMAL
                        }
                        
                        // Naprawa requestedFrameRate: NaN - ustaw rendererPriorityPolicy
                        try {
                            setRendererPriorityPolicy(
                                android.webkit.WebView.RENDERER_PRIORITY_IMPORTANT,
                                false
                            )
                        } catch (e: Exception) {
                            // Ignoruj jeśli metoda nie jest dostępna
                        }
                        
                        setBackgroundColor(0x00000000)

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                                return false
                            }

                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: android.webkit.WebResourceRequest?
                            ): android.webkit.WebResourceResponse? {
                                val url = request?.url?.toString() ?: return null

                                val isStreamUrl = streamUrl.let { url.contains(it, ignoreCase = true) } == true
                                if (isStreamUrl) {
                                    // Sprawdź czy tab z configu jest faktycznie widoczny
                                    val activeTab = pl.poznan.put.boatcontroller.socket.HttpStreamRepository.getActiveTab()
                                    val shouldBeVisible = config.tab == activeTab
                                    
                                    if (!shouldBeVisible) {
                                        // Blokuj request jeśli tab nie jest widoczny (zabezpieczenie)
                                        return android.webkit.WebResourceResponse(
                                            "text/plain",
                                            "utf-8",
                                            java.io.ByteArrayInputStream(ByteArray(0))
                                        )
                                    }
                                    
                                    // Zarejestruj request w repozytorium
                                    pl.poznan.put.boatcontroller.socket.HttpStreamRepository.registerRequest()
                                    
                                    return super.shouldInterceptRequest(view, request)
                                }

                                if (url.contains("ads", ignoreCase = true) ||
                                    url.contains("doubleclick", ignoreCase = true) ||
                                    url.contains("analytics", ignoreCase = true) ||
                                    url.contains("google-analytics", ignoreCase = true) ||
                                    url.contains("googletagmanager", ignoreCase = true)
                                ) {
                                    // Zwróć pustą odpowiedź zamiast pobierać dane
                                    return android.webkit.WebResourceResponse(
                                        "text/plain",
                                        "utf-8",
                                        java.io.ByteArrayInputStream(ByteArray(0))
                                    )
                                }

                                return super.shouldInterceptRequest(view, request)
                            }
                            
                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                            }
                            
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                            }
                            
                            override fun onReceivedError(
                                view: WebView?,
                                request: android.webkit.WebResourceRequest?,
                                error: android.webkit.WebResourceError?
                            ) {
                                super.onReceivedError(view, request, error)
                                
                                // WAŻNE: NIE ustawiamy showError natychmiast tutaj!
                                // Pozwalamy timeout 5 sekund zadziałać tak samo jak przy zmianie taba.
                                // onReceivedError może być wywoływane od razu po odświeżeniu, zanim minie 5 sekund,
                                // co powodowało natychmiastowe pokazanie błędu. Teraz czekamy na timeout.
                                
                                // Błędy są obsługiwane przez timeout 5 sekund w LaunchedEffect,
                                // który sprawdza connectionState po 5 sekundach.
                            }
                        }

                        webChromeClient = WebChromeClient()

                        post {
                            loadUrl(streamUrl)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds(),
                update = { view ->
                    // Sprawdź czy tab z configu jest faktycznie widoczny
                    val activeTab = pl.poznan.put.boatcontroller.socket.HttpStreamRepository.getActiveTab()
                    val shouldBeVisible = config.tab == activeTab
                    
                    if (!shouldBeVisible) {
                        // Tab nie jest widoczny - zatrzymaj WebView
                        view.onPause()
                        view.stopLoading()
                        view.clearHistory()
                        view.loadUrl("about:blank")
                        return@AndroidView
                    }
                    
                    // Tab jest widoczny - upewniamy się że stream jest załadowany
                    view.onResume()
                    if (view.url.isNullOrEmpty() || view.url != streamUrl) {
                        view.loadUrl(streamUrl)
                    }
                }
                )
            }
        } else if (showError) {
            // Wyświetl komunikat błędu z przyciskiem Refresh (po 5 sekundach timeout)
            // Uspójniony komunikat: "Brak połączenia"
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(16.dp)
                    ) {
                        Text(
                            text = errorMessage ?: "Błąd połączenia z $label",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                    
                    // Przycisk Refresh z ikoną strzałek w kółku
                    Button(
                        onClick = onRefresh,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Odśwież",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Odśwież",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        } else if (streamUrl == null) {
            // Brak URL - pokaż informację
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Nie udało się przechwycić danych z $label",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp
                )
            }
        } else {
            // Uspójniony komunikat - używamy displayState który jest zsynchronizowany z ConnectionStatusIndicator
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = displayState,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp
                )
            }
        }
    }
}
