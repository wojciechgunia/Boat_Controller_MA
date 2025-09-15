package pl.poznan.put.boatcontroller.dataclass

// ===========================
// UI data classes
// ===========================

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

// ===========================
// Backend API data classes
// ===========================

// Requests data classes
data class MissionCreateRequest(
    val name: String,
)

data class WaypointCreateRequest(
    val missionId: Int,
    val no: Int,
    val lat: String,
    val lon: String
)

data class WaypointUpdateRequest(
    val no: Int? = null,
    val lat: String? = null,
    val lon: String? = null
)

data class RunningCreateRequest(
    val missionId: Int,
    val stats: String
)

data class POICreateRequest(
    val missionId: Int,
    val lat: String,
    val lon: String,
    val name: String?,
    val description: String?,
    val pictures: List<String>?
)

data class POIUpdateRequest(
    val name: String?,
    val description: String?,
    val pictures: List<String>?
)

// Responses data classes
data class UserDto(
    val id: Int,
    val login: String,
    val role: String,
    val isBlocked: Boolean
)

data class MissionDto(
    val id: Int,
    val name: String,
    val userId: Int,
    val createdAt: String,
    val lastRouteUpdate: String?,
    val lastRunning: String?,
    val waypointsNo: Int,
    val pointsOfInterestsNo: Int,
    val runningsNo: Int
)

data class WaypointDto(
    val id: Int,
    val missionId: Int,
    var no: Int,
    val lat: String,
    val lon: String,
)

data class RunningDto(
    val id: Int,
    val missionId: Int,
    val date: String,
    val stats: String
)

data class PointOfInterestDto(
    val id: Int,
    val missionId: Int,
    val lat: String,
    val lon: String,
    val name: String?,
    val description: String?,
    val pictures: List<String>?
)
