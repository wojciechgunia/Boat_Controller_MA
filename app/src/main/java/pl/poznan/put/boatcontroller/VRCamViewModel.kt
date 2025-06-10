package pl.poznan.put.boatcontroller

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class VRCamViewModel(app: Application) : AndroidViewModel(app) {
    private val _videoUrl = MutableStateFlow("http://192.168.1.4:5000/stream")
    val videoUrl = _videoUrl.asStateFlow()
}
