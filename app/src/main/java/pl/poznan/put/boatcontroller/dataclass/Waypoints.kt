package pl.poznan.put.boatcontroller.dataclass

data class WaypointObject(
    var id: Int,
    val lon: Double,
    val lat: Double,
    var isCompleted: Boolean = false
)

open class BaseMessage(val type: String)

data class PositionMessage(
    val ship: ShipPosition
) : BaseMessage("POSITION")

data class CompletedWaypointMessage(
    val flags: List<WaypointObject>
) : BaseMessage("COMPLETED_WAYPOINT")

object FinishedMessage : BaseMessage("FINISHED")

data class CameraPositionState(
    val lon: Double,
    val lat: Double,
    val zoom: Double
)