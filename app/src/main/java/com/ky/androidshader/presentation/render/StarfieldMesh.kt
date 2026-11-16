package com.ky.androidshader.presentation.render

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 星空点云：固定种子随机分布在大半径球壳上，
 * 交错缓冲布局为 (x, y, z, seed)，stride = 16 字节。
 */
internal class StarfieldMesh(
    starCount: Int = DEFAULT_STAR_COUNT,
    shellRadius: Float = DEFAULT_SHELL_RADIUS,
    seed: Long = DEFAULT_SEED,
) {

    private val buffer: FloatBuffer
    private val count: Int

    init {
        val random = Random(seed)
        val data = FloatArray(starCount * FLOATS_PER_STAR)
        for (i in 0 until starCount) {
            // 球面均匀分布
            val z = random.nextDouble() * 2.0 - 1.0
            val azimuth = random.nextDouble() * 2.0 * PI
            val ringRadius = sqrt(1.0 - z * z)
            data[i * FLOATS_PER_STAR + 0] = (shellRadius * ringRadius * cos(azimuth)).toFloat()
            data[i * FLOATS_PER_STAR + 1] = (shellRadius * ringRadius * sin(azimuth)).toFloat()
            data[i * FLOATS_PER_STAR + 2] = (shellRadius * z).toFloat()
            data[i * FLOATS_PER_STAR + 3] = random.nextFloat()
        }
        buffer = ByteBuffer.allocateDirect(data.size * FLOAT_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(data); position(0) }
        count = starCount
    }

    /** 绑定 aPosition / aSeed 两个属性并绘制点精灵。 */
    fun draw(positionHandle: Int, seedHandle: Int) {
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, STRIDE_BYTES, buffer)

        GLES20.glEnableVertexAttribArray(seedHandle)
        buffer.position(SEED_FLOAT_OFFSET) // 以 buffer 位置充当属性偏移
        GLES20.glVertexAttribPointer(seedHandle, 1, GLES20.GL_FLOAT, false, STRIDE_BYTES, buffer)
        buffer.position(0)

        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, count)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(seedHandle)
    }

    private companion object {
        const val DEFAULT_STAR_COUNT = 650
        const val DEFAULT_SHELL_RADIUS = 42f
        const val DEFAULT_SEED = 2024L
        const val FLOATS_PER_STAR = 4
        const val FLOAT_BYTES = 4
        const val STRIDE_BYTES = FLOATS_PER_STAR * FLOAT_BYTES
        const val SEED_FLOAT_OFFSET = 3
    }
}
