package com.ky.androidshader.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class OrbitParamsTest {

    @Test
    fun `angleAt 返回初相位加角速度乘时间`() {
        val params = OrbitParams(radius = 2f, angularVelocity = 3f, initialPhase = 0.5f)

        assertEquals(0.5f, params.angleAt(elapsedSeconds = 0f), 0f)
        assertEquals(0.5f + 3f * 1.5f, params.angleAt(elapsedSeconds = 1.5f), 0f)
        assertEquals(0.5f + 3f * 100f, params.angleAt(elapsedSeconds = 100f), 1e-3f)
    }

    @Test
    fun `angleAt 对非整数秒（毫秒级）同样成立`() {
        val params = OrbitParams(radius = 1f, angularVelocity = 0.5f, initialPhase = 0f)

        assertEquals(0.5f * 0.25f, params.angleAt(elapsedSeconds = 0.25f), 0f)
        assertEquals(0.5f * 61.234f, params.angleAt(elapsedSeconds = 61.234f), 1e-4f)
    }
}
