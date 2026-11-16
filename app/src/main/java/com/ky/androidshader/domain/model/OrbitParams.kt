package com.ky.androidshader.domain.model

/**
 * 一条圆轨道的运动参数。
 *
 * @property radius           轨道半径（世界坐标单位，具体尺度由渲染层解释）。
 * @property angularVelocity  角速度，单位 弧度/秒；正值表示逆时针（数学正方向）。
 * @property initialPhase     t=0 时的初相位，单位 弧度。
 */
data class OrbitParams(
    val radius: Float,
    val angularVelocity: Float,
    val initialPhase: Float,
) {

    /**
     * 轨道角（弧度）。不做 2π 回绕，因此随时间单调递增；
     * 需要回绕的调用方自行取模。
     *
     * @param elapsedSeconds 距模拟起点的秒数。
     */
    fun angleAt(elapsedSeconds: Float): Float =
        initialPhase + angularVelocity * elapsedSeconds
}
