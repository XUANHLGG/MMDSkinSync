﻿
<p align="center">
  <img src="icon.png" alt="MMDSync 图标">
</p>

**MMDSync** 是 [MC-MMD-rust](https://github.com/shiroha-233/MC-MMD-rust) 模组的附属扩展，主要用来解决多人游戏中 MMD 模型（PMX）、动作（VMD）和贴图的同步问题。

> [!IMPORTANT]
> **本模组必须配合 [MC-MMD-rust](https://github.com/shiroha-233/MC-MMD-rust) 使用**。
> 目前模组端支持 Minecraft **1.21.1** 版本；插件服务端配合 [MmdSkin-Bukkit](https://github.com/opdent-cmd/MmdSkin-Bukkit.git) 使用，配合跨版本插件理论上对服务端没有版本限制（只要插件跑得起来）。

## ✨ 功能特性

*   **网页上传模型**：不用手动发文件给别人，直接在浏览器里（默认端口 5000）拖拽模型文件夹或压缩包就能上传。
*   **自动增量同步**：进服时自动检查缺少的模型或动作，只下载更新的部分，支持 GZIP 压缩。
*   **模型加密保护**：同步过程和本地存储都经过加密处理，防止服务器模型被玩家恶意提取。
*   **服务器绑定**：同步的模型只在对应的服务器里显示。换服务器或者退服后，这些模型会自动隐藏并清理内存。
*   **多平台支持**：提供 NeoForge、Fabric 模组版以及 [Bukkit/Paper 插件版](https://github.com/opdent-cmd/MmdSkin-Bukkit.git)。

## 🛠️ 安装与使用

### 1. 服务端 (NeoForge / Fabric / Bukkit)
1. 把对应的 `.jar` 放到 `mods` 或 `plugins` 文件夹里。
2. 启动服务器，会自动生成配置文件：
   - 模组版：`config/mmdsync-common.toml`
   - 插件版：`plugins/MmdSkin/config.yml`
3. 确保服务器防火墙放行了 5000 端口（默认端口）。

### 2. 客户端 (NeoForge / Fabric)
1. 安装模组并进入服务器。
2. 浏览器打开 `http://服务器IP:5000` 上传你的模型。
3. 游戏里让管理员输入 `/mmdsync` 即可开始同步。
4. 同步完后，在 MMD 菜单里就能直接看到并换上刚才上传的模型了。

## ⚙️ 配置文件说明 (Mod 示例)

```toml
[general]
# 是否开启内置同步服务器
enableServer = true
# 网页上传端口
serverPort = 5000
# 服务器私钥 (用于生成稳定的 serverId，不要随便改)
serverSecret = "自动生成的密钥"
# 限制下载带宽 (Mbps)，0 为不限制
maxBandwidthMbps = 0.0
# 开启 GZIP 压缩加速下载
enableGzip = true
```

## 📄 许可证

MIT License
