package pl.poznan.put.boatcontroller

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import pl.poznan.put.boatcontroller.dataclass.CameraPositionState
import pl.poznan.put.boatcontroller.dataclass.WaypointObject
import pl.poznan.put.boatcontroller.enums.FlagMode

class WaypointViewModel(app: Application) : AndroidViewModel(app) {
    var isToolbarOpened by mutableStateOf(false)
    val shipPosition = doubleArrayOf(52.404633, 16.957722)

    fun ownGetShipPosition(): DoubleArray {
        return shipPosition
    }

    fun getNextAvailableId(): Int {
        val usedIds = flagPositions.map { it.id }.toSet()
        var id = 1
        while (id in usedIds) {
            id++
        }
        return id
    }

    private val _flagBitmaps = mutableStateMapOf<Int, Bitmap>()
    val flagBitmaps: Map<Int, Bitmap> = _flagBitmaps
    var flagPositions = mutableStateListOf<WaypointObject>()

    fun setFlagBitmap(id: Int, bitmap: Bitmap) {
        _flagBitmaps[id] = bitmap
    }

//    fun hasBitmap(id: Int) = _flagBitmaps.containsKey(id)

    fun addFlag(lon: Double, lat: Double): WaypointObject {
        val id = getNextAvailableId()
        val waypoint = WaypointObject(id, lon, lat)
        flagPositions.add(waypoint)
        return waypoint
    }

    fun removeFlag(id: Int) {
        flagPositions.removeAll { it.id == id }
        reindexFlags(id)
    }

    fun moveFlag(id: Int, newLon: Double, newLat: Double) {
        val index = flagPositions.indexOfFirst { it.id == id }
        if (index != -1) {
            flagPositions[index] = flagPositions[index].copy(lon = newLon, lat = newLat)
        }
    }

    fun reindexFlags(removedId: Int) {
        flagPositions.forEach { wp ->
            if (wp.id > removedId) {
                wp.id -= 1
            }
        }
    }

    var flagToMoveId: Int? by mutableStateOf(null)
    var flagMode by mutableStateOf<FlagMode?>(null)
        private set

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

    fun updateMapSources(flagsSource: GeoJsonSource, linesSource: GeoJsonSource) {
        flagsSource.setGeoJson(FeatureCollection.fromFeatures(getFlagFeatures()))
        linesSource.setGeoJson(FeatureCollection.fromFeatures(getConnectionLines()))
    }

    fun getFlagFeatures(): List<Feature> {
        return flagPositions.map {
            Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat)).apply {
                addStringProperty("id", it.id.toString())
                addStringProperty("icon", "flag-icon-${it.id}")
            }
        }
    }

    fun getConnectionLines(): List<Feature> {
        val lines = mutableListOf<Feature>()
        val waypoints = flagPositions.sortedBy { it.id }

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

    private val _cameraPosition = mutableStateOf<CameraPositionState?>(null)
    val cameraPosition: MutableState<CameraPositionState?> = _cameraPosition

    fun saveCameraPosition(lat: Double, lng: Double, zoom: Double) {
        _cameraPosition.value = CameraPositionState(lat, lng, zoom)
    }
}

