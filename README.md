# android-shader

用 OpenGL ES 着色器动画呈现**太阳 / 地球 / 月亮轨道运动**的 Android 应用。

- **渲染**：OpenGL ES 2.0（GLSL ES 1.00）。太阳 fbm 米粒组织 + 呼吸脉动 + 临边辉光；地球程序化海陆 / 极地冰盖 + 昼夜晨昏线 + 蓝色大气 rim；月亮 fbm 环形山 / 月海 + 潮汐锁定明暗；650 颗星点精灵闪烁。
- **架构**：MVVM + Clean Architecture（domain / data / presentation 三层 + 手工 DI 组合根，不引入 Hilt/Dagger）。
- **确定性模拟**：轨道运动是纯时间函数——不读时钟、无内部状态，同一输入必得同一输出；20 个单元测试覆盖周期性、潮汐锁定、确定性等性质。

已在 Android 16 真机完成安装、启动与像素级动画验证（构建、单测、GL 日志、三体运动轨迹复核全部通过）。

## 环境要求

- JDK 17
- Android SDK：platforms;android-34、build-tools;34.0.0
- Gradle 8.5（由 Gradle Wrapper 自动获取，无需手工安装）

## 构建与运行

```bash
# Windows
gradlew.bat assembleDebug

# macOS / Linux
./gradlew assembleDebug
```

APK 产物：`app/build/outputs/apk/debug/app-debug.apk`

运行单元测试（20 用例）：

```bash
# Windows
gradlew.bat test

# macOS / Linux
./gradlew test
```

安装到已连接设备并启动：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.ky.androidshader/.MainActivity
```

应用无任何界面控件：启动即全屏渲染太阳系，太阳居中、地球绕日、月亮绕地，配以闪烁星空。

## 架构：MVVM + Clean Architecture

依赖规则只指向内层（presentation → domain ← data），domain 层为纯 Kotlin、零 Android import：

```
┌────────────────────────────────────────────────────────────────┐
│ presentation（表现层，MVVM）                                     │
│                                                                │
│   MainActivity ──repeatOnLifecycle(RESUMED)──collect──┐        │
│        │ GLSurfaceView(ES2)                            │        │
│        ▼                                               ▼        │
│   SolarSystemRenderer ◀──updateState(快照)── SolarSystemViewModel│
│    ├─ SphereMesh / StarfieldMesh（几何）               │ StateFlow│
│    └─ ShaderProgram + GlslSources（GLSL ES 1.00）      │ ~16ms tick│
└───────────────────────────┬────────────────────────────┴────────┘
                            │ 仅依赖 domain
┌───────────────────────────▼─────────────────────────────────────┐
│ domain（纯 Kotlin，零 Android import）                          │
│   model: CelestialBody / OrbitParams / CelestialPosition /     │
│          SolarSystemState                                      │
│   repository: SolarSystemRepository（接口）                     │
│   usecase: GetSolarSystemStateUseCase                          │
└───────────────────────────▲─────────────────────────────────────┘
                            │ 实现 domain 接口（依赖倒置）
┌───────────────────────────┴─────────────────────────────────────┐
│ data（数据层）                                                  │
│   CelestialConfig（唯一参数源：轨道半径/周期/相位/自转/大小）   │
│   SolarSystemRepositoryImpl（纯时间驱动、确定性求值）            │
└─────────────────────────────────────────────────────────────────┘

组合根：ServiceLocator（手工 DI）完成 data 实现 → domain 用例 →
ViewModel 的装配；表现层不出现任何 data 层类型。
```

每帧数据流：

1. `SolarSystemViewModel` 在可见期间以 ~16ms 协程 tick 计算 `elapsedMillis`（`SystemClock.elapsedRealtime()` − ViewModel 创建时刻）；
2. 调用 `GetSolarSystemStateUseCase(elapsedMillis)` → `SolarSystemRepositoryImpl.computeState()` 纯函数求值，得到三体世界坐标、公转角、自转角；
3. 结果经 `StateFlow<SolarSystemState>` 暴露，Activity 在 `RESUMED` 期间收集并 `renderer.updateState()` 推入渲染器（`@Volatile` 快照，UI 线程写 / GL 线程读）；
4. GL 线程 `onDrawFrame` 读取最新快照：位置 → `uCenter`、自转角 → `uPhase`、时间 → `uTime`，连同相机矩阵一起作为 uniform 传入后绘制。

生命周期要点：

- ViewModel 挂在 Activity 作用域，**旋转屏幕后动画时间轴不重置**；
- `onResume` / `onPause` 成对接管 `GLSurfaceView` 渲染线程与 ViewModel 帧节奏；不可见时 ViewModel 降频轮询、GL 线程暂停。

## Shader 动画原理

### 天体运动：uTime 驱动的确定性时间函数

模拟是**纯时间驱动**的：`computeState(elapsedMillis)` 不读时钟、不持可变状态，任意时刻的天体状态只由「自模拟起点流逝的毫秒数」决定：

```
地球公转角  θe = 0    + 2π·t/60s    （轨道半径 3.0，绕太阳）
月亮公转角  θm = π/3  + 2π·t/8s     （轨道半径 0.8，相对地球）
月亮位置    = 地球位置 + 绕地轨道偏移  （层级轨道）
自转角      = 初相位 + 2π·t/自转周期  （太阳 20s / 地球 4s / 月亮 8s）
月亮潮汐锁定：自转周期 = 公转周期（同一面恒朝地球）
全系统重复周期 = lcm(60, 8, 20, 4) = 120 s
```

角度不做 2π 回绕（随时间单调递增，浮点精度友好），需要 `[0, 2π)` 时由调用方取模。所有参数集中在 `CelestialConfig`（唯一事实来源），渲染层只消费 domain 算好的状态——**几何运动在 CPU（domain）确定，表面动画在 GPU（shader）由 uTime 展开**。

三颗天体共享同一单位球网格（20×36 经纬细分）与同一顶点着色器：`uCenter` / `uRadius` / `uPhase` 在 GPU 端组装模型变换；单位球局部坐标 `aPosition` 同时充当法线与程序化纹理坐标，并随天体自转。三体的视觉差异完全落在各自的片段着色器里。

uniform 一览：

| uniform | 含义 |
| --- | --- |
| `uVP` | 视图投影矩阵（透视相机，FOV 50°，从轨道面前上方俯视，俯角约 31°，轨道面呈倾角） |
| `uTime` | 距模拟起点的秒数，驱动所有表面动画（噪声流动 / 脉动 / 闪烁） |
| `uCenter` | 天体中心（世界坐标，来自 domain 计算） |
| `uRadius` | 天体绘制半径（太阳 1.0 / 地球 0.3 / 月亮 0.1） |
| `uPhase` | 天体自转角（绕 z 轴，弧度，未回绕） |
| `uColor` | 天体基色（太阳橙 / 地球海洋蓝 / 月亮灰） |
| `uEye` | 相机世界坐标（rim / 辉光的方向参考） |

世界坐标系约定：太阳在原点，黄道面为 z = 0，+z 为轨道面法向；地球 / 月亮的昼夜光照方向 `L = normalize(-vWorldPos)`（光源 = 太阳 = 原点）。

### 太阳：fbm 米粒组织 + 呼吸脉动 + 临边辉光

- `fbm(vLocal * 5.0 + z 向随 uTime * 0.18 流动)` 模拟对流**米粒组织**，热斑（基色 × 1.6 + 暖偏移）/ 冷斑（基色 × 0.7）双色混合；
- `pulse = 0.5 + 0.5·sin(uTime * 2.2 + granules * 2π)`——亮度以噪声耦合相位做整体**呼吸脉动**；
- `rim = pow(1 − N·V, 2.5)` **临边辉光**：视线掠过球缘处法线与视线夹角增大，叠加基色形成发光边缘。

### 地球：程序化海陆 + 昼夜晨昏线 + 大气 rim

- `fbm(vLocal * 2.6 + 常数偏移)` 生成大陆 mask（smoothstep 阈值 0.48–0.55），绿→棕双色陆地与 `uColor` 海洋混合；`|vLocal.z|` 高纬度 smoothstep 混合出**极地冰盖**；
- 昼夜明暗：`day = smoothstep(-0.12, 0.22, N·L)` 得到柔和**晨昏线**，昼侧按 `N·L` 漫反射着色，夜侧压暗至 2% 并带微弱深蓝环境光；
- `rim = pow(1 − N·V, 3.0)` 叠加**蓝色大气边缘散射**，昼侧 rim 更亮（`0.25 + 0.75·day` 调制）。

### 月亮：fbm 环形山 / 月海 + 潮汐锁定

- `fbm(vLocal * 6.5)` 高频噪声起伏做**环形山**明暗；`fbm(vLocal * 1.8)` 低频 mask 压暗 25% 做**月海**；
- 无大气 → 明暗过渡是硬的（直接 `clamp(N·L)`，无晨昏线柔化）；
- domain 保证月亮自转周期 = 公转周期（8s），**潮汐锁定**：同一半球始终朝向地球。

### 星空：650 颗点精灵闪烁

- 固定种子（2024）在大半径（42）球壳上均匀随机分布 650 颗星，`GL_POINTS` 绘制；
- 顶点着色器按每颗星的随机 `aSeed` 决定 `gl_PointSize`（1.5–4.0）与闪烁速率/相位（`sin(uTime * speed + seed * 2π)`）；
- 片段着色器以 `gl_PointCoord` 做径向衰减圆点，冷（蓝白）/ 暖（黄白）色调按亮度混合，开启 alpha 混合绘制，天体仍为不透明绘制。

### 噪声基础设施

所有片段着色器共享同一组 GLSL 工具：`hash13`（基于 `fract` 的散列，避免大数 `sin` 精度问题）→ `vnoise`（三线性插值值噪声）→ `fbm`（4 倍频，幅度减半）。片段着色器优先 `highp` 精度（噪声需要），不支持的设备自动退回 `mediump`。

## 依赖选型说明

依赖集中在 [gradle/libs.versions.toml](gradle/libs.versions.toml)（Version Catalog），当前仅含最小集：core-ktx、appcompat、lifecycle（viewmodel / runtime）、activity-ktx、kotlinx-coroutines-android、junit。不引入 Hilt/Dagger——组合根是一个手工装配的 `ServiceLocator`。

> **lifecycle 锁定 2.7.0**：2.8.x 起的 KMP 重定向工件（`lifecycle-viewmodel` → `lifecycle-viewmodel-android` 等）在 Gradle 8.5 / AGP 8.3 工具链下会被解析成空壳 AAR（ktx 符号缺失，编译失败）；2.7.0 是经典 AAR 结构，一切正常。升级 Gradle（8.13+ / 9.x）后可回到 2.8.x。
>
> 本工程亦未引入 `lifecycle-runtime-compose`（保持最小依赖集），因此 ViewModel 采用 `viewModelScope` 协程 ~16ms tick 驱动帧节奏，而非 `withFrameNanos` 帧回调。

## 版本矩阵

| 组件 | 版本 |
| --- | --- |
| Android Gradle Plugin | 8.3.0 |
| Kotlin | 2.0.21 |
| Gradle | 8.5 (wrapper) |
| compileSdk / targetSdk | 34 |
| minSdk | 24 |
| JDK | 17 |

## 工程结构

```
android-shader/
├── app/                                # 单模块 :app
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/ky/androidshader/
│       │   │   ├── MainActivity.kt           # 承载 GLSurfaceView（GLES2）
│       │   │   ├── ServiceLocator.kt         # 组合根（手工 DI）
│       │   │   ├── domain/                   # 纯 Kotlin：model / repository / usecase
│       │   │   │   ├── model/                # CelestialBody / OrbitParams /
│       │   │   │   │                         # CelestialPosition / SolarSystemState
│       │   │   │   ├── repository/           # SolarSystemRepository 接口
│       │   │   │   └── usecase/              # GetSolarSystemStateUseCase
│       │   │   ├── data/repository/          # CelestialConfig + SolarSystemRepositoryImpl
│       │   │   └── presentation/             # MVVM 表现层
│       │   │       ├── SolarSystemViewModel.kt
│       │   │       └── render/               # SolarSystemRenderer / GlslSources /
│       │   │                                 # ShaderProgram / SphereMesh / StarfieldMesh
│       │   └── res/                          # values / 图标
│       └── test/java/...                     # 单元测试（20 用例）
├── gradle/
│   ├── libs.versions.toml              # 版本目录
│   └── wrapper/                        # gradle-wrapper.jar/properties
├── build.gradle.kts                    # 根构建脚本（插件版本）
├── settings.gradle.kts
├── gradle.properties
├── gradlew / gradlew.bat
└── local.properties                    # sdk.dir（不入库）
```

## 测试

20 个单元测试（`app/src/test`，纯 JVM 可跑）覆盖：

- t=0 初值（位置 / 角度 / 键完整性）；
- 周期性：地球 60s、月亮 8s（相对地球坐标）、整系统 120s 复位；
- 任意时刻月亮相对地球的位置 = 轨道圆求值；
- 公转角与三体自转角随时间单调递增；
- 潮汐锁定：月亮自转角 ≡ 公转角；
- 负时间钳制为 0；同一输入重复求值结果一致（确定性）。
