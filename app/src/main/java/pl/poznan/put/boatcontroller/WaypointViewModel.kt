package pl.poznan.put.boatcontroller

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import pl.poznan.put.boatcontroller.auth.ApiClient
import pl.poznan.put.boatcontroller.auth.AuthClient
import pl.poznan.put.boatcontroller.auth.TokenManager
import pl.poznan.put.boatcontroller.dataclass.CameraPositionState
import pl.poznan.put.boatcontroller.dataclass.LoginRequest
import pl.poznan.put.boatcontroller.dataclass.MissionDto
import pl.poznan.put.boatcontroller.dataclass.POIObject
import pl.poznan.put.boatcontroller.dataclass.ShipPosition
import pl.poznan.put.boatcontroller.dataclass.WaypointObject
import pl.poznan.put.boatcontroller.enums.ShipDirection
import pl.poznan.put.boatcontroller.enums.WaypointMode
import pl.poznan.put.boatcontroller.mappers.toDomain

class WaypointViewModel(app: Application) : AndroidViewModel(app) {
    var isToolbarOpened by mutableStateOf(false)

    var waypointStartCoordinates = ShipPosition(52.404633, 16.957722)

    private val _shipPosition = mutableStateOf<ShipPosition>(waypointStartCoordinates)
    val shipPosition: MutableState<ShipPosition> = _shipPosition

    private var _currentShipDirection = mutableStateOf<ShipDirection>(ShipDirection.DEFAULT)
    var currentShipDirection: MutableState<ShipDirection> = _currentShipDirection

    private val _waypointBitmaps = mutableStateMapOf<Int, Bitmap>()
    val waypointBitmaps: Map<Int, Bitmap> = _waypointBitmaps

    private var _waypointPositions = mutableStateListOf<WaypointObject>()
    var waypointPositions: SnapshotStateList<WaypointObject> = _waypointPositions

    // Points Of Interest Positions
//    private var _poiPositions = mutableStateListOf<POIObject>(
//        POIObject(1, 52.407290230659044, 16.961791682925508),
//        POIObject(1, 52.40557452887799, 16.964251055063528),
//        POIObject(1, 52.404813373903465, 16.969023183764534),
//        POIObject(1, 52.40419750664509, 16.9765630569988),
//        POIObject(1, 52.402493273130546, 16.984584841400135),
//        POIObject(1, 52.40082567874097, 16.985875875771626),
//    )
    private var _poiPositions = mutableStateListOf<POIObject>()
    var poiPositions: SnapshotStateList<POIObject> = _poiPositions

    var arePoiVisible by mutableStateOf(false)

    private val _isShipMoving = mutableStateOf(false)
    val isShipMoving: MutableState<Boolean> = _isShipMoving

    var waypointToMoveId: Int? by mutableStateOf(null)
    var waypointMode by mutableStateOf<WaypointMode?>(null)
        private set

    private var shipMovingJob: Job? = null

    private val _cameraPosition = mutableStateOf<CameraPositionState?>(null)
    val cameraPosition: MutableState<CameraPositionState?> = _cameraPosition

    private val _shouldFinish = MutableLiveData<Boolean>(false)
    val shouldFinish: LiveData<Boolean> = _shouldFinish

    var mission: MissionDto? by mutableStateOf(null)

    fun initSocket() {
        SocketClientManager.setOnMessageReceivedListener { message ->
            handleServerMessage(message)
        }
    }

    init {
        initSocket()
        SocketClientManager.setOnLoginStatusChangedListener { loggedIn ->
            if (!loggedIn) {
                stopShipSimulation()
                onSimulationFinished()
                _shouldFinish.postValue(true)
            }
        }
        addWaypoint(_shipPosition.value.lon, _shipPosition.value.lat)

        // Backend łączenie się i wybór misji na razie na sztywno (id=1, name="Testowa")
        viewModelScope.launch {
            try {
                val loginResponse = AuthClient.authApi.login(
                    LoginRequest("admin", "admin123")
                )
                TokenManager.saveToken(getApplication(), loginResponse.access_token)

                val api = ApiClient.create(getApplication())
                mission = api.getMission(1)
                val missionId = 1
                Log.d("MISSION_POI_NO", "Amount of POI Points: ${mission?.pointsOfInterestsNo}")

                val poiList = api.getPoiList(missionId)
                val domainList = poiList.map { it.toDomain() }

                _poiPositions.clear()
                _poiPositions.addAll(domainList)
                Log.d("POI_POSITIONS", _poiPositions.toString())

            } catch (e: Exception) {
                Log.e("API", "Błąd logowania", e)
            }
        }
    }

    fun handleServerMessage(message: String) {
        //println(message)
        if (message.trim().startsWith("SSP:")) {
            val data = message.substringAfter("SSP:").split(":")
            val lat = data[0].toDouble()
            val lon = data[1].toDouble()
            _shipPosition.value = ShipPosition(lat, lon)
        }

        if (message.trim().startsWith("WCW:")) {
            val data = message.substringAfter("WCW:").split(":")
            val id = data[0].toInt()

            val index = _waypointPositions.indexOfFirst { it.id == id }
            if (index != -1) {
                _waypointPositions[index] = _waypointPositions[index].copy(isCompleted = true)
            }
        }

        if (message.trim().startsWith("WFW")) {
            onSimulationFinished()
        }
    }

    fun startShipSimulation() {
        val waypoints = waypointPositions.sortedBy { it.id }
        if (waypoints.isEmpty()) return

        shipMovingJob = viewModelScope.launch {
            try {
                val message = "WST"
                SocketClientManager.sendMessage(message)
                println("Sending via socket: $message")
                _isShipMoving.value = true
            } catch (e: Exception) {
                e.printStackTrace()
                _isShipMoving.value = false
                shipMovingJob = null
            }
        }
    }

    fun stopShipSimulation() {
        shipMovingJob?.cancel()
        shipMovingJob = null
        _isShipMoving.value = false
        val message = "WSP"
        SocketClientManager.sendMessage(message)
        println("Sending via socket: $message")
    }

    fun goToHome() {
        val message = "WGH"
        toggleShipDirection()
        SocketClientManager.sendMessage(message)
        println("Sending via socket: $message")
    }

    fun toggleShipDirection() {
        _currentShipDirection.value = when (_currentShipDirection.value) {
            ShipDirection.DEFAULT -> ShipDirection.REVERSE
            ShipDirection.REVERSE -> ShipDirection.DEFAULT
        }
    }
    fun toggleSimulation() {
        if (isShipMoving.value) {
            stopShipSimulation()
        } else {
            startShipSimulation()
        }
    }

    fun onSimulationFinished() {
        _waypointPositions.clear()
        addWaypoint(_shipPosition.value.lon, _shipPosition.value.lat)
        _isShipMoving.value = false
        _currentShipDirection.value = ShipDirection.DEFAULT
        shipMovingJob?.cancel()
        shipMovingJob = null
    }

    fun getNextAvailableId(): Int {
        val usedIds = _waypointPositions.map { it.id }.toSet()
        var id = 0
        while (id in usedIds) {
            id++
        }
        return id
    }

    fun setWaypointBitmap(id: Int, bitmap: Bitmap) {
        _waypointBitmaps[id] = bitmap
    }

//    fun hasBitmap(id: Int) = _waypointBitmaps.containsKey(id)

    fun addWaypoint(lon: Double, lat: Double): WaypointObject {
        val id = getNextAvailableId()
        val waypoint = WaypointObject(id, lon, lat)
        _waypointPositions.add(waypoint)
        val message = "WAP:${id}:${lat}:${lon}"
        SocketClientManager.sendMessage(message)
        println("Sending via socket: $message")
        return waypoint
    }

    fun removeWaypoint(id: Int) {
        _waypointPositions.removeAll { it.id == id }
        val message = "WDP:${id}"
        SocketClientManager.sendMessage(message)
        println("Sending via socket: $message")
        reindexWaypoints(id)
    }

    fun moveWaypoint(id: Int, newLon: Double, newLat: Double) {
        val index = _waypointPositions.indexOfFirst { it.id == id }
        if (index != -1) {
            _waypointPositions[index] = _waypointPositions[index].copy(lon = newLon, lat = newLat)
        }
        val message = "WUP:${id}:${newLat}:${newLon}"
        SocketClientManager.sendMessage(message)
        println("Sending via socket: $message")
    }

    fun reindexWaypoints(removedId: Int) {
        _waypointPositions.forEach { wp ->
            if (wp.id > removedId) {
                wp.id -= 1
            }
        }
    }

    fun getWaypointById(id: Int): WaypointObject? {
        return waypointPositions.find { it.id == id }
    }

    fun toggleWaypointEditMode(mode: WaypointMode?) {
        waypointMode = if(waypointMode != mode && mode != null) {
            mode
        } else {
            null
        }
    }

    fun updateMapSources(waypointSource: GeoJsonSource, waypointConnectionsSource: GeoJsonSource, shipSource: GeoJsonSource) {
        waypointSource.setGeoJson(FeatureCollection.fromFeatures(getWaypointsFeatures()))
        waypointConnectionsSource.setGeoJson(FeatureCollection.fromFeatures(getConnectionLines()))
        shipSource.setGeoJson(getShipFeature())
    }

    fun showMapPoiSources(poiSource: GeoJsonSource) {
        poiSource.setGeoJson(FeatureCollection.fromFeatures(getPoiFeatures()))
    }

    fun getShipFeature(): FeatureCollection? {
        val point = Point.fromLngLat(shipPosition.value.lon, shipPosition.value.lat)
        val feature = Feature.fromGeometry(point)
        val featureCollection = FeatureCollection.fromFeature(feature)
        return featureCollection
    }

    fun getWaypointsFeatures(): List<Feature> {
        return _waypointPositions.map {
            Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat)).apply {
                addStringProperty("id", it.id.toString())
                addStringProperty("icon", "waypoint-icon-${it.id}")
            }
        }
    }

    fun getPoiFeatures(): List<Feature> {
        return _poiPositions.map {
            Feature.fromGeometry(Point.fromLngLat(it.lat, it.lon)).apply {
                addStringProperty("icon", "poi-icon")
            }
        }
    }

    fun getConnectionLines(): List<Feature> {
        val lines = mutableListOf<Feature>()
        val waypoints = _waypointPositions.sortedBy { it.id }

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

    fun sendMessage(message: String) {
        SocketClientManager.sendMessage(message)
    }

    fun saveCameraPosition(lat: Double, lng: Double, zoom: Double) {
        _cameraPosition.value = CameraPositionState(lat, lng, zoom)
    }
}

