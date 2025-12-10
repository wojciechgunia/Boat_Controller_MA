package pl.poznan.put.boatcontroller

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket

object SocketClientManager {
    private var socket: Socket? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null
    private var listenJob: Job? = null
    private var tocken: String? = null

    private var onMessageReceived: ((String) -> Unit)? = null
    private var onDisconnected: (() -> Unit)? = null
    private var onLoginStatusChanged: ((Boolean) -> Unit)? = null

    private var _isLoggedIn = false
    val isLoggedIn: Boolean
        get() = _isLoggedIn

    fun init(socket: Socket) {
        this.socket = socket
        writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
        reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        _isLoggedIn = true
        onLoginStatusChanged?.invoke(true)
        startListening()
    }

    fun setTocken(tockenCode: String) {
        tocken = tockenCode
    }

    fun setOnMessageReceivedListener(listener: (String) -> Unit) {
        onMessageReceived = listener
    }

    fun setOnDisconnectedListener(listener: () -> Unit) {
        onDisconnected = listener
    }

    fun setOnLoginStatusChangedListener(listener: (Boolean) -> Unit) {
        onLoginStatusChanged = listener
    }

    private fun startListening() {
        listenJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                var line: String?
                while (reader?.readLine().also { line = it } != null) {
                    line?.let { onMessageReceived?.invoke(it) }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                disconnectInternal()
            }
        }
    }

    fun sendMessage(message: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (socket?.isConnected == true && writer != null) {
                    writer?.write("$message:$tocken")
                    writer?.flush()
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    fun disconnect() {
        CoroutineScope(Dispatchers.IO).launch {
            disconnectInternal()
        }
    }

    private fun disconnectInternal() {
        listenJob?.cancel()
        listenJob = null
        try {
            writer?.close()
            reader?.close()
            socket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        writer = null
        reader = null
        socket = null
      
        if (_isLoggedIn) {
            _isLoggedIn = false
            onLoginStatusChanged?.invoke(false)
        }

        onMessageReceived = null
        onDisconnected?.invoke()
        onDisconnected = null
    }

    fun isInitialized(): Boolean {
        return socket != null
    }

}