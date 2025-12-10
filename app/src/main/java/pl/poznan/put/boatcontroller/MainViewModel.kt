package pl.poznan.put.boatcontroller

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import pl.poznan.put.boatcontroller.api.ApiClient
import pl.poznan.put.boatcontroller.api.AuthClient
import pl.poznan.put.boatcontroller.api.TokenManager
import pl.poznan.put.boatcontroller.data.UserData
import pl.poznan.put.boatcontroller.dataclass.LoginRequest
import pl.poznan.put.boatcontroller.dataclass.MissionCreateRequest
import pl.poznan.put.boatcontroller.dataclass.MissionListItemDto
import pl.poznan.put.boatcontroller.dataclass.ShipListItemDto
import pl.poznan.put.boatcontroller.dataclass.ShipOption
import pl.poznan.put.boatcontroller.socket.SocketRepository

sealed class MainUiState {
    object Idle : MainUiState()
    object Loading : MainUiState()
    object LoggedIn : MainUiState()
    data class Error(val message: String) : MainUiState()
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repository(app.applicationContext)

    // --- Konfiguracja i Dane Logowania ---
    var serverIp by mutableStateOf("10.0.2.2")
        private set
    var serverPort by mutableStateOf("8000")
        private set
    var username by mutableStateOf("")
        private set
    var password by mutableStateOf("")
        private set
    var isRemembered by mutableStateOf(false)
        private set

    // --- Stan Aplikacji ---
    var uiState by mutableStateOf<MainUiState>(MainUiState.Idle)
        private set

    // --- Listy Danych ---
    // Używamy mutableStateListOf, aby Compose wiedział o zmianach w liście
    var missions = mutableStateListOf<MissionListItemDto>()
        private set
    var ships = mutableStateListOf<ShipListItemDto>()
        private set

    // Lokalne zaznaczenie (dla UI - np. podświetlenie kafelka)
    // Fizyczne przekazanie nastąpi do SessionManagera przy wyborze
    var selectedMissionId by mutableStateOf<Int?>(null)
        private set
    var selectedShipId by mutableStateOf<Int?>(null)
        private set

    // Czy użytkownik jest kapitanem wybranego statku (logika UI)
    var isCaptain by mutableStateOf(false)
        private set

    init {
        // Przy starcie ładujemy dane z bazy i próbujemy auto-login
        loadConfigAndAttemptAutoLogin()
    }

    // --- Metody Aktualizacji Stanu (Publiczne Settery) ---

    fun updateServerIP(value: String) { serverIp = value }
    fun updateServerPort(value: String) { serverPort = value }
    fun updateUsername(value: String) { username = value }
    fun updatePassword(value: String) { password = value }
    fun updateIsRemembered(value: Boolean) {
        isRemembered = value
        // Od razu aktualizujemy w bazie preferencję "zapamiętaj"
        viewModelScope.launch { repo.editRemember(value) }
    }

    // --- Logika Biznesowa ---

    private fun loadConfigAndAttemptAutoLogin() {
        viewModelScope.launch {
            val userData = repo.get().firstOrNull()

            if (userData != null) {
                serverIp = userData.ipAddress
                serverPort = userData.port
                isRemembered = userData.isRemembered

                if (isRemembered) {
                    username = userData.login
                    password = userData.password
                    performLogin()
                }
            }
        }
    }

    fun connectClicked() {
        // Zapisujemy konfigurację IP/Port przy próbie połączenia
        viewModelScope.launch { repo.editServer(serverIp, serverPort) }
        performLogin()
    }

    fun changeServerData() {
        // Ta metoda jest już poprawna i deleguje do repozytorium
        viewModelScope.launch {
            repo.editServer(serverIp, serverPort)
        }
    }

    private fun performLogin() {
        if (uiState is MainUiState.Loading) return // Zabezpieczenie przed podwójnym klikiem

        viewModelScope.launch {
            uiState = MainUiState.Loading

            try {
                // 1. Konfiguracja klientów HTTP
                val baseUrl = "http://${serverIp}:${serverPort}/"
                AuthClient.setBaseUrl(baseUrl)
                ApiClient.setBaseUrl(baseUrl)

                // 2. Strzał logowania
                val loginResponse = AuthClient.authApi.login(
                    LoginRequest(username, password)
                )

                // 3. Sukces logowania - aktualizacja bazy (jeśli zapamiętane)
                if (isRemembered) {
                    repo.edit(username, password)
                }
                TokenManager.saveToken(getApplication(), loginResponse.access_token)

                // 4. Aktualizacja SessionManagera (Global State)
                SessionManager.serverIp = serverIp
                SessionManager.serverHttpPort = serverPort
                SessionManager.authToken = loginResponse.access_token
                // Zakładam port socketu na sztywno lub z konfiguracji
                SessionManager.serverSocketPort = 9000

                // 5. Pobranie danych biznesowych (Misje)
                fetchDataAfterLogin()

                // 6. URUCHOMIENIE SOCKETU
                // Startujemy socket teraz, gdy mamy token i pewność połączenia
                SocketRepository.start(SessionManager.serverIp, SessionManager.serverSocketPort)

                uiState = MainUiState.LoggedIn

            } catch (e: Exception) {
                Log.e("MainViewModel", "Login error", e)
                uiState = MainUiState.Error("Błąd połączenia: ${e.message}")
                // W razie błędu czyścimy socket
                SocketRepository.stop()
            }
        }
    }

    private suspend fun fetchDataAfterLogin() {
        try {
            val api = ApiClient.create(getApplication())
            val missionsResponse = api.getMissions()

            if (missionsResponse.isSuccessful && missionsResponse.body() != null) {
                missions.clear()
                missions.addAll(missionsResponse.body()!!)
            } else {
                throw Exception("Nie udało się pobrać misji: ${missionsResponse.code()}")
            }
            // Można tu też pobrać statki, jeśli API na to pozwala bez wyboru misji
        } catch (e: Exception) {
            throw e // Rzucamy dalej, żeby obsłużył to blok catch w performLogin
        }
    }

    // --- Zarządzanie Misjami i Statkami ---

    fun createMission(name: String) {
        viewModelScope.launch {
            try {
                val api = ApiClient.create(getApplication())
                // 1. Wysyłamy żądanie. Zakładam, że ten call zwraca Response<Unit> lub Response<MissionListItemDto>
                val response = api.createMission(MissionCreateRequest(name))

                if (response.isSuccessful) {
                    // Jeśli API zwraca obiekt (DTO) misji, używamy go:
                    val newMissionDto = response.body()

                    // Jeśli API ZWRACA PUSTĄ ODPOWIEDŹ (Unit) - tworzymy DTO ręcznie (uproszczone):
                    val missionToAdd = newMissionDto ?: MissionListItemDto(
                        id = System.currentTimeMillis().toInt(), // Tymczasowe ID, jeśli API nie zwraca
                        name = name
                    )

                    // 2. Dodajemy nową misję do listy. Używasz mutableStateListOf, więc wystarczy add().
                    // Pamiętaj, że jeśli API zwraca *Response<Unit>*, musisz mieć inny endpoint
                    // do pobrania pełnej listy, aby mieć poprawne ID, lub API musi zwrócić ID.

                    // Na potrzeby demo, dodajemy obiekt:
                    missions.add(missionToAdd as MissionListItemDto)

                } else {
                    throw Exception("Błąd API: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Missions create error", e)
                uiState = MainUiState.Error("Nie udało się utworzyć misji: ${e.message}")
            }
        }
    }

    fun onMissionSelected(mission: MissionListItemDto) {
        selectedMissionId = mission.id
        // Aktualizujemy globalny stan
        SessionManager.setMission(mission)

        // Tutaj logika pobierania statków dla danej misji
        // (Zakładam, że statki zależą od misji, jeśli nie - pobierz je wcześniej)
        fetchShipsForMission(mission.id)
    }

    private fun fetchShipsForMission(missionId: Int) {
        viewModelScope.launch {
            // Symulacja pobierania statków (dostosuj do swojego API)
            try {
                // val api = ApiClient.create(getApplication())
                // val shipsResponse = api.getShips(missionId)
                // if (shipsResponse.isSuccessful) ...

                // Placeholder logic based on your previous code
                ships.clear()
                // ships.addAll(...)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Fetch ships error", e)
            }
        }
    }

    fun onShipSelected(shipOption: ShipOption) {

        val finalShip: ShipListItemDto = if (shipOption.name == "Demo") {
            // Specjalny przypadek "Demo": Użycie stałych danych i Twojego username/misji
            ShipListItemDto(
                id = -2,
                name = "Demo",
                connections = 1,
                captain = username, // Używa username zalogowanego użytkownika
                mission = SessionManager.selectedMission.value?.name ?: "Demo Mission"
            )
        } else {
            // Normalny przypadek: Znajdowanie pełnego obiektu DTO w pobranej liście statków
            ships.find { it.name == shipOption.name}
                ?: throw IllegalStateException("Nie znaleziono statku o nazwie: $shipOption.name")
        }

        // 1. Ustawienie roli Captain
        isCaptain = shipOption.role == "Captain"

        // 2. Ustawienie lokalnego ID dla UI (opcjonalnie)
        selectedShipId = finalShip.id

        // 3. Zapisanie pełnego obiektu do SessionManagera (Globalny stan)
        SessionManager.setShip(finalShip)
    }

    fun clearUiError() {
        // Wracamy do stanu bazowego, jeśli obecny stan to błąd.
        if (uiState is MainUiState.Error) {
            uiState = MainUiState.Idle
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            uiState = MainUiState.Idle
            missions.clear()
            ships.clear()
            selectedMissionId = null
            selectedShipId = null

            // Czyścimy sesję i zamykamy socket
            SessionManager.clearSession() // Ta metoda powinna wołać SocketRepository.stop()
        }
    }
}