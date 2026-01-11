package pl.poznan.put.boatcontroller.socket

import kotlinx.coroutines.flow.SharedFlow

/**
 * Repository do zarządzania połączeniami HTTP stream (kamera i sonar).
 * Podobny do SocketRepository - utrzymuje połączenia w tle.
 * Używa centralnej konfiguracji z HttpStreamConfigs.
 */
object HttpStreamRepository {
    private var cameraService: HttpStreamService? = null
    private var sonarService: HttpStreamService? = null
    
    fun startCamera() {
        cameraService?.stop()
        cameraService = HttpStreamService(HttpStreamConfigs.CAMERA)
        cameraService?.startConnectionLoop()
    }
    
    fun startSonar() {
        sonarService?.stop()
        sonarService = HttpStreamService(HttpStreamConfigs.SONAR)
        sonarService?.startConnectionLoop()
    }
    
    /**
     * Uruchamia wszystkie skonfigurowane streamy.
     */
    fun startAll() {
        startCamera()
        startSonar()
        // Dodaj tutaj nowe streamy gdy je dodasz do HttpStreamConfigs
    }
    
    fun getCameraUrl(): String? = cameraService?.streamUrl
    fun getSonarUrl(): String? = sonarService?.streamUrl
    
    val cameraConnectionState: SharedFlow<pl.poznan.put.boatcontroller.ConnectionState>?
        get() = cameraService?.connectionState
    
    val sonarConnectionState: SharedFlow<pl.poznan.put.boatcontroller.ConnectionState>?
        get() = sonarService?.connectionState
    
    val cameraErrorMessage: SharedFlow<String?>?
        get() = cameraService?.errorMessage
    
    val sonarErrorMessage: SharedFlow<String?>?
        get() = sonarService?.errorMessage
}

