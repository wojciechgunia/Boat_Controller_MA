package pl.poznan.put.boatcontroller.domain.models

data class WaypointUpdateRequest(
    val no: Int? = null,
    val lat: String? = null,
    val lon: String? = null
)