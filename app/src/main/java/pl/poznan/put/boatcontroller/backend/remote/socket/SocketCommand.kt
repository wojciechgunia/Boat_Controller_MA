package pl.poznan.put.boatcontroller.backend.remote.socket

sealed class SocketCommand {
    object GetBoatInformation : SocketCommand()

    /**
     * Silnik lewy/prawy: (0 = stop/neutral, 1-4 = tył, 5 = neutral, 6-10 = przód)
     * Zwijarka/wciągarka: 0 = góra, 1 = wyłączony, 2 = dół
     * */
    data class SetSpeed(
        val left: Int,
        val right: Int,
        val winch: Int, //
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
