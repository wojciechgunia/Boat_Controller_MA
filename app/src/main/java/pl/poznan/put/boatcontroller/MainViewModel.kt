package pl.poznan.put.boatcontroller

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import pl.poznan.put.boatcontroller.data.UserData
import java.net.Socket

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app.applicationContext)

    var serverIp by mutableStateOf("")
        private set

    var serverPort by mutableStateOf("")
        private set

    var username by mutableStateOf("")
        private set

    var password by mutableStateOf("")
        private set

    var isRemembered by mutableStateOf(false)
        private set

    var isLoggedIn by mutableStateOf<Boolean>(false)
        private set

    var socket by mutableStateOf<Socket?>(null)
        private set

    init {
        insertDatabase()
    }

    private fun insertDatabase() {
        val userData = UserData(0, "", "", "", "", false)
        CoroutineScope(viewModelScope.coroutineContext).launch {
            if (repo.getCount() == 0) {
                repo.insert(userData)
            }
        }
    }

    fun changeIsRemembered(isRemember: Boolean) {
        CoroutineScope(viewModelScope.coroutineContext).launch {
            repo.editRemember(isRemember)
        }
    }

    fun loadUserData() {
        CoroutineScope(viewModelScope.coroutineContext).launch {
            repo.get().collect { userData ->
                if(userData.isRemembered) {
                    serverIp = userData.ipAddress
                    serverPort = userData.port
                    username = userData.login
                    password = userData.password
                    isRemembered = true
                }
            }
        }
    }

    fun changeUserData(userData: UserData) {
        CoroutineScope(viewModelScope.coroutineContext).launch {
            repo.edit(userData.login, userData.password, userData.ipAddress, userData.port)
            repo.editRemember(userData.isRemembered)
        }
    }

    fun updateServerIP(value: String) {
        serverIp = value
    }

    fun updateIsRemembered(value: Boolean) {
        isRemembered = value
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

    fun updateSocket(value: Socket?) {
        socket = value
    }

    fun socketClose() {
        socket?.close()
    }
}