package pl.poznan.put.boatcontroller.templates

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import pl.poznan.put.boatcontroller.R
import pl.poznan.put.boatcontroller.dataclass.ShaderUtils
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class VRRenderer(
    private val context: Context,
    private val assets: AssetManager
) : GLSurfaceView.Renderer, SensorEventListener {

    private lateinit var cylinder: Cylinder
    private val rotationMatrix = FloatArray(16)

    private var textureId: Int = 0
    private var bitmap: Bitmap? = null

    private var surfaceWidth: Int = 0
    private var surfaceHeight: Int = 0

    override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glClearColor(0f, 0f, 0f, 1f)

        cylinder = Cylinder(radius = 2f, height = 4f, segments = 64)
        val shaderProgram = ShaderUtils.loadShaderProgram(assets, "vertex_shader.glsl", "fragment_shader.glsl")
        cylinder.initBuffers(shaderProgram)

        bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.panorama)
        textureId = ShaderUtils.loadTexture(bitmap!!)
    }

    override fun onSurfaceChanged(unused: GL10?, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(unused: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val halfWidth = surfaceWidth / 2

        GLES20.glViewport(0, 0, halfWidth, surfaceHeight)
        cylinder.draw(textureId, rotationMatrix)

        val shiftedViewMatrix = FloatArray(16)
        System.arraycopy(rotationMatrix, 0, shiftedViewMatrix, 0, 16)

        Matrix.translateM(shiftedViewMatrix, 0, -0.25f, 0f, 0f)

        GLES20.glViewport(halfWidth, 0, halfWidth, surfaceHeight)
        cylinder.draw(textureId, shiftedViewMatrix)

        drawCenterLine()
    }

    private fun drawCenterLine() {
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
        GLES20.glScissor(surfaceWidth / 2 - 1, 0, 2, surfaceHeight)
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
            val tempMatrix = FloatArray(16)
            SensorManager.getRotationMatrixFromVector(tempMatrix, event.values)

            SensorManager.remapCoordinateSystem(
                tempMatrix,
                SensorManager.AXIS_MINUS_Y,
                SensorManager.AXIS_X,
                rotationMatrix
            )
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
