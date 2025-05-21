package pl.poznan.put.boatcontroller

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import pl.poznan.put.boatcontroller.dataclass.WaypointObject
import pl.poznan.put.boatcontroller.enums.FlagMode

class WaypointViewModel(app: Application) : AndroidViewModel(app) {
    var isExpanded by mutableStateOf(false)
    val waypointPositions = mutableStateListOf<WaypointObject>()
    val linePositions = mutableListOf<Feature>()
    val shipPosition = doubleArrayOf(52.404633, 16.957722)
    private var nextFlagId = 1
    var flagToMoveId: Int? by mutableStateOf(null)

    fun addWaypoint(lon: Double, lat: Double): WaypointObject {
        val waypoint = WaypointObject(nextFlagId, lon, lat)
        waypointPositions.add(waypoint)
        nextFlagId++
        return waypoint
    }

    fun addWaypoint(id: Int, lon: Double, lat: Double) {
        waypointPositions.add(WaypointObject(id, lon, lat))
    }

    fun removeWaypoint(id: Int) {
        waypointPositions.removeIf { it.id == id }
    }

    fun reindexWaypoints() {
        waypointPositions.forEachIndexed { index, wp ->
            wp.id = index+1
        }
        nextFlagId = waypointPositions.size+1
    }

    var flagMode by mutableStateOf<FlagMode?>(null)
        private set

    fun setFlagEditMode(mode: FlagMode?) {
        flagMode = mode
    }

    fun closeToolbar() {
        isExpanded = false;
    }
}