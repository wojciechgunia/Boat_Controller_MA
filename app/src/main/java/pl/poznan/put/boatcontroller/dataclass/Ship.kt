package pl.poznan.put.boatcontroller.dataclass

data class ShipOption(
    val name: String,
    val role: String,
)

data class ShipListItemDto(
    val id: Int,
    val name: String,
    val connections: Int,
    val captain: String,
    val mission: String,
    )