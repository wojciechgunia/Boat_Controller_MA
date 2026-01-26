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
    // Akcelerometr (g)
    val accelX: Double = 0.0,
    val accelY: Double = 0.0,
    val accelZ: Double = 0.0,
    // Żyroskop (deg/s)
    val gyroX: Double = 0.0,
    val gyroY: Double = 0.0,
    val gyroZ: Double = 0.0,
    // Magnetometr (µT)
    val magX: Double = 0.0,
    val magY: Double = 0.0,
    val magZ: Double = 0.0,
    // Kąty (deg)
    val angleX: Int = 0,
    val angleY: Int = 0,
    val angleZ: Int = 0,
    // Głębokość (m)
    val depth: Double = 0.0,
)