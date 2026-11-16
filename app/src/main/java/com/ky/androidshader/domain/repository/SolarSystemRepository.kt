package com.ky.androidshader.domain.repository

import com.ky.androidshader.domain.model.SolarSystemState

/**
 * 太阳系状态仓库：按时间取状态的纯函数式抽象。
 *
 * 实现必须是纯时间驱动、确定性的：同一个 [elapsedMillis] 永远得到同一个状态，
 * 不得读取系统时钟或持有可变状态。时间起点（模拟从何时开始）由调用方维护。
 */
interface SolarSystemRepository {

    /**
     * 计算距模拟起点 [elapsedMillis] 毫秒时的太阳系状态。
     * 负数时间会被钳制为 0。
     */
    fun computeState(elapsedMillis: Long): SolarSystemState
}
