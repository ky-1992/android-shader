package com.ky.androidshader.domain.model

/**
 * 太阳系模拟中的天体。
 */
enum class CelestialBody {
    /** 太阳：位于系统原点，只自转、不公转。 */
    SUN,

    /** 地球：绕太阳公转。 */
    EARTH,

    /** 月亮：绕地球公转，且潮汐锁定（自转周期 = 公转周期）。 */
    MOON,
}
