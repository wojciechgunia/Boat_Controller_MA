package pl.poznan.put.boatcontroller.dataclass

data class ShipPosition(
    val lat: Double,
    val lon: Double,
)

data class HomePosition(
    val lat: Double,
    val lon: Double,
)

data class ShipSensorsData(
    val magnetic: Double,
    val depth: Double,
)