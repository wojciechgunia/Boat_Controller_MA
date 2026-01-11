package pl.poznan.put.boatcontroller.templates

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import pl.poznan.put.boatcontroller.ConnectionState

/**
 * Reużywalny komponent do wyświetlania strumieni HTTP w WebView.
 * WebView jest tworzony lokalnie, ale stan połączenia jest zarządzany przez HttpStreamRepository.
 * 
 * @param streamUrl URL do załadowania
 * @param connectionState Stan połączenia
 * @param errorMessage Komunikat błędu (null gdy brak błędu)
 * @param isTabVisible Czy zakładka jest obecnie widoczna
 * @param label Etykieta wyświetlana w komunikatach (np. "kamera", "sonar")
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HttpStreamView(
    streamUrl: String?,
    connectionState: ConnectionState,
    errorMessage: String?,
    isTabVisible: Boolean,
    label: String = "urządzenie"
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Zarządzanie życiowym cyklem WebView
    LaunchedEffect(isTabVisible) {
        webViewRef?.let { webView ->
            if (isTabVisible) {
                webView.onResume()
            } else {
                webView.onPause()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds() // Ważne: obetnij zawartość do granic Box
    ) {
        // Wyświetl WebView jeśli URL jest dostępny i nie ma błędu
        if (streamUrl != null && errorMessage == null) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        webViewRef = this
                        
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
                        
                        setBackgroundColor(0x00000000)

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                                return false
                            }
                        }

                        webChromeClient = WebChromeClient()
                        
                        // Załaduj URL
                        post {
                            loadUrl(streamUrl)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds(), // Obetnij WebView do granic
                update = { view ->
                    if (isTabVisible) {
                        view.onResume()
                        // Upewnij się że URL jest załadowany
                        if (view.url.isNullOrEmpty() || view.url != streamUrl) {
                            view.loadUrl(streamUrl)
                        }
                    } else {
                        view.onPause()
                    }
                }
            )
        } else if (errorMessage != null) {
            // Wyświetl komunikat błędu
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .padding(16.dp)
                        .background(
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(16.dp)
                ) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else if (connectionState == ConnectionState.Connecting) {
            // Wyświetl informację o ładowaniu
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Łączenie z $label...",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp
                )
            }
        } else {
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
        }
    }
}
