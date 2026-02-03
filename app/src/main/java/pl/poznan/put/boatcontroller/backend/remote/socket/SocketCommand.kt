package pl.poznan.put.boatcontroller.backend.remote.socket

sealed class SocketCommand {
    object GetBoatInformation : SocketCommand()

    data class SetSpeed(
        val left: Int, // 0-10 (0 = stop/neutral, 1-4 = reverse, 5 = neutral, 6-10 = forward)
        val right: Int, // 0-10
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
