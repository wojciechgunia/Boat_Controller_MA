package pl.poznan.put.boatcontroller.domain.dataclass

data class POIObject(
    var id: Int,
    var missionId: Int,
    val lon: Double,
    val lat: Double,
    var name: String? = null,
    var description: String? = null,
    val pictures: String? = null
)