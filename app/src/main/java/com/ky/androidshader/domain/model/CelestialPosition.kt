package com.ky.androidshader.domain.model

/**
 * 某天体在世界坐标系中的位置。
 *
 * 约定：太阳位于原点，黄道面为 z=0 的平面（本模拟为平面圆轨道）。
 */
data class CelestialPosition(
    val x: Float,
    val y: Float,
    val z: Float = 0f,
)
