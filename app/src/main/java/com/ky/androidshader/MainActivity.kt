package com.ky.androidshader

import android.opengl.GLSurfaceView
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ky.androidshader.presentation.SolarSystemViewModel
import com.ky.androidshader.presentation.render.SolarSystemRenderer
import kotlinx.coroutines.launch

/**
 * 表现层入口：承载 [GLSurfaceView]（OpenGL ES 2.0，连续渲染）。
 *
 * 装配关系（手工 DI）：
 *  - [ServiceLocator]（组合根）提供 domain 用例，注入 [SolarSystemViewModel]；
 *  - ViewModel 以 StateFlow 暴露最新太阳系状态，Activity 在 RESUMED 期间收集，
 *    每帧推送给 [SolarSystemRenderer]（GL 线程以 @Volatile 快照读取）；
 *  - onResume/onPause 正确接管 GLSurfaceView 渲染线程与 ViewModel 的帧节奏。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var renderer: SolarSystemRenderer
    private lateinit var viewModel: SolarSystemViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        renderer = SolarSystemRenderer()
        glSurfaceView = GLSurfaceView(this).apply {
            setEGLConfigChooser(8, 8, 8, 0, 16, 0) // 显式请求 16 位深度缓冲
            setEGLContextClientVersion(2)          // OpenGL ES 2.0
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
        setContentView(glSurfaceView)

        // Activity 作用域的 ViewModelProvider：配置变更（旋转屏幕）后 ViewModel 仍存活
        viewModel = ViewModelProvider(
            this,
            SolarSystemViewModel.factory(ServiceLocator.getSolarSystemState),
        )[SolarSystemViewModel::class.java]

        // RESUMED 期间收集最新状态并交给渲染器
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.uiState.collect { renderer.updateState(it) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onShown()
        glSurfaceView.onResume()
    }

    override fun onPause() {
        viewModel.onHidden()
        glSurfaceView.onPause()
        super.onPause()
    }
}
