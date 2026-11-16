package com.ky.androidshader.presentation

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ky.androidshader.domain.model.SolarSystemState
import com.ky.androidshader.domain.usecase.GetSolarSystemStateUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 太阳系表现层 ViewModel：按显示帧节奏驱动 [GetSolarSystemStateUseCase]，
 * 通过 [uiState] 向渲染层暴露最新 [SolarSystemState]。
 *
 * - 时间起点 = 本 ViewModel 创建时刻；ViewModel 挂在 Activity 作用域上，
 *   配置变更（旋转屏幕）后仍存活，动画时间轴不重置；
 * - 可见时以 ~16ms 的协程 tick 逐帧刷新（本工程锁定的 lifecycle 2.7.0 已移除
 *   withFrameNanos），不可见时降频轮询；
 * - 不持有任何 GL 类：绘制由 GL 线程上的 Renderer 完成。
 *   本层只依赖 domain 的 use case 与 model（Clean Architecture 依赖规则）。
 */
class SolarSystemViewModel(
    private val getSolarSystemState: GetSolarSystemStateUseCase,
    private val elapsedRealtimeMillis: () -> Long = { SystemClock.elapsedRealtime() },
) : ViewModel() {

    private val startElapsedRealtimeMillis: Long = elapsedRealtimeMillis()

    private val _uiState = MutableStateFlow(getSolarSystemState(0L))

    /** 最新太阳系状态（初始为 t=0 的快照）。 */
    val uiState: StateFlow<SolarSystemState> = _uiState.asStateFlow()

    /** Activity 前台状态：控制逐帧节奏是否激活。 */
    @Volatile
    private var frameTickerActive: Boolean = false

    init {
        viewModelScope.launch {
            while (isActive) {
                if (frameTickerActive) {
                    _uiState.value = getSolarSystemState(currentElapsedMillis())
                    delay(FRAME_TICK_MS)
                } else {
                    delay(HIDDEN_POLL_INTERVAL_MS)
                }
            }
        }
    }

    /** Activity onResume 时调用：激活逐帧刷新。 */
    fun onShown() {
        frameTickerActive = true
    }

    /** Activity onPause 时调用：暂停逐帧刷新（渲染线程由 GLSurfaceView.onPause 接管）。 */
    fun onHidden() {
        frameTickerActive = false
    }

    private fun currentElapsedMillis(): Long =
        (elapsedRealtimeMillis() - startElapsedRealtimeMillis).coerceAtLeast(0L)

    companion object {

        /** 可见时的目标刷新周期（~60fps）。 */
        private const val FRAME_TICK_MS = 16L

        private const val HIDDEN_POLL_INTERVAL_MS = 250L

        /** 手工 DI 工厂：MainActivity 用它构造本 ViewModel。 */
        fun factory(useCase: GetSolarSystemStateUseCase): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { SolarSystemViewModel(useCase) }
            }
    }
}
