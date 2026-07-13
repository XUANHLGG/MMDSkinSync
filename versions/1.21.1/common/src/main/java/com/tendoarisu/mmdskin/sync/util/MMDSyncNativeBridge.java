package com.tendoarisu.mmdskin.sync.util;

import java.nio.ByteBuffer;

public class MMDSyncNativeBridge {
    private static final long BRIDGE_HANDLE_MASK = 1L << 62;

    static {
        MMDSyncNativeLoader.load();
    }

    public static native long loadModelFromMemory(byte[] data, String dir, long layerCount);
    public static native long loadEncryptedModelFromFile(String path, String dir, long layerCount);
    public static native long loadTextureFromMemory(byte[] data);
    public static native long loadEncryptedTextureFromFile(String path);
    public static native long loadVMDFromMemory(byte[] data);
    public static native long loadEncryptedVMDFromFile(String path);
    public static native String getHardwareId();
    public static native String deriveHandshakePem(String serverSecret, byte[] targetHash, String clientHwid);
    public static native byte[] unwrapHandshakeKey(String encryptedAesKeyBase64, String serverSecret, byte[] targetHash, String clientHwid);
    public static native boolean installSessionMaterial(String encryptedAesKeyBase64, String serverSecret, byte[] targetHash, String clientHwid);
    public static native boolean hasSessionMaterial();
    public static native boolean clearSessionMaterial();
    public static native boolean isModelHandleValid(long handle);
    public static native boolean isAnimationHandleValid(long handle);
    public static native boolean isTextureHandleValid(long handle);
    public static native String signSyncEnvelope(String token, String ts, String nonce, String method, String rawPath);
    public static native String getPlaintextMd5FromFile(String path);
    public static native String getLibraryHash();
    public static native String getClassHash(byte[] classBytes);
    public static native byte[] aesEncrypt(byte[] data, byte[] aesKey);
    public static native byte[] aesDecrypt(byte[] encryptedData, byte[] aesKey);
    public static native long getVertexCount(long model);
    public static native long getPoss(long model);
    public static native long getNormals(long model);
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
    public static native void updateModel(long model, float deltaTime);
    public static native long getMaterialCount(long model);
    public static native String getMaterialTex(long model, long pos);
    public static native long getSubMeshCount(long model);
    public static native int batchGetSubMeshData(long model, ByteBuffer buffer);
    public static native void deleteModel(long model);
    public static native void changeModelAnim(long model, long anim, long layer);
    public static native void transitionLayerTo(long model, long anim, long layer, float transitionTime);
    public static native void setLayerLoop(long model, long layer, boolean loop);
    public static native void resetPhysics(long model);
    public static native void setFirstPersonMode(long model, boolean enabled);
    public static native boolean isFirstPersonMode(long model);
    public static native void getEyeBonePosition(long model, float[] out);
    public static native void setHeadAngle(long model, float headX, float headY, float headZ, boolean isHeadInSync);
    public static native void setEyeAngle(long model, float eyeX, float eyeY);
    public static native void setEyeMaxAngle(long model, float maxAngle);
    public static native void setEyeTrackingEnabled(long model, boolean enabled);
    public static native void setAutoBlinkEnabled(long model, boolean enabled);
    public static native void seekLayer(long model, long layer, float frame);
    public static native void deleteAnimation(long anim);
    public static native boolean hasCameraData(long anim);
    public static native boolean hasBoneData(long anim);
    public static native boolean hasMorphData(long anim);
    public static native float getAnimMaxFrame(long anim);
    public static native void getCameraTransform(long anim, float frame, ByteBuffer buffer);
    public static native void setModelPositionAndYaw(long model, float posX, float posY, float posZ, float yaw);
    public static native void setVRTrackingData(long model, float[] trackingData);
    public static native void setVREnabled(long model, boolean enabled);
    public static native void setVRIKParams(long model, float armIKStrength);
    public static native boolean isMaterialVisible(long model, int index);
    public static native void setMaterialVisible(long model, int index, boolean visible);
    public static native void setAllMaterialsVisible(long model, boolean visible);
    public static native String getMaterialName(long model, int index);
    public static native int getMorphCount(long model);
    public static native String getMorphName(long model, int index);
    public static native void resetAllMorphs(long model);
    public static native void setMorphWeight(long model, int index, float weight);
    public static native void syncGpuMorphWeights(long model);
    public static native int applyVpdMorph(long model, String filePath);
    public static native boolean mergeAnimation(long targetAnimation, long sourceAnimation);
    public static native boolean setLayerBoneMask(long model, int layer, String rootBoneName);
    public static native boolean setLayerBoneExclude(long model, int layer, String rootBoneName);
    public static native long getModelMemoryUsage(long model);
    public static native void setPhysicsEnabled(long model, boolean enabled);
    public static native boolean isPhysicsEnabled(long model);
    public static native boolean isVrmModel(long model);
    public static native int getTextureX(long tex);
    public static native int getTextureY(long tex);
    public static native long getTextureData(long tex);
    public static native boolean textureHasAlpha(long tex);
    public static native void deleteTexture(long tex);

    public static boolean isBridgeHandle(long handle) {
        return (handle & BRIDGE_HANDLE_MASK) != 0;
    }

    public static long tagBridgeHandle(long handle) {
        if (handle == 0L) {
            return 0L;
        }
        return handle | BRIDGE_HANDLE_MASK;
    }

    public static long stripBridgeHandle(long handle) {
        return handle & ~BRIDGE_HANDLE_MASK;
    }
}
