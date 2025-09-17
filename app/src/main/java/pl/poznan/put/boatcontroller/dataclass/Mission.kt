package pl.poznan.put.boatcontroller.dataclass

data class MissionDto(
    val name: String,
    val userId: Int,
    val waypointsNo: Int,
    val pointsOfInterestsNo: Int,
    val runningsNo: Int
)

data class MissionListItemDto(
    val id: Int,
    val name: String,
)

data class MissionAddDto(
    val name: String,
)

data class PointOfInterestDto(
    val missionId: Int,
    val lat: String,
    val lon: String,
    val name: String?,
    val description: String?,
    val pictures: List<String>?
)