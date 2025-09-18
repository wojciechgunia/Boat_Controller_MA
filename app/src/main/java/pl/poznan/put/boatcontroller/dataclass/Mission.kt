package pl.poznan.put.boatcontroller.dataclass

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

data class MissionCreateRequest(
    val name: String,
)

data class MissionListItemDto(
    val id: Int,
    val name: String,
)
