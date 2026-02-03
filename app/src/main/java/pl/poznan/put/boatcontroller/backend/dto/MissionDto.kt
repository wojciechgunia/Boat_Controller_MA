package pl.poznan.put.boatcontroller.backend.dto

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