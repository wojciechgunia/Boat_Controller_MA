package pl.poznan.put.boatcontroller.dataclass

data class WaypointObject(
    var id: Int,
    val lon: Double,
    val lat: Double,
    var isCompleted: Boolean = false
)

data class CameraPositionState(
    val lon: Double,
    val lat: Double,
    val zoom: Double
)