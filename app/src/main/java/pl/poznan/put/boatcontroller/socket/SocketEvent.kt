package pl.poznan.put.boatcontroller.socket

sealed class SocketEvent {
    data class BoatInformation(val name: String, val captain: String, val mission: String) : SocketEvent()
    data class BoatInformationChange(val name: String, val captain: String, val mission: String) : SocketEvent()

    data class PositionActualisation(
        val lon: Double, // Duża dokładność GPS
        val lat: Double, // Duża dokładność GPS
        val speed: Int, // speed w cm/s (zamiast Double w m/s)
        val sNum: Int
    ) : SocketEvent()

    data class SensorInformation(
        // Akcelerometr (g) - precyzja do 2 miejsc po przecinku (*100)
        val accelX: Int, // wartość * 100
        val accelY: Int,
        val accelZ: Int,
        // Żyroskop (deg/s) - precyzja do 2 miejsc po przecinku (*100)
        val gyroX: Int, // wartość * 100
        val gyroY: Int,
        val gyroZ: Int,
        // Magnetometr (µT) - precyzja do 2 miejsc po przecinku (*100)
        val magX: Int, // wartość * 100
        val magY: Int,
        val magZ: Int,
        // Kąty (deg) - jako Int (bez miejsc po przecinku dla łatwiejszego przesyłania)
        val angleX: Int, // wartość jako Int (stopnie)
        val angleY: Int,
        val angleZ: Int,
        // Głębokość (m) - precyzja do 2 miejsc po przecinku (*100)
        val depth: Int // wartość * 100
    ) : SocketEvent()

    data class WarningInformation(
        val infoCode: String
    ) : SocketEvent()

    data class LostInformation(
        val sNum: Int
    ) : SocketEvent()
    
    // ACK dla komend krytycznych (SetMission, SetAction)
    data class CommandAck(
        val commandType: String, // "SM" lub "SA"
        val sNum: Int
    ) : SocketEvent()
}
