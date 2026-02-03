package pl.poznan.put.boatcontroller.ui.vr

import android.content.pm.ActivityInfo
import android.hardware.Sensor
import android.hardware.SensorManager
import android.opengl.GLSurfaceView
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class VRActivity : AppCompatActivity() {
    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var renderer: VRRenderer
    private lateinit var sensorManager: SensorManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        glSurfaceView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(2)
            renderer = VRRenderer(this@VRActivity, assets)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }

        setContentView(glSurfaceView)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        sensorManager.registerListener(renderer, rotationSensor, SensorManager.SENSOR_DELAY_GAME)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(renderer)
        glSurfaceView.onPause()
    }

    override fun onResume() {
        super.onResume()
        glSurfaceView.onResume()
    }
}
