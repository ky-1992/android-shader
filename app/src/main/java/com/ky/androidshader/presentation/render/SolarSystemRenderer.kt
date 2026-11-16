package com.ky.androidshader.presentation.render

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import com.ky.androidshader.domain.model.CelestialBody
import com.ky.androidshader.domain.model.SolarSystemState
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * 太阳系 GLES2 渲染器。
 *
 * - 几何：共享单位球网格（由 uCenter/uRadius/uPhase 在 GPU 端按天体摆放）+ 星空点云；
 * - 程序：太阳/地球/月亮各一个片段着色器（共享 [GlslSources.BODY_VERTEX]）+ 星空点精灵程序；
 * - 相机：透视投影，从轨道面前上方俯视（轨道面呈倾角）；
 * - 状态：UI 线程经 [updateState] 推入最新 [SolarSystemState]（@Volatile 快照），GL 线程每帧读取。
 */
internal class SolarSystemRenderer : GLSurfaceView.Renderer {

    @Volatile
    private var latestState: SolarSystemState = SolarSystemState(
        elapsedMillis = 0L,
        positions = emptyMap(),
        orbitAngles = emptyMap(),
        rotationAngles = emptyMap(),
    )

    /** UI 线程每帧调用：推入最新太阳系状态；GL 线程在 [onDrawFrame] 读取。 */
    fun updateState(state: SolarSystemState) {
        latestState = state
    }

    private lateinit var sphereMesh: SphereMesh
    private lateinit var starfieldMesh: StarfieldMesh
    private lateinit var sunProgram: ShaderProgram
    private lateinit var earthProgram: ShaderProgram
    private lateinit var moonProgram: ShaderProgram
    private lateinit var starProgram: ShaderProgram

    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val vpMatrix = FloatArray(16)

    /** 相机位置：轨道面前上方（俯角约 31°，轨道面呈倾角）。 */
    private val eye = floatArrayOf(0f, -EYE_DISTANCE, EYE_HEIGHT)

    @Volatile
    private var firstFrameLogged = false

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        sunProgram = ShaderProgram(GlslSources.BODY_VERTEX, GlslSources.SUN_FRAGMENT)
        earthProgram = ShaderProgram(GlslSources.BODY_VERTEX, GlslSources.EARTH_FRAGMENT)
        moonProgram = ShaderProgram(GlslSources.BODY_VERTEX, GlslSources.MOON_FRAGMENT)
        starProgram = ShaderProgram(GlslSources.STAR_VERTEX, GlslSources.STAR_FRAGMENT)
        sphereMesh = SphereMesh()
        starfieldMesh = StarfieldMesh()

        GLES20.glClearColor(BACKGROUND_R, BACKGROUND_G, BACKGROUND_B, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)

        Log.i(TAG, "GL surface created: version=${GLES20.glGetString(GLES20.GL_VERSION)}")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val aspect = width.toFloat() / height.toFloat().coerceAtLeast(1f)
        Matrix.perspectiveM(projectionMatrix, 0, FOV_DEGREES, aspect, NEAR_PLANE, FAR_PLANE)
        Matrix.setLookAtM(
            viewMatrix, 0,
            eye[0], eye[1], eye[2], // 眼位：轨道面前上方
            0f, 0f, 0f,              // 看向太阳（原点）
            0f, 0f, 1f,              // up = +z（轨道面法向）
        )
        Matrix.multiplyMM(vpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
        Log.i(TAG, "surface changed: ${width}x${height}, aspect=$aspect")
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val state = latestState
        val timeSeconds = state.elapsedMillis / 1000f

        drawStarfield(timeSeconds)
        drawCelestialBody(CelestialBody.SUN, sunProgram, SUN_COLOR, SUN_VISUAL_RADIUS, timeSeconds, state)
        drawCelestialBody(CelestialBody.EARTH, earthProgram, EARTH_COLOR, EARTH_VISUAL_RADIUS, timeSeconds, state)
        drawCelestialBody(CelestialBody.MOON, moonProgram, MOON_COLOR, MOON_VISUAL_RADIUS, timeSeconds, state)

        if (!firstFrameLogged) {
            firstFrameLogged = true
            Log.i(TAG, "first frame drawn (elapsedMillis=${state.elapsedMillis})")
        }
    }

    /** 绘制一颗天体：uCenter/uRadius/uPhase/uTime/uColor/uEye 全部经 uniform 传入。 */
    private fun drawCelestialBody(
        body: CelestialBody,
        program: ShaderProgram,
        color: FloatArray,
        visualRadius: Float,
        timeSeconds: Float,
        state: SolarSystemState,
    ) {
        val position = state.positions[body] ?: return
        val rotationPhase = state.rotationAngles[body] ?: 0f
        program.use()
        program.setMatrix4(UNIFORM_VP, vpMatrix)
        program.setFloat3(UNIFORM_CENTER, position.x, position.y, position.z)
        program.setFloat(UNIFORM_RADIUS, visualRadius)
        program.setFloat(UNIFORM_PHASE, rotationPhase)
        program.setFloat(UNIFORM_TIME, timeSeconds)
        program.setFloat3(UNIFORM_COLOR, color[0], color[1], color[2])
        program.setFloat3(UNIFORM_EYE, eye[0], eye[1], eye[2])
        sphereMesh.draw(program.getAttributeLocation(ATTRIBUTE_POSITION))
    }

    /** 星空：开启混合绘制点精灵，随后关闭（天体为不透明绘制）。 */
    private fun drawStarfield(timeSeconds: Float) {
        starProgram.use()
        starProgram.setMatrix4(UNIFORM_VP, vpMatrix)
        starProgram.setFloat(UNIFORM_TIME, timeSeconds)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        starfieldMesh.draw(
            positionHandle = starProgram.getAttributeLocation(ATTRIBUTE_POSITION),
            seedHandle = starProgram.getAttributeLocation(ATTRIBUTE_SEED),
        )
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    companion object {
        private const val TAG = "SolarSystemRenderer"

        // —— uniform / attribute 命名（与 GlslSources 保持一致）——
        private const val UNIFORM_VP = "uVP"
        private const val UNIFORM_CENTER = "uCenter"
        private const val UNIFORM_RADIUS = "uRadius"
        private const val UNIFORM_PHASE = "uPhase"
        private const val UNIFORM_TIME = "uTime"
        private const val UNIFORM_COLOR = "uColor"
        private const val UNIFORM_EYE = "uEye"
        private const val ATTRIBUTE_POSITION = "aPosition"
        private const val ATTRIBUTE_SEED = "aSeed"

        // —— 相机（透视 + 轨道面倾角）——
        private const val FOV_DEGREES = 50f
        private const val NEAR_PLANE = 0.1f
        private const val FAR_PLANE = 120f
        private const val EYE_DISTANCE = 9.0f
        private const val EYE_HEIGHT = 5.5f

        // —— 深空背景色 ——
        private const val BACKGROUND_R = 0.02f
        private const val BACKGROUND_G = 0.03f
        private const val BACKGROUND_B = 0.07f

        // —— 表现层自有的视觉常量（绘制比例与颜色；位置/相位来自 domain 状态）——
        private const val SUN_VISUAL_RADIUS = 1.0f
        private const val EARTH_VISUAL_RADIUS = 0.3f
        private const val MOON_VISUAL_RADIUS = 0.1f
        private val SUN_COLOR = floatArrayOf(1.0f, 0.58f, 0.14f)
        private val EARTH_COLOR = floatArrayOf(0.05f, 0.18f, 0.45f)
        private val MOON_COLOR = floatArrayOf(0.62f, 0.63f, 0.66f)
    }
}
