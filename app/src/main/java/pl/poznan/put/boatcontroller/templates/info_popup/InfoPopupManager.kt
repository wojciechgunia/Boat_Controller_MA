package pl.poznan.put.boatcontroller.templates.info_popup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object InfoPopupManager {
    var message by mutableStateOf<String?>(null)
        private set
    var type by mutableStateOf<InfoPopupType?>(null)
        private set

    private var hideJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private const val defaultShowTime = 3000L

    fun show(message: String, type: InfoPopupType, duration: Long = defaultShowTime) {
        hideJob?.cancel()
        this.message = message
        this.type = type

        hideJob = scope.launch {
            delay(duration)
            this@InfoPopupManager.message = null
            this@InfoPopupManager.type = null
        }
    }

    fun hide() {
        hideJob?.cancel()
        message = null
        type = null
    }
}
