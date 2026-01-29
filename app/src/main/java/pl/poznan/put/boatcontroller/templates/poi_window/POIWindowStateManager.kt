package pl.poznan.put.boatcontroller.templates.poi_window

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable

/**
 * Manager stanu dla POIWindow - zapisuje stan przy obrocie ekranu
 * Przechowuje:
 * - currentPoiIndex - indeks aktualnego POI
 * - imageIndexMap - mapa POI ID -> indeks zdjęcia (zapamiętuje indeks zdjęcia dla każdego POI osobno)
 */
class POIWindowStateManager(
    initialPoiIndex: Int = 0
) {
    var currentPoiIndex by mutableIntStateOf(initialPoiIndex)
    // Mapa przechowująca indeks zdjęcia dla każdego POI (klucz: POI ID, wartość: indeks zdjęcia)
    private val imageIndexMap = mutableStateOf<Map<Int, Int>>(emptyMap())
    
    fun reset() {
        currentPoiIndex = 0
        imageIndexMap.value = emptyMap()
    }
    
    fun updatePoiIndex(index: Int) {
        currentPoiIndex = index
        // Nie resetuj indeksu zdjęcia - zachowaj dla każdego POI osobno
    }
    
    fun getImageIndex(poiId: Int): Int {
        return imageIndexMap.value[poiId] ?: 0
    }
    
    fun updateImageIndex(poiId: Int, index: Int) {
        imageIndexMap.value = imageIndexMap.value + (poiId to index)
    }
    
    companion object {
        val Saver: Saver<POIWindowStateManager, *> = Saver(
            save = { manager ->
                // Zapisz currentPoiIndex i mapę jako listę par [poiId, imageIndex]
                val mapEntries = manager.imageIndexMap.value.entries.flatMap { listOf(it.key, it.value) }
                listOf(manager.currentPoiIndex, manager.imageIndexMap.value.size) + mapEntries
            },
            restore = { saved ->
                val poiIndex = saved[0] as Int
                val mapSize = saved[1] as Int
                val map = mutableMapOf<Int, Int>()
                for (i in 0 until mapSize) {
                    val poiId = saved[2 + i * 2] as Int
                    val imageIndex = saved[3 + i * 2] as Int
                    map[poiId] = imageIndex
                }
                POIWindowStateManager(initialPoiIndex = poiIndex).apply {
                    imageIndexMap.value = map
                }
            }
        )
    }
}

val LocalPOIWindowState = compositionLocalOf<POIWindowStateManager> { 
    error("No POIWindowStateManager provided") 
}

@Composable
fun rememberPOIWindowState(): POIWindowStateManager {
    // Użyj rememberSaveable z custom Saver aby przetrwał obrót ekranu
    // Klucz "poi_window_state" zapewnia że stan jest zapisywany i przywracany nawet gdy kompozycja jest usuwana
    return rememberSaveable(key = "poi_window_state", saver = POIWindowStateManager.Saver) {
        POIWindowStateManager()
    }
}

