package pl.poznan.put.boatcontroller.backend.dto

data class WaypointDto(
    val id: Int,
    val missionId: Int,
    var no: Int,
    val lat: String,
    val lon: String,
)