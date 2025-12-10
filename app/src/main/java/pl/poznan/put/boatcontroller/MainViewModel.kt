package pl.poznan.put.boatcontroller

import  android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import pl.poznan.put.boatcontroller.api.ApiClient
import pl.poznan.put.boatcontroller.api.AuthClient
import pl.poznan.put.boatcontroller.api.TokenManager
import pl.poznan.put.boatcontroller.data.UserData
import pl.poznan.put.boatcontroller.dataclass.LoginRequest
import pl.poznan.put.boatcontroller.dataclass.MissionCreateRequest
import pl.poznan.put.boatcontroller.dataclass.MissionListItemDto
import pl.poznan.put.boatcontroller.dataclass.ShipListItemDto
import pl.poznan.put.boatcontroller.socket.SocketRepository
import pl.poznan.put.boatcontroller.socket.SocketCommand
import pl.poznan.put.boatcontroller.socket.SocketEvent
import java.util.concurrent.atomic.AtomicInteger

@SuppressLint("MutableCollectionMutableState")
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repository(app.applicationContext)

    var serverIp by mutableStateOf("")
        private set
    var serverPort by mutableStateOf("")
        private set
    var serverSocketPort by mutableStateOf("9000")
        private set
    var username by mutableStateOf("")
        private set
    var password by mutableStateOf("")
        private set
    var isRemembered by mutableStateOf(false)
        private set

    var error by mutableStateOf(false)
        private set

    var isLoggedIn by mutableStateOf<Boolean>(false)
        private set

    var selectedMission by mutableStateOf<MissionListItemDto>(MissionListItemDto(-1, ""))
        private set

    var selectedShip by mutableStateOf<ShipListItemDto>(ShipListItemDto(-1, "", 0, "", ""))
        private set

    var isCaptain by mutableStateOf<Boolean>(false)
        private set

    var missions by mutableStateOf<List<MissionListItemDto>>(emptyList())
    var ships by mutableStateOf<List<ShipListItemDto>>(emptyList())
    
    // Status połączenia socketu
    var isSocketConnected by mutableStateOf<Boolean>(false)
        private set
    
    // Ostatnia odpowiedź z socketu (do testowania)
    var lastSocketResponse by mutableStateOf<String>("")
        private set
    
    // Lista ostatnich wiadomości (do debugowania)
    var socketMessages by mutableStateOf<List<String>>(emptyList())
        private set

    private val seq = AtomicInteger(0)

    init {
        insertDatabase()
        loadUserData()
        observeSocketConnection()
        observeSocketEvents()
    }
    
    private fun observeSocketConnection() {
        viewModelScope.launch {
            SocketRepository.connectionState.collect { connected ->
                isSocketConnected = connected
                if (!connected) {
                    lastSocketResponse = "Disconnected"
                }
            }
        }
    }
    
    private fun observeSocketEvents() {
        viewModelScope.launch {
            SocketRepository.events.collect { event ->
                val eventStr = when (event) {
                    is SocketEvent.BoatInformation -> "BI: ${event.name}/${event.captain}/${event.mission}"
                    is SocketEvent.BoatInformationChange -> "BIC: ${event.name}/${event.captain}/${event.mission}"
                    is SocketEvent.PositionActualisation -> "PA: ${event.lat},${event.lon} speed=${event.speed} sNum=${event.sNum}"
                    is SocketEvent.SensorInformation -> "SI: mag=${event.magnetic} depth=${event.depth}"
                    is SocketEvent.WarningInformation -> "WI: ${event.infoCode}"
                    is SocketEvent.LostInformation -> "LI: sNum=${event.sNum}"
                }
                lastSocketResponse = eventStr
                socketMessages = (listOf("📥 $eventStr") + socketMessages).take(10) // Ostatnie 10 wiadomości
            }
        }
    }

    private fun insertDatabase() {
        val userData = UserData(1, "", "", "10.0.2.2", "8000", false)
        CoroutineScope(viewModelScope.coroutineContext).launch {
            if (repo.getCount() == 0) {
                repo.insert(userData)
            }
        }
    }

    fun changeIsRemembered() {
        CoroutineScope(viewModelScope.coroutineContext).launch {
            repo.editRemember(isRemembered)
        }
    }

    fun loadUserData() {
        CoroutineScope(viewModelScope.coroutineContext).launch {
            repo.get().collect { userData ->
                serverIp = userData.ipAddress
                serverPort = userData.port
                if (userData.isRemembered) {
                    username = userData.login
                    password = userData.password
                    isRemembered = true
                }
            }
        }
    }

    fun changeUserData() {
        CoroutineScope(viewModelScope.coroutineContext).launch {
            repo.edit(username, password)
        }
    }

    fun changeServerData() {
        CoroutineScope(viewModelScope.coroutineContext).launch {
            repo.editServer(serverIp, serverPort)
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

    fun updateError(value: Boolean) {
        error = value
    }

    fun updateSelectedMission(value: MissionListItemDto) {
        selectedMission = value
        if (isLoggedIn && value.id != -1) {
            sendSetMission(value.name)
        }
    }

    fun connect() {
        viewModelScope.launch {
            error = false
            getLoggedIn()
            getMissions()

            if (!error && isLoggedIn) {
                startSocketConnection()
            } else {
                // W razie niepowodzenia upewniamy się, że socket jest zatrzymany
                SocketRepository.stop()
            }
        }
    }

    fun disconnect() {
        isLoggedIn = false
        selectedShip = ShipListItemDto(-1, "", 0, "", "")
        selectedMission = MissionListItemDto(-1, "")
        SocketRepository.stop()
    }

    suspend fun getLoggedIn() {
        try {
            AuthClient.setBaseUrl("http://${serverIp}:${serverPort}/")
            val loginResponse = AuthClient.authApi.login(
                LoginRequest(username, password)
            )
            TokenManager.saveToken(getApplication(), loginResponse.access_token)
            changeIsRemembered()
            if(isRemembered) {
                changeUserData()
            }
        } catch (e: Exception) {
            Log.e("API", "Login error", e)
            error = true
        }
    }

    suspend fun getMissions() {
        try {
            ApiClient.setBaseUrl("http://${serverIp}:${serverPort}/")
            val api = ApiClient.create(getApplication())
            val response = api.getMissions()
            if(response.isSuccessful) {
                missions = response.body()!!
            } else {
                error = true
            }
            if(!error) {
                isLoggedIn = true
            }
        } catch (e: Exception) {
            Log.e("API", "Missions fetch error", e)
            isLoggedIn = false
            error = true
        }
    }


    fun createMission(name: String) {
        viewModelScope.launch {
            try {
                ApiClient.setBaseUrl("http://${serverIp}:${serverPort}/")
                val api = ApiClient.create(getApplication())
                var mission = api.createMission(MissionCreateRequest(name))
                missions = (missions + mission) as List<MissionListItemDto>
            } catch (e: Exception) {
                Log.e("API", "Missions create error", e)
            }
        }
    }

    fun updateSelectedShip(name: String, role: String) {
        isCaptain = role == "Captain"
        selectedShip = if(name=="Demo")
            ShipListItemDto(-2, "Demo", 1, username, selectedMission.name)
        else
            ships.find { it.name == name }!!
    }

    private fun startSocketConnection() {
        val port = serverSocketPort.toIntOrNull() ?: 9000
        SocketRepository.start(serverIp, port)
    }

    private fun nextSNum(): Int = seq.incrementAndGet()

    private fun sendSetMission(mission: String) {
        viewModelScope.launch {
            SocketRepository.send(SocketCommand.SetMission(mission, nextSNum()))
        }
    }
    
    // Metody testowe socketu
    fun testSocketConnection() {
        viewModelScope.launch {
            if (isSocketConnected) {
                lastSocketResponse = "Testing... (sending GBI)"
                socketMessages = listOf("📤 GBI:GBI") + socketMessages.take(9)
                SocketRepository.send(SocketCommand.GetBoatInformation)
            }
        }
    }
    
    fun testSetSpeed(left: Double = 0.5, right: Double = 0.5) {
        viewModelScope.launch {
            if (isSocketConnected) {
                val sNum = nextSNum()
                lastSocketResponse = "Testing SetSpeed: left=$left, right=$right, sNum=$sNum"
                socketMessages = listOf("📤 SS:$left:$right:$sNum:SS") + socketMessages.take(9)
                SocketRepository.send(SocketCommand.SetSpeed(left, right, sNum))
            }
        }
    }
    
    fun testSetAction(action: String, payload: String = "") {
        viewModelScope.launch {
            if (isSocketConnected) {
                val sNum = nextSNum()
                lastSocketResponse = "Testing SetAction: action=$action, payload=$payload, sNum=$sNum"
                socketMessages = listOf("📤 SA:$action:$payload:$sNum:SA") + socketMessages.take(9)
                SocketRepository.send(SocketCommand.SetAction(action, payload, sNum))
            }
        }
    }
}
