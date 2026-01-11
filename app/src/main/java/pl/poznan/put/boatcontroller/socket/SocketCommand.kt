package pl.poznan.put.boatcontroller.socket

sealed class SocketCommand {
    object GetBoatInformation : SocketCommand()

    data class SetSpeed(
        val left: Double,
        val right: Double,
        val winch: Int, // 0 = góra, 1 = wyłączony, 2 = dół
        val sNum: Int
    ) : SocketCommand()

    data class SetAction(
        val action: String,
        val payload: String,
        val sNum: Int
    ) : SocketCommand()

    data class SetMission(
        val mission: String,
        val sNum: Int
    ) : SocketCommand()

    data class InternalRequestLost(
        val sNum: Int
    ) : SocketCommand()
}
