package pl.poznan.put.boatcontroller.dataclass

data class WaypointObject(
    var id: Int,
    val lon: Double,
    val lat: Double
)

data class ShipUpdateMessage(
    val type: String,
    val ship: ShipPosition,
)

data class ShipPosition(
    val lat: Double,
    val lon: Double,
)

data class CameraPositionState(
    val lon: Double,
    val lat: Double,
    val zoom: Double
)