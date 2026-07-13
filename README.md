﻿# MMDSkinSync 多版本聚合构建

本仓库根目录是纯 Gradle composite 聚合层。可发布源码只位于两个隔离子构建：

- `versions/1.21.1/`：Minecraft 1.21.1、MC-MMD-rust 1.0.5、90-JNI；
- `versions/1.21.4/`：Minecraft 1.21.4、MC-MMD-rust 1.0.5、79-JNI。

根目录旧 `common/`、`fabric/`、`neoforge/` 与 `libs/` 是迁移工作副本，不属于顶层 Gradle 项目，也不是发行输入。每个版本拥有独立 Wrapper、源码、MC-MMD JAR 与 native，严禁跨版本复制或混用 JNI 声明/native。

## 稳定 Windows 构建流程

在仓库根目录使用 Java 21 依次运行：

```bat
rem 常规全量构建：构建两个版本、校验来源并收集三个发行 JAR
gradlew.bat build

rem 也可按版本或步骤单独执行
gradlew.bat compile1211 --rerun-tasks --no-daemon --stacktrace --console=plain
gradlew.bat compile1214 --rerun-tasks --no-daemon --stacktrace --console=plain
gradlew.bat build1211 --rerun-tasks --no-daemon --stacktrace --console=plain
gradlew.bat build1214 --rerun-tasks --no-daemon --stacktrace --console=plain
gradlew.bat buildAll --rerun-tasks --no-daemon --stacktrace --console=plain
gradlew.bat collectDistributions --rerun-tasks --no-daemon --stacktrace --console=plain
gradlew.bat verifySnapshotManifest --no-daemon --stacktrace --console=plain
```

顶层 `build` 是精确注册的聚合任务，等价于执行 `buildAll`、`verifySnapshotManifest` 和 `collectDistributions`。`build1211` 显式构建 1.21.1 的 common、Fabric 与 NeoForge；`build1214` 只构建 1.21.4 的 common 与 Fabric，因为上游高版本 MMD Skin 已不再提供 NeoForge。`buildAll` 使用两个独立 Wrapper 进程，规避 Architectury 3.4/Gradle 8.11 对相同内部项目名的 IDEA 模型冲突。`collectDistributions` 清理对应 dist 版本目录并复制三个受支持的无 classifier 最终 JAR。

每个隔离版本的 `build`/`check` 都会执行 `verifyMixinTargets`。该任务以版本目录内精确的 MMD Skin common JAR 为证据，逐项核对 strict Mixin JSON、所有 `remap=false` 目标类/方法、`@At` 中的 MMD Skin 调用、Invoker 和关键反射 collaborator。Fabric 生产环境会把 Minecraft 类和覆盖方法 remap 为 intermediary，因此继承自 `Screen` 的 selector 必须使用经生产 JAR 证明的 `method_25393`/`method_25394`/`method_25402`，而不能在 `remap=false` 下直接写 `tick`/`render`/`mouseClicked`。MC-MMD 自有方法保持稳定 named；当描述符包含 Minecraft 类型时，审计要求改用无歧义的裸 named 方法名，避免把 Mojmap 描述符冻结进生产 selector。此轻量审计验证类、方法和描述符存在性，但不替代实际 Mixin 应用与游戏内执行验证。

`verifySnapshotManifest` 验证 `docs/upstream/EVIDENCE_MANIFEST.sha256` 所保护的原始 patch、状态、来源说明及原始输入 manifest。`versions/` 是在冻结快照之后持续校正的工作树，因此该任务不会错误要求当前演进源码逐字节等于旧输入，也不会改写原始 patch/hash。

编译期间可能出现 MC-MMD 依赖注解导致的 `EnvType.CLIENT` classpath 警告，以及旧 Loom/Gradle deprecation 警告；当前它们是非致命警告，不能据此忽略真正的编译、remap 或审计失败。

## 资源上传兼容与安全边界

- ZIP 条目名不使用操作系统默认字符集：EFS/UTF-8 与 CRC 校验通过的 Info-ZIP Unicode Path `0x7075` 优先，其余名称按严格 UTF-8、包含中文时的严格 GBK、最后 CP437 回退。
- ZIP、目录和单文件都在进入网络前执行路径归一化和限额检查；拒绝绝对路径、盘符、NUL、`.`/`..`、符号链接、归一化重复路径、超量条目、超大展开内容和异常压缩比。目录上传直接枚举源目录，不再先制造中间 ZIP。
- 每个上传 CHUNK 根据完整 packet 元数据动态计算有效载荷，编码后的 plugin message 必须不超过 Bukkit 32,766 字节；超限在网络发送前受控拒绝。
- 新客户端通过 BEGIN 的 `cap=ack-v1` 与新服务端协商逐块 ACK 背压；旧端未声明能力时使用有节奏的兼容发送。FINISH 只有收到 `upload_finish_ok` 才向用户报告服务器确认完成；ABORT、超时和断线均清理会话与 staging。
- 集成 Fabric/NeoForge 服务端与 Bukkit 使用相同的 owner/server/zone/path、块顺序、块数、总大小与 SHA-256 绑定；畸形包和未知 opcode 返回 ABORT，不允许异常逃逸到网络 handler 导致玩家断线。

## F3 第一/第三人称生命周期

材质缺失根因位于 Sync bridge：上游 MMD Skin 的 `reset()` 会调用自己的 JNI 关闭第一人称材质掩码，而 bridge handle 不能进入该 JNI 域。Sync 现在在 reset 前将 bridge 模型切回第三人称并清除跟踪状态；两版均保留状态变化日志。MMD Skin 1.21.1/1.21.4 源码未修改，也不因此发布额外 MMD Skin JAR。

## 发行矩阵

| Minecraft | Loader | 精确 MMD Skin 下限 | 其他依赖 | JNI ABI | 最终产物 |
| --- | --- | --- | --- | --- | --- |
| 1.21.1 | Fabric | `1.0.5-1.21.1-2` | Java 21、Fabric Loader/API、Architectury 13 | 90-JNI | `dist/1.21.1/mmdsync-fabric-1.21.1-1.1.0.jar` |
| 1.21.1 | NeoForge | `1.0.5-1.21.1-2` | Java 21、NeoForge 21.1.219+、Architectury 13 | 90-JNI | `dist/1.21.1/mmdsync-neoforge-1.21.1-1.1.0.jar` |
| 1.21.4 | Fabric | `1.0.5-1.21.4-1` | Java 21、Fabric Loader/API、Architectury 15 | 79-JNI | `dist/1.21.4/mmdsync-fabric-1.21.4-1.1.0.jar` |

Minecraft 1.21.4 不再构建、收集或发布 NeoForge 版 Sync。旧 `mmdsync-neoforge-1.21.4-*.jar` 不受支持，必须从实例中删除。

每个客户端必须安装与 Minecraft 版本和 Loader 完全对应的 MMDSkinSync 与 MC-MMD-rust。不要把 1.21.1 的 90-JNI native/JAR 放入 1.21.4 发行包，反之亦然。

## Native 指纹

| Profile | Windows x64 SHA-256 | Linux x64 SHA-256 |
| --- | --- | --- |
| 1.21.1 / 90-JNI | `b872e6e12b970724fa049678174fc60999f732199cb84d8b64101f4779571eb3` | `9e69cde319230ca79a416ad844050c5bc756267d626947ecee4596fb412aa4c7` |
| 1.21.4 / 79-JNI | `62e6ae9b37a2deafb8036ea29eb163ffa8455d77d50728d917892d3713245093` | `9007fbb8d4a201a3cb21d37677ded5ba231bea46e89f83fc618ed1f89e90d511` |

只支持有发行证据的 Windows x64 与 Linux x64。聚合构建不生成或引入新的 native ABI。

## 部署

1. 按服务端/客户端 Minecraft 版本选择同一行产物；1.21.1 的 Fabric 与 NeoForge 不可互换，1.21.4 只能使用 Fabric。
2. 将模组 JAR 放入对应实例的 `mods`；安装精确匹配版本后缀的 MC-MMD-rust 1.0.5 及元数据列出的 Loader/API/Architectury 依赖。
3. Bukkit/Paper 场景将 MmdSkin-Bukkit 1.2.0 放入服务端 `plugins`，客户端仍安装本矩阵内对应的模组组合。
4. 启动前核对 JAR 与 native 指纹，删除旧同名 Sync JAR，尤其不能保留已取消支持的 1.21.4 NeoForge JAR。
5. 客户端和构建环境均建议使用 Java 21；更高版本 JVM 的受限 native-access 警告不应被误判为已知 Mixin selector 崩溃的根因。
6. 首次部署后必须在隔离测试服执行真实 Fabric/NeoForge/Paper 联机、握手、资源传输、Mixin 和 native 功能验证，再进入生产。

本仓库已执行 Gradle 编译/构建、纯 Java 测试和静态 JAR 审计；未执行 Minecraft、Paper、Fabric 或 NeoForge 游戏内联机测试。静态验证不能替代游戏内验证。
