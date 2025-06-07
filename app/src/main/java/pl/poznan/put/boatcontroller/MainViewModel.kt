package pl.poznan.put.boatcontroller

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import java.net.Socket

class MainViewModel(app: Application) : AndroidViewModel(app) {

    var serverIp by mutableStateOf("")
        private set
    var serverPort by mutableStateOf("")
        private set
    var username by mutableStateOf("")
        private set
    var password by mutableStateOf("")
        private set
    var isLoggedIn by mutableStateOf(false)
        private set

    var socket by mutableStateOf<Socket?>(null)
        private set

    init {
        SocketClientManager.setOnLoginStatusChangedListener { loggedIn ->
            updateLoggedIn(loggedIn)
//            if (!loggedIn) {
//
//            }
        }
    }

    fun updateServerIP(value: String) {
        serverIp = value
    }

    fun updateServerPort(value: String) {
        serverPort = value
    }

    fun updatePassword(value: String) {
        password = value
    }

    fun updateUsername(value: String) {
        username = value
    }

    fun updateLoggedIn(value: Boolean) {
        isLoggedIn = value
    }

    fun updateSocket(socket: Socket?) {
        this.socket = socket
        socket?.let {
            SocketClientManager.init(it)
            SocketClientManager.setOnDisconnectedListener {
                updateLoggedIn(false)
            }
        }
    }

    fun logout() {
        SocketClientManager.disconnect()
        socket?.close()
        socket = null
    }
}

