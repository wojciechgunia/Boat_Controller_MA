package pl.poznan.put.boatcontroller.backend.dto

data class PointOfInterestDto(
    val id: Int,
    val missionId: Int,
    val lat: String,
    val lon: String,
    val name: String?,
    val description: String?,
    val pictures: String?
)