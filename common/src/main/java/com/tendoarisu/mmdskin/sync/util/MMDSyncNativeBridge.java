package com.tendoarisu.mmdskin.sync.util;

import java.nio.ByteBuffer;

/**
 * 辅助 Native 库的桥接类
 * 仅用于 MMDSync 内部，负责从内存字节数组加载模型和贴图
 * 此库为非开源部分（或独立编译），不影响核心 Mod 的开源协议
 */
public class MMDSyncNativeBridge {
    private static final long BRIDGE_HANDLE_MASK = 1L << 62;

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
     * 对于非 MMDSync bridge 跟踪的句柄，Native 侧会按“拒绝通过”处理。
     */
    public static native boolean isModelHandleValid(long handle);

    /**
     * 校验动画句柄是否仍属于当前 Native 会话代际。
     * 对于非 MMDSync bridge 跟踪的句柄，Native 侧会按“拒绝通过”处理。
     */
    public static native boolean isAnimationHandleValid(long handle);

    /**
     * 校验贴图句柄是否仍属于当前 Native 会话代际。
     * 对于非 MMDSync bridge 跟踪的句柄，Native 侧会按“拒绝通过”处理。
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

    // --- 以下为代理主 Mod GPU Skinning 所需的接口 ---

    public static native long getVertexCount(long model);
    public static native long getIndexElementSize(long model);
    public static native long getIndexCount(long model);
    public static native long getIndices(long model);
    public static native int copyDataToByteBuffer(ByteBuffer buffer, long data, long size);
    public static native int copyOriginalPositionsToBuffer(long model, ByteBuffer buffer, int vertexCount);
    public static native int copyOriginalNormalsToBuffer(long model, ByteBuffer buffer, int vertexCount);
    public static native long getUVs(long model);
    public static native int copyBoneIndicesToBuffer(long model, ByteBuffer buffer, int vertexCount);
    public static native int copyBoneWeightsToBuffer(long model, ByteBuffer buffer, int vertexCount);
    public static native int getBoneCount(long model);
    public static native String getBoneNames(long model);
    public static native int copyBonePositionsToBuffer(long model, ByteBuffer buffer);
    public static native int copyRealtimeUVsToBuffer(long model, ByteBuffer buffer);
    public static native int copySkinningMatricesToBuffer(long model, ByteBuffer buffer);
    public static native void updateAnimationOnly(long model, float deltaTime);
    public static native long getMaterialCount(long model);
    public static native String getMaterialTex(long model, long pos);
    public static native long getSubMeshCount(long model);
    public static native int batchGetSubMeshData(long model, ByteBuffer buffer);
    public static native void deleteModel(long model);

    // --- 动画桥接 ---
    public static native void changeModelAnim(long model, long anim, long layer);
    public static native void transitionLayerTo(long model, long anim, long layer, float transitionTime);
    public static native void setLayerLoop(long model, long layer, boolean loop);
    public static native void resetPhysics(long model);

    // --- 第一人称桥接 ---
    public static native void setFirstPersonMode(long model, boolean enabled);
    public static native boolean isFirstPersonMode(long model);
    public static native void getEyeBonePosition(long model, float[] out);

    // --- 头部/眼部桥接 ---
    public static native void setHeadAngle(long model, float headX, float headY, float headZ, boolean isHeadInSync);
    public static native void setEyeAngle(long model, float eyeX, float eyeY);
    public static native void setEyeMaxAngle(long model, float maxAngle);
    public static native void setEyeTrackingEnabled(long model, boolean enabled);
    public static native void setAutoBlinkEnabled(long model, boolean enabled);
    public static native void seekLayer(long model, long layer, float frame);

    // --- 动画/镜头桥接 ---
    public static native void deleteAnimation(long anim);
    public static native boolean hasCameraData(long anim);
    public static native float getAnimMaxFrame(long anim);
    public static native void getCameraTransform(long anim, float frame, ByteBuffer buffer);

    // --- 场景模型桥接 ---
    public static native void setModelPositionAndYaw(long model, float posX, float posY, float posZ, float yaw);

    // --- VR 桥接 ---
    public static native void setVRTrackingData(long model, float[] trackingData);
    public static native void setVREnabled(long model, boolean enabled);
    public static native void setVRIKParams(long model, float armIKStrength);

    // --- 材质可见性桥接 ---
    public static native boolean isMaterialVisible(long model, int index);
    public static native void setMaterialVisible(long model, int index, boolean visible);
    public static native void setAllMaterialsVisible(long model, boolean visible);
    public static native String getMaterialName(long model, int index);

    // --- Morph / VPD 桥接 ---
    public static native void resetAllMorphs(long model);
    public static native int applyVpdMorph(long model, String filePath);

    // --- 贴图桥接 ---
    public static native int getTextureX(long tex);
    public static native int getTextureY(long tex);
    public static native long getTextureData(long tex);
    public static native boolean textureHasAlpha(long tex);
    public static native void deleteTexture(long tex);

    /**
     * 判断句柄是否为 Bridge 句柄 (仅根据 Java 侧 tag 位判断)。
     */
    public static boolean isBridgeHandle(long handle) {
        return (handle & BRIDGE_HANDLE_MASK) != 0;
    }

    /**
     * 为 Bridge 句柄打 tag，避免和上游 Native 句柄混用。
     */
    public static long tagBridgeHandle(long handle) {
        if (handle == 0L) {
            return 0L;
        }
        return handle | BRIDGE_HANDLE_MASK;
    }

    /**
     * 去掉 Bridge 句柄 tag，便于桥接层内部继续使用原始句柄。
     */
    public static long stripBridgeHandle(long handle) {
        return handle & ~BRIDGE_HANDLE_MASK;
    }
}
