package com.ky.androidshader.data.repository

import com.ky.androidshader.domain.model.OrbitParams
import kotlin.math.PI

/**
 * 太阳系模拟的全部参数（唯一事实来源）。
 *
 * 世界坐标系约定：太阳在原点，黄道面为 z=0。
 * 长度单位任意（“世界单位”），由渲染层按视口缩放。
 *
 * 各周期选取原则：演示友好（几十秒内可观察完一圈），且
 * 全系统重复周期 = lcm(60, 8, 20, 4) = 120 秒。
 */
object CelestialConfig {

    /** 2π。 */
    val TAU: Float = (2.0 * PI).toFloat()

    // ---------- 天体自身大小（供渲染层参考） ----------

    const val SUN_BODY_RADIUS: Float = 1.0f
    const val EARTH_BODY_RADIUS: Float = 0.30f
    const val MOON_BODY_RADIUS: Float = 0.10f

    // ---------- 地球绕太阳公转：60 秒一圈 ----------

    const val EARTH_ORBIT_PERIOD_S: Float = 60.0f
    const val EARTH_ORBIT_RADIUS: Float = 3.0f
    const val EARTH_ORBIT_INITIAL_PHASE: Float = 0.0f

    // ---------- 月亮绕地球公转：8 秒一圈 ----------

    const val MOON_ORBIT_PERIOD_S: Float = 8.0f
    const val MOON_ORBIT_RADIUS: Float = 0.8f
    val MOON_ORBIT_INITIAL_PHASE: Float = (PI / 3.0).toFloat()

    // ---------- 自转 ----------

    /** 太阳自转：20 秒一圈（演示节奏）。 */
    const val SUN_ROTATION_PERIOD_S: Float = 20.0f
    const val SUN_ROTATION_INITIAL_PHASE: Float = 0.0f

    /** 地球自转：4 秒一圈（演示节奏）。 */
    const val EARTH_ROTATION_PERIOD_S: Float = 4.0f
    const val EARTH_ROTATION_INITIAL_PHASE: Float = 0.0f

    /** 月亮自转：潮汐锁定，自转周期 = 公转周期。 */
    const val MOON_ROTATION_PERIOD_S: Float = MOON_ORBIT_PERIOD_S
    val MOON_ROTATION_INITIAL_PHASE: Float = MOON_ORBIT_INITIAL_PHASE

    // ---------- 组装后的轨道参数 ----------

    /** 地球绕太阳公转的轨道参数。 */
    val EARTH_ORBIT: OrbitParams = OrbitParams(
        radius = EARTH_ORBIT_RADIUS,
        angularVelocity = TAU / EARTH_ORBIT_PERIOD_S,
        initialPhase = EARTH_ORBIT_INITIAL_PHASE,
    )

    /** 月亮绕地球公转的轨道参数（相对地球）。 */
    val MOON_ORBIT: OrbitParams = OrbitParams(
        radius = MOON_ORBIT_RADIUS,
        angularVelocity = TAU / MOON_ORBIT_PERIOD_S,
        initialPhase = MOON_ORBIT_INITIAL_PHASE,
    )
}
