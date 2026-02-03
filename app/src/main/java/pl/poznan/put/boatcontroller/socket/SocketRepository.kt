package pl.poznan.put.boatcontroller.socket

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object SocketRepository {
    private val service = SocketService()
    private val _events = MutableSharedFlow<SocketEvent>(extraBufferCapacity = 50)
    val events = _events.asSharedFlow()

    val connectionState = service.connectionState.asStateFlow()

    private val _batteryLevel = MutableStateFlow<Int?>(100)
    val batteryLevel: StateFlow<Int?> = _batteryLevel.asStateFlow()

    // ACK + Retry dla komend krytycznych (SetMission, SetAction)
    private data class PendingCommand(
        val command: SocketCommand,
        val encoded: String,
        val commandType: String, // "SM" lub "SA"
        val sNum: Int,
        var retryCount: Int = 0,
        val maxRetries: Int = 3
    )
    
    private val pendingCommands = mutableMapOf<Int, PendingCommand>()
    private val pendingMutex = Mutex()
    private const val ACK_TIMEOUT_MS = 2000L // Timeout ~2s

    fun start(ip: String, port: Int) {
        service.startConnectionLoop(ip, port)

        CoroutineScope(Dispatchers.IO).launch {
            service.incomingRaw.collect { raw ->
                Log.d("SocketRepository", "📥 Raw message received: $raw")
                val event = SocketParser.parse(raw)
                if (event != null) {
                    when (event) {
                        is SocketEvent.PositionActualisation -> {
                            val speedMs = event.speed / 100.0
                            Log.d("SocketRepository", "📍 Parsed PA: lat=${event.lat}, lon=${event.lon}, speed=$speedMs m/s, sNum=${event.sNum}")
                        }
                        is SocketEvent.SensorInformation -> {
                            Log.d("SocketRepository", "📊 Parsed SI: accel=(${event.accelX/100.0},${event.accelY/100.0},${event.accelZ/100.0}), gyro=(${event.gyroX/100.0},${event.gyroY/100.0},${event.gyroZ/100.0}), mag=(${event.magX/100.0},${event.magY/100.0},${event.magZ/100.0}), angles=(${event.angleX},${event.angleY},${event.angleZ}), depth=${event.depth/100.0}")
                        }
                        else -> {
                            Log.d("SocketRepository", "📨 Parsed event: ${event::class.simpleName}")
                        }
                    }
                    handleIncomingEvent(event)
                } else {
                    Log.w("SocketRepository", "⚠️ Failed to parse: $raw")
                }
            }
        }
    }

    private suspend fun handleIncomingEvent(event: SocketEvent) {
        // Obsługa ACK dla komend krytycznych
        if (event is SocketEvent.CommandAck) {
            pendingMutex.withLock {
                val pending = pendingCommands.remove(event.sNum)
                if (pending != null) {
                    Log.d("SocketRepository", "✅ ACK received for ${event.commandType} sNum=${event.sNum}")
                } else {
                    Log.w("SocketRepository", "⚠️ ACK received for unknown sNum=${event.sNum}")
                }
            }
        }

        if (event is SocketEvent.LostInformation) {
            Log.d("SocketRepository", "📨 LostInformation ACK: sNum=${event.sNum}")
        }

        _events.emit(event)
    }

    suspend fun send(command: SocketCommand) {
        val encoded = encodeCommand(command)
        Log.d("SocketRepository", "📤 Sending command: ${command::class.simpleName} -> $encoded")
        val requiresAck = when (command) {
            is SocketCommand.SetMission, is SocketCommand.SetAction -> true
            else -> false
        }
        
        if (requiresAck) {
            // Komenda krytyczna - wymaga ACK + retry
            val commandType = when (command) {
                is SocketCommand.SetMission -> "SM"
                is SocketCommand.SetAction -> "SA"
                else -> ""
            }
            val sNum = when (command) {
                is SocketCommand.SetMission -> command.sNum
                is SocketCommand.SetAction -> command.sNum
                else -> 0
            }
            
            val pending = PendingCommand(
                command = command,
                encoded = encoded,
                commandType = commandType,
                sNum = sNum
            )
            
            pendingMutex.withLock {
                pendingCommands[sNum] = pending
            }
            
            // Wyślij pierwszą próbę
            service.send(encoded)
            
            // Uruchom retry loop
            CoroutineScope(Dispatchers.IO).launch {
                retryLoop(pending)
            }
        } else {
            // Komenda realtime (SetSpeed) - brak ACK, wysyłaj bez retry
            service.send(encoded)
        }
    }
    
    private suspend fun retryLoop(pending: PendingCommand) {
        delay(ACK_TIMEOUT_MS)
        
        pendingMutex.withLock {
            // Sprawdź czy ACK już przyszedł
            if (!pendingCommands.containsKey(pending.sNum)) {
                // ACK otrzymany - zakończ
                return
            }
            
            // ACK nie przyszedł - retry
            if (pending.retryCount < pending.maxRetries) {
                pending.retryCount++
                Log.w("SocketRepository", "🔄 RETRY ${pending.retryCount}/${pending.maxRetries} for ${pending.commandType} sNum=${pending.sNum}")
                service.send(pending.encoded)

                retryLoop(pending)
            } else {
                Log.e("SocketRepository", "❌ FAILED after ${pending.maxRetries} retries for ${pending.commandType} sNum=${pending.sNum}")
                pendingCommands.remove(pending.sNum)
            }
        }
    }

    private fun encodeCommand(cmd: SocketCommand): String {
        return when (cmd) {

            is SocketCommand.GetBoatInformation ->
                "GBI:GBI"

            is SocketCommand.SetSpeed ->
                "SS:${cmd.left}:${cmd.right}:${cmd.winch}:${cmd.sNum}:SS"

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