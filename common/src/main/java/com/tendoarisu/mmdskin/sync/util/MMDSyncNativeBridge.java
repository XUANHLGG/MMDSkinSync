package com.tendoarisu.mmdskin.sync.util;

/**
 * 辅助 Native 库的桥接类
 * 仅用于 MMDSync 内部，负责从内存字节数组加载模型和贴图
 * 此库为非开源部分（或独立编译），不影响核心 Mod 的开源协议
 */
public class MMDSyncNativeBridge {
    static {
        // 使用多平台加载器加载库
        MMDSyncNativeLoader.load();
    }

    /**
     * 从内存加载 PMX 模型
     * @param data 模型字节数据
     * @param dir 资源目录（用于定位纹理）
     * @param layerCount 图层数量
     * @return 模型句柄
     */
    public static native long loadModelFromMemory(byte[] data, String dir, long layerCount);

    /**
     * 直接从加密文件加载 PMX 模型，解密与明文解析全在 Native 内完成。
     */
    public static native long loadEncryptedModelFromFile(String path, String dir, long layerCount);

    /**
     * 从内存加载贴图
     * @param data 贴图字节数据
     * @return 贴图句柄
     */
    public static native long loadTextureFromMemory(byte[] data);

    /**
     * 直接从加密文件加载贴图，解密与明文解析全在 Native 内完成。
     */
    public static native long loadEncryptedTextureFromFile(String path);

    /**
     * 从内存加载 VMD 动画
     * @param data 动画字节数据
     * @return 动画句柄
     */
    public static native long loadVMDFromMemory(byte[] data);

    /**
     * 直接从加密文件加载 VMD 动画，解密与明文解析全在 Native 内完成。
     */
    public static native long loadEncryptedVMDFromFile(String path);

    /**
     * 获取当前设备的硬件指纹 (HWID)
     * 用于进阶加密，将加密文件绑定到特定设备
     * @return 硬件指纹字符串
     */
    public static native String getHardwareId();

    /**
     * 派生当前握手上下文对应的公钥材料。
     * @param serverSecret 服务器私密盐 (由服务端下发)
     * @return PEM 格式的公钥
     */
    public static native String deriveHandshakePem(String serverSecret, byte[] targetHash, String clientHwid);

    /**
     * 解包握手阶段下发的会话材料。
     * @param encryptedAesKeyBase64 Base64 格式的封装会话材料
     * @param serverSecret 服务器私密盐 (由服务端下发)
     * @param targetHash 目标 Native 库哈希（必须与服务端生成公钥时使用的哈希一致）
     * @param clientHwid 客户端硬件指纹 (由 Java 层获取传入，避免 Native 层获取导致崩溃)
     * @return 原始会话材料 (32字节)
     */
    public static native byte[] unwrapHandshakeKey(String encryptedAesKeyBase64, String serverSecret, byte[] targetHash, String clientHwid);

    /**
     * 解包并仅在 Native 内保存当前会话材料，不再把字节返回到 Java。
     */
    public static native boolean installSessionMaterial(String encryptedAesKeyBase64, String serverSecret, byte[] targetHash, String clientHwid);

    /**
     * Native 侧是否已持有当前会话材料。
     */
    public static native boolean hasSessionMaterial();

    /**
     * 清空 Native 侧当前会话材料。
     */
    public static native boolean clearSessionMaterial();

    /**
     * 校验模型句柄是否仍属于当前 Native 会话代际。
     * 对于非 MMDSync bridge 跟踪的句柄，Native 侧会按“允许通过”处理。
     */
    public static native boolean isModelHandleValid(long handle);

    /**
     * 校验动画句柄是否仍属于当前 Native 会话代际。
     * 对于非 MMDSync bridge 跟踪的句柄，Native 侧会按“允许通过”处理。
     */
    public static native boolean isAnimationHandleValid(long handle);

    /**
     * 校验贴图句柄是否仍属于当前 Native 会话代际。
     * 对于非 MMDSync bridge 跟踪的句柄，Native 侧会按“允许通过”处理。
     */
    public static native boolean isTextureHandleValid(long handle);

    /**
     * 使用 Native 内保存的会话材料生成同步请求 HMAC。
     */
    public static native String signSyncEnvelope(String token, String ts, String nonce, String method, String rawPath);

    /**
     * 从文件计算“解密后的明文 MD5”；若文件未加密，则计算原文件 MD5。
     */
    public static native String getPlaintextMd5FromFile(String path);

    /**
     * 获取 Native 核心库自身的 SHA256 哈希值
     * 用于服务器校验客户端 Native 库是否被篡改
     * @return 16进制哈希字符串
     */
    public static native String getLibraryHash();

    /**
     * 计算 Java 类文件的 SHA256 哈希值
     * @param classBytes 类文件的字节流
     * @return 16进制哈希字符串
     */
    public static native String getClassHash(byte[] classBytes);

    // 已移除冗余的 rsaEncrypt，服务器端直接用 Java 实现，客户端仅保留解密逻辑在 Native 以绑定 HWID

    /**
     * 使用 AES-GCM 加密模型数据
     * @param data 原始模型数据
     * @param aesKey AES 密钥
     * @return 加密后的模型数据 (包含 Nonce)
     */
    public static native byte[] aesEncrypt(byte[] data, byte[] aesKey);

    /**
     * 使用 AES-GCM 解密模型数据
     * @param encryptedData 加密后的模型数据
     * @param aesKey 已经解密出的原始 AES 密钥
     * @return 原始模型数据
     */
    public static native byte[] aesDecrypt(byte[] encryptedData, byte[] aesKey);
}
