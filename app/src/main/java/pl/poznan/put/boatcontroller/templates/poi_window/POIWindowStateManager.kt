package pl.poznan.put.boatcontroller.templates.poi_window

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manager stanu dla POIWindow - prosty singleton jak SocketRepository
 * Przechowuje tylko aktualny stan w MutableStateFlow (obserwowalny przez Compose i przetrwa obrót ekranu)
 * - currentPoiIndex - aktualny indeks POI w liście (który POI jest przeglądany)
 * - currentImageIndex - indeks zdjęcia dla aktualnego POI (tylko dla tego który przeglądamy)
 * - currentPoiId - ID aktualnego POI (do śledzenia zmiany POI)
 * - initialized - czy manager został już zainicjalizowany
 * - lastPoiId - ostatni poiId użyty do inicjalizacji
 */
object POIWindowStateManager {
    // Aktualny indeks POI w liście (np. 0 = pierwszy POI, 1 = drugi POI)
    private val _currentPoiIndex = MutableStateFlow(0)
    val currentPoiIndex: StateFlow<Int> = _currentPoiIndex.asStateFlow()
    
    // Indeks zdjęcia dla aktualnego POI (tylko dla tego który aktualnie przeglądamy)
    private val _currentImageIndex = MutableStateFlow(0)
    val currentImageIndex: StateFlow<Int> = _currentImageIndex.asStateFlow()
    
    // ID aktualnego POI (do śledzenia zmiany POI)
    private val _currentPoiId = MutableStateFlow(-1)
    val currentPoiId: StateFlow<Int> = _currentPoiId.asStateFlow()
    
    // Czy manager został już zainicjalizowany
    private var initialized = false
    
    // Ostatni poiId użyty do inicjalizacji
    private var lastPoiId = -1
    
    /**
     * Inicjalizuj manager z poiId (tylko raz przy pierwszym otwarciu lub gdy poiId się zmienia)
     */
    fun initializeIfNeeded(poiId: Int, poiListSize: Int) {
        if ((!initialized || poiId != lastPoiId) && poiListSize > 0) {
            initialized = true
            lastPoiId = poiId
            if (poiId >= 0 && poiId < poiListSize && _currentPoiIndex.value != poiId) {
                _currentPoiIndex.value = poiId
            }
        }
    }
    
    /**
     * Aktualizuj indeks POI
     */
    fun updatePoiIndex(index: Int) {
        _currentPoiIndex.value = index
    }
    
    /**
     * Aktualizuj indeks zdjęcia
     */
    fun updateImageIndex(index: Int) {
        _currentImageIndex.value = index
    }
    
    /**
     * Aktualizuj ID POI (gdy POI się zmienia)
     */
    fun updatePoiId(poiId: Int) {
        _currentPoiId.value = poiId
    }
    
    /**
     * Resetuj cały stan
     */
    fun resetAll() {
        _currentPoiIndex.value = 0
        _currentImageIndex.value = 0
        _currentPoiId.value = -1
        initialized = false
        lastPoiId = -1
    }
}
