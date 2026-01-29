package pl.poznan.put.boatcontroller.templates.poi_window

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manager stanu dla POIWindow - prosty singleton jak SocketRepository
 * Przechowuje tylko aktualny stan w MutableStateFlow (obserwowalny przez Compose i przetrwa obrót ekranu)
 * - currentPoiIndex - aktualny indeks POI w liście (który POI jest przeglądany)
 * - currentImageIndex - indeks zdjęcia dla aktualnego POI (tylko dla tego który przeglądamy)
 */
object POIWindowStateManager {
    private const val TAG = "POIWindowStateManager"
    
    // Aktualny indeks POI w liście (np. 0 = pierwszy POI, 1 = drugi POI)
    // MutableStateFlow - obserwowalny przez Compose i przetrwa obrót ekranu (jak SocketRepository)
    private val _currentPoiIndex = MutableStateFlow(0)
    val currentPoiIndex: StateFlow<Int> = _currentPoiIndex.asStateFlow()
    
    // Indeks zdjęcia dla aktualnego POI (tylko dla tego który aktualnie przeglądamy)
    // MutableStateFlow - obserwowalny przez Compose i przetrwa obrót ekranu
    private val _currentImageIndex = MutableStateFlow(0)
    val currentImageIndex: StateFlow<Int> = _currentImageIndex.asStateFlow()
    
    /**
     * Aktualizuj indeks POI
     */
    fun updatePoiIndex(index: Int) {
        Log.d(TAG, "updatePoiIndex: $index (było: ${_currentPoiIndex.value})")
        _currentPoiIndex.value = index
        Log.d(TAG, "updatePoiIndex: po aktualizacji = ${_currentPoiIndex.value}")
    }
    
    /**
     * Aktualizuj indeks zdjęcia
     */
    fun updateImageIndex(index: Int) {
        Log.d(TAG, "updateImageIndex: $index (było: ${_currentImageIndex.value})")
        _currentImageIndex.value = index
        Log.d(TAG, "updateImageIndex: po aktualizacji = ${_currentImageIndex.value}")
    }
    
    /**
     * Resetuj cały stan
     */
    fun resetAll() {
        Log.d(TAG, "resetAll")
        _currentPoiIndex.value = 0
        _currentImageIndex.value = 0
    }
    
    init {
        Log.d(TAG, "POIWindowStateManager initialized - currentPoiIndex=${_currentPoiIndex.value}, currentImageIndex=${_currentImageIndex.value}")
    }
}
