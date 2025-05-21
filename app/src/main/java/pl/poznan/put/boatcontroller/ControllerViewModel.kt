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

    val shipPosition = doubleArrayOf(52.404846, 16.959285)
    val sonarData = "Głębokość: 30m"
    val sensorsData = "Temperatura: 25°C\nWilgotność: 45%"
    val cameraFeedUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a4/Malta_Trybuny_Pozna%C5%84_RB1.JPG/960px-Malta_Trybuny_Pozna%C5%84_RB1.JPG"

}