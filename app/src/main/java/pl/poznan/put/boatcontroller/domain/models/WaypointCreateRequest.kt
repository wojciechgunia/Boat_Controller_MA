package pl.poznan.put.boatcontroller.domain.models

data class WaypointCreateRequest(
    val missionId: Int,
    val no: Int,
    val lat: String,
    val lon: String
)