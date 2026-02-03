package pl.poznan.put.boatcontroller.domain.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import pl.poznan.put.boatcontroller.domain.enums.ConnectionState
import pl.poznan.put.boatcontroller.domain.enums.ControllerTab
import pl.poznan.put.boatcontroller.backend.remote.http.HttpStreamConfig
import pl.poznan.put.boatcontroller.backend.remote.http.HttpStreamRepository
import java.io.ByteArrayInputStream
import kotlin.math.sqrt

/**
 * Reużywalny komponent do wyświetlania strumieni HTTP w WebView.
 * WebView jest tworzony lokalnie, ale stan połączenia jest zarządzany przez HttpStreamRepository.
 * 
 * @param streamUrl URL do załadowania
 * @param connectionState Stan połączenia (uspójniony z ConnectionStatusIndicator)
 * @param errorMessage Komunikat błędu (null gdy brak błędu)
 * @param label Etykieta wyświetlana w komunikatach (np. "kamera", "sonar")
 * @param config Konfiguracja streamu (używana do weryfikacji i rejestracji)
 * @param onTap Callback wywoływany gdy użytkownik kliknie w WebView (opcjonalny)
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HttpStreamView(
    streamUrl: String?,
    connectionState: ConnectionState,
    errorMessage: String?,
    label: String = "device",
    config: HttpStreamConfig? = null,
    onTap: (() -> Unit)? = null
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    
    // Sprawdź czy tab z configu jest aktywny - wywołujemy bezpośrednio zamiast tworzyć zmienną lokalną
    val isTabActive = config?.tab == HttpStreamRepository.getActiveTab()
    
    // Uspójniony stan wyświetlania - używany zarówno dla Box jak i komunikatu
    // Ten sam stan co w ConnectionStatusIndicator
    val displayState = when (connectionState) {
        ConnectionState.Connected -> "Connected"
        ConnectionState.Reconnecting -> "Reconnecting..."
        ConnectionState.Disconnected -> "Disconnected"
    }
    
    // Nagłówki HTTP używane do wymuszenia zamknięcia połączenia i wyłączenia cache
    // Używane w wielu miejscach - wyciągnięte do stałej aby uniknąć duplikacji
    val httpHeaders = remember {
        mapOf(
            "Connection" to "close",
            "Cache-Control" to "no-cache, no-store, must-revalidate",
            "Pragma" to "no-cache",
            "Expires" to "0"
        )
    }
    
    // Funkcja do odświeżania połączenia
    val onRefresh = {
        val tab = config?.tab
        if (tab != null && (tab == ControllerTab.SONAR || tab == ControllerTab.CAMERA)) {
            // Wywołaj forceReconnect - to zniszczy stare WebView w repozytorium i utworzy nowe
            HttpStreamRepository.forceReconnect(tab)
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
            } catch (_: Exception) {
                // Ignoruj błędy przy niszczeniu - WebView może być już zniszczony
            }
        }
    }
    
    // KLUCZOWE: DisposableEffect z kluczem isTabActive - niszczy WebView gdy tab traci aktywność
    DisposableEffect(isTabActive, streamUrl) {
        // Gdy tab traci aktywność, natychmiast niszczymy WebView i wyrejestrowujemy
        if (!isTabActive && webViewRef != null) {
            val currentWebView = webViewRef
            webViewRef = null // Ustaw null natychmiast, aby uniknąć wielokrotnych wywołań
            safeDestroyWebView(currentWebView)
            // Wyrejestruj WebView w repozytorium (używając bezpiecznej metody)
            HttpStreamRepository.unregisterWebView(currentWebView)
        }
        
        onDispose {
            // Ostateczne czyszczenie - używamy bezpiecznej funkcji
            // WAŻNE: Ustaw webViewRef na null PRZED wywołaniem safeDestroyWebView,
            // aby uniknąć wielokrotnych wywołań gdy DisposableEffect jest wywoływany wielokrotnie
            val currentWebView = webViewRef
            webViewRef = null // Ustaw null natychmiast
            if (currentWebView != null) {
                safeDestroyWebView(currentWebView)
                // Wyrejestruj WebView w repozytorium (używając bezpiecznej metody)
                HttpStreamRepository.unregisterWebView(currentWebView)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds() // Ważne: obetnij zawartość do granic Box
    ) {
        if (!isTabActive) {
            // Tab nie jest aktywny - NIE tworzymy WebView = zero pobierania danych
            // Wyświetl informację że stream jest zatrzymany
            return
        } else if (streamUrl != null && connectionState == ConnectionState.Connected) {
            // KLUCZOWE: key() z connectionState wymusza całkowite usunięcie AndroidView 
            // gdy połączenie się zmienia (isTabActive jest zawsze true w tym miejscu, więc nie jest potrzebne)
            key("$label-$streamUrl-$connectionState") {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                        webViewRef = this
                        
                        // Zarejestruj WebView w repozytorium z configiem
                        HttpStreamRepository.registerWebView(this, config)

                        // Ustaw layoutParams z WRAP_CONTENT, aby WebView nie próbował się rozciągać poza granice
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        // Ważne: clipuj WebView do granic parenta
                        clipToOutline = true
                        // Upewnij się, że WebView nie próbuje się rozciągnąć poza granice
                        overScrollMode = View.OVER_SCROLL_NEVER
                        
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            loadWithOverviewMode = false
                            useWideViewPort = false
                            builtInZoomControls = false
                            displayZoomControls = false
                            setSupportZoom(true)
                            cacheMode = WebSettings.LOAD_NO_CACHE
                            defaultTextEncodingName = "UTF-8"
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            standardFontFamily = "sans-serif"
                            // Zapobiega automatycznemu dopasowaniu szerokości
                            layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
                        }
                        
                        // Naprawa requestedFrameRate: NaN - ustaw rendererPriorityPolicy
                        try {
                            setRendererPriorityPolicy(
                                WebView.RENDERER_PRIORITY_IMPORTANT,
                                false
                            )
                        } catch (_: Exception) {
                            // Ignoruj jeśli metoda nie jest dostępna
                        }
                        
                        setBackgroundColor(0x00000000)
                        
                        // Obsługa kliknięć w WebView - wywołuje callback onTap gdy użytkownik kliknie
                        // w WebView (ale nie w interaktywne elementy jak przyciski)
                        if (onTap != null) {
                            var touchDownTime = 0L
                            var touchDownX = 0f
                            var touchDownY = 0f
                            
                            setOnTouchListener { _, event ->
                                when (event.action) {
                                    MotionEvent.ACTION_DOWN -> {
                                        touchDownTime = System.currentTimeMillis()
                                        touchDownX = event.x
                                        touchDownY = event.y
                                        false // Przekaż event dalej do WebView
                                    }
                                    MotionEvent.ACTION_UP -> {
                                        val touchDuration = System.currentTimeMillis() - touchDownTime
                                        val touchDistance = sqrt(
                                            (event.x - touchDownX) * (event.x - touchDownX) + 
                                            (event.y - touchDownY) * (event.y - touchDownY)
                                        )
                                        
                                        // Jeśli to był krótki tap (mniej niż 200ms) i bez dużego ruchu (mniej niż 10px)
                                        // oraz nie trafił w interaktywny element, wywołaj callback
                                        if (touchDuration < 200 && touchDistance < 10f) {
                                            // Sprawdź czy kliknięcie trafiło w interaktywny element używając JavaScript
                                            evaluateJavascript(
                                                """
                                                (function() {
                                                    var element = document.elementFromPoint(${event.x}, ${event.y});
                                                    if (element) {
                                                        var tag = element.tagName.toLowerCase();
                                                        var isInteractive = tag === 'button' || 
                                                                           tag === 'a' || 
                                                                           tag === 'input' || 
                                                                           tag === 'select' || 
                                                                           tag === 'textarea' ||
                                                                           element.onclick !== null ||
                                                                           element.style.cursor === 'pointer';
                                                        return isInteractive ? 'interactive' : 'non-interactive';
                                                    }
                                                    return 'non-interactive';
                                                })();
                                                """.trimIndent()
                                            ) { result ->
                                                // Jeśli kliknięcie nie trafiło w interaktywny element, wywołaj callback
                                                if (result != null && result.contains("non-interactive")) {
                                                    onTap()
                                                }
                                            }
                                        }
                                        false // Przekaż event dalej do WebView
                                    }
                                    else -> false // Przekaż event dalej do WebView
                                }
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                return false
                            }

                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                val url = request?.url?.toString() ?: return null

                                val isStreamUrl = url.contains(streamUrl, ignoreCase = true)
                                if (isStreamUrl) {
                                    // Sprawdź czy tab z configu jest faktycznie widoczny - wywołujemy bezpośrednio
                                    val shouldBeVisible = config.tab == HttpStreamRepository.getActiveTab()
                                    
                                    if (!shouldBeVisible) {
                                        // Blokuj request jeśli tab nie jest widoczny (zabezpieczenie)
                                        return WebResourceResponse(
                                            "text/plain",
                                            "utf-8",
                                            ByteArrayInputStream(ByteArray(0))
                                        )
                                    }
                                    
                                    // Zarejestruj request w repozytorium
                                    HttpStreamRepository.registerRequest()
                                    
                                    return super.shouldInterceptRequest(view, request)
                                }

                                if (url.contains("ads", ignoreCase = true) ||
                                    url.contains("doubleclick", ignoreCase = true) ||
                                    url.contains("analytics", ignoreCase = true) ||
                                    url.contains("google-analytics", ignoreCase = true) ||
                                    url.contains("googletagmanager", ignoreCase = true)
                                ) {
                                    // Zwróć pustą odpowiedź zamiast pobierać dane
                                    return WebResourceResponse(
                                        "text/plain",
                                        "utf-8",
                                        ByteArrayInputStream(ByteArray(0))
                                    )
                                }

                                return super.shouldInterceptRequest(view, request)
                            }
                            
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                
                                // Gdy strona zaczyna się ładować, sprawdź czy to stream URL
                                val startedUrl = url ?: ""
                                val isStreamUrl = startedUrl.contains(streamUrl, ignoreCase = true)
                                
                                if (isStreamUrl) {
                                    // Stream zaczyna się ładować - ustaw stan na Reconnecting (jeśli nie jest już Connected)
                                    // To pozwala na szybkie wykrycie rozpoczęcia ładowania
                                    val currentState = HttpStreamRepository.connectionState.replayCache.lastOrNull()
                                    if (currentState == ConnectionState.Disconnected) {
                                        HttpStreamRepository.setReconnecting()
                                    }
                                }
                            }
                            
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                
                                // Jeśli strona się załadowała pomyślnie i to jest stream URL, ustaw stan na Connected
                                val finishedUrl = url ?: ""
                                val isStreamUrl = finishedUrl.contains(streamUrl, ignoreCase = true)
                                
                                if (isStreamUrl && view != null) {
                                    // Strona streamu załadowała się pomyślnie - ustaw stan na Connected
                                    // Używamy małego opóźnienia aby upewnić się że WebView faktycznie załadował content
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        val currentState = HttpStreamRepository.connectionState.replayCache.lastOrNull()
                                        if (currentState != ConnectionState.Connected) {
                                            // Tylko jeśli nie byliśmy już połączeni, zmień stan
                                            HttpStreamRepository.setConnected()
                                        }
                                    }, 500) // 500ms opóźnienie aby upewnić się że stream się załadował
                                }
                            }
                            
                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                super.onReceivedError(view, request, error)
                                
                                // Wykryj błąd połączenia - jeśli request dotyczy streamu, natychmiast zmień stan na Reconnecting
                                val url = request?.url?.toString() ?: ""
                                val isStreamUrl = url.contains(streamUrl, ignoreCase = true)
                                
                                if (isStreamUrl) {
                                    // Błąd dotyczy streamu - zmień stan na Reconnecting (dajemy 5 sekund na próbę przywrócenia)
                                    val currentState = HttpStreamRepository.connectionState.replayCache.lastOrNull()
                                    if (currentState == ConnectionState.Connected) {
                                        // Tylko jeśli byliśmy połączeni, zmień na Reconnecting
                                        // Użyj publicznej metody która automatycznie ustawi reconnectingStartTime
                                        HttpStreamRepository.setReconnecting()
                                    }
                                }
                            }
                        }

                        webChromeClient = WebChromeClient()

                        post {
                            // Użyj wspólnych nagłówków HTTP
                            loadUrl(streamUrl, httpHeaders)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds(),
                update = { view ->
                    // Sprawdź czy tab z configu jest faktycznie widoczny - wywołujemy bezpośrednio
                    val shouldBeVisible = config.tab == HttpStreamRepository.getActiveTab()
                    
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
                        // Użyj wspólnych nagłówków HTTP
                        view.loadUrl(streamUrl, httpHeaders)
                    }
                }
                )
            }
        } else if (connectionState == ConnectionState.Disconnected) {
            // Wyświetl komunikat błędu z przyciskiem Refresh gdy connectionState == Disconnected
            // Uspójniony komunikat: "Brak połączenia"
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                // Kliknięcie w ekran (ale nie w przycisk Refresh) - wywołaj callback
                                onTap?.invoke()
                            }
                        )
                    },
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
                            text = errorMessage ?: "Reconnection failed after 3 attempts",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                    
                    // Przycisk Refresh z ikoną strzałek w kółku
                    Button(
                        onClick = onRefresh,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary // Ujednolicone z MaterialTheme
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onPrimary // Ujednolicone z MaterialTheme
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Refresh",
                            color = MaterialTheme.colorScheme.onPrimary, // Ujednolicone z MaterialTheme
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
                    text = "Data catching error for $label",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp
                )
            }
        } else {
            // Uspójniony komunikat - używamy displayState który jest zsynchronizowany z ConnectionStatusIndicator
            // To obejmuje stan Reconnecting i inne stany
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                // Kliknięcie w ekran - wywołaj callback
                                onTap?.invoke()
                            }
                        )
                    },
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
