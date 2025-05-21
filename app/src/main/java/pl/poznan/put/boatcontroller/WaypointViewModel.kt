package pl.poznan.put.boatcontroller

import android.app.Application
import androidx.lifecycle.AndroidViewModel

class WaypointViewModel(app: Application) : AndroidViewModel(app) {
    val shipPosition = doubleArrayOf(52.404633, 16.957722)
}