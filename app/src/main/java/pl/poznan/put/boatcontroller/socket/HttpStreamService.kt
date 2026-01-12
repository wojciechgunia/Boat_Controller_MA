package pl.poznan.put.boatcontroller.socket

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import pl.poznan.put.boatcontroller.ConnectionState

/**
 * Serwis do zarządzania połączeniem WebView w tle.
 * Podobny do SocketService - utrzymuje połączenie i automatycznie reconnectuje.
 * 
 * Optymalizacja: Sprawdza dostępność częściej gdy tab jest widoczny, rzadziej gdy nie jest widoczny.
 */
class HttpStreamService(
    config: HttpStreamConfig // Jako property aby można było używać
) {
    val streamUrl = config.getUrl()
    
    private var isRunning = false
    private var isTabVisible = false // Stan widoczności taba
    
    val connectionState = MutableSharedFlow<ConnectionState>(replay = 1)
    val errorMessage = MutableSharedFlow<String?>(replay = 1)

    fun startConnectionLoop() {
        isRunning = true
        
        CoroutineScope(Dispatchers.IO).launch {
            // Inicjalizuj stan jako Connecting
            connectionState.emit(ConnectionState.Connecting)
            errorMessage.emit(null)
            
            // Małe opóźnienie przed pierwszą próbą - daje czas na reset stanów w UI
            delay(100)
            
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
                    } else {
                        connectionState.emit(ConnectionState.Error)
                        errorMessage.emit("Nie udało się połączyć z urządzeniem")
                    }
                    response.close()
                } catch (e: Exception) {
                    connectionState.emit(ConnectionState.Error)
                    errorMessage.emit("Nie udało się połączyć z urządzeniem")
                }
                
                // Optymalizacja: Sprawdzaj częściej gdy tab jest widoczny, rzadziej gdy nie jest
                val checkInterval = if (isTabVisible) {
                    5000L // 5 sekund gdy tab jest widoczny
                } else {
                    30000L // 30 sekund gdy tab nie jest widoczny (oszczędność danych)
                }
                
                delay(checkInterval)
            }
        }
    }
    
    fun stop() {
        isRunning = false
    }
}

