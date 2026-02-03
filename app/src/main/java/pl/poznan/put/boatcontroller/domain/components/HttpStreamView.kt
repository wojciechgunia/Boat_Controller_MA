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
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
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

    val isTabActive = config?.tab == HttpStreamRepository.getActiveTab()

    val displayState = when (connectionState) {
        ConnectionState.Connected -> "Connected"
        ConnectionState.Reconnecting -> "Reconnecting..."
        ConnectionState.Disconnected -> "Disconnected"
    }

    val httpHeaders = remember {
        mapOf(
            "Connection" to "close",
            "Cache-Control" to "no-cache, no-store, must-revalidate",
            "Pragma" to "no-cache",
            "Expires" to "0"
        )
    }

    val onRefresh = {
        val tab = config?.tab
        if (tab != null && (tab == ControllerTab.SONAR || tab == ControllerTab.CAMERA)) {
            HttpStreamRepository.forceReconnect(tab)
        }
    }

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
    
    // DisposableEffect z kluczem isTabActive - niszczy WebView gdy tab traci aktywność
    DisposableEffect(isTabActive, streamUrl) {
        if (!isTabActive && webViewRef != null) {
            val currentWebView = webViewRef
            webViewRef = null
            safeDestroyWebView(currentWebView)
            HttpStreamRepository.unregisterWebView(currentWebView)
        }
        
        onDispose {
            val currentWebView = webViewRef
            webViewRef = null
            if (currentWebView != null) {
                safeDestroyWebView(currentWebView)
                HttpStreamRepository.unregisterWebView(currentWebView)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        if (!isTabActive) {
            return
        } else if (streamUrl != null && connectionState == ConnectionState.Connected) {
            key("$label-$streamUrl-$connectionState") {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                        webViewRef = this

                        HttpStreamRepository.registerWebView(this, config)
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        clipToOutline = true
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
                            layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
                        }

                        try {
                            setRendererPriorityPolicy(
                                WebView.RENDERER_PRIORITY_IMPORTANT,
                                false
                            )
                        } catch (_: Exception) {}
                        setBackgroundColor(0x00000000)

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
                                        false
                                    }
                                    MotionEvent.ACTION_UP -> {
                                        val touchDuration = System.currentTimeMillis() - touchDownTime
                                        val touchDistance = sqrt(
                                            (event.x - touchDownX) * (event.x - touchDownX) + 
                                            (event.y - touchDownY) * (event.y - touchDownY)
                                        )

                                        if (touchDuration < 200 && touchDistance < 10f) {
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
                                                if (result != null && result.contains("non-interactive")) {
                                                    onTap()
                                                }
                                            }
                                        }
                                        false
                                    }
                                    else -> false
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
                                    val shouldBeVisible = config.tab == HttpStreamRepository.getActiveTab()
                                    
                                    if (!shouldBeVisible) {
                                        return WebResourceResponse(
                                            "text/plain",
                                            "utf-8",
                                            ByteArrayInputStream(ByteArray(0))
                                        )
                                    }

                                    HttpStreamRepository.registerRequest()
                                    
                                    return super.shouldInterceptRequest(view, request)
                                }

                                if (url.contains("ads", ignoreCase = true) ||
                                    url.contains("doubleclick", ignoreCase = true) ||
                                    url.contains("analytics", ignoreCase = true) ||
                                    url.contains("google-analytics", ignoreCase = true) ||
                                    url.contains("googletagmanager", ignoreCase = true)
                                ) {
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

                                val startedUrl = url ?: ""
                                val isStreamUrl = startedUrl.contains(streamUrl, ignoreCase = true)
                                
                                if (isStreamUrl) {
                                    val currentState = HttpStreamRepository.connectionState.replayCache.lastOrNull()
                                    if (currentState == ConnectionState.Disconnected) {
                                        HttpStreamRepository.setReconnecting()
                                    }
                                }
                            }
                            
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)

                                val finishedUrl = url ?: ""
                                val isStreamUrl = finishedUrl.contains(streamUrl, ignoreCase = true)
                                
                                if (isStreamUrl && view != null) {
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        val currentState = HttpStreamRepository.connectionState.replayCache.lastOrNull()
                                        if (currentState != ConnectionState.Connected) {
                                            HttpStreamRepository.setConnected()
                                        }
                                    }, 500)
                                }
                            }
                            
                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                super.onReceivedError(view, request, error)

                                val url = request?.url?.toString() ?: ""
                                val isStreamUrl = url.contains(streamUrl, ignoreCase = true)
                                
                                if (isStreamUrl) {
                                    val currentState = HttpStreamRepository.connectionState.replayCache.lastOrNull()
                                    if (currentState == ConnectionState.Connected) {
                                        HttpStreamRepository.setReconnecting()
                                    }
                                }
                            }
                        }

                        webChromeClient = WebChromeClient()

                        post {
                            loadUrl(streamUrl, httpHeaders)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds(),
                update = { view ->
                    val shouldBeVisible = config.tab == HttpStreamRepository.getActiveTab()
                    
                    if (!shouldBeVisible) {
                        view.onPause()
                        view.stopLoading()
                        view.clearHistory()
                        view.loadUrl("about:blank")
                        return@AndroidView
                    }

                    view.onResume()
                    if (view.url.isNullOrEmpty() || view.url != streamUrl) {
                        view.loadUrl(streamUrl, httpHeaders)
                    }
                }
                )
            }
        } else if (connectionState == ConnectionState.Disconnected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
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

                    Button(
                        onClick = onRefresh,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Refresh",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        } else if (streamUrl == null) {
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
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
