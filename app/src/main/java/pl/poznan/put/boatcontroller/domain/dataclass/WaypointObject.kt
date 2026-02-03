package pl.poznan.put.boatcontroller.domain.dataclass

data class WaypointObject(
    var no: Int,
    val lon: Double,
    val lat: Double,
    var isCompleted: Boolean = false
)