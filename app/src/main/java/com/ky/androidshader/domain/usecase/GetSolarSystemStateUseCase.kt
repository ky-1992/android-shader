package com.ky.androidshader.domain.usecase

import com.ky.androidshader.domain.model.SolarSystemState
import com.ky.androidshader.domain.repository.SolarSystemRepository

/**
 * 用例：获取某时刻的太阳系状态。
 *
 * 渲染层每帧以流逝时间调用本用例（拉模式）即可；
 * 若后续需要观察者形式（Flow/tick），在仓库接口上扩展，而不改动本用例。
 */
class GetSolarSystemStateUseCase(
    private val repository: SolarSystemRepository,
) {

    /** 计算距模拟起点 [elapsedMillis] 毫秒的太阳系状态。 */
    operator fun invoke(elapsedMillis: Long): SolarSystemState =
        repository.computeState(elapsedMillis)
}
