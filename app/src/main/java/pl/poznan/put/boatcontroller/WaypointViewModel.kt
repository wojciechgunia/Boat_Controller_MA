package pl.poznan.put.boatcontroller

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import pl.poznan.put.boatcontroller.auth.ApiService
import pl.poznan.put.boatcontroller.auth.AuthClient
import pl.poznan.put.boatcontroller.auth.TokenManager
import pl.poznan.put.boatcontroller.dataclass.CameraPositionState
import pl.poznan.put.boatcontroller.dataclass.LoginRequest
import pl.poznan.put.boatcontroller.dataclass.POIObject
import pl.poznan.put.boatcontroller.dataclass.RunningCreateRequest
import pl.poznan.put.boatcontroller.dataclass.ShipPosition
import pl.poznan.put.boatcontroller.dataclass.WaypointCreateRequest
import pl.poznan.put.boatcontroller.dataclass.WaypointObject
import pl.poznan.put.boatcontroller.dataclass.WaypointUpdateRequest
import pl.poznan.put.boatcontroller.enums.ShipDirection
import pl.poznan.put.boatcontroller.enums.WaypointMode
import pl.poznan.put.boatcontroller.mappers.toDomain

class WaypointViewModel(app: Application) : AndroidViewModel(app) {
    private var backendApi: ApiService? = null
    var missionId by mutableIntStateOf(1)
        private set

    var isToolbarOpened by mutableStateOf(false)

    private val _shipPosition = mutableStateOf<ShipPosition>(ShipPosition(52.404633, 16.957722))
    val shipPosition: MutableState<ShipPosition> = _shipPosition

    private var _currentShipDirection = mutableStateOf<ShipDirection>(ShipDirection.DEFAULT)
    var currentShipDirection: MutableState<ShipDirection> = _currentShipDirection

    private val _waypointBitmaps = mutableStateMapOf<Int, Bitmap>()
    val waypointBitmaps: Map<Int, Bitmap> = _waypointBitmaps

    private var _waypointPositions = mutableStateListOf<WaypointObject>()
    var waypointPositions: SnapshotStateList<WaypointObject> = _waypointPositions

    // Points Of Interest Positions
    private var _poiPositions = mutableStateListOf<POIObject>()
    var poiPositions: SnapshotStateList<POIObject> = _poiPositions

    var arePoiVisible by mutableStateOf(false)

    private val _isShipMoving = mutableStateOf(false)
    val isShipMoving: MutableState<Boolean> = _isShipMoving

    var waypointToMoveNo: Int? by mutableStateOf(null)
    var waypointMode by mutableStateOf<WaypointMode?>(null)
        private set

    private var shipMovingJob: Job? = null

    private val _cameraPosition = mutableStateOf<CameraPositionState?>(null)
    val cameraPosition: MutableState<CameraPositionState?> = _cameraPosition

    private val _shouldFinish = MutableLiveData<Boolean>(false)
    val shouldFinish: LiveData<Boolean> = _shouldFinish


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

        viewModelScope.launch {
            try {
                loginAndInitApi()
                loadMission()
                backendApi?.let { api ->
                    val list = api.getWaypointsList(1).map { it.toDomain() }

                    if (list.isNotEmpty()) {
                        _waypointPositions.clear()
                        _waypointPositions.addAll(list)
                    }
                }
            } catch (e: Exception) {
                Log.e("API", "Błąd logowania", e)
            }
        }
    }

    private suspend fun loginAndInitApi() {
        val loginResponse = AuthClient.authApi.login(LoginRequest("admin", "admin123"))
        TokenManager.saveToken(getApplication(), loginResponse.access_token)
        backendApi = ApiClient.create(getApplication())
    }

    fun loadMission() {
        viewModelScope.launch {
            backendApi?.let { api ->
                val poiList = api.getPoiList(missionId).map { it.toDomain() }

                _poiPositions.clear()
                _poiPositions.addAll(poiList)
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

            val index = _waypointPositions.indexOfFirst { it.no == id }
            if (index != -1) {
                _waypointPositions[index] = _waypointPositions[index].copy(isCompleted = true)
            }
        }

        if (message.trim().startsWith("WFW")) {
            onSimulationFinished()
        }
    }

    fun startShipSimulation() {
        val waypoints = waypointPositions.sortedBy { it.no }
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
            val req = RunningCreateRequest(
                missionId = missionId
            )

            viewModelScope.launch {
                try {
                    backendApi?.createRunning(req)
                } catch (e: Exception) {
                    Log.e("API", "Błąd zapisywania przepływu statku (running)", e)
                }
            }
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

    fun getNextAvailableNo(): Int {
        val usedIds = _waypointPositions.map { it.no }.toSet()
        var no = 1
        while (no in usedIds) {
            no++
        }
        return no
    }

    fun setWaypointBitmap(id: Int, bitmap: Bitmap) {
        _waypointBitmaps[id] = bitmap
    }

//    fun hasBitmap(id: Int) = _waypointBitmaps.containsKey(id)

    fun addWaypoint(lon: Double, lat: Double) {
        val no = getNextAvailableNo()
        viewModelScope.launch {
            try {
                val req = WaypointCreateRequest(
                    missionId = missionId,
                    no = no,
                    lon = lon.toString(),
                    lat = lat.toString()
                )

                backendApi?.createWaypoint(req)

                val waypoint = WaypointObject(no, lon, lat)
                _waypointPositions.add(waypoint)
                val message = "WAP:${no}:${lat}:${lon}"
                SocketClientManager.sendMessage(message)
                println("Sending via socket: $message")
            } catch (e: Exception) {
                Log.e("API", "Błąd dodawania waypointu", e)
            }
        }
    }

    fun removeWaypoint(no: Int) {
        viewModelScope.launch {
            try {
                val waypoints = backendApi?.getWaypointsList(missionId)?.toMutableList() ?: mutableListOf()
                val targetWp = waypoints.firstOrNull { it.no == no }

                if (targetWp != null) {
                    backendApi?.deleteWaypoint(targetWp.id)
                    waypoints.remove(targetWp)

                    waypoints.filter { it.no > no }.forEach { wp ->
                        backendApi?.updateWaypoint(
                            wp.id,
                            WaypointUpdateRequest(no = wp.no - 1)
                        )
                        wp.no = wp.no - 1
                    }

                    _waypointPositions.clear()
                    _waypointPositions.addAll(waypoints.map { it.toDomain() })

                    val message = "WDP:${no}"
                    SocketClientManager.sendMessage(message)
                    println("Sending via socket: $message")
                } else {
                    Log.w("API", "Nie znaleziono waypointu o numerze $no")
                }
            } catch (e: Exception) {
                Log.e("API", "Błąd usuwania waypointu", e)
            }
        }
    }

    fun moveWaypoint(no: Int, newLon: Double, newLat: Double) {
        viewModelScope.launch {
            try {
                val waypoints = backendApi?.getWaypointsList(missionId)?.toMutableList() ?: mutableListOf()
                val targetWp = waypoints.firstOrNull { it.no == no }

                if (targetWp != null) {
                    val req = WaypointUpdateRequest(
                        lon = newLon.toString(),
                        lat = newLat.toString()
                    )
                    backendApi?.updateWaypoint(targetWp.id, req)

                    val index = _waypointPositions.indexOfFirst { it.no == no }
                    if (index != -1) {
                        _waypointPositions[index] = _waypointPositions[index].copy(lon = newLon, lat = newLat)
                    }
                    val message = "WUP:${no}:${newLat}:${newLon}"
                    SocketClientManager.sendMessage(message)
                    println("Sending via socket: $message")
                } else {
                    Log.w("API", "Nie znaleziono waypointu o numerze $no")
                }
            } catch (e: Exception) {
                Log.e("API", "Błąd przesuwania waypointu", e)
            }
        }
    }

    fun getWaypointByNo(no: Int): WaypointObject? {
        return waypointPositions.find { it.no == no }
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
                addStringProperty("no", it.no.toString())
                addStringProperty("icon", "waypoint-icon-${it.no}")
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

    fun sendMessage(message: String) {
        SocketClientManager.sendMessage(message)
    }

    fun saveCameraPosition(lat: Double, lng: Double, zoom: Double) {
        _cameraPosition.value = CameraPositionState(lat, lng, zoom)
    }
}

