﻿
<p align="center">
  <img src="icon.png" alt="MMDSync 图标">
</p>

**MMDSync** 是 [MC-MMD-rust](https://github.com/shiroha-233/MC-MMD-rust) 模组的附属扩展，主要用来解决多人游戏中 MMD 模型（PMX）、动作（VMD）和贴图的同步问题。

> [!IMPORTANT]
> **此目录是 Minecraft 1.21.4 的 Fabric-only 构建，必须配合 MMD Skin `1.0.5-1.21.4-1` 或更高兼容构建使用。**
> 上游高版本 MMD Skin 已不再支持 NeoForge，因此本版本没有 NeoForge 产物；不要使用旧的 1.21.4 NeoForge Sync JAR。

## ✨ 功能特性

*   **游戏内上传模型**：无需手动分发文件，直接在游戏内点击“上传模型资源”按钮，通过系统原生文件对话框（支持 ZIP、单文件、文件夹）即可完成上传。
*   **自动增量同步**：玩家进服时，模组会自动对比服务器与本地缓存，增量下载缺失的模型或动作文件，支持 GZIP 压缩。
*   **端到端加密保护**：同步过程与本地存储均经过加密，有效防止模型资源被非法提取。
*   **服务器绑定**：同步的模型资源与当前服务器 ID 严格绑定。切换服务器或退服后，相关模型将自动隐藏并释放内存，确保存储隔离。
*   **Fabric 与 Bukkit/Paper**：1.21.4 客户端仅支持 Fabric，并提供配套的 [Bukkit/Paper 插件版](https://github.com/opdent-cmd/MmdSkin-Bukkit)。

## 📦 上传兼容与安全

* ZIP 名称确定性支持 EFS/UTF-8、Info-ZIP Unicode Path `0x7075`、GBK 中文回退与 CP437，不读取平台默认字符集。
* 文件夹直接枚举；ZIP/目录统一拒绝路径穿越、绝对/盘符/NUL、符号链接、归一化重复、超量条目、超大展开体积和异常压缩比。
* CHUNK 按完整元数据动态预算，编码后严格不超过 32,766 字节。新服务端使用 `ack-v1` 逐块背压；旧服务端使用节奏兼容模式。FINISH 必须收到服务器确认，失败发送 ABORT 并清理状态。
* bridge 第一/第三人称 reset 路由与 1.21.1 保持一致；本修复不恢复或发布 1.21.4 NeoForge。

## ️ 安装与使用

### 1. 服务端 (Fabric / Bukkit)
1. 将对应的 `.jar` 放入 `mods` 或 `plugins` 文件夹。
2. 启动服务器以生成配置文件：
   - 模组版：`config/mmdsync-common.toml`
   - 插件版：`plugins/MmdSkin/config.yml`

### 2. 客户端 (Fabric)
1. 安装 1.21.4 Fabric 版 Sync、MMD Skin `1.0.5-1.21.4-1` 和匹配的 Fabric/Architectury 依赖后进入服务器。
2. 在 MMD 模型选择界面或通过管理员命令打开“上传模型资源”界面。
3. 选择对应的资源类型（PMX/VMD）和上传方式，即可将本地模型同步至服务器。
4. 管理员可使用 `/mmdsync` 强制全局同步，使用 `/mmdsync reload` 重载配置。

## ⚙️ 配置文件说明 (Mod 示例)

```toml
[general]
# 服务器唯一私密盐 (用于加固加密流程，请勿公开)
serverSecret = "自动生成的随机密钥"

# 资源分块等待超时(秒)
# 0 或负数 = 无限等待直到文件传完
# 正数 = 单个文件在该时间内未传完则视为传输失败
resourcePacketWaitTimeoutSeconds = 0
```

## 📄 许可证

MIT License
