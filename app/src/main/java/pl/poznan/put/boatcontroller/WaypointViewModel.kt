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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import pl.poznan.put.boatcontroller.dataclass.CameraPositionState
import pl.poznan.put.boatcontroller.dataclass.ShipPosition
import pl.poznan.put.boatcontroller.dataclass.ShipUpdateMessage
import pl.poznan.put.boatcontroller.dataclass.WaypointObject
import pl.poznan.put.boatcontroller.enums.FlagMode
import pl.poznan.put.boatcontroller.location_api.WaypointSocketClient

class WaypointViewModel(app: Application) : AndroidViewModel(app) {
    var isToolbarOpened by mutableStateOf(false)

    private val _shipPosition = mutableStateOf<ShipPosition>(ShipPosition(52.404633, 16.957722))
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

    fun startShipSimulation() {
        val flags = flagPositions.sortedBy { it.id }
        if (flags.isEmpty()) return
        val message = buildJsonShipPayload()

        socketClient = WaypointSocketClient(
            "192.168.1.4",
            2137,
            onGetMessage = { serverMessage ->
                val update = parseServerMessage(serverMessage)
                update?.let {
                    if (it.type == "POSITION") {
                        moveFlag(0, it.ship.lon, it.ship.lat)
                        _shipPosition.value = ShipPosition(it.ship.lat, it.ship.lon)
                    }
                    else if (it.type == "FINISHED") {
                        onSimulationFinished()
                        Log.d("FINISHED", "Ukończono trasę!")
                    }
                    else {
                        Log.w("Socket", "Nieznany typ wiadomości: ${it.type}")
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
            """{"id":${it.id},"lat":${it.lat},"lon":${it.lon}}"""
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

    fun parseServerMessage(json: String): ShipUpdateMessage? {
        return try {
            Gson().fromJson(json, ShipUpdateMessage::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun onSimulationFinished() {
        _isShipMoving.value = false
        shipMovingJob?.cancel()
        shipMovingJob = null
        socketClient?.disconnect()
        socketClient = null
    }

    fun getNextAvailableId(): Int {
        val usedIds = _flagPositions.map { it.id }.toSet()
        var id = 1
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

