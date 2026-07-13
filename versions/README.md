# Versioned MMDSkinSync builds

`1.21.1/` 与 `1.21.4/` 是两个互相隔离、可独立运行 Gradle Wrapper 的发行构建。顶层 composite 聚合层不会把根目录旧迁移工作副本纳入项目。

## 构建与产物

| 目录 | Minecraft | 精确 MC-MMD 下限 | JNI | Included build | 顶层任务 | 最终产物 |
| --- | --- | --- | --- | --- | --- | --- |
| `1.21.1/` | 1.21.1 | `1.0.5-1.21.1-2` | 90-JNI | `mc1211` | `compile1211` / `build1211` | Fabric、NeoForge 各一份 |
| `1.21.4/` | 1.21.4 | `1.0.5-1.21.4-1` | 79-JNI | `mc1214` | `compile1214` / `build1214` | 仅 Fabric 一份 |

1.21.1 含独立 common、Fabric、NeoForge、Wrapper、依赖 JAR 和 Windows/Linux native。1.21.4 因上游高版本 MMD Skin 不再支持 NeoForge，只包含 common 与 Fabric 发行构建；已取消的 NeoForge 源目录和分发项不会参与构建。`buildAll` 以两个 Wrapper 进程隔离运行，避免 Architectury 3.4 在 Gradle 8.11 中同时配置同名内部项目产生冲突。

从聚合仓根目录运行：

```bat
gradlew.bat compile1211 --rerun-tasks --no-daemon --stacktrace --console=plain
gradlew.bat compile1214 --rerun-tasks --no-daemon --stacktrace --console=plain
gradlew.bat build1211 --rerun-tasks --no-daemon --stacktrace --console=plain
gradlew.bat build1214 --rerun-tasks --no-daemon --stacktrace --console=plain
gradlew.bat buildAll --rerun-tasks --no-daemon --stacktrace --console=plain
gradlew.bat collectDistributions --rerun-tasks --no-daemon --stacktrace --console=plain
gradlew.bat verifySnapshotManifest --no-daemon --stacktrace --console=plain
```

收集目录固定为 `../dist/1.21.1/` 与 `../dist/1.21.4/`，共三个受支持产物。只部署无 `dev`、`dev-shadow`、`sources` classifier 的最终 JAR；不得部署旧的 1.21.4 NeoForge JAR。

## 隔离与兼容边界

- 1.21.1 发行包仅可包含 90-JNI Java 契约和 native。
- 1.21.4 发行包仅可包含 79-JNI Java 契约和 native。
- 禁止跨目录复制 MC-MMD JAR、native 或生成产物。
- MmdSkin-Bukkit 1.2.0 可认证两种客户端 profile，但其服务端运行时仍只装载 79-JNI；认证 90-JNI 的 hash 只是 PEM 派生输入。
- maid opcode 4/5 属于 1.21.1 客户端能力，Bukkit 只在已认证 1.21.1 profile 的发送者和接收者之间按目标 Loader 重编码转发。

`verifySnapshotManifest` 保护 `../docs/upstream/` 的不可变 provenance 证据；冻结后的版本源码允许演进，不会被伪装成旧快照。原始 patch、状态与输入 manifest 不会被静默重写。

## 验证边界

Gradle 构建、class major/native/Mixin/metadata 静态审计不等于游戏内测试。发布前仍须真实启动 1.21.1 Fabric/NeoForge、1.21.4 Fabric 与 Paper 测试服，验证加载、握手、资源传输、Mixin 注入和 native 调用。本次交付未执行此类联机测试。
