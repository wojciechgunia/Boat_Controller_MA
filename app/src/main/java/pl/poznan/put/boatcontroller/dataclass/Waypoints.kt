package pl.poznan.put.boatcontroller.dataclass

data class WaypointObject(
    var no: Int,
    val lon: Double,
    val lat: Double,
    var isCompleted: Boolean = false
)

data class POIObject(
    var id: Int,
    var missionId: Int,
    val lon: Double,
    val lat: Double,
    var name: String? = null,
    var description: String? = null,
    val pictures: String? = null
)

data class CameraPositionState(
    val lat: Double,
    val lon: Double,
    val zoom: Double
)
