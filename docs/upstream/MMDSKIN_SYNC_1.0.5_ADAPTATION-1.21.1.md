# MMDSkinSync 对 MC-MMD-rust 1.0.5 / Minecraft 1.21.1 适配变更报告

> 报告范围：当前工作区中 `MC-MMD-rust`、`MMDSkinSync` 与 `mmdsync-bridge` 三个仓库的既有适配工作。本文仅记录已经完成并核验的变更、构建和产物，不代表工作树已提交。

## 1. 背景与目标

MMDSkinSync 原有实现依赖旧版 MmdSkin 的类布局、直接 `NativeFunc` 调用点与 JNI 行为。MC-MMD-rust 1.0.5 在 Minecraft 1.21.1 上重组了模型加载、渲染、舞台、UI 和运行时端口，旧 Mixin 目标及直接 JNI 分流因此不能继续原样使用。

本次适配目标如下：

1. 将 MMDSkinSync 的 Java、Gradle、资源声明和 Mixin 目标迁移到 MC-MMD-rust 1.0.5。
2. 同时支持 Minecraft 1.21.1 的 Fabric 与 NeoForge。
3. 以中心化运行时端口优先、必要 Mixin 补充的方式隔离“上游原生句柄”和“MMDSync bridge 句柄”。
4. 使加密 PMX、VMD、VPD 和纹理继续在 native 侧完成会话校验、解密、加载与生命周期管理。
5. 同步独立 bridge 内嵌的 Rust 引擎源码与 1.0.5 API，并补齐 Java 所需 JNI 能力。
6. 生成 Windows x86-64、Linux x86-64 原生库以及 Fabric、NeoForge 可交付 JAR，并完成哈希核验。

## 2. 仓库和版本基线

| 仓库 | 当前分支 | 核验提交 / 基线 | 用途 |
|---|---:|---|---|
| [`MC-MMD-rust/`](MC-MMD-rust/) | `1.21.1` | `48c31b6acf3ece211e7bdbe0482ceb1be1a41205` | 上游 MC-MMD-rust 基线；目标版本 `1.0.5-1.21.1-2` |
| [`MMDSkinSync/`](MMDSkinSync/) | `1.21.1` | 当前 HEAD `613f2cb81c1741920ae2b779fe4afa80f88d24f6` 加未提交适配改动 | Java 模组、平台模块、Mixin、内置 native 与最终 JAR |
| [`mmdsync-bridge/`](mmdsync-bridge/) | `main` | 当前 HEAD `df2d8b41b09ff244c05020e47aa4c748ce3bf50f` 加未提交适配改动 | 独立 Rust JNI bridge 及其私有引擎源码镜像 |

核心版本参数见 [`MMDSkinSync/gradle.properties`](MMDSkinSync/gradle.properties)：

- Minecraft：`1.21.1`
- MMDSync：`1.1.0`
- MC-MMD-rust / MmdSkin 依赖：`1.0.5-1.21.1-2`
- Java：`21`
- Architectury：`13.0.6`
- Fabric Loader：`0.16.10`
- Fabric API：`0.106.0+1.21.1`
- NeoForge：`21.1.219`
- Parchment：`1.21.1:2024.11.17`

适配使用的上游 common JAR 位于 [`MMDSkinSync/libs/mmdskin-common-1.0.5-1.21.1-2.jar`](MMDSkinSync/libs/mmdskin-common-1.0.5-1.21.1-2.jar)，大小 `931,910` 字节，SHA-256 为 `0e23791e1c989ed3f22c7919dc1dca5bf34d14bde7dcfdb331b5065ce51c3576`。

## 3. Java、Gradle 与 Mixin 适配

### 3.1 Java 与 Gradle

- 根构建脚本 [`MMDSkinSync/build.gradle`](MMDSkinSync/build.gradle) 将 Java 编译版本固定为 21，并使用 Architectury/Loom 构建 Fabric 与 NeoForge 双平台产物。
- [`MMDSkinSync/common/build.gradle`](MMDSkinSync/common/build.gradle) 将编译期依赖切换至 `mmdskin-common-1.0.5-1.21.1-2.jar`，同时从 MMDSync JAR 排除 `com/shiroha/mmdskin/**`，避免把上游实现重复打入产物。
- [`MMDSkinSync/settings.gradle`](MMDSkinSync/settings.gradle) 保留按请求任务裁剪平台模块的逻辑：common/Fabric 单独任务不必初始化 NeoForge，完整构建仍包含两端。
- [`MC-MMD-rust/settings.gradle`](MC-MMD-rust/settings.gradle) 同样支持 common-only 任务裁剪，并配置 Fabric、Architectury、Forge/NeoForge 及 Maven 镜像仓库。
- Fabric 依赖下限在 [`MMDSkinSync/fabric/src/main/resources/fabric.mod.json`](MMDSkinSync/fabric/src/main/resources/fabric.mod.json) 中声明为 Minecraft 1.21.1、MmdSkin 1.0.5；NeoForge 对应依赖在 [`MMDSkinSync/neoforge/src/main/resources/META-INF/neoforge.mods.toml`](MMDSkinSync/neoforge/src/main/resources/META-INF/neoforge.mods.toml) 中声明。

### 3.2 Mixin 目标迁移

1.0.5 将大量职责拆入新的运行时包。适配已把旧目标迁移到实际存在的模型加载、GPU/OpenGL 渲染、纹理、舞台、相机、第一人称、材质与 UI 类。公共 Mixin 清单集中在 [`MMDSkinSync/common/src/main/resources/mmdsync-common.mixins.json`](MMDSkinSync/common/src/main/resources/mmdsync-common.mixins.json)。

关键迁移包括：

- 模型加载：[`MMDSkinSync/common/src/main/java/com/tendoarisu/mmdskin/sync/mixin/MixinMMDModelManager.java`](MMDSkinSync/common/src/main/java/com/tendoarisu/mmdskin/sync/mixin/MixinMMDModelManager.java) 适配 `ModelLoadCoordinator` 与运行时 `MMDModelManager`。
- GPU 路径：[`MMDSkinSync/common/src/main/java/com/tendoarisu/mmdskin/sync/mixin/MixinMMDModelGpuSkinning.java`](MMDSkinSync/common/src/main/java/com/tendoarisu/mmdskin/sync/mixin/MixinMMDModelGpuSkinning.java)、[`MixinMMDModelGpuSkinningUploader.java`](MMDSkinSync/common/src/main/java/com/tendoarisu/mmdskin/sync/mixin/MixinMMDModelGpuSkinningUploader.java) 和 [`MixinMMDModelGpuSkinningRenderer.java`](MMDSkinSync/common/src/main/java/com/tendoarisu/mmdskin/sync/mixin/MixinMMDModelGpuSkinningRenderer.java) 覆盖加载、缓冲上传与渲染查询。
- OpenGL 路径：[`MixinMMDModelOpenGLFactory.java`](MMDSkinSync/common/src/main/java/com/tendoarisu/mmdskin/sync/mixin/MixinMMDModelOpenGLFactory.java)、[`MixinMMDModelOpenGL.java`](MMDSkinSync/common/src/main/java/com/tendoarisu/mmdskin/sync/mixin/MixinMMDModelOpenGL.java) 和 [`MixinMMDModelOpenGLRenderer.java`](MMDSkinSync/common/src/main/java/com/tendoarisu/mmdskin/sync/mixin/MixinMMDModelOpenGLRenderer.java) 保持加密模型加载、更新和渲染数据分流。
- 动画与舞台：[`MixinMMDAnimManager.java`](MMDSkinSync/common/src/main/java/com/tendoarisu/mmdskin/sync/mixin/MixinMMDAnimManager.java)、[`MixinStageRuntimeAnimationLoad.java`](MMDSkinSync/common/src/main/java/com/tendoarisu/mmdskin/sync/mixin/MixinStageRuntimeAnimationLoad.java)、[`MixinStageRuntimeAnimationOps.java`](MMDSkinSync/common/src/main/java/com/tendoarisu/mmdskin/sync/mixin/MixinStageRuntimeAnimationOps.java) 和 [`MixinStagePack.java`](MMDSkinSync/common/src/main/java/com/tendoarisu/mmdskin/sync/mixin/MixinStagePack.java) 适配运行时动画加载、合并、扫描和释放。
- 状态与 UI：[`MixinLivingEntityModelStateHelper.java`](MMDSkinSync/common/src/main/java/com/tendoarisu/mmdskin/sync/mixin/MixinLivingEntityModelStateHelper.java)、[`MixinModelLifecycleMetrics.java`](MMDSkinSync/common/src/main/java/com/tendoarisu/mmdskin/sync/mixin/MixinModelLifecycleMetrics.java)、[`MixinPerformanceHud.java`](MMDSkinSync/common/src/main/java/com/tendoarisu/mmdskin/sync/mixin/MixinPerformanceHud.java) 以及材质、选模、舞台选择相关 Mixin 适配新 API。

平台 UI Mixin 已分别拆到 Fabric 和 NeoForge，清单为 [`MMDSkinSync/fabric/src/main/resources/mmdsync-fabric.mixins.json`](MMDSkinSync/fabric/src/main/resources/mmdsync-fabric.mixins.json) 与 [`MMDSkinSync/neoforge/src/main/resources/mmdsync-neoforge.mixins.json`](MMDSkinSync/neoforge/src/main/resources/mmdsync-neoforge.mixins.json)，避免平台类在另一加载器上解析。

公共 Mixin 配置的 `defaultRequire=0` 用于兼容 1.0.5 内部可选调用点；平台选择界面 Mixin 保持 `defaultRequire=1`，让必需注入点失配时尽早失败而不是静默降级。

## 4. 中心句柄路由设计

### 4.1 句柄域隔离

Java bridge 在 [`MMDSyncNativeBridge.java`](MMDSkinSync/common/src/main/java/com/tendoarisu/mmdskin/sync/util/MMDSyncNativeBridge.java) 中使用第 62 位掩码 `1L << 62` 标记 bridge 句柄。模型、动画、纹理及需要返回给 Java 的原生数据指针由 `tagBridgeHandle` 标记，进入 bridge 前由 `stripBridgeHandle` 还原。

路由原则：

- 普通句柄：始终委托 MC-MMD-rust 1.0.5 上游端口或 `NativeFunc`。
- 有效 bridge 句柄：只进入 `MMDSyncNativeBridge`。
- 过期 bridge 句柄：返回安全默认值或拒绝操作，绝不回落到上游原生引擎。
- 混合模型/动画句柄：明确阻止，避免把一个 native 实例的句柄传给另一个 native 实例。

这条隔离规则防止 bridge 的私有 `mmd_engine` 注册表句柄被错误送入随 MC-MMD-rust 加载的 `mmd_engine`，从根本上规避跨库解引用、重复释放及悬空句柄。

### 4.2 中心端口安装

[`MMDSyncRuntimePorts.java`](MMDSkinSync/common/src/main/java/com/tendoarisu/mmdskin/sync/util/MMDSyncRuntimePorts.java) 在客户端初始化时由 [`MMDSyncModClient.java`](MMDSkinSync/common/src/main/java/com/opdent/mmdskin/sync/MMDSyncModClient.java) 安装，并包装 1.0.5 的以下中心协作者：

- `NativeModelPort`
- `NativeModelQueryPort`
- `NativeMorphPort`
- `ModelRuntimeBridge`

包装端口被配置给模型 API、材质/骨骼/统计查询、表情应用、VR 骨骼驱动及运行时 bridge。覆盖能力包括骨骼层 mask/exclude、模型内存统计、第一人称、眼骨位置、VR tracking、材质可见性、模型删除、材质/骨骼/顶点/索引查询、Morph 查询与写入、VPD 应用等。

无法由 1.0.5 中心端口完整表达的调用点继续使用小范围 Mixin 重定向，例如模型文件加载、纹理解码、动画创建/合并、GPU/OpenGL 缓冲和部分实体姿态同步。由此避免恢复旧版“大面积直接拦截全部 `NativeFunc`”的结构。

## 5. Rust 引擎同步

独立 bridge 通过 [`mmdsync-bridge/mmdsync-bridge/Cargo.toml`](mmdsync-bridge/mmdsync-bridge/Cargo.toml) 的本地路径依赖使用 [`mmdsync-bridge/MC-MMD-rust/rust_engine/`](mmdsync-bridge/MC-MMD-rust/rust_engine/) 中的私有引擎副本。本次已将该副本同步到适配所需的 1.0.5 引擎结构，覆盖：

- PMX 模型、材质、SubMesh、骨骼、Morph 与物理运行时；
- VMD 动画读取、分层播放、过渡、循环、seek、骨骼 mask/exclude、动画合并；
- VPD 骨骼覆盖与 Morph 应用；
- GPU skinning 矩阵、原始顶点/法线、实时 UV、骨骼索引与权重导出；
- 第一人称、头眼跟踪、自动眨眼、VR tracking 与 IK 参数；
- 材质可见性、Morph 状态与模型内存统计；
- 模型、动画、纹理注册表，以及 FBX/纹理缓存的会话级清理。

bridge 使用 `default-features = false` 嵌入引擎，避免引入不必要的模组侧 JNI 注册面；发布配置启用 LTO、单 codegen unit、`panic=abort` 和 strip，见 [`mmdsync-bridge/mmdsync-bridge/Cargo.toml`](mmdsync-bridge/mmdsync-bridge/Cargo.toml)。

## 6. JNI 新增与对齐能力

Java 声明集中在 [`MMDSyncNativeBridge.java`](MMDSkinSync/common/src/main/java/com/tendoarisu/mmdskin/sync/util/MMDSyncNativeBridge.java)，Rust 导出集中在 [`mmdsync-bridge/mmdsync-bridge/src/lib.rs`](mmdsync-bridge/mmdsync-bridge/src/lib.rs)。最终静态核验结果：

| 项目 | 数量 |
|---|---:|
| Java `MMDSyncNativeBridge` native 声明 | 90 |
| Rust `Java_com_tendoarisu_mmdskin_sync_util_MMDSyncNativeBridge_*` 导出 | 90 |
| Java 声明缺失导出 | 0 |
| Rust 额外导出 | 0 |
| `Java_com_shiroha_mmdskin_NativeFunc_*` 导出 | 0 |

`NativeFunc JNI = 0` 是有意设计：bridge 不伪装或替换 MC-MMD-rust 自己的 `NativeFunc` 原生库，全部新增 JNI 均归属 `MMDSyncNativeBridge`，由句柄路由层选择。

90 项接口覆盖以下能力组：

1. 内存/加密文件模型、动画、纹理加载；
2. HWID、握手公钥派生、会话密钥安装/清理、同步信封签名；
3. native 库哈希、class 哈希、解密后 MD5、AES-GCM；
4. 模型网格、索引、UV、骨骼、材质、SubMesh 与 skinning 缓冲查询；
5. 动画切换、过渡、循环、seek、摄像机数据与动画合并；
6. 第一人称、头眼、自动眨眼、VR、模型位置与物理开关；
7. 材质可见性、Morph、VPD、骨骼层过滤与内存统计；
8. 模型、动画、纹理有效性校验及显式释放。

## 7. Session 与资源安全

native 会话实现位于 [`mmdsync-bridge/mmdsync-bridge/src/lib.rs`](mmdsync-bridge/mmdsync-bridge/src/lib.rs)，Java 会话生命周期由 [`CryptoUtils.java`](MMDSkinSync/common/src/main/java/com/tendoarisu/mmdskin/sync/util/CryptoUtils.java) 与 [`SyncManager.java`](MMDSkinSync/common/src/main/java/com/opdent/mmdskin/sync/SyncManager.java) 驱动。

主要安全措施：

- 会话材料通过运行时 HWID、服务器 challenge/secret 与目标 native SHA-256 绑定；密钥解封前核验当前已加载 native 文件哈希。
- 敏感解封路径在 Windows 检查调试器，在 Linux 检查 `/proc/self/status` 的 `TracerPid`。
- AES-256 会话密钥不以连续明文长期存放；native 将其拆为四个 8 字节 shard，使用随机 seed 和绑定当前库哈希的 mask 包装。临时明文缓冲使用后清零。
- 安装新会话或清理会话都会递增 session generation，清空模型、动画、纹理的跟踪句柄与私有引擎注册表；旧句柄因 generation 不匹配而失效。
- 模型、动画和纹理分别维护 generation 跟踪表；每次 JNI 操作先验证句柄属于当前会话。
- 删除模型、动画和纹理时同时移除引擎注册对象和跟踪记录，重复/过期释放安全返回。
- 断开服务器时，[`SyncManager.clearClientSessionState()`](MMDSkinSync/common/src/main/java/com/opdent/mmdskin/sync/SyncManager.java:235) 会重置服务器上下文、舞台刷新状态、模型选择与 native session，并触发上游缓存清理。
- 舞台 VMD 扫描使用 `try/finally` 释放临时动画句柄，见 [`MixinStagePack.java`](MMDSkinSync/common/src/main/java/com/tendoarisu/mmdskin/sync/mixin/MixinStagePack.java)。
- bridge 句柄若已过期，模型预加载、材质查询、动画切换、舞台操作等入口均拒绝继续执行，且不降级传给上游 native。

## 8. 格式支持矩阵

下表描述的是 **MMDSkinSync 加密资源路径**；普通未加密格式仍由 MC-MMD-rust 1.0.5 上游加载器处理。

| 资源格式 | 加密资源支持 | 实现与行为 |
|---|---:|---|
| PMX | 支持 | native 解密后由同步的 PMX 内存解析链路创建 bridge 模型；支持 GPU/OpenGL、材质、Morph、物理等运行时能力。 |
| PMD | 不支持 | 1.0.5 未向 bridge 提供完整 PMD 内存加载 API；Java 与 native 均明确拒绝，返回空句柄，禁止误按 PMX 解析。 |
| VRM | 不支持 | 1.0.5 未向 bridge 提供完整 VRM 内存加载 API；明确拒绝，返回空句柄，禁止误按 PMX 解析。 |
| VMD | 支持 | native 解密并从内存加载；支持骨骼、Morph、摄像机数据检测、切换、过渡、seek 与同域动画合并。 |
| VPD | 支持 | 通过 bridge VPD 应用能力同步骨骼覆盖与 Morph 状态，并在应用后同步 GPU Morph 权重。 |
| 纹理 | 支持 | native 解密后在内存解码，支持 PNG、JPEG、BMP、TGA；提供尺寸、像素指针、Alpha 检测和显式释放。 |

明确限制：加密 PMD 和加密 VRM **不是降级支持**，而是主动拒绝。该行为可防止错误解析造成崩溃、越界读取或产生无效模型句柄。

## 9. Windows / Linux 原生构建

### 9.1 通用信息

- Rust crate：[`mmdsync-bridge/mmdsync-bridge/`](mmdsync-bridge/mmdsync-bridge/)
- 构建类型：`cdylib`，release
- 固定构建变量：`MMDSYNC_BUILD_HWID=2026030809410d000721`
- Windows 目标：x86-64 DLL
- Linux 目标：ELF64 x86-64，最低 GLIBC `2.17`
- 两个平台必须使用同一固定构建变量；变量只用于构建一致性，握手确定性派生不依赖构建机身份。
- native 构建要求仓库相对布局保持不变，因为 bridge 通过相对路径引用本地 Rust 引擎。

### 9.2 原生库产物

| 平台 | 内置文件 | 大小 | SHA-256 | ABI |
|---|---|---:|---|---|
| Windows x86-64 | [`MMDSkinSync/common/src/main/resources/natives/windows-x64/mmdsync_bridge.dll`](MMDSkinSync/common/src/main/resources/natives/windows-x64/mmdsync_bridge.dll) | 1,709,568 字节 | `b872e6e12b970724fa049678174fc60999f732199cb84d8b64101f4779571eb3` | PE x86-64 |
| Linux x86-64 | [`MMDSkinSync/common/src/main/resources/natives/linux-x64/libmmdsync_bridge.so`](MMDSkinSync/common/src/main/resources/natives/linux-x64/libmmdsync_bridge.so) | 2,272,832 字节 | `9e69cde319230ca79a416ad844050c5bc756267d626947ecee4596fb412aa4c7` | ELF64 x86-64，GLIBC 2.17 |

## 10. 最终模组产物及哈希

| 加载器 | 产物 | 大小 | SHA-256 |
|---|---|---:|---|
| Fabric | [`MMDSkinSync/fabric/build/libs/mmdsync-fabric-1.21.1-1.1.0.jar`](MMDSkinSync/fabric/build/libs/mmdsync-fabric-1.21.1-1.1.0.jar) | 2,191,476 字节 | `58ef36e7918fa9ef93fc679004da275ef095417e42e63ae3682593b40fa77486` |
| NeoForge | [`MMDSkinSync/neoforge/build/libs/mmdsync-neoforge-1.21.1-1.1.0.jar`](MMDSkinSync/neoforge/build/libs/mmdsync-neoforge-1.21.1-1.1.0.jar) | 2,190,440 字节 | `4481853efe349ddd84ac2464fceaa8e58ffcda144e16a06a2281e47304f5c6fb` |

上述 JAR 已包含对应资源目录中的 Windows DLL 和 Linux SO。dev、sources 与 shadow 中间产物不是最终交付文件。

## 11. 构建与测试结果

| 检查项 | 结果 |
|---|---|
| Rust 引擎测试 | 82 通过，0 失败，1 忽略 |
| MMDSkinSync Gradle clean build | 31 个任务成功 |
| Java / Rust JNI 集合比对 | 90 / 90；缺失 0；额外 0 |
| 非预期 `NativeFunc` JNI 导出 | 0 |
| Windows DLL 大小与 SHA-256 | 已核验，匹配第 9 节 |
| Linux SO 大小与 SHA-256 | 已核验，匹配第 9 节 |
| Fabric / NeoForge JAR 大小与 SHA-256 | 已核验，匹配第 10 节 |
| Linux ABI | ELF64 x86-64，GLIBC 2.17 |

“构建通过”表示编译、资源处理、remap、shadow/打包等 31 个 Gradle 任务全部成功；不等同于覆盖所有显卡驱动、资源包和多人服务器组合的人工游戏内回归。

## 12. 平台与功能限制

1. 当前内置 native 仅覆盖 Windows x86-64 与 Linux x86-64；不包含 macOS、ARM64、Android 或 32 位平台。
2. Linux 产物要求 x86-64 和 GLIBC 2.17 或更高版本，不适用于 musl-only 环境。
3. 加密 PMD、加密 VRM 明确不支持并拒绝加载；普通未加密 PMD/VRM 仍由上游能力决定。
4. bridge 未暴露 GPU Morph offsets 缓冲查询；bridge 模型在中心查询端报告相关计数/大小为 0，继续采用已有 CPU Morph/权重同步路径。
5. bridge 未导出上游手部矩阵句柄能力；bridge 模型不会把私有句柄交给上游矩阵路径。
6. 模型与动画必须来自同一句柄域；普通模型不能挂载 bridge 动画，bridge 模型也不能使用无效或跨会话动画。
7. 会话绑定 native 文件哈希；替换、修改或使用错误平台 native 会导致会话材料安装/校验失败。
8. 敏感会话解封在检测到运行时调试观察器时会拒绝执行。
9. Mixin 适配基于 MC-MMD-rust `1.0.5-1.21.1-2` 的类与调用结构；后续版本若再次重构，需要重新核验目标和注入点。

## 13. 关键文件清单

### 13.1 基线与构建

- [`MC-MMD-rust/gradle.properties`](MC-MMD-rust/gradle.properties)：上游 Minecraft、平台和依赖版本。
- [`MC-MMD-rust/settings.gradle`](MC-MMD-rust/settings.gradle)：上游模块裁剪与仓库配置。
- [`MMDSkinSync/gradle.properties`](MMDSkinSync/gradle.properties)：适配目标版本与 MMDSync 版本。
- [`MMDSkinSync/build.gradle`](MMDSkinSync/build.gradle)：Java 21、Loom、Architectury 与仓库配置。
- [`MMDSkinSync/common/build.gradle`](MMDSkinSync/common/build.gradle)：1.0.5 common JAR 编译依赖和打包排除。
- [`MMDSkinSync/settings.gradle`](MMDSkinSync/settings.gradle)：common、Fabric、NeoForge 模块选择。

### 13.2 Java bridge、会话与路由

- [`MMDSyncNativeBridge.java`](MMDSkinSync/common/src/main/java/com/tendoarisu/mmdskin/sync/util/MMDSyncNativeBridge.java)：90 项 JNI 声明与 bridge 句柄标记。
- [`MMDSyncNativeLoader.java`](MMDSkinSync/common/src/main/java/com/tendoarisu/mmdskin/sync/util/MMDSyncNativeLoader.java)：按平台加载内置 native。
- [`MMDSyncRuntimePorts.java`](MMDSkinSync/common/src/main/java/com/tendoarisu/mmdskin/sync/util/MMDSyncRuntimePorts.java)：1.0.5 中心运行时端口路由。
- [`CryptoUtils.java`](MMDSkinSync/common/src/main/java/com/tendoarisu/mmdskin/sync/util/CryptoUtils.java)：Java 侧加密资源识别与 session 材料安装入口。
- [`SyncManager.java`](MMDSkinSync/common/src/main/java/com/opdent/mmdskin/sync/SyncManager.java)：服务器上下文、同步资源与断服会话清理。
- [`MMDSyncModClient.java`](MMDSkinSync/common/src/main/java/com/opdent/mmdskin/sync/MMDSyncModClient.java)：客户端启动时安装中心路由。

### 13.3 Mixin 与平台资源

- [`mmdsync-common.mixins.json`](MMDSkinSync/common/src/main/resources/mmdsync-common.mixins.json)：公共适配 Mixin 清单。
- [`mmdsync-fabric.mixins.json`](MMDSkinSync/fabric/src/main/resources/mmdsync-fabric.mixins.json)：Fabric UI Mixin。
- [`mmdsync-neoforge.mixins.json`](MMDSkinSync/neoforge/src/main/resources/mmdsync-neoforge.mixins.json)：NeoForge UI Mixin。
- [`MixinMMDModelManager.java`](MMDSkinSync/common/src/main/java/com/tendoarisu/mmdskin/sync/mixin/MixinMMDModelManager.java)：PMX 分流以及 PMD/VRM 明确拒绝。
- [`MixinMMDAnimManager.java`](MMDSkinSync/common/src/main/java/com/tendoarisu/mmdskin/sync/mixin/MixinMMDAnimManager.java)：VMD 加载和动画句柄生命周期。
- [`MixinMMDTextureManager.java`](MMDSkinSync/common/src/main/java/com/tendoarisu/mmdskin/sync/mixin/MixinMMDTextureManager.java)：纹理加载、查询与释放。
- [`MixinStagePack.java`](MMDSkinSync/common/src/main/java/com/tendoarisu/mmdskin/sync/mixin/MixinStagePack.java)：加密 VMD 扫描和临时句柄释放。
- [`MixinMaterialVisibilityGateway.java`](MMDSkinSync/common/src/main/java/com/tendoarisu/mmdskin/sync/mixin/MixinMaterialVisibilityGateway.java)：1.0.5 材质网关路由。
- [`MixinLivingEntityModelStateHelper.java`](MMDSkinSync/common/src/main/java/com/tendoarisu/mmdskin/sync/mixin/MixinLivingEntityModelStateHelper.java)：实体模型状态补充路由。
- [`MixinModelLifecycleMetrics.java`](MMDSkinSync/common/src/main/java/com/tendoarisu/mmdskin/sync/mixin/MixinModelLifecycleMetrics.java)：模型生命周期统计查询。
- [`MixinPerformanceHud.java`](MMDSkinSync/common/src/main/java/com/tendoarisu/mmdskin/sync/mixin/MixinPerformanceHud.java)：调试 HUD 查询路由。

### 13.4 Rust 与原生产物

- [`mmdsync-bridge/mmdsync-bridge/src/lib.rs`](mmdsync-bridge/mmdsync-bridge/src/lib.rs)：会话安全、资源解密、90 项 JNI 导出与句柄生命周期。
- [`mmdsync-bridge/mmdsync-bridge/Cargo.toml`](mmdsync-bridge/mmdsync-bridge/Cargo.toml)：bridge 依赖、cdylib 和 release 配置。
- [`mmdsync-bridge/mmdsync-bridge/build.rs`](mmdsync-bridge/mmdsync-bridge/build.rs)：本地引擎路径及固定构建变量注入。
- [`mmdsync-bridge/MC-MMD-rust/rust_engine/`](mmdsync-bridge/MC-MMD-rust/rust_engine/)：bridge 使用的同步引擎源码。
- [`mmdsync_bridge.dll`](MMDSkinSync/common/src/main/resources/natives/windows-x64/mmdsync_bridge.dll)：Windows 内置 native。
- [`libmmdsync_bridge.so`](MMDSkinSync/common/src/main/resources/natives/linux-x64/libmmdsync_bridge.so)：Linux 内置 native。

## 14. 复现构建命令

以下命令假设终端当前目录为工作区根目录，且三个仓库保持当前相对布局。Windows 与 Linux 原生库应分别在对应操作系统上构建；构建完成后将 release 文件复制到 MMDSkinSync 的资源目录，再执行 Gradle clean build。

### 14.1 Windows 10 / cmd.exe

```bat
cd mmdsync-bridge\mmdsync-bridge
set MMDSYNC_BUILD_HWID=2026030809410d000721
cargo clean
cargo build --release
cd ..\..
copy /Y mmdsync-bridge\mmdsync-bridge\target\release\mmdsync_bridge.dll MMDSkinSync\common\src\main\resources\natives\windows-x64\mmdsync_bridge.dll

cd MMDSkinSync
call gradlew.bat clean build
cd ..

certutil -hashfile MMDSkinSync\common\src\main\resources\natives\windows-x64\mmdsync_bridge.dll SHA256
certutil -hashfile MMDSkinSync\common\src\main\resources\natives\linux-x64\libmmdsync_bridge.so SHA256
certutil -hashfile MMDSkinSync\fabric\build\libs\mmdsync-fabric-1.21.1-1.1.0.jar SHA256
certutil -hashfile MMDSkinSync\neoforge\build\libs\mmdsync-neoforge-1.21.1-1.1.0.jar SHA256
```

引擎测试可在 Windows cmd 中执行：

```bat
cd mmdsync-bridge\MC-MMD-rust\rust_engine
cargo test --lib
cd ..\..\..
```

### 14.2 Linux shell

```bash
cd mmdsync-bridge/mmdsync-bridge
export MMDSYNC_BUILD_HWID=2026030809410d000721
cargo clean
cargo build --release
cd ../..
cp -f mmdsync-bridge/mmdsync-bridge/target/release/libmmdsync_bridge.so \
  MMDSkinSync/common/src/main/resources/natives/linux-x64/libmmdsync_bridge.so

cd MMDSkinSync
./gradlew clean build
cd ..

sha256sum MMDSkinSync/common/src/main/resources/natives/windows-x64/mmdsync_bridge.dll
sha256sum MMDSkinSync/common/src/main/resources/natives/linux-x64/libmmdsync_bridge.so
sha256sum MMDSkinSync/fabric/build/libs/mmdsync-fabric-1.21.1-1.1.0.jar
sha256sum MMDSkinSync/neoforge/build/libs/mmdsync-neoforge-1.21.1-1.1.0.jar
file MMDSkinSync/common/src/main/resources/natives/linux-x64/libmmdsync_bridge.so
readelf --version-info MMDSkinSync/common/src/main/resources/natives/linux-x64/libmmdsync_bridge.so
```

引擎测试可在 Linux shell 中执行：

```bash
cd mmdsync-bridge/MC-MMD-rust/rust_engine
cargo test --lib
cd ../../..
```

> 注意：固定变量必须使用任务指定值 `MMDSYNC_BUILD_HWID=2026030809410d000721`。本文未记录任何无关本机用户名、绝对目录、令牌、服务器 secret 或运行时 HWID。

## 15. 未提交工作树说明

本报告对应的是 **未提交工作树快照**，不是某个单一 commit：

- [`MC-MMD-rust/`](MC-MMD-rust/) 的 HEAD 已核验为要求的提交 `48c31b6acf3ece211e7bdbe0482ceb1be1a41205`，当前另有 [`MC-MMD-rust/settings.gradle`](MC-MMD-rust/settings.gradle) 未提交修改。
- [`MMDSkinSync/`](MMDSkinSync/) 当前包含 36 条已跟踪修改、删除或未跟踪适配项；范围覆盖 Gradle、1.0.5 依赖 JAR、Java 路由、Mixin、平台元数据、Windows/Linux native 与新增运行时适配文件。
- [`mmdsync-bridge/`](mmdsync-bridge/) 当前包含 58 条已跟踪修改或未跟踪项；主要是 bridge Rust 实现及其本地 `rust_engine` 同步内容。
- 构建目录和其他被 Git 忽略的生成物不计入上述工作树条目数。
- 本次文档任务不创建 commit，也不清理、暂存或重置既有修改。

因此，复现或移交时必须保留当前未提交改动，不能仅检出上述 HEAD 提交后期待得到相同 native/JAR。最终交付物应按第 9、10 节给出的大小与 SHA-256 做内容级确认。

## 16. 结论

MMDSkinSync 已完成对 MC-MMD-rust `1.0.5-1.21.1-2` 和 Minecraft `1.21.1` 的 Fabric/NeoForge 双端适配。核心方案通过第 62 位标记区分 bridge 句柄，以 1.0.5 中心运行时端口统一路由，并由有限 Mixin 覆盖尚未端口化的加载和渲染调用。Rust bridge 已同步引擎能力，Java/Rust JNI 90 对 90 完全一致，未劫持 `NativeFunc` JNI；会话 generation、注册表清理和句柄有效性检查保证断服、换服及资源释放安全。

最终 Windows DLL、Linux SO、Fabric JAR 与 NeoForge JAR 均已生成并完成大小、SHA-256 核验；Rust 引擎测试为 82 通过、0 失败、1 忽略，Gradle clean build 为 31 个任务成功。加密 PMX、VMD、VPD 和纹理受支持；加密 PMD、VRM 因 1.0.5 缺少相应完整内存加载 API而被明确拒绝。
