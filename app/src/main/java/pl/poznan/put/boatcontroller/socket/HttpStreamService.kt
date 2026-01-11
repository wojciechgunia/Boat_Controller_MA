package pl.poznan.put.boatcontroller.socket

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import pl.poznan.put.boatcontroller.ConnectionState

/**
 * Serwis do zarządzania połączeniem WebView w tle.
 * Podobny do SocketService - utrzymuje połączenie i automatycznie reconnectuje.
 */
class HttpStreamService(
    private val config: HttpStreamConfig
) {
    val streamUrl = config.getUrl()
    
    private var isRunning = false
    
    val connectionState = MutableSharedFlow<ConnectionState>(replay = 1)
    val errorMessage = MutableSharedFlow<String?>(replay = 1)
    
    fun startConnectionLoop() {
        isRunning = true
        CoroutineScope(Dispatchers.IO).launch {
            // Inicjalizuj stan jako Connecting
            connectionState.emit(ConnectionState.Connecting)
            errorMessage.emit(null)
            
            // Sprawdź czy URL jest dostępny przez prosty HTTP request
            while (isRunning) {
                try {
                    val client = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                    
                    val request = okhttp3.Request.Builder()
                        .url(streamUrl)
                        .head() // Użyj HEAD żeby sprawdzić dostępność bez pobierania całej strony
                        .build()
                    
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        connectionState.emit(ConnectionState.Connected)
                        errorMessage.emit(null)
                        Log.d("HttpStreamService", "✅ URL available: $streamUrl")
                    } else {
                        connectionState.emit(ConnectionState.Error)
                        errorMessage.emit("Nie udało się połączyć z urządzeniem")
                        Log.w("HttpStreamService", "⚠️ URL not available: $streamUrl (${response.code})")
                    }
                    response.close()
                } catch (e: Exception) {
                    connectionState.emit(ConnectionState.Error)
                    errorMessage.emit("Nie udało się połączyć z urządzeniem")
                    Log.w("HttpStreamService", "⚠️ Connection check failed, retrying in 5s...", e)
                }
                
                // Sprawdzaj co 5 sekund
                delay(5000)
            }
        }
    }
    
    fun stop() {
        isRunning = false
    }
}

