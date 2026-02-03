package pl.poznan.put.boatcontroller.ui.controller

import android.app.Application
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import pl.poznan.put.boatcontroller.backend.remote.db.ApiClient
import pl.poznan.put.boatcontroller.backend.remote.db.ApiService
import pl.poznan.put.boatcontroller.domain.dataclass.CameraPositionState
import pl.poznan.put.boatcontroller.domain.dataclass.ShipHomePosition
import pl.poznan.put.boatcontroller.domain.models.POICreateRequest
import pl.poznan.put.boatcontroller.domain.dataclass.POIObject
import pl.poznan.put.boatcontroller.domain.models.POIUpdateRequest
import pl.poznan.put.boatcontroller.domain.dataclass.ShipPosition
import pl.poznan.put.boatcontroller.domain.dataclass.ShipSensorsData
import pl.poznan.put.boatcontroller.domain.dataclass.WaypointObject
import pl.poznan.put.boatcontroller.domain.enums.ControllerTab
import pl.poznan.put.boatcontroller.domain.enums.MapLayersVisibilityMode
import pl.poznan.put.boatcontroller.backend.local.Repository
import pl.poznan.put.boatcontroller.backend.mappers.toDomain
import pl.poznan.put.boatcontroller.backend.remote.http.HttpStreamRepository
import pl.poznan.put.boatcontroller.backend.remote.socket.SocketCommand
import pl.poznan.put.boatcontroller.backend.remote.socket.SocketEvent
import pl.poznan.put.boatcontroller.backend.remote.socket.SocketRepository
import pl.poznan.put.boatcontroller.domain.components.info_popup.InfoPopupManager
import pl.poznan.put.boatcontroller.domain.components.info_popup.InfoPopupType
import pl.poznan.put.boatcontroller.domain.enums.ConnectionState
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.cos

class ControllerViewModel(app: Application) : AndroidViewModel(app) {
    private var backendApi: ApiService? = null
    private val repo = Repository(app.applicationContext)
    private val seq = AtomicInteger(0)

    private val _missionId = MutableStateFlow(-1)
    val missionId = _missionId.asStateFlow()

    private val _openPOIDialog = MutableStateFlow(false)
    val openPOIDialog = _openPOIDialog.asStateFlow()

    private val _poiId = MutableStateFlow(-1)
    val poiId = _poiId.asStateFlow()

    // Silniki drona (lewy + prawy), zakres od -80..80 (mapowane do LoRa na zakres 0..100)
    private val _leftEnginePower = MutableStateFlow(0)
    val leftEnginePower = _leftEnginePower.asStateFlow()

    private val _rightEnginePower = MutableStateFlow(0)
    val rightEnginePower = _rightEnginePower.asStateFlow()

    // Stan silnika zwijarki: 2 = góra (up), 1 = wyłączony (stop), 0 = dół (down)
    private val _winchState = MutableStateFlow(1) // Domyślnie wyłączony
    val winchState = _winchState.asStateFlow()

    private val _currentSpeed = MutableStateFlow(0.0f)
    val currentSpeed = _currentSpeed.asStateFlow()

    // Mechanizm wysyłania SS z burst + keep-alive (optymalizacja LoRa)
    private var currentSpeedSendJob: Job? = null
    private var keepAliveJob: Job? = null
    private val ssBurstCount = 4 // Liczba ramek w burst (3-5)
    private val ssBurstIntervalMs = 200L // Interwał między ramkami w burst (150-300ms)
    private val ssKeepAliveIntervalMs = 2500L // Keep-alive co 2-3s

    // Ostatnie wysłane wartości (dla keep-alive)
    private var lastSentLeft = _leftEnginePower.value
    private var lastSentRight = _rightEnginePower.value
    private var lastSentWinch = _winchState.value

    private val _selectedTab = MutableStateFlow(ControllerTab.MAP)
    val selectedTab = _selectedTab.asStateFlow()

    private val _mapLibreMapState = MutableStateFlow<MapLibreMap?>(null)
    val mapLibreMapState = _mapLibreMapState.asStateFlow()

    private val _phonePosition = MutableStateFlow<DoubleArray?>(null)
    val phonePosition = _phonePosition.asStateFlow()

    private val _Ship_homePosition = MutableStateFlow(ShipHomePosition(0.0, 0.0))
    val homePosition = _Ship_homePosition.asStateFlow()

    private val _shipPosition = MutableStateFlow(ShipPosition(52.404633, 16.957722))
    val shipPosition = _shipPosition.asStateFlow()

    private val _waypointPositions = MutableStateFlow<List<WaypointObject>>(emptyList())
    val waypointPositions = _waypointPositions.asStateFlow()

    private val _poiPositions = MutableStateFlow<List<POIObject>>(emptyList())
    val poiPositions = _poiPositions.asStateFlow()

    private val _waypointBitmaps = MutableStateFlow<Map<Int, Bitmap>>(emptyMap())
    val waypointBitmaps = _waypointBitmaps.asStateFlow()

    private val _cameraPosition = MutableStateFlow<CameraPositionState?>(null)
    val cameraPosition = _cameraPosition.asStateFlow()

    private val _layersMode = MutableStateFlow(MapLayersVisibilityMode.BOTH_VISIBLE)
    val layersMode = _layersMode.asStateFlow()

    // Używamy wspólnego stanu baterii z SocketRepository
    private val _externalBatteryLevel = MutableStateFlow<Int?>(SocketRepository.batteryLevel.value)
    val externalBatteryLevel = _externalBatteryLevel.asStateFlow()

    private val _sensorsData = MutableStateFlow(ShipSensorsData())
    val sensorsData = _sensorsData.asStateFlow()

    // ===================== HTTP Streams – konfiguracja =====================
    // Jeden stan połączenia dla aktywnego streamu
    private val _httpConnectionState = MutableStateFlow(ConnectionState.Disconnected)
    val httpConnectionState = _httpConnectionState.asStateFlow()

    private val _httpErrorMessage = MutableStateFlow<String?>(null)
    val httpErrorMessage = _httpErrorMessage.asStateFlow()

    init {
        // Obserwuj zmiany baterii z SocketRepository
        viewModelScope.launch {
            SocketRepository.batteryLevel.collect { level ->
                _externalBatteryLevel.value = level
            }
        }
    }

    // Setters
    fun setMissionId(id: Int) { _missionId.value = id }
    fun setOpenPOIDialog(open: Boolean) { _openPOIDialog.value = open }
    fun setPoiId(id: Int) { _poiId.value = id }
    fun setLeftEnginePower(power: Int) { _leftEnginePower.value = power }
    fun setRightEnginePower(power: Int) { _rightEnginePower.value = power }
    fun setSelectedTab(tab: ControllerTab) { _selectedTab.value = tab }

    fun mapUpdate(latitude: Double, longitude: Double, speed: Float) {
        _shipPosition.value = ShipPosition(latitude, longitude)
        _currentSpeed.value = speed
        println("Ship position: ${shipPosition.value}")
    }

    fun updateHomePosition(shipHomePosition: ShipHomePosition) {
        _Ship_homePosition.value = shipHomePosition
        println("Home position: $shipHomePosition")
    }

    fun updateSensorsData(sensorsData: ShipSensorsData) {
        _sensorsData.value = sensorsData
        println("Sensors data: $sensorsData")
    }

    fun initModel() {
        viewModelScope.launch {
            try {
                Log.d("Get Mission Id", missionId.value.toString())
                backendApi = ApiClient.create(getApplication())
                loadMission()
                backendApi?.let { api ->
                    val response = api.getWaypointsList(missionId.value)
                    if (response.isSuccessful) {
                        val list = response.body()!!.map { it.toDomain() }
                        if (list.isNotEmpty()) {
                            _waypointPositions.value = list
                        }
                        Log.d("Way", _waypointPositions.value.toString())
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
                val response = api.getPoiList(missionId.value)
                if (response.isSuccessful) {
                    val poiList = response.body()!!.map { it.toDomain() }
                    _poiPositions.value = poiList
                    Log.d("POI", _poiPositions.value.toString())
                }
            }
        }
    }

    init {
        observeSocket()
        observeSocketConnection()
        loadSavedMission()
        observeHttpStreamConnections()
    }
    
    private fun observeHttpStreamConnections() {
        viewModelScope.launch {
            HttpStreamRepository.connectionState.collect { state ->
                _httpConnectionState.value = state
            }
        }
        viewModelScope.launch {
            HttpStreamRepository.errorMessage.collect { error ->
                _httpErrorMessage.value = error
            }
        }
    }
    
    private fun loadSavedMission() {
        viewModelScope.launch {
            try {
                repo.get().collect { userData ->
                    if (userData.selectedMissionId != -1 && missionId.value == -1) {
                        _missionId.value = userData.selectedMissionId
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
                        mapUpdate(event.lat, event.lon, speedMs.toFloat())
                    }
                    is SocketEvent.SensorInformation -> {
                        // Konwersja z Int na Double
                        // accel/gyro/mag/depth: *100, kąty: bez konwersji (już Int jako Double)
                        val conversionFrac = 100.0
                        updateSensorsData(
                            ShipSensorsData(
                                accelX = event.accelX / conversionFrac,
                                accelY = event.accelY / conversionFrac,
                                accelZ = event.accelZ / conversionFrac,
                                gyroX = event.gyroX / conversionFrac,
                                gyroY = event.gyroY / conversionFrac,
                                gyroZ = event.gyroZ / conversionFrac,
                                magX = event.magX / conversionFrac,
                                magY = event.magY / conversionFrac,
                                magZ = event.magZ / conversionFrac,
                                angleX = event.angleX,
                                angleY = event.angleY,
                                angleZ = event.angleZ,
                                depth = event.depth / conversionFrac
                            )
                        )
                    }
                    is SocketEvent.BoatInformation -> {
                        Log.d("Socket", "Boat info: ${event.name}/${event.captain}/${event.mission}")
                    }
                    is SocketEvent.BoatInformationChange -> {
                        Log.d("Socket", "Boat info change: ${event.name}/${event.captain}/${event.mission}")
                    }
                    is SocketEvent.WarningInformation -> {
                        Log.w("Socket", "Warning: ${event.infoCode}")
                        val message = when (event.infoCode) {
                            "COLLISION" -> "Wykryto kolizję! Zatrzymaj łódkę natychmiast!"
                            else -> "Ostrzeżenie: ${event.infoCode}"
                        }
                        InfoPopupManager.show(
                            message = message,
                            type = InfoPopupType.WARNING
                        )
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
            SocketRepository.connectionState.collect { connected ->
                if (connected) {
                    // Wysyłamy tryb manual przy połączeniu
                    sendAction("SM", "manual")
                    
                    // Po każdym ponownym zestawieniu połączenia wyślij aktualne moce silników,
                    // żeby łódka (lub serwer testowy) od razu dostała wartości SS
                    // nawet jeśli użytkownik nic nie przesunie po reconnect.
                    // Używamy sendSpeedWithInterval aby wysłać z interwałem
                    Log.d(
                        "ControllerViewModel",
                        "Socket connected – resending current speed L=${leftEnginePower.value} R=${rightEnginePower.value}"
                    )
                    sendSpeedWithInterval(leftEnginePower.value, rightEnginePower.value, winchState.value)
                }
            }
        }
    }

    private fun nextSNum(): Int = seq.incrementAndGet()

    private fun convertSpeedToControllerFormat(speed: Int): Int {
        return ((speed + 80) / 1.6).toInt().coerceIn(0, 100)
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
        
        // Konwertuj wartości na format kontrolera (0-100, gdzie 0 = stop)
        val leftConverted = convertSpeedToControllerFormat(left)
        val rightConverted = convertSpeedToControllerFormat(right)
        
        // Zapisz ostatnie wartości (dla keep-alive)
        lastSentLeft = leftConverted
        lastSentRight = rightConverted
        lastSentWinch = winch
        
        // BURST: Wysyłaj 3-5 ramek przy zmianie (event-driven)
        currentSpeedSendJob = viewModelScope.launch {
            repeat(ssBurstCount) {
                val sNum = nextSNum()
                SocketRepository.send(
                    SocketCommand.SetSpeed(
                        left = leftConverted,
                        right = rightConverted,
                        winch = winch,
                        sNum = sNum
                    )
                )
                
                // Czekaj przed następnym wysłaniem (tylko jeśli to nie ostatnia iteracja)
                if (it < ssBurstCount - 1) {
                    delay(ssBurstIntervalMs)
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
            delay(ssKeepAliveIntervalMs)
            
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
                
                delay(ssKeepAliveIntervalMs)
            }
        }
    }

    fun sendSpeed(left: Int, right: Int) {
        viewModelScope.launch {
            _currentSpeed.value = ((left + right) / 2.0).toFloat()
            // Wyślij z interwałem
            sendSpeedWithInterval(left, right, winchState.value)
        }
    }

    fun sendAction(action: String, payload: String = "") {
        viewModelScope.launch {
            SocketRepository.send(SocketCommand.SetAction(action, payload, nextSNum()))
        }
    }
    
    /**
     * Zmienia stan zwijarki i wysyła komendę SS przez socket tylko jeśli stan się zmienił.
     * @param newState 2 = góra (up), 1 = wyłączony (stop), 1 = dół (down)
     */
    fun updateWinchState(newState: Int) {
        if (_winchState.value != newState) {
            _winchState.value = newState
            // Wyślij SS z aktualnymi wartościami silników i nowym stanem zwijarki
            sendSpeed(leftEnginePower.value, rightEnginePower.value)
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
            _Ship_homePosition.value.lon,
            _Ship_homePosition.value.lat
        )

        val shipFeature = Feature.fromGeometry(shipCoordinates).apply {
            addStringProperty("title", "Home")
        }

        return FeatureCollection.fromFeature(shipFeature)
    }

    fun getWaypointsFeature(): List<Feature> {
        return _waypointPositions.value.map {
            Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat)).apply {
                addStringProperty("no", it.no.toString())
                addStringProperty("icon", "waypoint-icon-${it.no}")
            }
        }
    }

    fun getPoiFeature(): List<Feature> {
        return _poiPositions.value.map {
            Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat)).apply {
                addStringProperty("id", it.id.toString())
                addStringProperty("icon", "poi-icon")
            }
        }
    }

    fun getPhoneLocationFeature(): FeatureCollection {
        // Jeśli nie mamy pozycji telefonu – nie pokazujemy go w ogóle
        val phonePos = _phonePosition.value ?: return FeatureCollection.fromFeatures(emptyList())

        val lat = phonePos[0]
        val lon = phonePos[1]

        val phoneCoordinates = Point.fromLngLat(lon, lat)

        val phoneFeature = Feature.fromGeometry(phoneCoordinates).apply {
            addStringProperty("title", "Phone")
        }

        return FeatureCollection.fromFeature(phoneFeature)
    }

    fun getConnectionLinesFeature(): List<Feature> {
        val lines = mutableListOf<Feature>()
        val waypoints = _waypointPositions.value.sortedBy { it.no }

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
        _waypointBitmaps.update { it + (id to bitmap) }
    }

    fun setPhonePosition(lat: Double, lon: Double) {
        _phonePosition.value = doubleArrayOf(lat, lon)
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
    
    /**
     * Konwertuje bitmapę na base64 string.
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        // Kompresuj do JPEG z jakością 85% aby zmniejszyć rozmiar
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
    
    /**
     * Sprawdza czy istnieje POI w pobliżu danej pozycji.
     * @param lat Szerokość geograficzna
     * @param lon Długość geograficzna
     * @param radiusMeters Promień w metrach (domyślnie 10m)
     * @return POI w pobliżu lub null jeśli nie znaleziono
     */
    private fun findNearbyPoi(lat: Double, lon: Double, radiusMeters: Double = 10.0): POIObject? {
        // Przybliżone obliczenie odległości w metrach (Haversine formula uproszczona dla małych odległości)
        // 1 stopień ≈ 111 km
        val latDiff = radiusMeters / 111000.0
        val lonDiff = radiusMeters / (111000.0 * cos(Math.toRadians(lat)))
        
        return _poiPositions.value.firstOrNull { poi ->
            val latDistance = abs(poi.lat - lat)
            val lonDistance = abs(poi.lon - lon)
            latDistance <= latDiff && lonDistance <= lonDiff
        }
    }
    
    /**
     * Tworzy lub aktualizuje POI z obrazem.
     * Jeśli POI istnieje w pobliżu (promień ~10m), dodaje obraz do istniejącego POI.
     * W przeciwnym razie tworzy nowy POI.
     * 
     * @param bitmap Bitmapa do zapisania (z kamery lub sonaru)
     * @param name Opcjonalna nazwa POI
     * @param description Opcjonalny opis POI
     */
    fun createPoiWithImage(
        bitmap: Bitmap?,
        name: String? = null,
        description: String? = null
    ) {
        if (bitmap == null) {
            Log.e("ControllerViewModel", "❌ Bitmap is null, cannot create POI")
            InfoPopupManager.show(
                message = "Nie można przechwycić obrazu. Spróbuj ponownie.",
                type = InfoPopupType.ERROR
            )
            return
        }
        
        if (missionId.value == -1) {
            Log.e("ControllerViewModel", "❌ Mission ID is -1, cannot create POI")
            InfoPopupManager.show(
                message = "Brak wybranej misji. Wybierz misję przed zapisem POI.",
                type = InfoPopupType.ERROR
            )
            return
        }
        
        viewModelScope.launch {
            try {
                // Pobierz aktualną pozycję łódki
                val shipPos = _shipPosition.value
                val lat = shipPos.lat
                val lon = shipPos.lon
                
                // Sprawdź czy istnieje POI w pobliżu
                val nearbyPoi = findNearbyPoi(lat, lon)
                
                // Konwertuj bitmapę na base64
                val imageBase64 = bitmapToBase64(bitmap)
                // Tworzymy URL w formacie data URI (tymczasowe rozwiązanie)
                val imageUrl = "data:image/jpeg;base64,$imageBase64"
                
                if (nearbyPoi != null) {
                    // POI istnieje w pobliżu - dodajemy obraz do istniejącego POI
                    Log.d("ControllerViewModel", "📍 Found nearby POI (id=${nearbyPoi.id}), adding image")
                    
                    // Pobierz aktualną listę obrazów
                    val existingPictures = nearbyPoi.pictures?.let { pics ->
                        try {
                            // Parsuj JSON string do listy
                            val gson = Gson()
                            val listType = object : TypeToken<List<String>>() {}.type
                            gson.fromJson<List<String>>(pics, listType) ?: emptyList()
                        } catch (e: Exception) {
                            Log.w("ControllerViewModel", "Failed to parse existing pictures", e)
                            emptyList()
                        }
                    } ?: emptyList()
                    
                    // Dodaj nowy obraz do listy
                    val updatedPictures = existingPictures + imageUrl
                    
                    // Konwertuj z powrotem na JSON string
                    val gson = Gson()
                    val picturesJson = gson.toJson(updatedPictures)
                    
                    // Aktualizuj POI z nową listą obrazów
                    updatePoiWithPictures(nearbyPoi.id, picturesJson)
                } else {
                    // Nie znaleziono POI w pobliżu - tworzymy nowy
                    Log.d("ControllerViewModel", "📍 No nearby POI found, creating new POI")
                    createNewPoi(lat, lon, name, description, listOf(imageUrl))
                }
            } catch (e: Exception) {
                Log.e("ControllerViewModel", "❌ Error creating POI with image", e)
                InfoPopupManager.show(
                    message = "Błąd podczas zapisywania POI: ${e.message}",
                    type = InfoPopupType.ERROR
                )
            }
        }
    }
    
    /**
     * Aktualizuje istniejący POI z nową listą obrazów.
     */
    private suspend fun updatePoiWithPictures(poiId: Int, picturesJson: String) {
        val backendApi = backendApi ?: run {
            Log.e("ControllerViewModel", "❌ Backend API is null")
            return
        }
        
        // Utwórz request z aktualizacją pictures
        val request = POIUpdateRequest(
            name = null, // Nie zmieniamy nazwy
            description = null, // Nie zmieniamy opisu
            pictures = picturesJson
        )
        
        // Wyślij request
        val response = backendApi.updatePoi(poiId, request)
        
        if (response.isSuccessful) {
            Log.d("ControllerViewModel", "✅ POI updated successfully with new image")
            InfoPopupManager.show(
                message = "Obraz dodany do istniejącego POI",
                type = InfoPopupType.SUCCESS
            )
            // Odśwież listę POI
            loadMission()
        } else {
            Log.e("ControllerViewModel", "❌ Failed to update POI: ${response.code()}")
            InfoPopupManager.show(
                message = "Błąd podczas aktualizacji POI: ${response.code()}",
                type = InfoPopupType.ERROR
            )
        }
    }
    
    /**
     * Tworzy nowy POI z listą obrazów.
     */
    private suspend fun createNewPoi(
        lat: Double,
        lon: Double,
        name: String?,
        description: String?,
        pictures: List<String>
    ) {
        val backendApi = backendApi ?: run {
            Log.e("ControllerViewModel", "❌ Backend API is null")
            return
        }
        
        // Konwertuj listę obrazów na JSON string
        val gson = Gson()
        val picturesJson = gson.toJson(pictures)
        
        // Utwórz request
        val request = POICreateRequest(
            missionId = missionId.value,
            lat = lat.toString(),
            lon = lon.toString(),
            name = name,
            description = description,
            pictures = picturesJson
        )
        
        // Wyślij request
        val response = backendApi.createPoi(request)
        
        if (response.isSuccessful) {
            Log.d("ControllerViewModel", "✅ POI created successfully")
            InfoPopupManager.show(
                message = "POI zapisany pomyślnie",
                type = InfoPopupType.SUCCESS
            )
            // Odśwież listę POI
            loadMission()
        } else {
            Log.e("ControllerViewModel", "❌ Failed to create POI: ${response.code()}")
            InfoPopupManager.show(
                message = "Błąd podczas zapisywania POI: ${response.code()}",
                type = InfoPopupType.ERROR
            )
        }
    }
}

