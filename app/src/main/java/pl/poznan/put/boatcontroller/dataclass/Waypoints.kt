package pl.poznan.put.boatcontroller.dataclass

data class MissionDto(
    val name: String,
    val userId: Int,
    val waypointsNo: Int,
    val pointsOfInterestsNo: Int,
    val runningsNo: Int
)

data class UserDto(
    val login: String,
    val role: String,
    val isBlocked: Boolean
)

data class WaypointDto(
    val missionId: Int,
    val no: Int,
    val lat: String,
    val lon: String
)

data class PointOfInterestDto(
    val missionId: Int,
    val lat: String,
    val lon: String,
    val name: String?,
    val description: String?,
    val pictures: List<String>?
)

data class RunningDto(
    val missionId: Int,
    val date: String,
    val stats: String
)

data class WaypointObject(
    var id: Int,
    val lon: Double,
    val lat: Double,
    var isCompleted: Boolean = false
)

data class POIObject(
    val lon: Double,
    val lat: Double,
)

data class CameraPositionState(
    val lon: Double,
    val lat: Double,
    val zoom: Double
)