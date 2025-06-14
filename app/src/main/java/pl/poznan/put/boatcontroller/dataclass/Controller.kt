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
    val temperature: Double,
    val humidity: Double,
    val depth: Double,
)