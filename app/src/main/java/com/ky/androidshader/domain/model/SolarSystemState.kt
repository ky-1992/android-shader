package com.ky.androidshader.domain.model

/**
 * 某个时刻 t 的太阳系快照：三颗天体的位置、公转轨道角与自转角。
 *
 * @property elapsedMillis   距模拟起点的毫秒数（实现保证 >= 0）。
 * @property positions       三颗天体的世界坐标位置；太阳恒为原点。
 * @property orbitAngles     公转轨道角（弧度，未回绕，随时间单调递增）。
 *                           仅含 [CelestialBody.EARTH] 与 [CelestialBody.MOON]——太阳不公转。
 * @property rotationAngles  三颗天体的自转角（弧度，未回绕，随时间单调递增）。
 */
data class SolarSystemState(
    val elapsedMillis: Long,
    val positions: Map<CelestialBody, CelestialPosition>,
    val orbitAngles: Map<CelestialBody, Float>,
    val rotationAngles: Map<CelestialBody, Float>,
)
