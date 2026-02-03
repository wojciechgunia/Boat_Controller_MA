package pl.poznan.put.boatcontroller.ui.waypoint

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import pl.poznan.put.boatcontroller.domain.dataclass.MapMode
import pl.poznan.put.boatcontroller.domain.dataclass.POIObject
import pl.poznan.put.boatcontroller.domain.models.POIUpdateRequest
import pl.poznan.put.boatcontroller.domain.dataclass.ShipPosition
import pl.poznan.put.boatcontroller.domain.models.WaypointCreateRequest
import pl.poznan.put.boatcontroller.domain.dataclass.WaypointObject
import pl.poznan.put.boatcontroller.domain.models.WaypointUpdateRequest
import pl.poznan.put.boatcontroller.domain.enums.ShipDirection
import pl.poznan.put.boatcontroller.backend.local.Repository
import pl.poznan.put.boatcontroller.backend.mappers.toDomain
import pl.poznan.put.boatcontroller.backend.remote.socket.SocketCommand
import pl.poznan.put.boatcontroller.backend.remote.socket.SocketEvent
import pl.poznan.put.boatcontroller.backend.remote.socket.SocketRepository
import pl.poznan.put.boatcontroller.domain.components.info_popup.InfoPopupManager
import pl.poznan.put.boatcontroller.domain.components.info_popup.InfoPopupType
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.sqrt

class WaypointViewModel(app: Application) : AndroidViewModel(app) {
    private var backendApi: ApiService? = null
    private val repo = Repository(app.applicationContext)
    
    private val _missionId = MutableStateFlow(-1)
    val missionId = _missionId.asStateFlow()

    private val _isToolbarOpened = MutableStateFlow(false)
    val isToolbarOpened = _isToolbarOpened.asStateFlow()
    fun setToolbarOpened(opened: Boolean) { _isToolbarOpened.value = opened }

    private val _openPOIDialog = MutableStateFlow(false)
    val openPOIDialog = _openPOIDialog.asStateFlow()
    fun setOpenPOIDialog(open: Boolean) { _openPOIDialog.value = open }

    private val _poiId = MutableStateFlow(-1)
    val poiId = _poiId.asStateFlow()
    fun setPoiId(id: Int) { _poiId.value = id }

    private val _mapLibreMapState = MutableStateFlow<MapLibreMap?>(null)
    val mapLibreMapState = _mapLibreMapState.asStateFlow()

    private val _phonePosition = MutableStateFlow<DoubleArray?>(null)
    val phonePosition = _phonePosition.asStateFlow()

    private val _shipPosition = MutableStateFlow(ShipPosition(52.404633, 16.957722))
    val shipPosition = _shipPosition.asStateFlow()

    private val _currentShipDirection = MutableStateFlow(ShipDirection.DEFAULT)
    val currentShipDirection = _currentShipDirection.asStateFlow()

    private val _waypointPositions = MutableStateFlow<List<WaypointObject>>(emptyList())
    val waypointPositions = _waypointPositions.asStateFlow()

    private val _waypointBitmaps = MutableStateFlow<Map<Int, Bitmap>>(emptyMap())
    val waypointBitmaps = _waypointBitmaps.asStateFlow()

    private val _waypointToMoveNo = MutableStateFlow<Int?>(null)
    val waypointToMoveNo = _waypointToMoveNo.asStateFlow()
    fun setWaypointToMoveNo(no: Int?) { _waypointToMoveNo.value = no }

    private val _mapMode = MutableStateFlow<MapMode>(MapMode.None)
    val mapMode = _mapMode.asStateFlow()

    private val _poiPositions = MutableStateFlow<List<POIObject>>(emptyList())
    val poiPositions = _poiPositions.asStateFlow()

    private val _arePoiVisible = MutableStateFlow(false)
    val arePoiVisible = _arePoiVisible.asStateFlow()
    fun setArePoiVisible(visible: Boolean) { _arePoiVisible.value = visible }

    private val _isShipMoving = MutableStateFlow(false)
    val isShipMoving = _isShipMoving.asStateFlow()
    
    // Nawigacja waypointowa
    private var currentWaypointIndex = -1
    private var homePosition: ShipPosition? = null
    private var isGoingHome = false  // Flaga czy wracamy do domu
    private var lastCompletedWaypoint: WaypointObject? = null  // Ostatni osiągnięty waypoint (zapamiętany po zakończeniu trasy)
    private val waypointReachedThreshold = 0.0001 // ~11 metrów

    private val _cameraPosition = MutableStateFlow<CameraPositionState?>(null)
    val cameraPosition = _cameraPosition.asStateFlow()

    // Używamy wspólnego stanu baterii z SocketRepository
    private val _externalBatteryLevel = MutableStateFlow<Int?>(SocketRepository.batteryLevel.value)
    val externalBatteryLevel = _externalBatteryLevel.asStateFlow()

    private val seq = AtomicInteger(0)

    init {
        observeSocket()
        loadSavedMission()
        // Wysyłamy tryb waypoint przy starcie ViewModel
        sendMode("waypoint")
        
        // Obserwuj zmiany baterii z SocketRepository
        viewModelScope.launch {
            SocketRepository.batteryLevel.collectLatest { level ->
                _externalBatteryLevel.value = level
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
                Log.e("WaypointViewModel", "Error loading saved mission", e)
            }
        }
    }

    private fun observeSocket() {
        viewModelScope.launch {
            SocketRepository.events.collectLatest { event ->
                when (event) {
                    is SocketEvent.PositionActualisation -> {
                        // lat/lon już jako Double
                        val newPosition = ShipPosition(event.lat, event.lon)
                        _shipPosition.value = newPosition
                        
                        // Sprawdź czy osiągnięto dom (jeśli wracamy do domu)
                        if (_isShipMoving.value && isGoingHome && homePosition != null) {
                            val distanceToHome = calculateDistance(
                                newPosition.lat, newPosition.lon,
                                homePosition!!.lat, homePosition!!.lon
                            )
                            
                            if (distanceToHome < waypointReachedThreshold) {
                                Log.d("WaypointViewModel", "🏠 Dom osiągnięty! Zatrzymuję statek.")
                                // Zatrzymaj statek
                                sendAction("SP", "")
                                SocketRepository.send(SocketCommand.SetSpeed(0, 0, 1, nextSNum())) // winch = 1 (stop), speed = 0 (stop)
                                _isShipMoving.value = false
                                isGoingHome = false
                            }
                        }
                        
                        // Sprawdź czy osiągnięto ostatni zapamiętany waypoint (gdy waypointy są puste)
                        if (_isShipMoving.value && !isGoingHome && _waypointPositions.value.isEmpty() && lastCompletedWaypoint != null) {
                            val distance = calculateDistance(
                                newPosition.lat, newPosition.lon,
                                lastCompletedWaypoint!!.lat, lastCompletedWaypoint!!.lon
                            )
                            
                            if (distance < waypointReachedThreshold) {
                                Log.d("WaypointViewModel", "Ostatni zapamiętany waypoint osiągnięty! Zatrzymuję statek.")
                                // Zatrzymaj statek
                                sendAction("SP", "")
                                SocketRepository.send(SocketCommand.SetSpeed(0, 0, 1, nextSNum())) // winch = 1 (stop), speed = 0 (stop)
                                _isShipMoving.value = false
                                lastCompletedWaypoint = null  // Wyczyść zapamiętany waypoint
                            }
                        }
                        
                        // Sprawdź czy osiągnięto waypoint (tylko w trybie waypoint i gdy statek się porusza, ale NIE gdy wracamy do domu)
                        if (_isShipMoving.value && !isGoingHome && currentWaypointIndex >= 0 && _waypointPositions.value.isNotEmpty()) {
                            // Sortuj waypointy przed użyciem
                            val sortedWaypoints = _waypointPositions.value.sortedBy { it.no }
                            
                            if (currentWaypointIndex < sortedWaypoints.size) {
                                val targetWp = sortedWaypoints[currentWaypointIndex]
                                val distance = calculateDistance(
                                    newPosition.lat, newPosition.lon,
                                    targetWp.lat, targetWp.lon
                                )
                                
                                if (distance < waypointReachedThreshold) {
                                    Log.d("WaypointViewModel", "Waypoint ${targetWp.no} osiągnięty! Przechodzę do następnego.")
                                    // Przejdź do następnego waypointa
                                    currentWaypointIndex++
                                    if (currentWaypointIndex < sortedWaypoints.size) {
                                        val nextWp = sortedWaypoints[currentWaypointIndex]
                                        sendAction("SW", "${nextWp.lon};${nextWp.lat}")
                                        Log.d("WaypointViewModel", "Wysyłam kolejny waypoint: ${nextWp.no} (${nextWp.lon}, ${nextWp.lat})")
                                    } else {
                                        // Wszystkie waypointy osiągnięte - zapamiętaj ostatni i usuń wszystkie waypointy
                                        val lastWp = sortedWaypoints.lastOrNull()
                                        if (lastWp != null) {
                                            lastCompletedWaypoint = lastWp
                                            Log.d("WaypointViewModel", "Wszystkie waypointy osiągnięte! Zapamiętano ostatni: ${lastWp.no}")
                                        }
                                        
                                        // Usuń wszystkie waypointy z backendu i z listy
                                        clearAllWaypoints()
                                        
                                        // Zatrzymaj statek
                                        toggleStartStop()
                                    }
                                }
                            }
                        }
                    }
                    // WaypointViewModel NIE obsługuje SI - tylko ControllerViewModel
                    is SocketEvent.BoatInformationChange -> {
                        Log.d("Socket", "Boat info change: ${event.name}/${event.captain}/${event.mission}")
                    }
                    is SocketEvent.BoatInformation -> {
                        Log.d("Socket", "Boat info: ${event.name}/${event.captain}/${event.mission}")
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
                    else -> null
                }
            }
        }
    }
    
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        // Prosta odległość euklidesowa w stopniach (dla małych odległości)
        val dlat = lat2 - lat1
        val dlon = lon2 - lon1
        return sqrt(dlat * dlat + dlon * dlon)
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

    private fun nextSNum(): Int = seq.incrementAndGet()

    private fun sendAction(action: String, payload: String = "") {
        viewModelScope.launch {
            SocketRepository.send(SocketCommand.SetAction(action, payload, nextSNum()))
        }
    }

    fun sendMode(mode: String) {
        sendAction("SM", mode)
    }

    fun goToHome() {
        viewModelScope.launch {
            // Zapamiętaj pozycję startową jeśli nie jest zapisana
            if (homePosition == null) {
                homePosition = _shipPosition.value
                Log.d("WaypointViewModel", "Zapisano pozycję startową jako home: ${homePosition?.lat}, ${homePosition?.lon}")
            }
            
            // Wyślij GH (Go Home)
            sendAction("GH", "")
            
            // Ustaw flagę że wracamy do domu
            isGoingHome = true
            currentWaypointIndex = -1  // Reset waypoint index
            
            // Jeśli statek nie jest uruchomiony, uruchom go
            if (!_isShipMoving.value) {
                // Wyślij ST (Start)
                sendAction("ST", "")
                
                // Wyślij SS (Set Speed) z prędkością, żeby łódka zaczęła płynąć
                SocketRepository.send(SocketCommand.SetSpeed(5, 5, 1, nextSNum())) // winch = 1 (stop), speed = 5 (średnia prędkość)
                
                _isShipMoving.value = true
                Log.d("WaypointViewModel", "🏠 Powrót do domu - uruchomiono statek")
            } else {
                // Statek już płynie - upewnij się że ma prędkość
                SocketRepository.send(SocketCommand.SetSpeed(5, 5, 1, nextSNum())) // winch = 1 (stop), speed = 5 (średnia prędkość)
                Log.d("WaypointViewModel", "🏠 Powrót do domu - kontynuuję z prędkością")
            }
        }
    }
    
    fun toggleStartStop() {
        viewModelScope.launch {
            if (!_isShipMoving.value) {
                // Start
                if (isGoingHome) {
                    // Jeśli wracamy do domu, kontynuuj powrót
                    if (homePosition == null) {
                        Log.w("WaypointViewModel", "Brak zapisanej pozycji startowej - nie można wrócić do domu")
                        return@launch
                    }
                    
                    // Wyślij ST (Start)
                    sendAction("ST", "")
                    
                    // Wyślij GH (Go Home) ponownie, żeby upewnić się że cel jest ustawiony
                    sendAction("GH", "")
                    
                    // Wyślij SS (Set Speed) z prędkością
                    SocketRepository.send(SocketCommand.SetSpeed(5, 5, 1, nextSNum())) // winch = 1 (stop), speed = 5 (średnia prędkość)
                    
                    _isShipMoving.value = true
                    Log.d("WaypointViewModel", "🏠 Wznowiono powrót do domu")
                } else {
                    // Normalna nawigacja waypointowa
                    if (_waypointPositions.value.isEmpty()) {
                        // Jeśli waypointy są puste, ale mamy zapamiętany ostatni waypoint (po powrocie do domu)
                        if (lastCompletedWaypoint != null) {
                            // Zapisz pozycję startową jako home
                            if (homePosition == null) {
                                homePosition = _shipPosition.value
                            }
                            isGoingHome = false  // Reset flagi
                            
                            // Wyślij ST (Start)
                            sendAction("ST", "")
                            
                            // Wyślij SS (Set Speed) z domyślną prędkością
                            SocketRepository.send(SocketCommand.SetSpeed(5, 5, 1, nextSNum())) // winch = 1 (stop), speed = 5 (średnia prędkość)
                            
                            // Wyślij ostatni waypoint jako cel
                            sendAction("SW", "${lastCompletedWaypoint!!.lon};${lastCompletedWaypoint!!.lat}")
                            
                            _isShipMoving.value = true
                            Log.d("WaypointViewModel", "🚀 Start nawigacji do ostatniego waypointa (${lastCompletedWaypoint!!.lon}, ${lastCompletedWaypoint!!.lat})")
                        } else {
                            Log.w("WaypointViewModel", "Brak waypointów i brak zapamiętanego ostatniego waypointa - nie można rozpocząć nawigacji")
                            return@launch
                        }
                    } else {
                        // Normalna nawigacja z listą waypointów
                        // Zapisz pozycję startową jako home
                        homePosition = _shipPosition.value
                        isGoingHome = false  // Reset flagi
                        lastCompletedWaypoint = null  // Reset zapamiętanego waypointa
                        
                        // Sortuj waypointy po numerze (no) przed użyciem
                        val sortedWaypoints = _waypointPositions.value.sortedBy { it.no }
                        
                        // Wyślij ST (Start)
                        sendAction("ST", "")
                        
                        // Wyślij SS (Set Speed) z domyślną prędkością, żeby łódka zaczęła płynąć
                        // Używamy 0.5 dla obu silników (średnia prędkość)
                        SocketRepository.send(SocketCommand.SetSpeed(5, 5, 1, nextSNum())) // winch = 1 (stop), speed = 5 (średnia prędkość)
                        
                        // Wyślij pierwszy waypoint
                        currentWaypointIndex = 0
                        val firstWp = sortedWaypoints[0]
                        sendAction("SW", "${firstWp.lon};${firstWp.lat}")
                        
                        _isShipMoving.value = true
                        Log.d("WaypointViewModel", "🚀 Start nawigacji do waypointa ${firstWp.no} (${firstWp.lon}, ${firstWp.lat})")
                    }
                }
            } else {
                // Stop/Pause
                sendAction("SP", "")
                // Zatrzymaj silniki (speed = 0)
                SocketRepository.send(SocketCommand.SetSpeed(0, 0, 1, nextSNum())) // winch = 1 (stop), speed = 0 (stop)
                _isShipMoving.value = false
                Log.d("WaypointViewModel", "⏸️  Pauza nawigacji")
            }
        }
    }



    fun getNextAvailableWaypointNo(): Int {
        val usedIds = _waypointPositions.value.map { it.no }.toSet()
        var no = 1
        while (no in usedIds) {
            no++
        }
        return no
    }

    fun getWaypointByNo(no: Int): WaypointObject? {
        return waypointPositions.value.find { it.no == no }
    }

    fun addWaypoint(lon: Double, lat: Double) {
        val no = getNextAvailableWaypointNo()
        viewModelScope.launch {
            try {
                val req = WaypointCreateRequest(
                    missionId = missionId.value,
                    no = no,
                    lon = lon.toString(),
                    lat = lat.toString()
                )
                Log.d("WAYPOINTS", req.toString())
                backendApi?.createWaypoint(req)

                val waypoint = WaypointObject(no, lon, lat)
                _waypointPositions.value = _waypointPositions.value + waypoint
                sendAction("SW", "${lon};${lat}")
            } catch (e: Exception) {
                Log.e("API", "Błąd dodawania waypointu", e)
            }
        }
    }

    private fun clearAllWaypoints() {
        viewModelScope.launch {
            try {
                val response = backendApi?.getWaypointsList(missionId.value)
                if (response == null || !response.isSuccessful) {
                    Log.e("API", "Nie udało się pobrać waypointów do usunięcia")
                    return@launch
                }

                val waypoints = response.body()?.toMutableList() ?: mutableListOf()
                
                // Usuń wszystkie waypointy z backendu
                waypoints.forEach { wp ->
                    try {
                        backendApi?.deleteWaypoint(wp.id)
                        Log.d("WaypointViewModel", "Usunięto waypoint ${wp.no} z backendu")
                    } catch (e: Exception) {
                        Log.e("API", "Błąd usuwania waypointu ${wp.no}", e)
                    }
                }

                // Wyczyść listę waypointów
                _waypointPositions.value = emptyList()
                
                Log.d("WaypointViewModel", "Wszystkie waypointy zostały usunięte")
            } catch (e: Exception) {
                Log.e("API", "Błąd usuwania wszystkich waypointów", e)
            }
        }
    }

    fun removeWaypoint(no: Int) {
        viewModelScope.launch {
            try {
                val response = backendApi?.getWaypointsList(missionId.value)
                if (response == null || !response.isSuccessful) {
                    Log.e("API", "Nie udało się pobrać waypointów")
                    return@launch
                }

                val waypoints = response.body()?.toMutableList() ?: mutableListOf()
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

                    _waypointPositions.value = waypoints.map { it.toDomain() }

                    // Brak dedykowanej komendy usunięcia w nowym protokole – pomijamy wysyłkę.
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
                val response = backendApi?.getWaypointsList(missionId.value)
                if (response == null || !response.isSuccessful) {
                    Log.e("API", "Nie udało się pobrać waypointów")
                    return@launch
                }
                val waypoints = response.body()?.toMutableList() ?: mutableListOf()
                val targetWp = waypoints.firstOrNull { it.no == no }

                if (targetWp != null) {
                    val req = WaypointUpdateRequest(
                        lon = newLon.toString(),
                        lat = newLat.toString()
                    )
                    backendApi?.updateWaypoint(targetWp.id, req)

                    val currentList = _waypointPositions.value.toMutableList()
                    val index = currentList.indexOfFirst { it.no == no }
                    if (index != -1) {
                        currentList[index] = currentList[index].copy(lon = newLon, lat = newLat)
                        _waypointPositions.value = currentList
                    }
                    // Aktualizujemy pozycję poprzez akcję SW (ustaw waypoint)
                    sendAction("SW", "${newLon};${newLat}")
                } else {
                    Log.w("API", "Nie znaleziono waypointu o numerze $no")
                }
            } catch (e: Exception) {
                Log.e("API", "Błąd przesuwania waypointu", e)
            }
        }
    }

    fun setWaypointBitmap(id: Int, bitmap: Bitmap) {
        _waypointBitmaps.update { it + (id to bitmap) }
    }

    fun setMapReady(map: MapLibreMap) {
        _mapLibreMapState.value = map
    }

    fun setPhonePosition(lat: Double, lon: Double) {
        _phonePosition.value = doubleArrayOf(lat, lon)
    }

    fun toggleMapEditMode(mode: MapMode) {
        val current = _mapMode.value
        _mapMode.value = if (current == mode) {
            MapMode.None
        } else {
            mode
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

    fun getWaypointsFeature(): List<Feature> {
        return _waypointPositions.value.map {
            Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat)).apply {
                addStringProperty("no", it.no.toString())
                addStringProperty("icon", "waypoint-icon-${it.no}")
            }
        }
    }

    fun getPoiFeature(): List<Feature> {
        Log.d("POI", _poiPositions.value.toString())
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
