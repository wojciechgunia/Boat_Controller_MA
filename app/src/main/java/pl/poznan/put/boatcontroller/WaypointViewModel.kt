package pl.poznan.put.boatcontroller

import android.app.Application
import android.graphics.Bitmap
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
import pl.poznan.put.boatcontroller.dataclass.WaypointObject
import pl.poznan.put.boatcontroller.enums.FlagMode

class WaypointViewModel(app: Application) : AndroidViewModel(app) {
    var isExpanded by mutableStateOf(false)
    var flagToMoveId: Int? by mutableStateOf(null)
    val shipPosition = doubleArrayOf(52.404633, 16.957722)

    var waypointPositions = mutableStateListOf<WaypointObject>()
    var nextFlagId = 1

    private val _flagBitmaps = mutableStateMapOf<Int, Bitmap>()
    val flagBitmaps: Map<Int, Bitmap> = _flagBitmaps

    fun setFlagBitmap(id: Int, bitmap: Bitmap) {
        _flagBitmaps[id] = bitmap
    }

    fun hasBitmap(id: Int) = _flagBitmaps.containsKey(id)

    fun getNextAvailableId(): Int {
        val usedIds = waypointPositions.map { it.id }.toSet()
        var id = 1
        while (id in usedIds) {
            id++
        }
        return id
    }

    fun addWaypoint(lon: Double, lat: Double): WaypointObject {
        val id = getNextAvailableId()
        val waypoint = WaypointObject(id, lon, lat)
        waypointPositions.add(waypoint)
        return waypoint
    }

    fun removeWaypoint(id: Int) {
        waypointPositions.removeAll { it.id == id }
        reindexWaypoints()
    }

    fun moveWaypoint(id: Int, newLon: Double, newLat: Double) {
        val index = waypointPositions.indexOfFirst { it.id == id }
        if (index != -1) {
            waypointPositions[index] = waypointPositions[index].copy(lon = newLon, lat = newLat)
        }
    }

    fun reindexWaypoints() {
        waypointPositions.forEachIndexed { index, wp ->
            val oldId = wp.id
            val newId = index + 1
            if (oldId != newId) {
                wp.id = newId
            }
        }
        nextFlagId = waypointPositions.size + 1
    }

    var flagMode by mutableStateOf<FlagMode?>(null)
        private set

    fun setFlagEditMode(mode: FlagMode?) {
        flagMode = mode
    }

    fun closeToolbar() {
        isExpanded = false;
    }

    fun updateMapSources(flagsSource: GeoJsonSource, linesSource: GeoJsonSource) {
        flagsSource.setGeoJson(FeatureCollection.fromFeatures(getFlagFeatures()))
        linesSource.setGeoJson(FeatureCollection.fromFeatures(getConnectionLines()))
        flagToMoveId = null
        setFlagEditMode(null)
    }

    fun getFlagFeatures(): List<Feature> {
        return waypointPositions.map {
            Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat)).apply {
                addStringProperty("id", it.id.toString())
                addStringProperty("icon", "flag-icon-${it.id}")
            }
        }
    }

    fun getConnectionLines(): List<Feature> {
        val lines = mutableListOf<Feature>()
        val waypoints = waypointPositions.sortedBy { it.id }

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
}

