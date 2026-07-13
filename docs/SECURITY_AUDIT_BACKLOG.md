# MMDSync 加密与资源协议安全审计及修复待办

> 审计日期：2026-07-13  
> 状态：待修复  
> 总体风险：**Critical**  
> 适用范围：MMDSkinSync 1.21.1（Fabric/NeoForge、90-JNI）、MMDSkinSync 1.21.4（Fabric、79-JNI）、MmdSkin-Bukkit、mmdsync-bridge

## 1. 文档目的

本文记录当前 MMDSync 加密、认证、资源传输和密钥管理中已确认的问题，作为后续安全改造、测试与发布验收的依据。

本次审计接受以下现实边界：

- 客户端最终需要在本机解密和渲染资源，因此无法彻底防御客户端运行时内存 DUMP。
- 无法从密码学上阻止完全控制合法客户端的用户在解密后重新导出或二次分发资源。
- 反调试、代码混淆、JNI、符号剥离和密钥分片只能提高逆向成本，不能替代安全的握手、密钥派生、认证和权限控制。

内存 DUMP 不作为本文的待修复缺陷，但除内存 DUMP 外的被动抓包、重放、恶意模组、恶意代理、越权访问、路径写入、明文降级和拒绝服务均属于需要处理的安全问题。

---

## 2. 当前数据流与安全属性

### 2.1 当前流程

1. 服务器保存一个长期静态秘密。
2. 服务器将静态秘密进行 SHA-256，得到全服共享的 32 字节资源密钥。
3. 服务端向客户端发送随机 challenge。
4. 客户端根据 challenge、平台、自报 HWID、目标 native 哈希和固定盐，确定性生成 RSA-2048 密钥对。
5. 客户端把公钥发给服务端；服务端使用 RSA PKCS#1 v1.5 包装全局资源密钥。
6. 客户端 native 解包并保存资源密钥。
7. 受支持的模型、动作和纹理资源使用 AES-256-GCM 加密，封装为 MMDARC v1。
8. 密文通过 Minecraft 自定义载荷分块下载并持久化到客户端。
9. native 在加载模型或动作时解密并直接交给运行时解析。
10. 客户端上传方向目前发送明文，服务端先写入 `.part` 暂存文件，再进行摘要校验和原子提交。

### 2.2 当前密码学事实

| 项目 | 当前实现 |
|---|---|
| 资源加密 | AES-256-GCM |
| nonce | 每次随机生成 12 字节 |
| GCM 标签 | 16 字节 |
| AAD | 未使用 |
| 资源容器 | `MMDARC` + 版本 + nonce + 密文及标签 |
| 握手密钥对 | 根据公开或客户端自报材料确定性生成 RSA-2048 |
| 密钥包装 | RSA/ECB/PKCS1Padding，即 PKCS#1 v1.5 |
| 服务器资源密钥 | SHA-256（服务器静态秘密） |
| 下载内容摘要 | 实际为 MD5，字段语义存在误导 |
| 上传内容摘要 | SHA-256，但不是签名或 MAC |
| 资源协议 MAC | 未接入；虽然代码中存在 HMAC-SHA-256 实现 |

### 2.3 必须区分的安全属性

- **保密性**：没有密钥的人不能直接读取资源。
- **完整性**：传输或存储内容被修改后能够被检测。
- **真实性**：能够确认数据确实来自预期服务器和会话，而不是恶意代理或合法密钥持有者伪造。
- **访问控制**：能够确认玩家是否有权查看、下载或上传资源。
- **逆向加固**：仅提高提取密钥或分析实现的成本，不构成密码学边界。

当前 AES-GCM 可以为单个密文提供一定的保密性和完整性，但握手、共享密钥、会话绑定和访问控制缺陷使整体系统不能提供可靠的端到端安全保证。

---

## 3. Critical 问题

### SEC-C01：确定性 RSA 私钥可由公开握手材料重建

- **级别**：Critical
- **状态**：未修复
- **影响范围**：1.21.1、1.21.4、Bukkit、bridge
- **利用前提**：能够观察一次完整握手；不需要内存 DUMP。

#### 问题

bridge 使用 challenge、客户端自报 HWID、平台、目标 native 哈希和固定盐作为确定性随机种子，生成 RSA 密钥对。上述输入均可从网络、发行二进制或客户端上报数据中获得。

相同输入会生成相同 RSA 私钥，因此被动抓包者可以独立重建客户端私钥，并解开服务端通过 RSA 包装的全局 AES 资源密钥。

#### 证据

- [`generate_deterministic_keypair()`](../../mmdsync-bridge/mmdsync-bridge/src/lib.rs:163)
- [`HandshakePacket`](../versions/1.21.1/common/src/main/java/com/opdent/mmdskin/sync/network/HandshakePacket.java:13)
- [`MmdSkinBukkit.handleHandshake()`](../../MmdSkin-Bukkit/src/main/java/com/opdent/mmdskin/bukkit/MmdSkinBukkit.java:450)
- [`build.rs`](../../mmdsync-bridge/mmdsync-bridge/build.rs:17)

#### 当前缓解

- challenge 使用高熵随机数。
- challenge 有 60 秒有效期。
- Bukkit 会绑定真实玩家 UUID，并在认证成功后消费 challenge。

这些措施只能防止直接重复提交旧握手，不能阻止攻击者重建私钥。

#### 修复要求

- [ ] 废弃确定性 RSA 密钥对。
- [ ] 使用客户端临时 X25519/ECDH 密钥对。
- [ ] 使用服务器长期 Ed25519 或等价签名身份认证握手。
- [ ] 使用 HKDF-SHA-256 从共享秘密和完整握手 transcript 派生会话密钥。
- [ ] transcript 必须绑定协议版本、服务器身份、真实玩家 UUID、连接随机数和双方临时公钥。
- [ ] 客户端必须验证服务器签名，并拒绝未知或不匹配的服务器身份。
- [ ] 服务端必须拒绝重复、过期或连接不匹配的握手。

---

### SEC-C02：资源清单、下载和上传没有强制认证与权限控制

- **级别**：Critical
- **状态**：未修复
- **影响范围**：Bukkit、Sync 内置服务端
- **利用前提**：能够正常进入服务器并发送插件消息。

#### 问题

资源传输入口没有要求玩家先完成握手认证，也没有独立的下载与上传权限。任意连接玩家可能枚举资源、请求下载、创建上传会话和覆盖已有资源。

现有上传路径安全、摘要校验、所有者绑定和原子提交只保证数据按协议到达，不能证明玩家有权执行上传。

#### 证据

- [`MmdSkinBukkit.onPluginMessageReceived()`](../../MmdSkin-Bukkit/src/main/java/com/opdent/mmdskin/bukkit/MmdSkinBukkit.java:571)
- [`MmdSkinBukkit.handleResourceTransferPacket()`](../../MmdSkin-Bukkit/src/main/java/com/opdent/mmdskin/bukkit/MmdSkinBukkit.java:674)
- [`ResourceTransferServerPacketHandler.handleServerboundPacket()`](../versions/1.21.1/common/src/main/java/com/opdent/mmdskin/sync/network/resource/ResourceTransferServerPacketHandler.java:19)
- [`MmdSkinBukkit.onCommand()`](../../MmdSkin-Bukkit/src/main/java/com/opdent/mmdskin/bukkit/MmdSkinBukkit.java:172)
- [`plugin.yml`](../../MmdSkin-Bukkit/src/main/resources/plugin.yml:8)

#### 修复要求

- [ ] 所有 MANIFEST、REQUEST_CHUNK 和 UPLOAD 操作必须绑定已认证连接会话。
- [ ] 未认证玩家默认拒绝所有资源操作。
- [ ] 下载权限和上传权限分离。
- [ ] 上传默认仅管理员或明确白名单可用。
- [ ] 认证会话必须绑定真实玩家 UUID、当前连接、服务器身份和协议版本。
- [ ] 玩家退出、认证过期、配置重载或服务端停用时取消相关传输。
- [ ] 添加每玩家和全局的并发、速率、容量、每日配额及允许扩展名限制。

---

### SEC-C03：客户端信任服务端路径，可能发生资源根目录外写入

- **级别**：Critical
- **状态**：未修复
- **影响范围**：1.21.1、1.21.4
- **利用前提**：连接恶意或被攻陷的服务器，或可信代理能够篡改资源包。

#### 问题

客户端直接使用服务端下发的服务器标识、zone、文件夹名和相对路径构造目标路径。当前下载接收和最终复制路径没有实施与上传端同等级别的规范化、根目录约束和符号链接检查。

即使最终资源因 GCM 标签不正确而无法加载，越界文件写入也可能已经发生。

#### 证据

- [`ResourceTransferClientManager.TransferSession.acceptChunk()`](../versions/1.21.1/common/src/main/java/com/opdent/mmdskin/sync/network/resource/ResourceTransferClientManager.java:180)
- [`SyncManager.syncZoneViaPackets()`](../versions/1.21.1/common/src/main/java/com/opdent/mmdskin/sync/SyncManager.java:442)
- [`SafeUploadCollector.collectZip()`](../versions/1.21.1/common/src/main/java/com/opdent/mmdskin/sync/resource/SafeUploadCollector.java:47)

#### 修复要求

- [ ] 对所有服务端下发路径执行严格规范化。
- [ ] 拒绝绝对路径、Windows 盘符、UNC、反斜杠、空段、`.`、`..`、NUL 和超长路径。
- [ ] 拒绝目标路径中任何符号链接祖先。
- [ ] 规范化后确认目标始终位于固定资源根目录内。
- [ ] 下载内容先写入安全创建的随机 staging 文件。
- [ ] 完整校验块顺序、总大小、摘要、会话 MAC 和 AEAD 后再原子提交。
- [ ] 失败时删除 staging 文件，不得修改最终目标。

---

## 4. High 问题

### SEC-H01：全服长期共享一个资源密钥，缺少玩家、连接、资源和 epoch 隔离

- **级别**：High
- **状态**：未修复

服务器静态秘密直接经过 SHA-256，得到所有玩家、连接和资源共用的固定密钥。任何单个客户端、握手、配置或备份泄漏都会扩大为整个服务器当前及历史资源泄漏。

MMDARC 没有 AAD，未绑定服务器、玩家、路径、资源类型、版本或密钥 epoch，因此不能阻止跨会话或跨资源重放。

#### 证据

- [`SyncAuthSupport.deriveSyncKey()`](../../MmdSkin-Bukkit/src/main/java/com/opdent/mmdskin/bukkit/SyncAuthSupport.java:46)
- [`ServerAuthManager.getServerSyncKey()`](../versions/1.21.1/common/src/main/java/com/opdent/mmdskin/sync/ServerAuthManager.java:83)
- [`MmdSkinBukkit.sendSyncUrl()`](../../MmdSkin-Bukkit/src/main/java/com/opdent/mmdskin/bukkit/MmdSkinBukkit.java:525)
- [`CryptoUtils.installSessionMaterial()`](../versions/1.21.1/common/src/main/java/com/tendoarisu/mmdskin/sync/util/CryptoUtils.java:69)

#### 修复要求

- [ ] 静态秘密只作为根密钥，不直接作为资源密钥。
- [ ] 使用 HKDF 派生服务器 epoch 密钥。
- [ ] 分别派生客户端到服务器、服务器到客户端的方向密钥。
- [ ] 派生按连接、玩家和资源隔离的密钥。
- [ ] AAD 绑定协议版本、服务器身份、玩家 UUID、规范路径、资源版本和 epoch。
- [ ] 支持密钥轮换、有限历史 epoch 和泄漏响应。
- [ ] 客户端拒绝未请求、已消费、过期或服务器不匹配的握手响应。

---

### SEC-H02：native 不可用或加密失败时可能静默降级为明文

- **级别**：High
- **状态**：未修复

Bukkit 在 native 不可用时可能直接发送原文；Rust JNI 加密函数的多个错误路径会返回输入原文，而不是返回明确错误。

#### 证据

- [`MmdSkinBukkit.prepareTransferPayload()`](../../MmdSkin-Bukkit/src/main/java/com/opdent/mmdskin/bukkit/MmdSkinBukkit.java:1005)
- [`Java_com_tendoarisu_mmdskin_sync_util_MMDSyncNativeBridge_aesEncrypt()`](../../mmdsync-bridge/mmdsync-bridge/src/lib.rs:817)

#### 修复要求

- [ ] 受保护扩展名的加密必须严格 fail closed。
- [ ] JNI 返回状态与输出数据分离，不得以返回原输入表示错误。
- [ ] native 缺失、随机源失败、密钥错误或加密异常时禁止发送资源。
- [ ] 启动时执行加密已知答案自检。
- [ ] 明文模式只能由管理员显式配置，默认关闭并产生显著告警。
- [ ] MMDARC 生成前验证输出确实为成功生成的 AEAD 密文。

---

### SEC-H03：公开 JNI 可以直接返回 32 字节原始会话密钥

- **级别**：High
- **状态**：未修复
- **边界说明**：修复只能增加恶意本机模组的提取成本，不能阻止完全控制客户端的攻击者。

#### 证据

- [`MMDSyncNativeBridge.unwrapHandshakeKey()`](../versions/1.21.1/common/src/main/java/com/tendoarisu/mmdskin/sync/util/MMDSyncNativeBridge.java:20)
- [`Java_com_tendoarisu_mmdskin_sync_util_MMDSyncNativeBridge_unwrapHandshakeKey()`](../../mmdsync-bridge/mmdsync-bridge/src/lib.rs:230)

#### 修复要求

- [ ] 从 90-JNI Java 声明和 native 导出中移除原始密钥返回接口。
- [ ] 从 79-JNI Java 声明和 native 导出中移除原始密钥返回接口。
- [ ] 正常流程仅允许 native 内部安装、使用、轮换和清除密钥。
- [ ] CI 审计两个 ABI 的导出表，禁止接口重新出现。
- [ ] 不得为了统一接口破坏 90-JNI 与 79-JNI 的严格隔离。

---

### SEC-H04：资源元数据、分块、ACK 和 ABORT 未做会话 MAC 或签名

- **级别**：High
- **状态**：未修复

项目存在 HMAC-SHA-256 实现，但当前资源协议没有使用。资源操作码、服务器身份、transfer ID、资源路径、块序号、块总数、总长度和摘要之间没有统一的密码学绑定。

#### 证据

- [`CryptoUtils.signSyncEnvelope()`](../versions/1.21.1/common/src/main/java/com/tendoarisu/mmdskin/sync/util/CryptoUtils.java:120)
- [`ResourceTransferClientManager.acceptPacket()`](../versions/1.21.1/common/src/main/java/com/opdent/mmdskin/sync/network/resource/ResourceTransferClientManager.java:24)
- [`ResourceTransferClientManager.TransferSession.acceptChunk()`](../versions/1.21.1/common/src/main/java/com/opdent/mmdskin/sync/network/resource/ResourceTransferClientManager.java:180)

#### 修复要求

- [ ] 为两个传输方向派生不同的 MAC 或 AEAD 密钥。
- [ ] 认证操作码、协议版本、连接 ID、真实玩家 UUID、服务器身份和 transfer ID。
- [ ] 认证资源 ID、规范路径、块序号、块总数、总长度和内容摘要。
- [ ] ACK、ABORT、BEGIN、CHUNK 和 FINISH 全部进入认证状态机。
- [ ] 拒绝重复块、越序块、缺块、冲突元数据和旧会话重放。
- [ ] 仅在完整认证通过后提交最终文件。

---

## 5. Medium 问题

### SEC-M01：握手、下载和上传缺少完整 DoS 防护

- **状态**：未修复

风险包括：重复触发 RSA-2048 确定性生成、完整文件一次性读入内存、完整加密副本、单会话最高 512 MiB、缺少玩家级和全局并发限制、缺少失败退避及统一日志限速。

#### 修复要求

- [ ] 握手按玩家和 IP 使用令牌桶及失败退避。
- [ ] 下载改为流式读取和流式加密。
- [ ] 限制每玩家和全局活动传输数。
- [ ] 限制单文件、单次、每日和 staging 总容量。
- [ ] 为空闲会话设置超时，并定时回收。
- [ ] 对重复失败日志进行限速和聚合。

### SEC-M02：native 提取与加载缺少独立发布签名验证

- **状态**：未修复

当前 native 自哈希只能确认当前加载文件的内容，不能证明该文件来自可信发布者，也不能完全消除提取目录替换、符号链接和 TOCTOU 风险。

#### 证据

- [`MMDSyncNativeLoader.extractLibrary()`](../versions/1.21.1/common/src/main/java/com/tendoarisu/mmdskin/sync/util/MMDSyncNativeLoader.java:139)
- [`MMDSyncNativeLoader.extractLibrary()`](../../MmdSkin-Bukkit/src/main/java/com/tendoarisu/mmdskin/sync/util/MMDSyncNativeLoader.java:65)

#### 修复要求

- [ ] 使用 owner-only 的版本化安全目录。
- [ ] 拒绝符号链接和非普通文件。
- [ ] 先写随机临时文件，验证签名后原子重命名。
- [ ] 使用独立固定公钥验证 Ed25519 发布签名。
- [ ] 分别验证 90-JNI 与 79-JNI 的 ABI、摘要和导出表。

### SEC-M03：上传明文进入网络与 staging，崩溃可能遗留 `.part`

- **状态**：未修复

#### 证据

- [`ResourceUploadSession.append()`](../../MmdSkin-Bukkit/src/main/java/com/opdent/mmdskin/bukkit/resource/ResourceUploadSession.java:121)
- [`MmdSkinBukkit.beginResourceUpload()`](../../MmdSkin-Bukkit/src/main/java/com/opdent/mmdskin/bukkit/MmdSkinBukkit.java:775)
- [`ResourceTransferClientManager.TransferSession.getCiphertextCacheRoot()`](../versions/1.21.1/common/src/main/java/com/opdent/mmdskin/sync/network/resource/ResourceTransferClientManager.java:223)

#### 修复要求

- [ ] 上传方向也使用会话 AEAD。
- [ ] staging 目录设置 owner-only 权限。
- [ ] 启动时清理过期 `.part` 文件。
- [ ] staging 排除普通未加密备份。
- [ ] 客户端密文缓存设置容量和保存期限。
- [ ] 清理逻辑必须保证不会删除资源根目录外文件。

### SEC-M04：下载摘要使用 MD5，静态秘密只做快速 SHA-256

- **状态**：未修复

MD5 只能作为非对抗环境下的损坏检测，不能用作来源认证。若管理员手工配置低熵静态口令，单次 SHA-256 不能抵抗离线字典攻击。

#### 证据

- [`BukkitResourceTransferCodec.ManifestEntry`](../../MmdSkin-Bukkit/src/main/java/com/opdent/mmdskin/bukkit/resource/BukkitResourceTransferCodec.java:43)
- [`MmdSkinBukkit.md5Hex()`](../../MmdSkin-Bukkit/src/main/java/com/opdent/mmdskin/bukkit/MmdSkinBukkit.java:1060)
- [`SyncAuthSupport.deriveSyncKey()`](../../MmdSkin-Bukkit/src/main/java/com/opdent/mmdskin/bukkit/SyncAuthSupport.java:46)

#### 修复要求

- [ ] 内容摘要统一改为 SHA-256 或 BLAKE3，并修正字段命名。
- [ ] 来源真实性由会话 MAC 或签名提供，不依赖裸摘要。
- [ ] 静态根密钥保持随机 256 位，不接受短口令作为等价输入。
- [ ] 如必须支持口令，使用 Argon2id 或 scrypt 加随机盐，再进入 HKDF。

### SEC-M05：服务器静态秘密、明文资源和备份权限依赖宿主默认值

- **状态**：未修复

#### 证据

- [`config.yml`](../../MmdSkin-Bukkit/src/main/resources/config.yml:20)
- [`MmdSkinBukkit.loadSecurityMaterial()`](../../MmdSkin-Bukkit/src/main/java/com/opdent/mmdskin/bukkit/MmdSkinBukkit.java:401)
- [`Config.save()`](../versions/1.21.1/common/src/main/java/com/tendoarisu/mmdskin/sync/Config.java:69)

#### 修复要求

- [ ] 尽力设置 POSIX 0600 或 Windows owner-only ACL。
- [ ] 发现组用户或其他用户可读时警告或拒绝启动。
- [ ] 密钥与资源备份分离并加密。
- [ ] 记录密钥 epoch，支持轮换与泄漏响应。
- [ ] 文档明确：完全被攻陷的服务端天然拥有资源明文和密钥，客户端侧无法防御。

---

## 6. Low 与兼容性问题

### SEC-L01：日志泄露路径和资源元数据

- [ ] 默认不记录资源绝对路径。
- [ ] 资源名优先记录规范化 ID 或摘要前缀。
- [ ] 握手和资源错误按玩家、IP 和原因限速。
- [ ] 敏感诊断仅在显式 debug 模式启用。
- [ ] 继续禁止记录静态秘密、会话密钥、RSA 私钥和完整 challenge。

### SEC-L02：旧协议和 ACK 降级缺少认证语义

- [ ] legacy 通道继续默认关闭。
- [ ] 协议降级必须显式配置并记录安全告警。
- [ ] 不允许从已认证协议静默降级到无认证协议。
- [ ] ACK 兼容模式不得绕过权限、会话绑定和完整性检查。
- [ ] Sync 内置服务端上传会话键应绑定玩家 UUID 和连接，而不是只使用 transfer ID。

---

## 7. 不构成密码学边界的现有机制

以下机制只能增加逆向成本：

- HWID 绑定，因为 HWID 由客户端自报；
- 固定盐和字符串混淆；
- debugger、TracerPid 等反调试检查；
- 会话密钥分片和同进程掩码；
- native 自哈希但没有独立发布签名；
- LTO、strip 和符号隐藏；
- 把明文处理移动到 JNI；
- 移除原始密钥 JNI 接口。

相关证据：

- [`reject_observed_sensitive_path()`](../../mmdsync-bridge/mmdsync-bridge/src/lib.rs:294)
- [`derive_session_mask_block()`](../../mmdsync-bridge/mmdsync-bridge/src/lib.rs:359)
- [`CryptoUtils.getIntegrityHash()`](../versions/1.21.1/common/src/main/java/com/tendoarisu/mmdskin/sync/util/CryptoUtils.java:222)
- [`Cargo.toml`](../../mmdsync-bridge/mmdsync-bridge/Cargo.toml:35)

这些机制可以作为纵深防御保留，但安全设计和验收不能以它们为核心依据。

---

## 8. 当前可防御和不可防御的攻击者

### 当前能够一定程度防御

- 只会直接浏览客户端资源目录和缓存目录的低能力用户。
- 不持有密钥者对 AES-GCM 密文的随机修改。
- 服务端上传方向常见 Zip Slip、绝对路径、符号链接逃逸、乱序分块和摘要不一致。
- 对已经被成功消费的 C2S challenge 进行简单重复提交。

### 当前不能防御

- 被动抓包者，因为确定性 RSA 私钥可从公开输入重建。
- 任意合法在线玩家对无权限资源端点的调用。
- 恶意代理或受损代理链，因为没有独立服务器签名和完整资源 MAC。
- 同进程恶意模组，因为客户端具有解密能力且当前存在原始密钥 JNI。
- 合法客户端的解密后二次分发。
- 恶意或完全被攻陷的 Bukkit、Fabric 或 NeoForge 服务端。
- 能读取服务端资源目录、配置或备份的攻击者。
- 多服务器复用同一静态秘密造成的跨服泄漏。
- 客户端运行时内存 DUMP；这是本文明确接受的不可彻底防御边界。

---

## 9. 推荐实施顺序

### 第一阶段：立即阻断高危攻击面

- [ ] 修复客户端服务端下发路径越界。
- [ ] 资源端点强制认证与权限检查。
- [ ] 加密错误改为严格 fail closed。
- [ ] 增加上传、下载和握手限速及配额。
- [ ] 服务端配置和 staging 权限加固。

### 第二阶段：替换握手和密钥体系

- [ ] 设计协议 v2，不与现有不安全握手静默兼容。
- [ ] 引入服务器长期 Ed25519 身份。
- [ ] 引入客户端临时 X25519/ECDH。
- [ ] 使用 HKDF-SHA-256 派生方向、连接、玩家和资源密钥。
- [ ] 为 MMDARC v2 增加 AAD 上下文和密钥 epoch。
- [ ] 加入握手与资源重放状态机。

### 第三阶段：native 与协议纵深加固

- [ ] 移除两套 ABI 的原始密钥导出接口。
- [ ] native 加入独立发布签名与加载前验证。
- [ ] 资源分块、ACK、ABORT 接入会话 MAC/AEAD。
- [ ] 上传方向启用会话 AEAD。
- [ ] 改造流式加密、暂存和缓存生命周期。

### 第四阶段：发布与迁移

- [ ] 同时完成 1.21.1 Fabric、1.21.1 NeoForge、1.21.4 Fabric 和 Bukkit 协议 v2 支持。
- [ ] 明确拒绝不安全协议降级，或设置短期、显式、可审计的迁移窗口。
- [ ] 编写服务端根密钥和身份密钥迁移说明。
- [ ] 发布前执行双 ABI、协议互操作、攻击面和回滚测试。

---

## 10. 必须补充的安全回归测试

### 10.1 bridge 密码学测试

- [ ] AES-GCM 已知答案测试。
- [ ] 标签、nonce、版本、头部和密文篡改必须失败。
- [ ] 加密错误必须返回显式失败，禁止返回原输入。
- [ ] 大样本 nonce 唯一性统计回归。
- [ ] HKDF 上下文改变后必须得到不同密钥。
- [ ] AAD 中服务器、玩家、路径或 epoch 改变后解密必须失败。

### 10.2 握手互操作与保密性测试

- [ ] 90-JNI Windows 测试向量。
- [ ] 90-JNI Linux 测试向量。
- [ ] 79-JNI Windows 测试向量。
- [ ] 79-JNI Linux 测试向量。
- [ ] 仅持有抓包 transcript 的测试主体不能恢复会话密钥。
- [ ] 未知服务器签名、错误玩家、错误连接、过期或重复响应必须拒绝。
- [ ] 协议版本和服务器身份必须进入签名 transcript。

### 10.3 资源访问控制测试

- [ ] 未认证玩家请求清单必须拒绝。
- [ ] 未认证玩家下载必须拒绝。
- [ ] 未认证玩家上传必须拒绝。
- [ ] 已认证但无上传权限的玩家必须拒绝。
- [ ] 权限撤销、配置重载和退出后活动传输必须终止。
- [ ] 每玩家和全局配额必须生效。

### 10.4 客户端路径安全测试

- [ ] `..` 路径拒绝。
- [ ] 绝对路径拒绝。
- [ ] Windows 盘符和 UNC 路径拒绝。
- [ ] 反斜杠、空段、NUL 和超长 Unicode 路径拒绝。
- [ ] 符号链接祖先拒绝。
- [ ] 所有失败场景均不得创建根目录外文件。

### 10.5 客户端分块状态机测试

- [ ] 重复块拒绝。
- [ ] 越序块拒绝。
- [ ] 缺块拒绝。
- [ ] 冲突块总数、总大小和摘要拒绝。
- [ ] 错误服务器 ID、transfer ID 和资源 ID 拒绝。
- [ ] 伪造 ACK、ABORT、BEGIN 和 FINISH 拒绝。
- [ ] 失败传输不得覆盖最终文件。

### 10.6 故障注入与 DoS 测试

- [ ] native 缺失时不得发送受保护资源明文。
- [ ] JNI 抛错、随机源失败、错误密钥长度时必须 fail closed。
- [ ] 并发握手、上传和下载保持 CPU、内存和磁盘有界。
- [ ] 空闲上传会话按时回收。
- [ ] 崩溃遗留 `.part` 在启动时安全清理。
- [ ] 重复错误不能造成无限日志洪泛。

### 10.7 双 ABI 发布测试

- [ ] 1.21.1 Fabric 只能包含 90-JNI native。
- [ ] 1.21.1 NeoForge 只能包含 90-JNI native。
- [ ] 1.21.4 Fabric 只能包含 79-JNI native。
- [ ] Bukkit 运行时只能包含并加载 79-JNI native。
- [ ] Bukkit 可保留 90-JNI 客户端 profile 哈希，但不得加载 90-JNI binary。
- [ ] CI 固定并比较两套 JNI 导出表、native 摘要和签名。

---

## 11. 版本与 ABI 约束

当前发行边界：

| Minecraft | Loader | JNI ABI | 状态 |
|---|---|---:|---|
| 1.21.1 | Fabric | 90-JNI | 支持 |
| 1.21.1 | NeoForge | 90-JNI | 支持 |
| 1.21.4 | Fabric | 79-JNI | 支持 |
| 1.21.4 | NeoForge | 不适用 | 不支持 |
| Bukkit runtime | Bukkit/Paper | 79-JNI | 支持 |

证据见 [`versions/README.md`](../versions/README.md:7)。

后续安全改造必须遵守：

- 90-JNI 与 79-JNI 分别维护 Java 声明、Rust 导出表、native 文件、摘要和签名。
- 不得为了统一安全接口而复制或混用两个 ABI 的 native。
- Bukkit 仍只加载 79-JNI binary。
- Bukkit 可以识别 1.21.1/90-JNI 客户端认证 profile，但该 profile 的哈希只作为协议身份信息，不能被当作秘密或密钥材料。
- 每次发布必须分别验证三个客户端产物和一个 Bukkit 产物。

---

## 12. 修复完成验收条件

只有同时满足下列条件，才可将本安全待办标记为完成：

- [ ] 被动观察完整握手不能恢复服务器根密钥、资源密钥或会话密钥。
- [ ] 未认证玩家无法列出、下载或上传资源。
- [ ] 无权限玩家无法上传或覆盖服务端资源。
- [ ] 恶意服务器路径不能在客户端资源根目录外创建或修改文件。
- [ ] 加密失败时绝不发送或落盘伪装成密文的明文资源。
- [ ] 旧握手、旧资源包和旧 ACK 不能跨连接、服务器、玩家或 epoch 重放。
- [ ] 单个客户端泄漏不会自动暴露其他玩家、其他服务器 epoch 或所有资源的长期密钥。
- [ ] 上传、下载和握手均有可验证的速率、并发和容量边界。
- [ ] 90-JNI 与 79-JNI 隔离测试全部通过。
- [ ] 安全回归测试进入默认构建和发布审计流程。
- [ ] 文档明确说明仍然无法彻底防御内存 DUMP、完全受控合法客户端和被攻陷服务端。

---

## 13. 风险跟踪表

| ID | 级别 | 问题 | 状态 | 优先阶段 |
|---|---|---|---|---|
| SEC-C01 | Critical | 确定性 RSA 私钥可重建 | 待修复 | 第二阶段 |
| SEC-C02 | Critical | 资源接口无认证与权限 | 待修复 | 第一阶段 |
| SEC-C03 | Critical | 客户端路径越界写入 | 待修复 | 第一阶段 |
| SEC-H01 | High | 全服长期共享密钥 | 待修复 | 第二阶段 |
| SEC-H02 | High | 加密失败明文降级 | 待修复 | 第一阶段 |
| SEC-H03 | High | JNI 导出原始会话密钥 | 待修复 | 第三阶段 |
| SEC-H04 | High | 资源协议无会话 MAC | 待修复 | 第三阶段 |
| SEC-M01 | Medium | DoS 限额不足 | 待修复 | 第一阶段 |
| SEC-M02 | Medium | native 缺少发布签名 | 待修复 | 第三阶段 |
| SEC-M03 | Medium | 上传明文和暂存残留 | 待修复 | 第三阶段 |
| SEC-M04 | Medium | MD5 与弱口令派生风险 | 待修复 | 第二阶段 |
| SEC-M05 | Medium | 配置和备份权限不足 | 待修复 | 第一阶段 |
| SEC-L01 | Low | 日志元数据泄漏 | 待修复 | 第一阶段 |
| SEC-L02 | Low | 旧协议降级和会话差异 | 待修复 | 第二阶段 |

---

## 14. 最终判断

当前 AES-256-GCM 文件加密的算法和 nonce 长度本身没有发现明显错误，但整体安全体系受到握手可重建、长期共享密钥、资源接口无访问控制、客户端路径信任和明文降级的破坏。

在完成修复前，现有方案应被定义为：

> 能阻止低能力用户直接浏览离线缓存，并增加普通逆向提取成本；不能形成抵抗被动抓包、合法客户端、恶意模组、恶意代理或被攻陷服务器的完整端到端密码学边界。

后续修复应优先建立正确的信任边界、认证、授权和安全路径，再进行 native 混淆或反逆向加固。