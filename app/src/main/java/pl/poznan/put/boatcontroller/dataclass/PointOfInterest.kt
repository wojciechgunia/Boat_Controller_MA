package pl.poznan.put.boatcontroller.dataclass

data class PointOfInterestDto(
    val id: Int,
    val missionId: Int,
    val lat: String,
    val lon: String,
    val name: String?,
    val description: String?,
    val pictures: List<String>?
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