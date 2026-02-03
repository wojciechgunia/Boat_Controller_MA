package pl.poznan.put.boatcontroller.socket

import pl.poznan.put.boatcontroller.enums.ControllerTab

/**
 * Konfiguracja pojedynczego HTTP streamu.
 * 
 * @param name Nazwa streamu (np. "camera", "sonar")
 * @param port Port na którym znajduje się stream
 * @param tab Tab w którym stream jest wyświetlany
 * @param path Opcjonalna ścieżka w URL (np. "/stream", "/api/data"). Jeśli null, używa tylko portu.
 * @param baseIp Adres IP (domyślnie "100.103.230.44")
 */
data class HttpStreamConfig(
    val name: String,
    val port: Int,
    val tab: ControllerTab,
    val path: String? = null,
    val baseIp: String = "100.103.230.44"
) {
    fun getUrl(): String {
        return if (path != null) {
            "http://$baseIp:$port$path"
        } else {
            "http://$baseIp:$port"
        }
    }
}

/**
 * Centralna konfiguracja wszystkich HTTP streamów.
 */
object HttpStreamConfigs {
    // Podstawowy adres IP dla wszystkich streamów
    // private const val BASE_IP = "100.103.230.44" // Oryginalne IP
    private const val BASE_IP = "192.168.1.12" // Testy bez drona
    
    // Konfiguracje streamów
    val CAMERA = HttpStreamConfig(
        name = "camera",
        port = 8080,
        tab = ControllerTab.CAMERA,
        path = null, 
        baseIp = BASE_IP
    )
    
    val SONAR = HttpStreamConfig(
        name = "sonar",
        port = 8081,
        tab = ControllerTab.SONAR,
        path = null,
        baseIp = BASE_IP
    )
    
    /**
     * Zwraca konfigurację streamu dla danego taba.
     */
    fun getConfigForTab(tab: ControllerTab): HttpStreamConfig? {
        return when (tab) {
            ControllerTab.CAMERA -> CAMERA
            ControllerTab.SONAR -> SONAR
            else -> null
        }
    }
    
    // Przykład jak dodać nowy stream:
    // val NEW_STREAM = HttpStreamConfig(
    //     name = "new_stream",
    //     port = 8082,
    //     path = "/api/data", // opcjonalnie
    //     baseIp = BASE_IP
    // )
}

