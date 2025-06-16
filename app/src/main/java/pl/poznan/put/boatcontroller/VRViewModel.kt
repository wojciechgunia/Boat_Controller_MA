package pl.poznan.put.boatcontroller

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import java.io.ByteArrayOutputStream
import java.util.Base64

class VRViewModel(app: Application) : AndroidViewModel(app) {
    var cameraFeed by mutableStateOf(drawableToByteArray(app, R.drawable.panorama))
        private set

    fun updateCameraFeed(cameraFeed: ByteArray) {
        this.cameraFeed = cameraFeed
    }

    fun drawableToByteArray(context: Context, drawableResId: Int, format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG, quality: Int = 100): ByteArray {
        val bitmap = BitmapFactory.decodeResource(context.resources, drawableResId)
        val stream = ByteArrayOutputStream()
        bitmap.compress(format, quality, stream)
        return stream.toByteArray()
    }

    fun initSocket() {
        SocketClientManager.setOnMessageReceivedListener { message ->
            handleServerMessage(message)
        }
    }

    init {
        initSocket()
    }

    fun handleServerMessage(message: String) {
        if (message.startsWith("CAM_UPDATE:")) {
            updateCameraFeed(Base64.getDecoder().decode(message.substringAfter("CAM_UPDATE:").trim()))
        }
    }

    fun sendMessage(message: String) {
        SocketClientManager.sendMessage(message)
    }
}