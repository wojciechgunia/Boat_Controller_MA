package pl.poznan.put.boatcontroller.dataclass

data class PointOfInterestDto(
    val id: Int,
    val missionId: Int,
    val lat: String,
    val lon: String,
    val name: String?,
    val description: String?,
    val pictures: String?
)

data class POICreateRequest(
    val missionId: Int,
    val lat: String,
    val lon: String,
    val name: String?,
    val description: String?,
    val pictures: String?
)

data class POIUpdateRequest(
    val name: String?,
    val description: String?,
    val pictures: String? = null  // lista URL w formie JSON stringa np. '["url1","url2"]'
)