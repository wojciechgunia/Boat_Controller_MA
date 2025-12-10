package pl.poznan.put.boatcontroller

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import pl.poznan.put.boatcontroller.dataclass.MissionListItemDto
import pl.poznan.put.boatcontroller.dataclass.ShipListItemDto
import pl.poznan.put.boatcontroller.socket.SocketRepository

/**
 * Trzyma stan całej aplikacji dostępny dla każdego ViewModelu.
 */
object SessionManager {
    var serverIp: String = ""
    var serverHttpPort: String = ""
    var serverSocketPort: Int = 9000 // Domyślnie lub pobierane z bazy

    var authToken: String? = null
    private val _selectedMission = MutableStateFlow<MissionListItemDto?>(null)
    val selectedMission = _selectedMission.asStateFlow()

    private val _selectedShip = MutableStateFlow<ShipListItemDto?>(null)
    val selectedShip = _selectedShip.asStateFlow()

    val isSessionActive: Boolean
        get() = authToken != null

    fun setMission(mission: MissionListItemDto) {
        _selectedMission.value = mission
    }

    fun setShip(ship: ShipListItemDto) {
        _selectedShip.value = ship
    }

    fun clearSession() {
        authToken = null
        _selectedMission.value = null
        _selectedShip.value = null
        SocketRepository.stop() // Metoda stop w repo do dodania (woła service.stop())
    }
}