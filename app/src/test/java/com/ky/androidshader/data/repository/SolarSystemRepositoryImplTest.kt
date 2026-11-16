package com.ky.androidshader.data.repository

import com.ky.androidshader.domain.model.CelestialBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class SolarSystemRepositoryImplTest {

    private val repository = SolarSystemRepositoryImpl()

    /** 位置容差：足够覆盖浮点三角函数的舍入。 */
    private val epsilon = 1e-4f

    /** 角度容差：覆盖大角度取模后的浮点误差。 */
    private val angleEpsilon = 1e-3f

    // ---------- t=0 初始位置 ----------

    @Test
    fun `t=0 太阳位于原点`() {
        val state = repository.computeState(0L)
        val sun = state.positions.getValue(CelestialBody.SUN)
        assertEquals(0f, sun.x, 0f)
        assertEquals(0f, sun.y, 0f)
        assertEquals(0f, sun.z, 0f)
    }

    @Test
    fun `t=0 地球位于轨道初相位处且公转角为零`() {
        val state = repository.computeState(0L)
        val earth = state.positions.getValue(CelestialBody.EARTH)
        // 初相位 0、半径 3.0：地球在 +x 轴上
        assertEquals(CelestialConfig.EARTH_ORBIT_RADIUS, earth.x, epsilon)
        assertEquals(0f, earth.y, epsilon)
        assertEquals(0f, earth.z, epsilon)
        assertEquals(0f, state.orbitAngles.getValue(CelestialBody.EARTH), 0f)
    }

    @Test
    fun `t=0 月亮位于地球加月球轨道初相位偏移处`() {
        val state = repository.computeState(0L)
        val moon = state.positions.getValue(CelestialBody.MOON)
        val expectedPhase = (PI / 3.0).toFloat() // 独立于 config 计算，校验初相位取值
        assertEquals(
            CelestialConfig.EARTH_ORBIT_RADIUS +
                CelestialConfig.MOON_ORBIT_RADIUS * cos(expectedPhase),
            moon.x,
            epsilon,
        )
        assertEquals(
            CelestialConfig.MOON_ORBIT_RADIUS * sin(expectedPhase),
            moon.y,
            epsilon,
        )
    }

    // ---------- 周期性 ----------

    @Test
    fun `地球公转周期 60 秒：t 与 t+T 位置一致`() {
        for (t in longArrayOf(0L, 1_234L, 30_000L, 59_999L)) {
            val a = repository.computeState(t).positions.getValue(CelestialBody.EARTH)
            val b = repository.computeState(t + 60_000L).positions.getValue(CelestialBody.EARTH)
            assertEquals("t=$t x", a.x, b.x, epsilon)
            assertEquals("t=$t y", a.y, b.y, epsilon)
            assertEquals("t=$t z", a.z, b.z, epsilon)
        }
    }

    @Test
    fun `月亮公转周期 8 秒：t 与 t+T 相对地球的位置一致`() {
        for (t in longArrayOf(0L, 999L, 4_000L, 7_999L)) {
            val a = repository.computeState(t)
            val b = repository.computeState(t + 8_000L)
            val moonA = a.positions.getValue(CelestialBody.MOON)
            val earthA = a.positions.getValue(CelestialBody.EARTH)
            val moonB = b.positions.getValue(CelestialBody.MOON)
            val earthB = b.positions.getValue(CelestialBody.EARTH)
            // 比较相对地球的偏移：地球自身 60 秒才回位，月亮 8 秒相对地球回位
            assertEquals("t=$t 相对 x", moonA.x - earthA.x, moonB.x - earthB.x, epsilon)
            assertEquals("t=$t 相对 y", moonA.y - earthA.y, moonB.y - earthB.y, epsilon)
        }
    }

    @Test
    fun `整系统周期 120 秒：t 与 t+120s 的位置与模 2π 角度一致`() {
        val t = 3_456L
        val a = repository.computeState(t)
        val b = repository.computeState(t + 120_000L)

        for (body in CelestialBody.entries) {
            assertEquals("$body x", a.positions.getValue(body).x, b.positions.getValue(body).x, epsilon)
            assertEquals("$body y", a.positions.getValue(body).y, b.positions.getValue(body).y, epsilon)
        }
        for (body in CelestialBody.entries) {
            assertEquals(
                "$body 自转角（模 2π）",
                wrapToTau(a.rotationAngles.getValue(body)),
                wrapToTau(b.rotationAngles.getValue(body)),
                angleEpsilon,
            )
        }
        assertEquals(
            "地球公转角（模 2π）",
            wrapToTau(a.orbitAngles.getValue(CelestialBody.EARTH)),
            wrapToTau(b.orbitAngles.getValue(CelestialBody.EARTH)),
            angleEpsilon,
        )
        assertEquals(
            "月亮公转角（模 2π）",
            wrapToTau(a.orbitAngles.getValue(CelestialBody.MOON)),
            wrapToTau(b.orbitAngles.getValue(CelestialBody.MOON)),
            angleEpsilon,
        )
    }

    // ---------- 月亮相对地球 ----------

    @Test
    fun `任意时刻月亮相对地球的位置等于月球轨道圆上求值`() {
        for (t in longArrayOf(0L, 1_500L, 4_000L, 12_345L, 40_000L)) {
            val state = repository.computeState(t)
            val earth = state.positions.getValue(CelestialBody.EARTH)
            val moon = state.positions.getValue(CelestialBody.MOON)
            val tSeconds = t / 1000f
            val expectedAngle = CelestialConfig.MOON_ORBIT_INITIAL_PHASE +
                CelestialConfig.TAU * tSeconds / CelestialConfig.MOON_ORBIT_PERIOD_S
            assertEquals(
                "t=$t 相对 x",
                CelestialConfig.MOON_ORBIT_RADIUS * cos(expectedAngle),
                moon.x - earth.x,
                epsilon,
            )
            assertEquals(
                "t=$t 相对 y",
                CelestialConfig.MOON_ORBIT_RADIUS * sin(expectedAngle),
                moon.y - earth.y,
                epsilon,
            )
        }
    }

    // ---------- 单调性 ----------

    @Test
    fun `地球公转角随时间单调递增`() {
        var previous = Float.NEGATIVE_INFINITY
        var t = 0L
        while (t <= 5_000L) {
            val angle = repository.computeState(t).orbitAngles.getValue(CelestialBody.EARTH)
            assertTrue("t=$t 公转角未递增: $previous -> $angle", angle > previous)
            previous = angle
            t += 250L
        }
    }

    @Test
    fun `三颗天体自转角随时间单调递增`() {
        for (body in CelestialBody.entries) {
            var previous = Float.NEGATIVE_INFINITY
            var t = 0L
            while (t <= 3_000L) {
                val angle = repository.computeState(t).rotationAngles.getValue(body)
                assertTrue("$body t=$t 自转角未递增: $previous -> $angle", angle > previous)
                previous = angle
                t += 300L
            }
        }
    }

    // ---------- 潮汐锁定与角速度 ----------

    @Test
    fun `月亮潮汐锁定：自转角与公转角始终相等`() {
        for (t in longArrayOf(0L, 1L, 2_500L, 9_999L, 33_333L)) {
            val state = repository.computeState(t)
            assertEquals(
                "t=$t 月亮自转角应与公转角同步",
                state.orbitAngles.getValue(CelestialBody.MOON),
                state.rotationAngles.getValue(CelestialBody.MOON),
                epsilon,
            )
        }
    }

    @Test
    fun `地球公转角按角速度线性计算`() {
        val t = 10_000L
        val angle = repository.computeState(t).orbitAngles.getValue(CelestialBody.EARTH)
        assertEquals(CelestialConfig.TAU * 10f / 60f, angle, epsilon)
    }

    @Test
    fun `地球自转周期 4 秒：t=1s 时自转角为 90 度`() {
        val angle = repository.computeState(1_000L).rotationAngles.getValue(CelestialBody.EARTH)
        assertEquals((PI / 2.0).toFloat(), angle, epsilon)
    }

    // ---------- 不变量 ----------

    @Test
    fun `状态包含完整的天体键`() {
        val state = repository.computeState(7_777L)
        val allBodies = setOf(CelestialBody.SUN, CelestialBody.EARTH, CelestialBody.MOON)
        assertEquals(allBodies, state.positions.keys)
        assertEquals(allBodies, state.rotationAngles.keys)
        assertEquals(setOf(CelestialBody.EARTH, CelestialBody.MOON), state.orbitAngles.keys)
    }

    @Test
    fun `负时间被钳制为 0`() {
        assertEquals(repository.computeState(0L), repository.computeState(-42L))
    }

    @Test
    fun `状态携带钳制后的时间戳`() {
        assertEquals(0L, repository.computeState(-1L).elapsedMillis)
        assertEquals(12_345L, repository.computeState(12_345L).elapsedMillis)
    }

    @Test
    fun `同一输入得到同一输出（确定性、无时钟依赖）`() {
        assertEquals(repository.computeState(9_876L), repository.computeState(9_876L))
    }

    // ---------- 工具 ----------

    private fun wrapToTau(angle: Float): Float {
        val tau = CelestialConfig.TAU
        return ((angle % tau) + tau) % tau
    }
}
