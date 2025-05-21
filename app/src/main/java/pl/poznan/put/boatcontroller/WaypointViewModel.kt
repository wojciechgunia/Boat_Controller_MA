package pl.poznan.put.boatcontroller

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import pl.poznan.put.boatcontroller.dataclass.WaypointObject
import pl.poznan.put.boatcontroller.enums.FlagMode

class WaypointViewModel(app: Application) : AndroidViewModel(app) {
    var isExpanded by mutableStateOf(false)
    val waypointPositions = mutableStateListOf<WaypointObject>()
    val shipPosition = doubleArrayOf(52.404633, 16.957722)
    private var nextFlagId = 1

    fun addWaypoint(lon: Double, lat: Double): WaypointObject {
        val waypoint = WaypointObject(nextFlagId, lon, lat)
        waypointPositions.add(waypoint)
        nextFlagId++
        return waypoint
    }

    fun removeWaypoint(id: Int) {
        waypointPositions.removeIf { it.id == id }
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