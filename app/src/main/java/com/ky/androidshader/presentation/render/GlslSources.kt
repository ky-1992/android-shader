package com.ky.androidshader.presentation.render

/**
 * GLSL ES 1.00 着色器源码（OpenGL ES 2.0，vertex/fragment 成对）。
 *
 * - [BODY_VERTEX] 为三颗天体共享的顶点着色器（uCenter/uRadius/uPhase 在 GPU 端组装模型变换）；
 * - [SUN_FRAGMENT] / [EARTH_FRAGMENT] / [MOON_FRAGMENT] 分别实现各自的着色风格；
 * - [STAR_VERTEX] / [STAR_FRAGMENT] 为星空点精灵着色器。
 */
internal object GlslSources {

    // ------------------------------------------------------------------
    // 天体共享顶点着色器
    // ------------------------------------------------------------------

    /** 单位球局部坐标 aPosition 同时充当法线与程序化纹理坐标。 */
    val BODY_VERTEX = """
        uniform mat4 uVP;          // 视图投影矩阵
        uniform vec3 uCenter;      // 天体中心（世界坐标）
        uniform float uRadius;     // 天体绘制半径
        uniform float uPhase;      // 自转角（绕 z 轴，弧度）
        attribute vec3 aPosition;  // 单位球局部坐标

        varying vec3 vLocal;       // 局部坐标：供片段着色器做程序化纹理（随天体自转）
        varying vec3 vNormal;      // 世界空间法线
        varying vec3 vWorldPos;    // 世界空间位置

        void main() {
            float c = cos(uPhase);
            float s = sin(uPhase);
            vec3 p = aPosition * uRadius;
            vec3 rotated = vec3(c * p.x - s * p.y, s * p.x + c * p.y, p.z);
            vLocal = aPosition;
            vNormal = vec3(c * aPosition.x - s * aPosition.y,
                           s * aPosition.x + c * aPosition.y,
                           aPosition.z);
            vWorldPos = rotated + uCenter;
            gl_Position = uVP * vec4(vWorldPos, 1.0);
        }
    """.trimIndent()

    // ------------------------------------------------------------------
    // 太阳：脉动辉光 + fbm 噪声发光
    // ------------------------------------------------------------------

    val SUN_FRAGMENT = FRAGMENT_PRECISION + NOISE_HELPERS + """
        uniform float uTime;
        uniform vec3 uColor;
        uniform vec3 uEye;

        varying vec3 vLocal;
        varying vec3 vNormal;
        varying vec3 vWorldPos;

        void main() {
            // 随时间流动的表面“米粒组织”（对流噪纹）
            float granules = fbm(vLocal * 5.0 + vec3(0.0, 0.0, uTime * 0.18));
            // 整体呼吸式脉动
            float pulse = 0.5 + 0.5 * sin(uTime * 2.2 + granules * 6.2831853);
            // 冷热斑块
            vec3 hot  = uColor * 1.6 + vec3(0.25, 0.15, 0.0);
            vec3 cool = uColor * 0.7;
            vec3 surface = mix(cool, hot, granules);
            // 临边辉光
            vec3 V = normalize(uEye - vWorldPos);
            float rim = pow(1.0 - clamp(dot(normalize(vNormal), V), 0.0, 1.0), 2.5);
            vec3 color = surface * (1.05 + 0.30 * pulse) + uColor * rim * 1.1;
            gl_FragColor = vec4(color, 1.0);
        }
    """.trimIndent()

    // ------------------------------------------------------------------
    // 地球：程序化海陆/极冰 + 昼夜明暗 + 大气边缘
    // ------------------------------------------------------------------

    val EARTH_FRAGMENT = FRAGMENT_PRECISION + NOISE_HELPERS + """
        uniform float uTime;
        uniform vec3 uColor;       // 海洋色
        uniform vec3 uEye;

        varying vec3 vLocal;
        varying vec3 vNormal;
        varying vec3 vWorldPos;

        void main() {
            vec3 N = normalize(vNormal);
            vec3 L = normalize(-vWorldPos);   // 太阳位于世界原点

            // 程序化地表：海洋(uColor) / 陆地 / 极地冰盖
            float continents = fbm(vLocal * 2.6 + vec3(5.2, 1.3, 7.9));
            float landMask = smoothstep(0.48, 0.55, continents);
            vec3 land = mix(vec3(0.16, 0.30, 0.10), vec3(0.45, 0.36, 0.22),
                            smoothstep(0.55, 0.75, continents));
            vec3 surface = mix(uColor, land, landMask);
            float polar = smoothstep(0.78, 0.88, abs(vLocal.z));
            surface = mix(surface, vec3(0.88, 0.92, 0.95), polar);

            // 昼夜明暗（柔和晨昏线）
            float ndl = dot(N, L);
            float day = smoothstep(-0.12, 0.22, ndl);
            vec3 dayLit = surface * (0.18 + 1.05 * clamp(ndl, 0.0, 1.0));
            vec3 night = surface * 0.02 + vec3(0.008, 0.015, 0.04);
            vec3 color = mix(night, dayLit, day);

            // 大气边缘散射（昼侧更亮的蓝色 rim）
            vec3 V = normalize(uEye - vWorldPos);
            float rim = pow(1.0 - clamp(dot(N, V), 0.0, 1.0), 3.0);
            color += vec3(0.22, 0.42, 0.85) * rim * (0.25 + 0.75 * day);
            gl_FragColor = vec4(color, 1.0);
        }
    """.trimIndent()

    // ------------------------------------------------------------------
    // 月亮：fbm 环形山/月海 + 无大气的硬明暗
    // ------------------------------------------------------------------

    val MOON_FRAGMENT = FRAGMENT_PRECISION + NOISE_HELPERS + """
        uniform float uTime;
        uniform vec3 uColor;
        uniform vec3 uEye;

        varying vec3 vLocal;
        varying vec3 vNormal;
        varying vec3 vWorldPos;

        void main() {
            vec3 N = normalize(vNormal);
            vec3 L = normalize(-vWorldPos);   // 太阳位于世界原点

            float craters = fbm(vLocal * 6.5 + vec3(9.1, 3.7, 1.4));
            float maria = smoothstep(0.45, 0.62, fbm(vLocal * 1.8 + vec3(2.4, 8.8, 6.1)));
            vec3 surface = uColor * (0.55 + 0.45 * craters) * (1.0 - 0.25 * maria);

            float ndl = clamp(dot(N, L), 0.0, 1.0);
            vec3 color = surface * (0.05 + 1.1 * ndl);
            gl_FragColor = vec4(color, 1.0);
        }
    """.trimIndent()

    // ------------------------------------------------------------------
    // 星空：点精灵 + 闪烁
    // ------------------------------------------------------------------

    val STAR_VERTEX = """
        uniform mat4 uVP;
        uniform float uTime;
        attribute vec3 aPosition;  // 星壳上的位置
        attribute float aSeed;     // 每颗星的随机种子

        varying float vBrightness;

        void main() {
            gl_Position = uVP * vec4(aPosition, 1.0);
            gl_PointSize = 1.5 + 2.5 * fract(aSeed * 7.31);
            float speed = 0.4 + 1.3 * fract(aSeed * 5.17);
            vBrightness = 0.55 + 0.45 * sin(uTime * speed + aSeed * 6.2831853);
        }
    """.trimIndent()

    val STAR_FRAGMENT = """
        precision mediump float;

        varying float vBrightness;

        void main() {
            float r = length(gl_PointCoord - vec2(0.5));
            float alpha = (1.0 - smoothstep(0.05, 0.5, r)) * vBrightness;
            vec3 tint = mix(vec3(0.72, 0.82, 1.0), vec3(1.0, 0.94, 0.82),
                            fract(vBrightness * 4.7));
            gl_FragColor = vec4(tint * vBrightness, alpha);
        }
    """.trimIndent()

    // ------------------------------------------------------------------
    // 复用片段
    // ------------------------------------------------------------------

    /** 片段着色器精度声明：优先 highp（噪声需要），不支持时退回 mediump。 */
    private const val FRAGMENT_PRECISION = """
        #ifdef GL_FRAGMENT_PRECISION_HIGH
        precision highp float;
        #else
        precision mediump float;
        #endif

    """

    /** 值噪声/fbm 工具函数（hash13 基于 fract，避免 sin 大数精度问题）。 */
    private const val NOISE_HELPERS = """
        float hash13(vec3 p) {
            p = fract(p * 0.3183099 + vec3(0.71, 0.113, 0.419));
            p *= 17.0;
            return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
        }

        float vnoise(vec3 x) {
            vec3 i = floor(x);
            vec3 f = fract(x);
            f = f * f * (3.0 - 2.0 * f);
            return mix(
                mix(mix(hash13(i + vec3(0.0, 0.0, 0.0)), hash13(i + vec3(1.0, 0.0, 0.0)), f.x),
                    mix(hash13(i + vec3(0.0, 1.0, 0.0)), hash13(i + vec3(1.0, 1.0, 0.0)), f.x),
                    f.y),
                mix(mix(hash13(i + vec3(0.0, 0.0, 1.0)), hash13(i + vec3(1.0, 0.0, 1.0)), f.x),
                    mix(hash13(i + vec3(0.0, 1.0, 1.0)), hash13(i + vec3(1.0, 1.0, 1.0)), f.x),
                    f.y),
                f.z);
        }

        float fbm(vec3 p) {
            float v = 0.0;
            float a = 0.5;
            for (int i = 0; i < 4; i++) {
                v += a * vnoise(p);
                p = p * 2.03 + vec3(11.5, 7.7, 4.9);
                a *= 0.5;
            }
            return v;
        }

    """
}
