package com.ky.androidshader.presentation.render

import android.opengl.GLES20
import android.util.Log

/**
 * 极简 shader program 封装：编译/链接 + uniform 位置缓存。
 *
 * 编译或链接失败时抛出 [IllegalStateException]（携带 GL 日志与源码），
 * 便于在开发期立即暴露问题；GLSurfaceView 会将其记录到 logcat。
 */
internal class ShaderProgram(vertexSource: String, fragmentSource: String) {

    private val programId: Int
    private val uniformLocations = HashMap<String, Int>()

    init {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        programId = GLES20.glCreateProgram().also { program ->
            GLES20.glAttachShader(program, vertexShader)
            GLES20.glAttachShader(program, fragmentShader)
            GLES20.glLinkProgram(program)
        }
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(programId, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(programId)
            GLES20.glDeleteProgram(programId)
            throw IllegalStateException("ShaderProgram link failed: $log")
        }
        // 链接完成后即可删除 shader 对象
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
    }

    fun use() {
        GLES20.glUseProgram(programId)
    }

    fun getAttributeLocation(name: String): Int =
        GLES20.glGetAttribLocation(programId, name)

    fun setFloat(name: String, value: Float) {
        GLES20.glUniform1f(location(name), value)
    }

    fun setFloat3(name: String, x: Float, y: Float, z: Float) {
        GLES20.glUniform3f(location(name), x, y, z)
    }

    fun setMatrix4(name: String, value: FloatArray) {
        GLES20.glUniformMatrix4fv(location(name), 1, false, value, 0)
    }

    private fun location(name: String): Int =
        uniformLocations.getOrPut(name) { GLES20.glGetUniformLocation(programId, name) }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            val kind = if (type == GLES20.GL_VERTEX_SHADER) "vertex" else "fragment"
            Log.e(TAG, "$kind shader compile failed: $log")
            throw IllegalStateException("$kind shader compile failed: $log")
        }
        return shader
    }

    private companion object {
        const val TAG = "ShaderProgram"
    }
}
