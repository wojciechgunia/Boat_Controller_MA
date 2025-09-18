package pl.poznan.put.boatcontroller.dataclass

data class WaypointObject(
    var no: Int,
    val lon: Double,
    val lat: Double,
    var isCompleted: Boolean = false
)

data class POIObject(
    var missionId: Int,
    val lon: Double,
    val lat: Double,
    val name: String? = null,
    val description: String? = null,
    val pictures: List<String>? = null
)

data class CameraPositionState(
    val lon: Double,
    val lat: Double,
    val zoom: Double
)
