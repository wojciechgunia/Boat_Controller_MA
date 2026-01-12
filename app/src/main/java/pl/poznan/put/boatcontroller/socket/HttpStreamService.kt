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
 * WAŻNE: Sprawdza czy tab jest aktywny przed próbą połączenia - nie działa w tle.
 */

private const val MAX_RECONNECTING_ATTEMPTS = 3 // Maksymalna liczba prób połączenia

class HttpStreamService(
    config: HttpStreamConfig, // Jako property aby można było używać
    private val getActiveTabCallback: () -> pl.poznan.put.boatcontroller.enums.ControllerTab // Callback do pobierania aktywnego taba
) {
    val streamUrl = config.getUrl()
    val expectedTab = config.tab // Tab dla którego ten serwis jest przeznaczony
    
    private var isRunning = false
    private var prevTab: pl.poznan.put.boatcontroller.enums.ControllerTab? = null // Poprzedni tab od momentu rozpoczęcia połączenia
    private var reconnectingAttempts = 0 // Licznik prób połączenia w stanie Reconnecting
    private var previousState: ConnectionState? = null // Poprzedni stan do wykrywania zmian
    
    val connectionState = MutableSharedFlow<ConnectionState>(replay = 1)
    val errorMessage = MutableSharedFlow<String?>(replay = 1)

    fun startConnectionLoop() {
        isRunning = true
        
        CoroutineScope(Dispatchers.IO).launch {
            // Inicjalizuj stan jako Reconnecting
            connectionState.emit(ConnectionState.Reconnecting)
            errorMessage.emit(null)
            reconnectingAttempts = 0
            
            // Zapisz początkowy tab
            prevTab = getActiveTabCallback()
            
            // Małe opóźnienie przed pierwszą próbą - daje czas na reset stanów w UI
            delay(100)
            
            while (isRunning) {
                // WAŻNE: Sprawdź czy tab się zmienił - jeśli tak, natychmiast rozłącz
                val currentTab = getActiveTabCallback()
                if (prevTab != null && currentTab != prevTab) {
                    // Tab się zmienił - natychmiast rozłącz
                    connectionState.emit(ConnectionState.Disconnected)
                    errorMessage.emit("Tab has been changed")
                    reconnectingAttempts = 0
                    delay(1000L) // Sprawdzaj co sekundę
                    continue
                }
                
                // Sprawdź czy tab jest aktywny - jeśli nie, zatrzymaj próby połączenia
                if (currentTab != expectedTab) {
                    // Tab nie jest aktywny - zatrzymaj próby połączenia
                    delay(1000L) // Sprawdzaj co sekundę czy tab się nie zmienił
                    continue
                }
                
                // Aktualizuj prevTab jeśli tab jest aktywny
                prevTab = currentTab
                
                // Sprawdź aktualny stan - reagujemy tylko na zmiany stanu
                val currentState = connectionState.replayCache.lastOrNull() ?: ConnectionState.Reconnecting
                
                // Wykryj zmianę stanu z Connected na Reconnecting - zresetuj licznik prób
                if (previousState == ConnectionState.Connected && currentState == ConnectionState.Reconnecting) {
                    reconnectingAttempts = 0 // Zresetuj licznik gdy przechodzimy z Connected na Reconnecting
                }
                previousState = currentState
                
                if (currentState == ConnectionState.Reconnecting) {
                    // Jesteśmy w stanie Reconnecting - wykonaj próbę połączenia
                    reconnectingAttempts++
                    
                    // Tworzymy nowy klient HTTP przy każdej próbie, aby uniknąć cache'owanych połączeń TCP
                    val connectionPool = okhttp3.ConnectionPool(1, 1, java.util.concurrent.TimeUnit.SECONDS)
                    val client = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
                        .writeTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
                        .connectionPool(connectionPool)
                        .retryOnConnectionFailure(false)
                        .build()
                    
                    var connectionSuccessful = false
                    try {
                        val request = okhttp3.Request.Builder()
                            .url(streamUrl)
                            .head()
                            .header("Connection", "close")
                            .header("Cache-Control", "no-cache, no-store, must-revalidate")
                            .header("Pragma", "no-cache")
                            .header("Expires", "0")
                            .build()
                        
                        val response = client.newCall(request).execute()
                        if (response.isSuccessful) {
                            // Serwer odpowiada - zmień stan na Connected
                            connectionState.emit(ConnectionState.Connected)
                            errorMessage.emit(null)
                            reconnectingAttempts = 0
                            connectionSuccessful = true
                        }
                        response.close()
                    } catch (_: Exception) {
                        // Błąd połączenia - pozostaw w Reconnecting
                    }
                    
                    if (!connectionSuccessful) {
                        // Próba nie powiodła się - sprawdź czy to była ostatnia próba
                        if (reconnectingAttempts >= MAX_RECONNECTING_ATTEMPTS) {
                            // Wykonano wszystkie próby - zmień na Disconnected
                            connectionState.emit(ConnectionState.Disconnected)
                            errorMessage.emit("Reconnection failed after $MAX_RECONNECTING_ATTEMPTS attemps")
                            reconnectingAttempts = 0
                        } else {
                            // Czekaj przed następną próbą (interwał między próbami)
                            delay(2000L)
                        }
                    }
                } else if (currentState == ConnectionState.Connected) {
                    // Jesteśmy połączeni - okresowo sprawdzaj dostępność serwera (co 5 sekund)
                    // To pozwala wykryć utratę połączenia nawet gdy WebView nie wywołuje onReceivedError
                    val connectionPool = okhttp3.ConnectionPool(1, 1, java.util.concurrent.TimeUnit.SECONDS)
                    val client = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
                        .writeTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
                        .connectionPool(connectionPool)
                        .retryOnConnectionFailure(false)
                        .build()
                    
                    try {
                        val request = okhttp3.Request.Builder()
                            .url(streamUrl)
                            .head()
                            .header("Connection", "close")
                            .header("Cache-Control", "no-cache, no-store, must-revalidate")
                            .header("Pragma", "no-cache")
                            .header("Expires", "0")
                            .build()
                        
                        val response = client.newCall(request).execute()
                        if (!response.isSuccessful) {
                            // Serwer nie odpowiada poprawnie - zmień na Reconnecting
                            connectionState.emit(ConnectionState.Reconnecting)
                            errorMessage.emit(null)
                        }
                        response.close()
                    } catch (_: Exception) {
                        // Błąd połączenia - zmień na Reconnecting
                        connectionState.emit(ConnectionState.Reconnecting)
                        errorMessage.emit(null)
                    }
                    
                    // Sprawdzaj dostępność co 5 sekund gdy jesteśmy połączeni
                    delay(5000L)
                } else {
                    // Jesteśmy w stanie Disconnected - nie sprawdzamy (czekamy na forceReconnect)
                    reconnectingAttempts = 0
                    delay(1000L)
                }
            }
        }
    }
    
    fun stop() {
        isRunning = false
    }
}

