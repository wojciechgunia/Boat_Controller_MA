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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import pl.poznan.put.boatcontroller.socket.SocketEvent
import pl.poznan.put.boatcontroller.socket.SocketRepository
import pl.poznan.put.boatcontroller.socket.SocketCommand

class ControllerViewModel(app: Application) : AndroidViewModel(app) {
    private var backendApi: ApiService? = null
    private val repo = Repository(app.applicationContext)
    private val seq = AtomicInteger(0)
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

    private val _externalBatteryLevel = mutableStateOf<Int?>(100)
    val externalBatteryLevel: MutableState<Int?> = _externalBatteryLevel

    var currentSpeed by mutableFloatStateOf(0.0f)
        private set

    var sonarData by mutableStateOf(ByteArray(0))
        private set

    var sensorsData by mutableStateOf(ShipSensorsData(0.0, 0.0))
        private set

    var cameraFeed by mutableStateOf(ByteArray(0))
        private set

    // ===================== Kamera – konfiguracja źródła =====================
    // TODO: Podmień na swój docelowy URL kamery (np. z VPN):
    // przykład: "http://10.8.0.5:8080/camera.jpg" albo "http://ip:port/camera"
    private val cameraUrl = "http://100.103.230.44:8080/stream"
    private val cameraClient = OkHttpClient.Builder()
        .readTimeout(5, TimeUnit.SECONDS)
        .connectTimeout(5, TimeUnit.SECONDS)
        .build()
    private var cameraStreamingStarted = false

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

    /**
     * Strumieniowe pobieranie obrazu z kamery.
     *
     * Obsługuje dwa przypadki:
     *  - multipart/x-mixed-replace (MJPEG) – jedno długie połączenie, z którego wyciągamy kolejne klatki JPEG,
     *  - zwykły JPEG – pojedynczy obraz, po którym ponawiamy połączenie po krótkiej przerwie.
     */
    private fun startCameraStream() {
        if (cameraStreamingStarted) return
        cameraStreamingStarted = true

        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val request = Request.Builder()
                        .url(cameraUrl)
                        .get()
                        .build()

                    cameraClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            Log.e("ControllerViewModel", "Camera HTTP error: ${response.code}")
                        } else {
                            val body = response.body ?: return@use
                            val contentType = response.header("Content-Type") ?: ""

                            if (contentType.startsWith("multipart/x-mixed-replace")) {
                                // MJPEG – jedno długie połączenie, wyciągamy kolejne JPEG-i po znacznikach FFD8/FFD9
                                parseMjpegStream(body.byteStream())
                            } else {
                                // Pojedynczy JPEG
                                val bytes = body.bytes()
                                if (bytes.isNotEmpty()) {
                                    withContext(Dispatchers.Main) {
                                        updateCameraFeed(bytes)
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ControllerViewModel", "Camera fetch error", e)
                }

                // Przy MJPEG wyjdziemy tu dopiero po zerwaniu połączenia – dajmy chwilę przerwy przed reconnectem
                delay(1000)
            }
        }
    }

    /**
     * Bardzo prosty parser MJPEG: szuka sekwencji bajtów 0xFF 0xD8 (SOI) ... 0xFF 0xD9 (EOI)
     * i każdą taką ramkę traktuje jako kompletnego JPEG-a.
     */
    private suspend fun parseMjpegStream(input: java.io.InputStream) {
        val buffer = ByteArray(4096)
        val frameBuffer = ByteArrayOutputStream()
        var inFrame = false
        var prevByte: Int = -1

        try {
            while (true) {
                val bytesRead = input.read(buffer)
                if (bytesRead == -1) break

                for (i in 0 until bytesRead) {
                    val b = buffer[i].toInt() and 0xFF

                    // Start of Image: FF D8
                    if (prevByte == 0xFF && b == 0xD8) {
                        // Nowa klatka – zacznij od zera
                        frameBuffer.reset()
                        frameBuffer.write(0xFF)
                        frameBuffer.write(0xD8)
                        inFrame = true
                    } else if (inFrame) {
                        frameBuffer.write(b)
                    }

                    // End of Image: FF D9
                    if (prevByte == 0xFF && b == 0xD9 && inFrame) {
                        val frameBytes = frameBuffer.toByteArray()
                        if (frameBytes.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                updateCameraFeed(frameBytes)
                            }
                        }
                        inFrame = false
                        frameBuffer.reset()
                    }

                    prevByte = b
                }
            }
        } catch (e: Exception) {
            Log.e("ControllerViewModel", "MJPEG stream parse error", e)
        } finally {
            try {
                input.close()
            } catch (_: Exception) {
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

    init {
        observeSocket()
        observeSocketConnection()
        loadSavedMission()
        startCameraStream()
    }
    
    private fun loadSavedMission() {
        viewModelScope.launch {
            try {
                repo.get().collect { userData ->
                    if (userData.selectedMissionId != -1 && missionId == -1) {
                        missionId = userData.selectedMissionId
                        initModel()
                    }
                }
            } catch (e: Exception) {
                Log.e("ControllerViewModel", "Error loading saved mission", e)
            }
        }
    }

    private fun observeSocket() {
        viewModelScope.launch {
            SocketRepository.events.collectLatest { event ->
                when (event) {
                    is SocketEvent.PositionActualisation -> {
                        mapUpdate(event.lat, event.lon, event.speed.toFloat())
                    }
                    is SocketEvent.SensorInformation -> {
                        updateSensorsData(
                            ShipSensorsData(
                                magnetic = event.magnetic,
                                depth = event.depth
                            )
                        )
                    }
                    is SocketEvent.BoatInformation -> {
                        // Można zaktualizować _homePosition lub wyświetlić info; na razie tylko log
                        Log.d("Socket", "Boat info: ${event.name}/${event.captain}/${event.mission}")
                    }
                    is SocketEvent.BoatInformationChange -> {
                        Log.d("Socket", "Boat info change: ${event.name}/${event.captain}/${event.mission}")
                    }
                    is SocketEvent.WarningInformation -> {
                        Log.w("Socket", "Warning: ${event.infoCode}")
                    }
                    is SocketEvent.LostInformation -> {
                        Log.d("Socket", "Lost info ack for sNum=${event.sNum}")
                    }
                }
            }
        }
    }

    private fun observeSocketConnection() {
        viewModelScope.launch {
            SocketRepository.connectionState.collectLatest { connected ->
                if (connected) {
                    // Po każdym ponownym zestawieniu połączenia wyślij aktualne moce silników,
                    // żeby łódka (lub serwer testowy) od razu dostała wartości SS
                    // nawet jeśli użytkownik nic nie przesunie po reconnect.
                    Log.d(
                        "ControllerViewModel",
                        "Socket connected – resending current speed L=$leftEnginePower R=$rightEnginePower"
                    )
                    SocketRepository.send(
                        SocketCommand.SetSpeed(
                            left = leftEnginePower.toDouble(),
                            right = rightEnginePower.toDouble(),
                            sNum = nextSNum()
                        )
                    )
                }
            }
        }
    }

    private fun nextSNum(): Int = seq.incrementAndGet()

    fun sendSpeed(left: Double, right: Double) {
        viewModelScope.launch {
            currentSpeed = ((left + right) / 2.0).toFloat()
            SocketRepository.send(SocketCommand.SetSpeed(left, right, nextSNum()))
        }
    }

    fun sendAction(action: String, payload: String = "") {
        viewModelScope.launch {
            SocketRepository.send(SocketCommand.SetAction(action, payload, nextSNum()))
        }
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