package pl.poznan.put.boatcontroller

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point
import pl.poznan.put.boatcontroller.api.ApiClient
import pl.poznan.put.boatcontroller.api.ApiService
import pl.poznan.put.boatcontroller.dataclass.HomePosition
import pl.poznan.put.boatcontroller.dataclass.POIObject
import pl.poznan.put.boatcontroller.dataclass.ShipPosition
import pl.poznan.put.boatcontroller.dataclass.ShipSensorsData
import pl.poznan.put.boatcontroller.mappers.toDomain
import java.util.Base64

class ControllerViewModel(app: Application) : AndroidViewModel(app) {
    private var backendApi: ApiService? = null
    var missionId by mutableIntStateOf(-1)
        private set

    private var _poiPositions = mutableStateListOf<POIObject>()
    var poiPositions: SnapshotStateList<POIObject> = _poiPositions

    var leftEnginePower by mutableIntStateOf(0)
    var rightEnginePower by mutableIntStateOf(0)
    var selectedTab by mutableIntStateOf(0)

    var waypointStartCoordinates = ShipPosition(52.404846, 16.959285)
    var shipPosition by mutableStateOf<ShipPosition>(waypointStartCoordinates)
        private set

    var waypointHomeCoordinates = HomePosition(0.0, 0.0)
    var homePosition by mutableStateOf<HomePosition>(waypointHomeCoordinates)
        private set

    var currentSpeed by mutableFloatStateOf(0.0f)
        private set

    var sonarData by mutableStateOf(ByteArray(0))
        private set

    var sensorsData by mutableStateOf(ShipSensorsData(0.0, 0.0, 0.0))
        private set

    var cameraFeed by mutableStateOf(ByteArray(0))
        private set

    var arePoiVisible by mutableStateOf(false)

    fun mapUpdate(latitude: Double, longitude: Double, speed: Float) {
        shipPosition = ShipPosition(latitude, longitude)
        currentSpeed = speed
        println("Ship position: $shipPosition")
    }

    fun updateHomePosition(homePosition: HomePosition) {
        this.homePosition = homePosition
        println("Home position: $homePosition")
    }

    fun updateSonarData(sonarData: ByteArray) {
        this.sonarData = sonarData
    }

    fun updateSensorsData(sensorsData: ShipSensorsData) {
        this.sensorsData = sensorsData
        println("Sensors data: $sensorsData")
    }

    fun updateCameraFeed(cameraFeed: ByteArray) {
        this.cameraFeed = cameraFeed
    }

    fun updateMissionId(missionId: Int) {
        this.missionId = missionId
        initModel()
    }

    fun initModel() {
        viewModelScope.launch {
            try {
                Log.d("Get Mission Id", missionId.toString())
                backendApi = ApiClient.create(getApplication())
                loadMission()
            } catch (e: Exception) {
                Log.e("API", "Błąd logowania", e)
            }
        }
    }

    fun loadMission() {
        viewModelScope.launch {
            backendApi?.let { api ->
                val response = api.getPoiList(missionId)
                if (response.isSuccessful) {
                    val poiList = response.body()!!.map { it.toDomain() }
                    _poiPositions.clear()
                    _poiPositions.addAll(poiList)
                    Log.d("POI", _poiPositions.toString())
                }
            }
        }
    }

    fun initSocket() {
        SocketClientManager.setOnMessageReceivedListener { message ->
            handleServerMessage(message)
        }
    }

    init {
        initSocket()
    }

    fun handleServerMessage(message: String) {
        if (message.trim().startsWith("ALL_UPDATE:")) {
            val data = message.substringAfter("ALL_UPDATE:").split(",")
            val latitude = data[0].toDouble()
            val longitude = data[1].toDouble()
            val currentSpeed = data[2].toFloat()
            val sensorData = ShipSensorsData(
                data[3].toDouble(),
                data[4].toDouble(),
                data[5].toDouble())
            if(data.size > 6) {
                homePosition = HomePosition(data[6].toDouble(), data[7].toDouble())
            }
            mapUpdate(latitude, longitude, currentSpeed)
            updateSensorsData(sensorData)

        } else if (message.trim().startsWith("MAP_UPDATE:")) {
            val coordinates = message.substringAfter("MAP_UPDATE:").split(",")
            val latitude = coordinates[0].toDouble()
            val longitude = coordinates[1].toDouble()
            val currentSpeed = coordinates[2].toFloat()
            mapUpdate(latitude, longitude, currentSpeed)
        } else if (message.startsWith("SEN_UPDATE:")) {
            val sensorData = message.substringAfter("SEN_UPDATE:").split(",")
            currentSpeed = sensorData[3].toFloat()
            updateSensorsData(ShipSensorsData(
                sensorData[0].toDouble(),
                sensorData[1].toDouble(),
                sensorData[2].toDouble()
            ))
        } else if (message.startsWith("SNR_UPDATE:")) {
            updateSonarData(Base64.getDecoder().decode(message.substringAfter("SNR_UPDATE:").trim()))
        } else if (message.startsWith("CAM_UPDATE:")) {
            updateCameraFeed(Base64.getDecoder().decode(message.substringAfter("CAM_UPDATE:").trim()))
        } else if (message.startsWith("CAM2_UPDATE:")) {
            currentSpeed = message.substringAfter("CAM2_UPDATE:").toFloat()
        } else if (message.startsWith("SNR2_UPDATE:")) {
            val sonarData = message.substringAfter("SNR2_UPDATE:").split(",")
            updateSensorsData(sensorsData.copy(depth = sonarData[0].toDouble()))
            currentSpeed = sonarData[1].toFloat()
        }
    }

    fun sendMessage(message: String) {
        SocketClientManager.sendMessage(message)
    }

    fun getPoiFeature(): List<Feature> {
        Log.d("POI", _poiPositions.toString())
        return _poiPositions.map {
            Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat)).apply {
                addStringProperty("id", it.id.toString())
                addStringProperty("icon", "poi-icon")
            }
        }
    }
}