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
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import pl.poznan.put.boatcontroller.dataclass.BaseMessage
import pl.poznan.put.boatcontroller.dataclass.CameraPositionState
import pl.poznan.put.boatcontroller.dataclass.CompletedWaypointMessage
import pl.poznan.put.boatcontroller.dataclass.FinishedMessage
import pl.poznan.put.boatcontroller.dataclass.PositionMessage
import pl.poznan.put.boatcontroller.dataclass.ShipPosition
import pl.poznan.put.boatcontroller.dataclass.WaypointObject
import pl.poznan.put.boatcontroller.enums.FlagMode
import pl.poznan.put.boatcontroller.location_api.WaypointSocketClient

class WaypointViewModel(app: Application) : AndroidViewModel(app) {
    var isToolbarOpened by mutableStateOf(false)
    var waypointStartCoordinates = ShipPosition(52.404633, 16.957722)

    private val _shipPosition = mutableStateOf<ShipPosition>(waypointStartCoordinates)
    val shipPosition: MutableState<ShipPosition> = _shipPosition

    private val _flagBitmaps = mutableStateMapOf<Int, Bitmap>()
    val flagBitmaps: Map<Int, Bitmap> = _flagBitmaps

    private val _flagPositions = mutableStateListOf<WaypointObject>()
    var flagPositions: SnapshotStateList<WaypointObject> = _flagPositions

    private val _isShipMoving = mutableStateOf(false)
    val isShipMoving: MutableState<Boolean> = _isShipMoving

    var flagToMoveId: Int? by mutableStateOf(null)
    var flagMode by mutableStateOf<FlagMode?>(null)
        private set

    private var shipMovingJob: Job? = null
    private var socketClient: WaypointSocketClient? = null

    private val _cameraPosition = mutableStateOf<CameraPositionState?>(null)
    val cameraPosition: MutableState<CameraPositionState?> = _cameraPosition

    init {
        addFlag(_shipPosition.value.lon, _shipPosition.value.lat)
    }

    fun startShipSimulation() {
        val flags = flagPositions.sortedBy { it.id }
        if (flags.isEmpty()) return
        val message = buildJsonShipPayload()

        socketClient = WaypointSocketClient(
            "192.168.1.4",
            2137,
            onGetMessage = { serverMessage ->
                when (val update = parseServerMessage(serverMessage)) {
                    is PositionMessage -> {
                        _shipPosition.value = ShipPosition(update.ship.lat, update.ship.lon)
//                        Log.d("POSITION_ZERO", "Pozycja poczatkowa: ${waypointStartPosition.value.lat}, ${waypointStartPosition.value.lon}")
//                        Log.d("POSITION_SHIP", "Pozycja statku: ${update.ship.lat}, ${update.ship.lon}")
                    }
                    is CompletedWaypointMessage -> {
                        update.flags.forEach { updatedFlag ->
                            flagPositions.find { it.id == updatedFlag.id }?.isCompleted = updatedFlag.isCompleted
                        }
                    }
                    is FinishedMessage -> {
                        onSimulationFinished()
                        Log.d("FINISHED", "Ukończono trasę!")
                    } else -> {
                        Log.w("Socket", "Nieznany typ wiadomości: ${update?.type}")
                    }
                }
            }
        )

        shipMovingJob = viewModelScope.launch {
            try {
                socketClient?.connectAndSend(message)
                _isShipMoving.value = true
            } catch (e: Exception) {
                e.printStackTrace()
                _isShipMoving.value = false
                shipMovingJob = null
            }
        }
    }

    fun stopShipSimulation() {
        socketClient?.disconnect()
        shipMovingJob?.cancel()
        shipMovingJob = null
        _isShipMoving.value = false
    }

    fun toggleSimulation() {
        if (isShipMoving.value) {
            stopShipSimulation()
        } else {
            startShipSimulation()
        }
    }

    private fun buildJsonShipPayload(): String {
        val flagsJson = flagPositions.sortedBy { it.id }.joinToString(",") {
            """{"id":${it.id},"lat":${it.lat},"lon":${it.lon},"isCompleted":${it.isCompleted}}"""
        }

        val ship = shipPosition.value
        return """
        {
            "type": "START",
            "ship": {"lat": ${ship.lat}, "lon": ${ship.lon}},
            "flags": [$flagsJson]
        }
    """.trimIndent() + "\n"
    }

    fun parseServerMessage(rawJson: String): BaseMessage? {
        return try {
            val jsonObject = JsonParser.parseString(rawJson).asJsonObject
            val type = jsonObject["type"].asString

            val gson = Gson()

            when (type) {
                "POSITION" -> gson.fromJson(rawJson, PositionMessage::class.java)
                "COMPLETED_WAYPOINT" -> gson.fromJson(rawJson, CompletedWaypointMessage::class.java)
                "FINISHED" -> FinishedMessage
                else -> {
                    Log.w("Socket", "Nieznany typ wiadomości: $type")
                    null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    private fun onSimulationFinished() {
        _flagPositions.clear()
        addFlag(_shipPosition.value.lon, _shipPosition.value.lat)
        _isShipMoving.value = false
        shipMovingJob?.cancel()
        shipMovingJob = null
        socketClient?.disconnect()
        socketClient = null
    }

    fun getNextAvailableId(): Int {
        val usedIds = _flagPositions.map { it.id }.toSet()
        var id = 0
        while (id in usedIds) {
            id++
        }
        return id
    }

    fun setFlagBitmap(id: Int, bitmap: Bitmap) {
        _flagBitmaps[id] = bitmap
    }

//    fun hasBitmap(id: Int) = _flagBitmaps.containsKey(id)

    fun addFlag(lon: Double, lat: Double): WaypointObject {
        val id = getNextAvailableId()
        val waypoint = WaypointObject(id, lon, lat)
        _flagPositions.add(waypoint)
        return waypoint
    }

    fun removeFlag(id: Int) {
        _flagPositions.removeAll { it.id == id }
        reindexFlags(id)
    }

    fun moveFlag(id: Int, newLon: Double, newLat: Double) {
        val index = _flagPositions.indexOfFirst { it.id == id }
        if (index != -1) {
            _flagPositions[index] = _flagPositions[index].copy(lon = newLon, lat = newLat)
        }
    }

    fun reindexFlags(removedId: Int) {
        _flagPositions.forEach { wp ->
            if (wp.id > removedId) {
                wp.id -= 1
            }
        }
    }

    fun getWaypointById(id: Int): WaypointObject? {
        return flagPositions.find { it.id == id }
    }

//    fun setFlagEditMode(mode: FlagMode?) {
//        flagMode = mode
//    }

    fun toggleFlagEditMode(mode: FlagMode?) {
        flagMode = if(flagMode != mode && mode != null) {
            mode
        } else {
            null
        }
    }

    fun updateMapSources(flagsSource: GeoJsonSource, linesSource: GeoJsonSource, shipSource: GeoJsonSource) {
        flagsSource.setGeoJson(FeatureCollection.fromFeatures(getFlagFeatures()))
        linesSource.setGeoJson(FeatureCollection.fromFeatures(getConnectionLines()))
        shipSource.setGeoJson(getShipFeature())
    }

    fun getShipFeature(): FeatureCollection? {
        val point = Point.fromLngLat(shipPosition.value.lon, shipPosition.value.lat)
        val feature = Feature.fromGeometry(point)
        val featureCollection = FeatureCollection.fromFeature(feature)
        return featureCollection
    }

    fun getFlagFeatures(): List<Feature> {
        return _flagPositions.map {
            Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat)).apply {
                addStringProperty("id", it.id.toString())
                addStringProperty("icon", "flag-icon-${it.id}")
            }
        }
    }

    fun getConnectionLines(): List<Feature> {
        val lines = mutableListOf<Feature>()
        val waypoints = _flagPositions.sortedBy { it.id }

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
}

