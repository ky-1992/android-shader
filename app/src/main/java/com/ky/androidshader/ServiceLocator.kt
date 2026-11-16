package com.ky.androidshader

import com.ky.androidshader.data.repository.SolarSystemRepositoryImpl
import com.ky.androidshader.domain.repository.SolarSystemRepository
import com.ky.androidshader.domain.usecase.GetSolarSystemStateUseCase

/**
 * 组合根（手工 DI，不引入 Hilt/Dagger）。
 *
 * 表现层只依赖 domain；由本对象完成 data 层实现与 domain 接口的装配，
 * 对外仅暴露用例。
 */
object ServiceLocator {

    /** 太阳系状态仓库（纯时间驱动实现）。 */
    val solarSystemRepository: SolarSystemRepository by lazy {
        SolarSystemRepositoryImpl()
    }

    /** 获取太阳系状态的用例。 */
    val getSolarSystemState: GetSolarSystemStateUseCase by lazy {
        GetSolarSystemStateUseCase(solarSystemRepository)
    }
}
