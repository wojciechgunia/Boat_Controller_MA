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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
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
import pl.poznan.put.boatcontroller.enums.ControllerTab
import pl.poznan.put.boatcontroller.enums.MapLayersVisibilityMode
import pl.poznan.put.boatcontroller.mappers.toDomain
import java.util.concurrent.atomic.AtomicInteger
import pl.poznan.put.boatcontroller.socket.SocketEvent
import pl.poznan.put.boatcontroller.socket.SocketRepository
import pl.poznan.put.boatcontroller.socket.SocketCommand
import pl.poznan.put.boatcontroller.socket.HttpStreamRepository

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
    var selectedTab by mutableStateOf(ControllerTab.MAP)

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
    var sensorsData by mutableStateOf(ShipSensorsData())
        private set

    // ===================== HTTP Streams – konfiguracja =====================
    // Jeden stan połączenia dla aktywnego streamu
    var httpConnectionState by mutableStateOf<ConnectionState>(ConnectionState.Disconnected)
        private set
    var httpErrorMessage by mutableStateOf<String?>(null)
        private set
    
    // Stan silnika zwijarki: 0 = góra (up), 1 = wyłączony (stop), 2 = dół (down)
    var winchState by mutableIntStateOf(1) // Domyślnie wyłączony
    
    // Mechanizm wysyłania SS z burst + keep-alive (optymalizacja LoRa)
    private var currentSpeedSendJob: Job? = null
    private var keepAliveJob: Job? = null
    private val SS_BURST_COUNT = 4 // Liczba ramek w burst (3-5)
    private val SS_BURST_INTERVAL_MS = 200L // Interwał między ramkami w burst (150-300ms)
    private val SS_KEEP_ALIVE_INTERVAL_MS = 2500L // Keep-alive co 2-3s
    
    // Ostatnie wysłane wartości (dla keep-alive)
    private var lastSentLeft: Int = 1
    private var lastSentRight: Int = 1
    private var lastSentWinch: Int = 1

    fun mapUpdate(latitude: Double, longitude: Double, speed: Float) {
        _shipPosition.value = ShipPosition(latitude, longitude)
        currentSpeed = speed
        println("Ship position: $shipPosition")
    }

    fun updateHomePosition(homePosition: HomePosition) {
        _homePosition.value = homePosition
        println("Home position: $homePosition")
    }

    fun updateSensorsData(sensorsData: ShipSensorsData) {
        this.sensorsData = sensorsData
        println("Sensors data: $sensorsData")
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
        // Uruchom połączenia HTTP stream przy inicjalizacji ViewModel
        HttpStreamRepository.startAll()
        
        // Obserwuj stany połączeń
        observeHttpStreamConnections()
    }
    
    private fun observeHttpStreamConnections() {
        viewModelScope.launch {
            HttpStreamRepository.connectionState.collectLatest { state ->
                httpConnectionState = state
            }
        }
        viewModelScope.launch {
            HttpStreamRepository.errorMessage.collectLatest { error ->
                httpErrorMessage = error
            }
        }
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
                        // lat/lon już jako Double, speed z cm/s na m/s
                        val speedMs = event.speed / 100.0 // cm/s -> m/s
                        Log.d("ControllerViewModel", "📍 PA received: lat=${event.lat}, lon=${event.lon}, speed=$speedMs m/s, sNum=${event.sNum}")
                        mapUpdate(event.lat, event.lon, speedMs.toFloat())
                    }
                    is SocketEvent.SensorInformation -> {
                        // Konwersja z Int na Double
                        // accel/gyro/mag/depth: *100, kąty: bez konwersji (już Int jako Double)
                        updateSensorsData(
                            ShipSensorsData(
                                accelX = event.accelX / 100.0,
                                accelY = event.accelY / 100.0,
                                accelZ = event.accelZ / 100.0,
                                gyroX = event.gyroX / 100.0,
                                gyroY = event.gyroY / 100.0,
                                gyroZ = event.gyroZ / 100.0,
                                magX = event.magX / 100.0,
                                magY = event.magY / 100.0,
                                magZ = event.magZ / 100.0,
                                angleX = event.angleX.toDouble(), // kąty jako Int
                                angleY = event.angleY.toDouble(),
                                angleZ = event.angleZ.toDouble(),
                                depth = event.depth / 100.0
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
                    is SocketEvent.CommandAck -> {
                        Log.d("Socket", "✅ Command ACK received: ${event.commandType} sNum=${event.sNum}")
                    }
                }
            }
        }
    }

    private fun observeSocketConnection() {
        viewModelScope.launch {
            SocketRepository.connectionState.collectLatest { connected ->
                if (connected) {
                    // Wysyłamy tryb manual przy połączeniu
                    sendAction("SM", "manual")
                    
                    // Po każdym ponownym zestawieniu połączenia wyślij aktualne moce silników,
                    // żeby łódka (lub serwer testowy) od razu dostała wartości SS
                    // nawet jeśli użytkownik nic nie przesunie po reconnect.
                    // Używamy sendSpeedWithInterval aby wysłać z interwałem
                    Log.d(
                        "ControllerViewModel",
                        "Socket connected – resending current speed L=$leftEnginePower R=$rightEnginePower"
                    )
                    sendSpeedWithInterval(leftEnginePower, rightEnginePower, winchState)
                }
            }
        }
    }

    private fun nextSNum(): Int = seq.incrementAndGet()
    
    /**
     * Konwertuje wartość prędkości z zakresu aplikacji mobilnej (-80..80) na format dla kontrolera (0..10).
     * Mapowanie zgodne z silnik_test.py i lora_motor_service.py:
     * - 0 = stop (neutral) -> PWM 7.5%
     * - 1-4 = reverse (wstecz) -> PWM 5.0% - 7.5%
     * - 5 = neutral (stop) -> PWM 7.5% (alternatywa dla 0)
     * - 6-10 = forward (przód) -> PWM 7.5% - 10.0%
     * 
     * Format dla ESC Hobbywing 880 QuickRun:
     * - 5.0% = max reverse, 7.5% = neutral, 10.0% = max forward
     */
    private fun convertSpeedToControllerFormat(speed: Int): Int {
        return when {
            speed == 0 -> 0 // Stop (neutral) - używamy 0 jako stop zgodnie z wymaganiami
            speed < 0 -> {
                // Reverse: -80..-1 -> 1..4
                // speed = -80 -> 1 (max reverse), speed = -1 -> 4 (min reverse)
                val mapped = (5.0 + (speed / 80.0) * 4.0).toInt().coerceIn(1, 4)
                mapped
            }
            else -> {
                // Forward: 1..80 -> 6..10
                // speed = 1 -> 6 (min forward), speed = 80 -> 10 (max forward)
                val mapped = (5.0 + (speed / 80.0) * 5.0).toInt().coerceIn(6, 10)
                mapped
            }
        }
    }
    
    /**
     * Wysyła komendę SS z burst (event-driven) + keep-alive.
     * Model hybrydowy zgodny z optymalizacją LoRa:
     * - Burst: 3-5 ramek przy zmianie (odstęp 150-300ms)
     * - Keep-alive: aktualny stan co 2-3s nawet bez zmian
     */
    private fun sendSpeedWithInterval(left: Int, right: Int, winch: Int) {
        // Anuluj poprzedni burst job jeśli istnieje
        currentSpeedSendJob?.cancel()
        
        // Konwertuj wartości na format kontrolera (0-10, gdzie 0 = stop)
        val leftConverted = convertSpeedToControllerFormat(left)
        val rightConverted = convertSpeedToControllerFormat(right)
        
        // Zapisz ostatnie wartości (dla keep-alive)
        lastSentLeft = leftConverted
        lastSentRight = rightConverted
        lastSentWinch = winch
        
        // BURST: Wysyłaj 3-5 ramek przy zmianie (event-driven)
        currentSpeedSendJob = viewModelScope.launch {
            repeat(SS_BURST_COUNT) {
                val sNum = nextSNum()
                SocketRepository.send(
                    SocketCommand.SetSpeed(
                        left = leftConverted,
                        right = rightConverted,
                        winch = winch,
                        sNum = sNum
                    )
                )
                Log.d("ControllerViewModel", "📤 SS BURST: left=$leftConverted, right=$rightConverted, winch=$winch, sNum=$sNum (${it + 1}/$SS_BURST_COUNT)")
                
                // Czekaj przed następnym wysłaniem (tylko jeśli to nie ostatnia iteracja)
                if (it < SS_BURST_COUNT - 1) {
                    delay(SS_BURST_INTERVAL_MS)
                }
            }
        }
        
        // KEEP-ALIVE: Uruchom/restartuj keep-alive
        startKeepAlive()
    }
    
    /**
     * Uruchamia keep-alive - wysyła aktualny stan co 2-3s nawet bez zmian.
     * Zapewnia, że łódka otrzymuje aktualny stan regularnie (watchdog na łódce).
     */
    private fun startKeepAlive() {
        // Anuluj poprzedni keep-alive jeśli istnieje
        keepAliveJob?.cancel()
        
        keepAliveJob = viewModelScope.launch {
            // Poczekaj chwilę po burst (żeby nie wysyłać od razu)
            delay(SS_KEEP_ALIVE_INTERVAL_MS)
            
            // Wysyłaj keep-alive w pętli
            while (true) {
                val sNum = nextSNum()
                SocketRepository.send(
                    SocketCommand.SetSpeed(
                        left = lastSentLeft,
                        right = lastSentRight,
                        winch = lastSentWinch,
                        sNum = sNum
                    )
                )
                Log.d("ControllerViewModel", "💓 SS KEEP-ALIVE: left=$lastSentLeft, right=$lastSentRight, winch=$lastSentWinch, sNum=$sNum")
                
                delay(SS_KEEP_ALIVE_INTERVAL_MS)
            }
        }
    }

    fun sendSpeed(left: Double, right: Double) {
        viewModelScope.launch {
            // Konwertuj Double na Int (wartości z sliderów są -80..80)
            val leftInt = left.toInt()
            val rightInt = right.toInt()
            currentSpeed = ((leftInt + rightInt) / 2.0).toFloat()
            Log.d("ControllerViewModel", "🚢 sendSpeed called: left=$leftInt, right=$rightInt, winch=$winchState")
            // Wyślij z interwałem
            sendSpeedWithInterval(leftInt, rightInt, winchState)
        }
    }

    fun sendAction(action: String, payload: String = "") {
        viewModelScope.launch {
            SocketRepository.send(SocketCommand.SetAction(action, payload, nextSNum()))
        }
    }
    
    /**
     * Zmienia stan zwijarki i wysyła komendę SS przez socket tylko jeśli stan się zmienił.
     * @param newState 0 = góra (up), 1 = wyłączony (stop), 2 = dół (down)
     */
    fun updateWinchState(newState: Int) {
        if (winchState != newState) {
            winchState = newState
            // Wyślij SS z aktualnymi wartościami silników i nowym stanem zwijarki
            sendSpeed(leftEnginePower.toDouble(), rightEnginePower.toDouble())
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

enum class ConnectionState {
    Connected,
    Reconnecting,
    Disconnected,
}