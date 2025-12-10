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
        val magnetic: Double,
        val depth: Double
    ) : SocketEvent()

    data class WarningInformation(
        val infoCode: String
    ) : SocketEvent()

    data class LostInformation(
        val sNum: Int
    ) : SocketEvent()
}
