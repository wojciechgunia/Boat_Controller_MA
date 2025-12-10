package pl.poznan.put.boatcontroller.socket

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket

class SocketService {
    private var socket: Socket? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null

    private val writeMutex = Mutex()
    private var isRunning = false
    val incomingRaw = MutableSharedFlow<String>(extraBufferCapacity = 20)
    val connectionState = MutableSharedFlow<Boolean>(replay = 1)

    fun startConnectionLoop(ip: String, port: Int) {
        isRunning = true
        CoroutineScope(Dispatchers.IO).launch {
            while (isRunning) {
                try {
                    connect(ip, port)
                } catch (e: Exception) {
                    connectionState.emit(false)
                    delay(3000)
                }
            }
        }
    }

    private suspend fun connect(ip: String, port: Int) {
        try {
            socket = Socket()
            socket?.connect(InetSocketAddress(ip, port), 5000)

            writer = BufferedWriter(OutputStreamWriter(socket!!.getOutputStream()))
            reader = BufferedReader(InputStreamReader(socket!!.getInputStream()))

            connectionState.emit(true)
            println("Socket: Connected to $ip:$port")

            // Pętla czytania - blokuje dopóki połączenie jest aktywne
            while (isRunning && socket?.isConnected == true) {
                val line = reader?.readLine()
                if (line != null) {
                    incomingRaw.emit(line)
                } else {
                    throw IOException("Server closed connection")
                }
            }
        } finally {
            cleanup()
        }
    }

    suspend fun send(msg: String) {
        if (!isRunning || socket?.isConnected != true) return
        writeMutex.withLock {
            try {
                withContext(Dispatchers.IO) {
                    writer?.write(msg)
                    writer?.write("\n")
                    writer?.flush()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stop() {
        isRunning = false
        cleanup()
    }

    private fun cleanup() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        writer = null
        reader = null
    }
}
