package pl.poznan.put.boatcontroller.dataclass

data class WaypointDto(
    val id: Int,
    val missionId: Int,
    var no: Int,
    val lat: String,
    val lon: String,
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

