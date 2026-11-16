package com.ky.androidshader.domain.usecase

import com.ky.androidshader.domain.model.CelestialBody
import com.ky.androidshader.domain.model.CelestialPosition
import com.ky.androidshader.domain.model.SolarSystemState
import com.ky.androidshader.domain.repository.SolarSystemRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class GetSolarSystemStateUseCaseTest {

    /** 固定返回值的假仓库，用于验证用例的委托行为。 */
    private class FakeRepository : SolarSystemRepository {

        var lastRequestedMillis: Long = -1L

        override fun computeState(elapsedMillis: Long): SolarSystemState {
            lastRequestedMillis = elapsedMillis
            return SolarSystemState(
                elapsedMillis = elapsedMillis,
                positions = mapOf(CelestialBody.SUN to CelestialPosition(0f, 0f, 0f)),
                orbitAngles = emptyMap(),
                rotationAngles = emptyMap(),
            )
        }
    }

    @Test
    fun `invoke 把时间参数原样传给仓库并返回其结果`() {
        val fake = FakeRepository()
        val useCase = GetSolarSystemStateUseCase(fake)

        val state = useCase(12_345L)

        assertEquals(12_345L, fake.lastRequestedMillis)
        assertEquals(12_345L, state.elapsedMillis)
        assertEquals(CelestialPosition(0f, 0f, 0f), state.positions.getValue(CelestialBody.SUN))
    }
}
