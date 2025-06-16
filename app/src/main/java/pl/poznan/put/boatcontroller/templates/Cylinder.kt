package pl.poznan.put.boatcontroller.templates

import android.opengl.GLES20
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.cos
import kotlin.math.sin

class Cylinder(
    private val radius: Float,
    private val height: Float,
    private val segments: Int
) {
    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var texBuffer: FloatBuffer
    private var vertexCount = 0
    private var program = 0

    fun initBuffers(program: Int) {
        this.program = program
        val vertices = mutableListOf<Float>()
        val texCoords = mutableListOf<Float>()

        for (i in 0..segments) {
            val angle = 2.0 * Math.PI * i / segments
            val x = cos(angle).toFloat()
            val z = sin(angle).toFloat()
            val u = 1.0f - i / segments.toFloat()

            // dolny
            vertices.addAll(listOf(x * radius, -height / 2, z * radius))
            texCoords.addAll(listOf(u, 1f))

            // górny
            vertices.addAll(listOf(x * radius, height / 2, z * radius))
            texCoords.addAll(listOf(u, 0f))
        }

        vertexCount = vertices.size / 3

        vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply {
                put(vertices.toFloatArray())
                position(0)
            }

        texBuffer = ByteBuffer.allocateDirect(texCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply {
                put(texCoords.toFloatArray())
                position(0)
            }
    }

    fun draw(texture: Int, viewMatrix: FloatArray) {
        GLES20.glUseProgram(program)

        val mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        val positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        val texCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
        val textureHandle = GLES20.glGetUniformLocation(program, "uTexture")

        val mvpMatrix = FloatArray(16)
        val projMatrix = FloatArray(16)
        val modelMatrix = FloatArray(16)

        Matrix.setIdentityM(modelMatrix, 0)

        Matrix.rotateM(modelMatrix, 0, -90f, 1f, 0f, 0f)

        Matrix.perspectiveM(projMatrix, 0, 80f, 1f, 0.1f, 100f)

        // MVP = proj * view * model
        val tempMatrix = FloatArray(16)
        Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, tempMatrix, 0)

        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)

        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texBuffer)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glUniform1i(textureHandle, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, vertexCount)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
    }

}
