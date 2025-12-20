package pl.poznan.put.boatcontroller.socket

sealed class SocketEvent {
    data class BoatInformation(val name: String, val captain: String, val mission: String) : SocketEvent()
    data class BoatInformationChange(val name: String, val captain: String, val mission: String) : SocketEvent()

    data class PositionActualisation(
        val lon: Double,
        val lat: Double,
        val speed: Double,
        val sNum: Int
    ) : SocketEvent()

    data class SensorInformation(
        // Akcelerometr (g)
        val accelX: Double,
        val accelY: Double,
        val accelZ: Double,
        // Żyroskop (deg/s)
        val gyroX: Double,
        val gyroY: Double,
        val gyroZ: Double,
        // Magnetometr (µT)
        val magX: Double,
        val magY: Double,
        val magZ: Double,
        // Kąty (deg)
        val angleX: Double,
        val angleY: Double,
        val angleZ: Double,
        // Głębokość (m)
        val depth: Double
    ) : SocketEvent()

    data class WarningInformation(
        val infoCode: String
    ) : SocketEvent()

    data class LostInformation(
        val sNum: Int
    ) : SocketEvent()
}
