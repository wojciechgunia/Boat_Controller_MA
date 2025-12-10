package pl.poznan.put.boatcontroller.socket

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

object SocketRepository {
    private val service = SocketService() // Twój poprawiony serwis z reconnectem
    private val _events = MutableSharedFlow<SocketEvent>(extraBufferCapacity = 50)
    val events = _events.asSharedFlow()

    private var lastSequenceNumber: Int = -1
    private var isInitialized = false

    fun start(ip: String, port: Int) {
        lastSequenceNumber = -1
        isInitialized = false

        service.startConnectionLoop(ip, port)

        CoroutineScope(Dispatchers.IO).launch {
            service.incomingRaw.collect { raw ->
                val event = SocketParser.parse(raw)
                if (event != null) {
                    handleIncomingEvent(event)
                }
                // else: ignorujemy śmieci, parser sam loguje błędy
            }
        }
    }

    private suspend fun handleIncomingEvent(event: SocketEvent) {
        // Sprawdzamy s_num tylko dla eventów, które go posiadają
        val currentSNum = when (event) {
            is SocketEvent.PositionActualisation -> event.sNum
            is SocketEvent.LostInformation -> event.sNum // Odpowiedź na LI też ma sNum
            // Inne typy wiadomości mogą nie mieć sNum, wtedy -1
            else -> -1
        }

        if (currentSNum != -1) {
            checkSequenceAndRequestLost(currentSNum)
        }

        _events.emit(event)
    }

    private suspend fun checkSequenceAndRequestLost(current: Int) {
        if (!isInitialized) {
            // Pierwszy pakiet po połączeniu - po prostu zapisujemy
            lastSequenceNumber = current
            isInitialized = true
            return
        }

        val diff = current - lastSequenceNumber

        when {
            diff == 1 -> {
                // Idealnie, kolejny pakiet
                lastSequenceNumber = current
            }
            diff > 1 -> {
                // WYKRYTO DZIURĘ!
                // Mamy np. 10, przyszło 13. Brakuje 11 i 12.
                // Prosimy o dane zaczynając od pierwszego brakującego (11)
                val missingStart = lastSequenceNumber + 1
                println("Socket: GAP DETECTED! Last: $lastSequenceNumber, Current: $current. Requesting from $missingStart")

                requestLost(missingStart)

                // Aktualizujemy lastSequenceNumber do bieżącego, żeby nie zapętlić próśb,
                // chyba że wolisz czekać aż dziura zostanie załatana (trudniejsze w implementacji)
                lastSequenceNumber = current
            }
            else -> {
                // Pakiet z przeszłości lub restart licznika po stronie łódki.
                // Zazwyczaj ignorujemy lub resetujemy licznik jeśli różnica jest duża.
                if (current < 10 && lastSequenceNumber > 1000) {
                    lastSequenceNumber = current // Reset licznika na łódce
                }
            }
        }
    }

    private suspend fun requestLost(startFromSNum: Int) {
        // LI:{s_num}:LI - poproś o logi od tego numeru
        send(SocketCommand.InternalRequestLost(startFromSNum))
    }

    suspend fun send(command: SocketCommand) {
        service.send(encodeCommand(command))
    }

    private fun encodeCommand(cmd: SocketCommand): String {
        return when (cmd) {

            is SocketCommand.GetBoatInformation ->
                "GBI:GBI"

            is SocketCommand.SetSpeed ->
                "SS:${cmd.left}:${cmd.right}:${cmd.sNum}:SS"

            is SocketCommand.SetAction ->
                "SA:${cmd.action}:${cmd.payload}:${cmd.sNum}:SA"

            is SocketCommand.SetMission ->
                "SM:${cmd.mission}:${cmd.sNum}:SM"

            is SocketCommand.InternalRequestLost ->
                "LI:${cmd.sNum}:LI"
        }
    }

    fun stop() {
        service.stop()
    }
}