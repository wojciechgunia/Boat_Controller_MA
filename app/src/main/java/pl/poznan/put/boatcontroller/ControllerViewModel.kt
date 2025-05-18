package pl.poznan.put.boatcontroller

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel

class ControllerViewModel(app: Application) : AndroidViewModel(app) {

    var leftEnginePower by mutableIntStateOf(0)
    var rightEnginePower by mutableIntStateOf(0)
    var selectedTab by mutableIntStateOf(0)

    val shipPosition = doubleArrayOf(52.404633, 16.957722)
    val sonarData = "Głębokość: 30m"
    val sensorsData = "Temperatura: 25°C\nWilgotność: 45%"
    val cameraFeedUrl = "https://example.com/camera_feed.jpg"

}