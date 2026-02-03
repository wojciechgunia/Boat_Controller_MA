package pl.poznan.put.boatcontroller.ui.vr

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils

object ShaderUtils {
    fun loadShaderProgram(assets: AssetManager, vertexPath: String, fragmentPath: String): Int {
        val vertexShaderCode = assets.open(vertexPath).bufferedReader().use { it.readText() }
        val fragmentShaderCode = assets.open(fragmentPath).bufferedReader().use { it.readText() }

        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        return GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }
    }

    fun loadTexture(bitmap: Bitmap): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0])
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

        return textures[0]
    }

    private fun compileShader(type: Int, code: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, code)
            GLES20.glCompileShader(shader)
        }
    }
}
