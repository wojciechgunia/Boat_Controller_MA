package pl.poznan.put.boatcontroller.domain.models

data class POICreateRequest(
    val missionId: Int,
    val lat: String,
    val lon: String,
    val name: String?,
    val description: String?,
    val pictures: String?
)