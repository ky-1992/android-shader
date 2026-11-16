package com.ky.androidshader.data.repository

import com.ky.androidshader.domain.model.CelestialBody
import com.ky.androidshader.domain.model.CelestialPosition
import com.ky.androidshader.domain.model.OrbitParams
import com.ky.androidshader.domain.model.SolarSystemState
import com.ky.androidshader.domain.repository.SolarSystemRepository
import kotlin.math.cos
import kotlin.math.sin

/**
 * 纯时间驱动的太阳系状态计算（[SolarSystemRepository] 的默认实现）。
 *
 * 模型（世界坐标，黄道面 z=0，太阳在原点）：
 *  - 地球位置 = 地球轨道参数在圆上求值（绕太阳）；
 *  - 月亮位置 = 地球位置 + 月亮轨道参数在圆上求值（层级轨道）；
 *  - 各天体自转角 = 自转初相位 + 2π·t/自转周期。
 *
 * 所有参数见 [CelestialConfig]。计算为纯函数：不读时钟、不持可变状态，
 * 同一输入必得同一输出；负时间钳制为 0。
 */
class SolarSystemRepositoryImpl : SolarSystemRepository {

    override fun computeState(elapsedMillis: Long): SolarSystemState {
        val clampedMillis = elapsedMillis.coerceAtLeast(0L)
        val tSeconds = clampedMillis / MILLIS_PER_SECOND

        // 公转角（未回绕，随时间单调递增）
        val earthOrbitAngle = CelestialConfig.EARTH_ORBIT.angleAt(tSeconds)
        val moonOrbitAngle = CelestialConfig.MOON_ORBIT.angleAt(tSeconds)

        // 位置：地球绕太阳；月亮 = 地球 + 绕地轨道偏移
        val earth = positionOnCircle(CelestialConfig.EARTH_ORBIT, earthOrbitAngle)
        val moonOffset = positionOnCircle(CelestialConfig.MOON_ORBIT, moonOrbitAngle)
        val moon = CelestialPosition(
            x = earth.x + moonOffset.x,
            y = earth.y + moonOffset.y,
            z = 0f,
        )

        return SolarSystemState(
            elapsedMillis = clampedMillis,
            positions = mapOf(
                CelestialBody.SUN to CelestialPosition(x = 0f, y = 0f, z = 0f),
                CelestialBody.EARTH to earth,
                CelestialBody.MOON to moon,
            ),
            orbitAngles = mapOf(
                CelestialBody.EARTH to earthOrbitAngle,
                CelestialBody.MOON to moonOrbitAngle,
            ),
            rotationAngles = mapOf(
                CelestialBody.SUN to rotationAngle(
                    initialPhase = CelestialConfig.SUN_ROTATION_INITIAL_PHASE,
                    periodS = CelestialConfig.SUN_ROTATION_PERIOD_S,
                    tSeconds = tSeconds,
                ),
                CelestialBody.EARTH to rotationAngle(
                    initialPhase = CelestialConfig.EARTH_ROTATION_INITIAL_PHASE,
                    periodS = CelestialConfig.EARTH_ROTATION_PERIOD_S,
                    tSeconds = tSeconds,
                ),
                CelestialBody.MOON to rotationAngle(
                    initialPhase = CelestialConfig.MOON_ROTATION_INITIAL_PHASE,
                    periodS = CelestialConfig.MOON_ROTATION_PERIOD_S,
                    tSeconds = tSeconds,
                ),
            ),
        )
    }

    /** 在以原点为圆心、参数为 [orbit] 的轨道上，[angle] 弧度处的位置。 */
    private fun positionOnCircle(orbit: OrbitParams, angle: Float): CelestialPosition =
        CelestialPosition(
            x = orbit.radius * cos(angle),
            y = orbit.radius * sin(angle),
            z = 0f,
        )

    /** 自转角（未回绕）：初相位 + 2π·t/周期。 */
    private fun rotationAngle(initialPhase: Float, periodS: Float, tSeconds: Float): Float =
        initialPhase + CelestialConfig.TAU * tSeconds / periodS

    private companion object {
        const val MILLIS_PER_SECOND = 1000f
    }
}
