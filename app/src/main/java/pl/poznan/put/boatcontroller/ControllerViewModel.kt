package pl.poznan.put.boatcontroller

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import pl.poznan.put.boatcontroller.api.ApiClient
import pl.poznan.put.boatcontroller.api.ApiService
import pl.poznan.put.boatcontroller.dataclass.CameraPositionState
import pl.poznan.put.boatcontroller.dataclass.HomePosition
import pl.poznan.put.boatcontroller.dataclass.POIObject
import pl.poznan.put.boatcontroller.dataclass.POIUpdateRequest
import pl.poznan.put.boatcontroller.dataclass.ShipPosition
import pl.poznan.put.boatcontroller.dataclass.ShipSensorsData
import pl.poznan.put.boatcontroller.dataclass.WaypointObject
import pl.poznan.put.boatcontroller.enums.MapLayersVisibilityMode
import pl.poznan.put.boatcontroller.mappers.toDomain
import java.util.Base64

class ControllerViewModel(app: Application) : AndroidViewModel(app) {
    private var backendApi: ApiService? = null
    var missionId by mutableIntStateOf(-1)
        private set

    var openPOIDialog by mutableStateOf(false)
    var poiId by mutableIntStateOf(-1)

    var leftEnginePower by mutableIntStateOf(0)
    var rightEnginePower by mutableIntStateOf(0)
    var selectedTab by mutableIntStateOf(0)

    private val _mapLibreMapState = mutableStateOf<MapLibreMap?>(null)
    val mapLibreMapState: MutableState<MapLibreMap?> = _mapLibreMapState

    private val _phonePosition = mutableStateOf<DoubleArray?>(null)
    val phonePosition: MutableState<DoubleArray?> = _phonePosition

    private val _homePosition = mutableStateOf<HomePosition>(HomePosition(0.0, 0.0))
    val homePosition: MutableState<HomePosition> = _homePosition

    private val _shipPosition = mutableStateOf<ShipPosition>(ShipPosition(52.404633, 16.957722))
    var shipPosition: MutableState<ShipPosition> = _shipPosition

    private var _waypointPositions = mutableStateListOf<WaypointObject>()
    var waypointPositions: SnapshotStateList<WaypointObject> = _waypointPositions

    private var _poiPositions = mutableStateListOf<POIObject>()
    var poiPositions: SnapshotStateList<POIObject> = _poiPositions

    private val _waypointBitmaps = mutableStateMapOf<Int, Bitmap>()
    val waypointBitmaps: Map<Int, Bitmap> = _waypointBitmaps

    private val _cameraPosition = mutableStateOf<CameraPositionState?>(null)
    val cameraPosition: MutableState<CameraPositionState?> = _cameraPosition

    private val _layersMode = mutableStateOf(MapLayersVisibilityMode.BOTH_VISIBLE)
    val layersMode: MutableState<MapLayersVisibilityMode> = _layersMode

    private val _externalBatteryLevel = mutableStateOf<Int?>(70)
    val externalBatteryLevel: MutableState<Int?> = _externalBatteryLevel

    var currentSpeed by mutableFloatStateOf(0.0f)
        private set

    var sonarData by mutableStateOf(ByteArray(0))
        private set

    var sensorsData by mutableStateOf(ShipSensorsData(0.0, 0.0, 0.0))
        private set

    var cameraFeed by mutableStateOf(ByteArray(0))
        private set

    fun mapUpdate(latitude: Double, longitude: Double, speed: Float) {
        _shipPosition.value = ShipPosition(latitude, longitude)
        currentSpeed = speed
        println("Ship position: $shipPosition")
    }

    fun updateHomePosition(homePosition: HomePosition) {
        _homePosition.value = homePosition
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
                backendApi?.let { api ->
                    val response = api.getWaypointsList(missionId)
                    if (response.isSuccessful) {
                        val list = response.body()!!.map { it.toDomain() }
                        if (list.isNotEmpty()) {
                            _waypointPositions.clear()
                            _waypointPositions.addAll(list)
                        }
                        Log.d("Way", _waypointPositions.toString())
                    } else {
                        Log.e("API", "Błąd pobierania waypoints")
                    }

                }
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
                _homePosition.value = HomePosition(data[6].toDouble(), data[7].toDouble())
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

    fun getShipFeature(): FeatureCollection {
        val shipCoordinates = Point.fromLngLat(
            _shipPosition.value.lon,
            _shipPosition.value.lat
        )

        val shipFeature = Feature.fromGeometry(shipCoordinates).apply {
            addStringProperty("title", "Ship")
        }

        return FeatureCollection.fromFeature(shipFeature)
    }

    fun getHomeFeature(): FeatureCollection {
        val shipCoordinates = Point.fromLngLat(
            _homePosition.value.lon,
            _homePosition.value.lat
        )

        val shipFeature = Feature.fromGeometry(shipCoordinates).apply {
            addStringProperty("title", "Home")
        }

        return FeatureCollection.fromFeature(shipFeature)
    }

    fun getWaypointsFeature(): List<Feature> {
        return _waypointPositions.map {
            Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat)).apply {
                addStringProperty("no", it.no.toString())
                addStringProperty("icon", "waypoint-icon-${it.no}")
            }
        }
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

    fun getPhoneLocationFeature(): FeatureCollection {
        val phoneCoordinates = Point.fromLngLat(
            _phonePosition.value?.get(0) ?: _shipPosition.value.lon,
            _phonePosition.value?.get(1) ?: _shipPosition.value.lat,
        )

        val phoneFeature = Feature.fromGeometry(phoneCoordinates).apply {
            addStringProperty("title", "Phone")
        }

        return FeatureCollection.fromFeature(phoneFeature)
    }

    fun getConnectionLinesFeature(): List<Feature> {
        val lines = mutableListOf<Feature>()
        val waypoints = _waypointPositions.sortedBy { it.no }

        for (i in 0 until waypoints.size - 1) {
            val start = waypoints[i]
            val end = waypoints[i + 1]
            val line = LineString.fromLngLats(
                listOf(
                    Point.fromLngLat(start.lon, start.lat),
                    Point.fromLngLat(end.lon, end.lat)
                )
            )
            lines.add(Feature.fromGeometry(line))
        }

        return lines
    }

    fun setWaypointBitmap(id: Int, bitmap: Bitmap) {
        _waypointBitmaps[id] = bitmap
    }

    fun setPhonePosition(lat: Double, lon: Double) {
        _phonePosition.value = doubleArrayOf(lat, lon)
    }

    fun setPhonePositionFallback() {
        val shipPos = _shipPosition.value
        setPhonePosition(shipPos.lat, shipPos.lon)
    }

    fun setMapReady(map: MapLibreMap) {
        _mapLibreMapState.value = map
    }

    fun saveCameraPosition(lat: Double, lng: Double, zoom: Double) {
        _cameraPosition.value = CameraPositionState(lat, lng, zoom)
    }

    fun toggleMapLayersMode() {
        _layersMode.value = when (_layersMode.value) {
            MapLayersVisibilityMode.BOTH_VISIBLE -> MapLayersVisibilityMode.WAYPOINTS
            MapLayersVisibilityMode.WAYPOINTS -> MapLayersVisibilityMode.POI
            MapLayersVisibilityMode.POI -> MapLayersVisibilityMode.NONE
            MapLayersVisibilityMode.NONE -> MapLayersVisibilityMode.BOTH_VISIBLE
        }
    }

    fun updatePoiData(id: Int, name: String, description: String) {
        viewModelScope.launch {
            Log.d("POI", "updatePoiData: $id, $name, $description")
            val response = backendApi?.updatePoi(id, POIUpdateRequest(name = name, description = description))
            if(response == null || !response.isSuccessful) {
                Log.e("API", "Błąd aktualizacji POI", Exception("Response is null or not successful"))
                Log.d("API", "Response: $response")
                return@launch
            }
            loadMission()
        }
    }

    fun deletePoi(id: Int) {
        viewModelScope.launch {
            backendApi?.deletePoi(id)
            loadMission()
        }
    }
}