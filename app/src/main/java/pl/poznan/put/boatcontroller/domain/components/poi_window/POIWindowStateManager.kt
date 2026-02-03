package pl.poznan.put.boatcontroller.domain.components.poi_window

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
    private val _currentPoiIndex = MutableStateFlow(0)
    val currentPoiIndex: StateFlow<Int> = _currentPoiIndex.asStateFlow()

    private val _currentImageIndex = MutableStateFlow(0)
    val currentImageIndex: StateFlow<Int> = _currentImageIndex.asStateFlow()

    private val _currentPoiId = MutableStateFlow(-1)
    val currentPoiId: StateFlow<Int> = _currentPoiId.asStateFlow()

    private var initialized = false
    private var lastPoiId = -1

    fun initializeIfNeeded(poiId: Int, poiListSize: Int) {
        if ((!initialized || poiId != lastPoiId) && poiListSize > 0) {
            initialized = true
            lastPoiId = poiId
            if (poiId >= 0 && poiId < poiListSize && _currentPoiIndex.value != poiId) {
                _currentPoiIndex.value = poiId
            }
        }
    }

    fun updatePoiIndex(index: Int) {
        _currentPoiIndex.value = index
    }

    fun updateImageIndex(index: Int) {
        _currentImageIndex.value = index
    }

    fun updatePoiId(poiId: Int) {
        _currentPoiId.value = poiId
    }

    fun resetAll() {
        _currentPoiIndex.value = 0
        _currentImageIndex.value = 0
        _currentPoiId.value = -1
        initialized = false
        lastPoiId = -1
    }
}
