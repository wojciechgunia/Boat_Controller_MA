package pl.poznan.put.boatcontroller.domain.components.info_popup

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object InfoPopupManager {
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    
    private val _type = MutableStateFlow<InfoPopupType?>(null)
    val type = _type.asStateFlow()

    private var hideJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private const val DEFAULT_SHOW_TIME = 3000L

    fun show(message: String, type: InfoPopupType, duration: Long = DEFAULT_SHOW_TIME) {
        hideJob?.cancel()
        _message.value = message
        _type.value = type

        hideJob = scope.launch {
            delay(duration)
            _message.value = null
            _type.value = null
        }
    }

    fun hide() {
        hideJob?.cancel()
        _message.value = null
        _type.value = null
    }
}
