package pl.poznan.put.boatcontroller.location_api

import kotlinx.coroutines.*
import java.io.*
import java.net.Socket

class WaypointSocketClient(
    private val host: String,
    private val port: Int,
    private val onGetMessage: (String) -> Unit
) {
    private var socket: Socket? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null
    private var listenJob: Job? = null

    suspend fun connectAndSend(message: String) {
        withContext(Dispatchers.IO) {
            socket = Socket(host, port)
            writer = BufferedWriter(OutputStreamWriter(socket!!.getOutputStream()))
            reader = BufferedReader(InputStreamReader(socket!!.getInputStream()))
            writer?.write(message)
            writer?.flush()

            listenJob = listenForServerMessages()
        }
    }

    private fun listenForServerMessages(): Job = CoroutineScope(Dispatchers.IO).launch {
        try {
            var line: String?
            while (reader?.readLine().also { line = it } != null) {
                line?.let { onGetMessage(it) }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            disconnect()
        }
    }

    fun disconnect() {
        listenJob?.cancel()
        writer?.close()
        reader?.close()
        socket?.close()
    }
}