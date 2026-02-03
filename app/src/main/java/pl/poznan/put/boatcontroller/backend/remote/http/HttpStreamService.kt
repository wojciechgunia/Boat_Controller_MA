package pl.poznan.put.boatcontroller.backend.remote.http

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import pl.poznan.put.boatcontroller.domain.enums.ConnectionState
import pl.poznan.put.boatcontroller.domain.enums.ControllerTab
import java.util.concurrent.TimeUnit

const val MAX_RECONNECTING_ATTEMPTS = 3

class HttpStreamService(
    config: HttpStreamConfig, // Jako property aby można było używać
    private val getActiveTabCallback: () -> ControllerTab // Callback do pobierania aktywnego taba
) {
    val streamUrl = config.getUrl()
    val expectedTab = config.tab // Tab dla którego ten serwis jest przeznaczony

    private var isRunning = false
    private var prevTab: ControllerTab? = null // Poprzedni tab od momentu rozpoczęcia połączenia
    private var reconnectingAttempts = 0 // Licznik prób połączenia w stanie Reconnecting
    private var previousState: ConnectionState? = null // Poprzedni stan do wykrywania zmian

    val connectionState = MutableStateFlow(ConnectionState.Disconnected)
    val errorMessage = MutableStateFlow<String?>(null)

    fun startConnectionLoop() {
        isRunning = true

        CoroutineScope(Dispatchers.IO).launch {
            connectionState.value = ConnectionState.Reconnecting
            errorMessage.value = null
            reconnectingAttempts = 0

            prevTab = getActiveTabCallback()

            while (isRunning) {
                val currentTab = getActiveTabCallback()
                if (prevTab != null && currentTab != prevTab) {
                    connectionState.value = ConnectionState.Disconnected
                    errorMessage.value = "Tab has been changed"
                    reconnectingAttempts = 0
                    delay(1000L)
                    continue
                }

                if (currentTab != expectedTab) {
                    delay(1000L)
                    continue
                }

                prevTab = currentTab
                val currentState = connectionState.replayCache.lastOrNull() ?: ConnectionState.Reconnecting

                if (previousState == ConnectionState.Connected && currentState == ConnectionState.Reconnecting) {
                    reconnectingAttempts = 0
                }
                previousState = currentState

                if (currentState == ConnectionState.Reconnecting) {
                    reconnectingAttempts++

                    val connectionPool = ConnectionPool(1, 1, TimeUnit.SECONDS)
                    val client = OkHttpClient.Builder()
                        .connectTimeout(2, TimeUnit.SECONDS)
                        .readTimeout(2, TimeUnit.SECONDS)
                        .writeTimeout(2, TimeUnit.SECONDS)
                        .connectionPool(connectionPool)
                        .retryOnConnectionFailure(false)
                        .build()

                    var connectionSuccessful = false
                    try {
                        val request = Request.Builder()
                            .url(streamUrl)
                            .head()
                            .header("Connection", "close")
                            .header("Cache-Control", "no-cache, no-store, must-revalidate")
                            .header("Pragma", "no-cache")
                            .header("Expires", "0")
                            .build()

                        val response = client.newCall(request).execute()
                        if (response.isSuccessful) {
                            connectionState.value = ConnectionState.Connected
                            errorMessage.value = null
                            reconnectingAttempts = 0
                            connectionSuccessful = true
                        }
                        response.close()
                    } catch (_: Exception) { }

                    if (!connectionSuccessful) {
                        if (reconnectingAttempts >= MAX_RECONNECTING_ATTEMPTS) {
                            connectionState.value = ConnectionState.Disconnected
                            errorMessage.value = "Reconnection failed after $MAX_RECONNECTING_ATTEMPTS attemps"
                            reconnectingAttempts = 0
                        } else {
                            delay(2000L)
                        }
                    }
                } else if (currentState == ConnectionState.Connected) {
                    val connectionPool = ConnectionPool(1, 1, TimeUnit.SECONDS)
                    val client = OkHttpClient.Builder()
                        .connectTimeout(2, TimeUnit.SECONDS)
                        .readTimeout(2, TimeUnit.SECONDS)
                        .writeTimeout(2, TimeUnit.SECONDS)
                        .connectionPool(connectionPool)
                        .retryOnConnectionFailure(false)
                        .build()

                    try {
                        val request = Request.Builder()
                            .url(streamUrl)
                            .head()
                            .header("Connection", "close")
                            .header("Cache-Control", "no-cache, no-store, must-revalidate")
                            .header("Pragma", "no-cache")
                            .header("Expires", "0")
                            .build()

                        val response = client.newCall(request).execute()
                        if (!response.isSuccessful) {
                            connectionState.value = ConnectionState.Reconnecting
                            errorMessage.value = null
                        }
                        response.close()
                    } catch (_: Exception) {
                        connectionState.value = ConnectionState.Reconnecting
                        errorMessage.value = null
                    }

                    delay(5000L)
                } else {
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