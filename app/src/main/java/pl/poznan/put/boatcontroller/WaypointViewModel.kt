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
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import pl.poznan.put.boatcontroller.api.ApiClient
import pl.poznan.put.boatcontroller.api.ApiService
import pl.poznan.put.boatcontroller.dataclass.CameraPositionState
import pl.poznan.put.boatcontroller.dataclass.POIObject
import pl.poznan.put.boatcontroller.dataclass.POIUpdateRequest
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
    var missionId by mutableIntStateOf(-1)
        private set

    var isToolbarOpened by mutableStateOf(false)

    var openPOIDialog by mutableStateOf(false)

    var poiId by mutableIntStateOf(-1)

    private val _shipPosition = mutableStateOf<ShipPosition>(ShipPosition(52.404633, 16.957722))
    val shipPosition: MutableState<ShipPosition> = _shipPosition

    private var _currentShipDirection = mutableStateOf<ShipDirection>(ShipDirection.DEFAULT)
    var currentShipDirection: MutableState<ShipDirection> = _currentShipDirection

    private var _waypointPositions = mutableStateListOf<WaypointObject>()
    var waypointPositions: SnapshotStateList<WaypointObject> = _waypointPositions

    private val _waypointBitmaps = mutableStateMapOf<Int, Bitmap>()
    val waypointBitmaps: Map<Int, Bitmap> = _waypointBitmaps

    var waypointToMoveNo: Int? by mutableStateOf(null)
    var waypointMode by mutableStateOf<WaypointMode?>(null)
        private set

    private var _poiPositions = mutableStateListOf<POIObject>()
    var poiPositions: SnapshotStateList<POIObject> = _poiPositions

    var arePoiVisible by mutableStateOf(false)

    private val _isShipMoving = mutableStateOf(false)
    val isShipMoving: MutableState<Boolean> = _isShipMoving
    private var shipMovingJob: Job? = null

    private val _shouldFinish = MutableLiveData<Boolean>(false)
    val shouldFinish: LiveData<Boolean> = _shouldFinish

    private val _cameraPosition = mutableStateOf<CameraPositionState?>(null)
    val cameraPosition: MutableState<CameraPositionState?> = _cameraPosition

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
    }
    fun initModel() {
        viewModelScope.launch {
            try {
                Log.d("Get Mission Id", missionId.toString())
                backendApi = ApiClient.create(getApplication())
                loadMission()
                backendApi?.let { api ->
                    val list = api.getWaypointsList(missionId).map { it.toDomain() }
                    if (list.isNotEmpty()) {
                        _waypointPositions.clear()
                        _waypointPositions.addAll(list)
                    }
                    Log.d("Way", _waypointPositions.toString())
                }
            } catch (e: Exception) {
                Log.e("API", "Błąd logowania", e)
            }
        }
    }

    fun loadMission() {
        viewModelScope.launch {
            backendApi?.let { api ->
                val poiList = api.getPoiList(missionId).map { it.toDomain() }
                _poiPositions.clear()
                _poiPositions.addAll(poiList)
                Log.d("POI", _poiPositions.toString())
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
                missionId = missionId,
                stats = "test"
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
        _isShipMoving.value = false
        _currentShipDirection.value = ShipDirection.DEFAULT
        shipMovingJob?.cancel()
        shipMovingJob = null
    }

    fun getNextAvailableWaypointNo(): Int {
        val usedIds = _waypointPositions.map { it.no }.toSet()
        var no = 1
        while (no in usedIds) {
            no++
        }
        return no
    }

    fun getWaypointByNo(no: Int): WaypointObject? {
        return waypointPositions.find { it.no == no }
    }

    fun addWaypoint(lon: Double, lat: Double) {
        val no = getNextAvailableWaypointNo()
        viewModelScope.launch {
            try {
                val req = WaypointCreateRequest(
                    missionId = missionId,
                    no = no,
                    lon = lon.toString(),
                    lat = lat.toString()
                )
                Log.d("WAYPOINTS", req.toString())
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

    fun setWaypointBitmap(id: Int, bitmap: Bitmap) {
        _waypointBitmaps[id] = bitmap
    }

    fun toggleWaypointEditMode(mode: WaypointMode?) {
        waypointMode = if(waypointMode != mode && mode != null) {
            mode
        } else {
            null
        }
    }

    fun getShipFeature(): FeatureCollection? {
        val point = Point.fromLngLat(shipPosition.value.lon, shipPosition.value.lat)
        val feature = Feature.fromGeometry(point)
        val featureCollection = FeatureCollection.fromFeature(feature)
        return featureCollection
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

    fun getConnectionLinesFeature(): List<Feature> {
        val lines = mutableListOf<Feature>()
        val waypoints = _waypointPositions.sortedBy { it.no }

        waypoints.firstOrNull()?.let { firstWp ->
            val shipPos = _shipPosition.value
            val initShipLine = LineString.fromLngLats(
                listOf(
                    Point.fromLngLat(shipPos.lon, shipPos.lat),
                    Point.fromLngLat(firstWp.lon, firstWp.lat)
                )
            )
            lines.add(Feature.fromGeometry(initShipLine))
        }

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

    fun saveCameraPosition(lat: Double, lng: Double, zoom: Double) {
        _cameraPosition.value = CameraPositionState(lat, lng, zoom)
    }

    fun sendMessage(message: String) {
        SocketClientManager.sendMessage(message)
    }

    fun updateMissionId(missionId: Int) {
        this.missionId = missionId
        initModel()
    }

    fun updatePoiData(id: Int, name: String, description: String) {
        viewModelScope.launch {
            backendApi?.updatePoi(id, POIUpdateRequest(name = name, description = description))
        }
    }
}

