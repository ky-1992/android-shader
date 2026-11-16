package com.ky.androidshader.presentation.render

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 单位球网格（经纬细分）：仅顶点位置——单位球上法线 == 位置，
 * 片段着色器直接用 aPosition 同时作法线与程序化纹理坐标。
 */
internal class SphereMesh(
    stackCount: Int = DEFAULT_STACKS,
    sliceCount: Int = DEFAULT_SLICES,
) {

    private val vertexBuffer: FloatBuffer
    private val indexBuffer: ShortBuffer
    private val indexCount: Int

    init {
        val vertexCount = (stackCount + 1) * (sliceCount + 1)
        require(vertexCount < Short.MAX_VALUE) { "sphere mesh too dense for 16-bit indices" }

        val vertices = FloatArray(vertexCount * 3)
        var v = 0
        for (i in 0..stackCount) {
            val phi = PI * i / stackCount // 0..π（+z 极点到 -z 极点）
            val z = cos(phi)
            val ringRadius = sin(phi)
            for (j in 0..sliceCount) {
                val theta = 2.0 * PI * j / sliceCount
                vertices[v++] = (ringRadius * cos(theta)).toFloat()
                vertices[v++] = (ringRadius * sin(theta)).toFloat()
                vertices[v++] = z.toFloat()
            }
        }

        val indices = ShortArray(stackCount * sliceCount * 6)
        var k = 0
        for (i in 0 until stackCount) {
            for (j in 0 until sliceCount) {
                val a = (i * (sliceCount + 1) + j).toShort()
                val b = (a + 1).toShort()
                val c = (a + (sliceCount + 1)).toShort()
                val d = (c + 1).toShort()
                indices[k++] = a; indices[k++] = c; indices[k++] = b
                indices[k++] = b; indices[k++] = c; indices[k++] = d
            }
        }

        vertexBuffer = directFloatBuffer(vertices)
        indexBuffer = directShortBuffer(indices)
        indexCount = indices.size
    }

    /** 绑定 aPosition 属性并绘制三角面片。 */
    fun draw(positionHandle: Int) {
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, indexBuffer)
        GLES20.glDisableVertexAttribArray(positionHandle)
    }

    private fun directFloatBuffer(array: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(array.size * FLOAT_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(array); position(0) }

    private fun directShortBuffer(array: ShortArray): ShortBuffer =
        ByteBuffer.allocateDirect(array.size * SHORT_BYTES)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .apply { put(array); position(0) }

    private companion object {
        const val DEFAULT_STACKS = 20
        const val DEFAULT_SLICES = 36
        const val FLOAT_BYTES = 4
        const val SHORT_BYTES = 2
    }
}
