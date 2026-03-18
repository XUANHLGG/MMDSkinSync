package com.tendoarisu.mmdskin.sync.mixin;

import com.opdent.mmdskin.sync.MMDSyncMod;
import com.shiroha.mmdskin.NativeFunc;
import com.tendoarisu.mmdskin.sync.util.CryptoUtils;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.File;
import java.nio.ByteBuffer;

@Mixin(targets = "com.shiroha.mmdskin.renderer.runtime.model.gpu.MMDModelGpuSkinning", remap = false)
public class MixinMMDModelGpuSkinning {

    private static boolean useBridge(long model) {
        return model != 0L && MMDSyncNativeBridge.isBridgeHandle(model);
    }

    private static boolean useBridgeData(long dataPtr) {
        return dataPtr != 0L && MMDSyncNativeBridge.isBridgeHandle(dataPtr);
    }

    @Redirect(
        method = "Create",
        at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;LoadModelPMX(Ljava/lang/String;Ljava/lang/String;J)J"),
        remap = false
    )
    private static long redirectLoadModelPMX(NativeFunc instance, String filename, String dir, long layerCount) {
        File file = new File(filename);
        if (!CryptoUtils.isEncrypted(file)) {
            return instance.LoadModelPMX(filename, dir, layerCount);
        }

        MMDSyncMod.LOGGER.info("已拦截 GPU PMX 加密模型加载: {}", filename);
        long handle = MMDSyncNativeBridge.loadEncryptedModelFromFile(filename, dir, layerCount);
        if (handle == 0L && !CryptoUtils.hasSessionMaterial()) {
            if (!CryptoUtils.waitForSessionMaterial(2000L)) {
                MMDSyncMod.LOGGER.warn("GPU 加载加密模型等待 SessionKey 仍未就绪: {}", filename);
            }
            handle = MMDSyncNativeBridge.loadEncryptedModelFromFile(filename, dir, layerCount);
        }
        if (handle == 0L) {
            MMDSyncMod.LOGGER.error("GPU 加载加密模型失败: Native 返回空句柄: {}", filename);
        }
        return handle;
    }

    @Redirect(
        method = "Create",
        at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;LoadModelPMD(Ljava/lang/String;Ljava/lang/String;J)J"),
        remap = false
    )
    private static long redirectLoadModelPMD(NativeFunc instance, String filename, String dir, long layerCount) {
        File file = new File(filename);
        if (!CryptoUtils.isEncrypted(file)) {
            return instance.LoadModelPMD(filename, dir, layerCount);
        }

        MMDSyncMod.LOGGER.info("已拦截 GPU PMD 加密模型加载: {}", filename);
        long handle = MMDSyncNativeBridge.loadEncryptedModelFromFile(filename, dir, layerCount);
        if (handle == 0L && !CryptoUtils.hasSessionMaterial()) {
            if (!CryptoUtils.waitForSessionMaterial(2000L)) {
                MMDSyncMod.LOGGER.warn("GPU 加载加密模型等待 SessionKey 仍未就绪: {}", filename);
            }
            handle = MMDSyncNativeBridge.loadEncryptedModelFromFile(filename, dir, layerCount);
        }
        if (handle == 0L) {
            MMDSyncMod.LOGGER.error("GPU 加载加密模型失败: Native 返回空句柄: {}", filename);
        }
        return handle;
    }

    @Redirect(
        method = "Create",
        at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;DeleteModel(J)V"),
        remap = false
    )
    private static void redirectDeleteModel(NativeFunc instance, long model) {
        if (useBridge(model)) {
            MMDSyncNativeBridge.deleteModel(model);
            return;
        }
        instance.DeleteModel(model);
    }

    @Redirect(
        method = "createFromHandle",
        at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;InitGpuSkinningData(J)V"),
        remap = false
    )
    private static void redirectInitGpuSkinningData(NativeFunc instance, long model) {
        if (useBridge(model)) {
            return;
        }
        instance.InitGpuSkinningData(model);
    }

    @Redirect(
        method = "createFromHandle",
        at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;GetVertexCount(J)J"),
        remap = false
    )
    private static long redirectGetVertexCount(NativeFunc instance, long model) {
        if (useBridge(model)) {
            return MMDSyncNativeBridge.getVertexCount(model);
        }
        return instance.GetVertexCount(model);
    }

    @Redirect(
        method = "createFromHandle",
        at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;GetBoneCount(J)I"),
        remap = false
    )
    private static int redirectGetBoneCount(NativeFunc instance, long model) {
        if (useBridge(model)) {
            return MMDSyncNativeBridge.getBoneCount(model);
        }
        return instance.GetBoneCount(model);
    }

    @Redirect(
        method = "createFromHandle",
        at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;GetIndexElementSize(J)J"),
        remap = false
    )
    private static long redirectGetIndexElementSize(NativeFunc instance, long model) {
        if (useBridge(model)) {
            return MMDSyncNativeBridge.getIndexElementSize(model);
        }
        return instance.GetIndexElementSize(model);
    }

    @Redirect(
        method = "createFromHandle",
        at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;GetIndexCount(J)J"),
        remap = false
    )
    private static long redirectGetIndexCount(NativeFunc instance, long model) {
        if (useBridge(model)) {
            return MMDSyncNativeBridge.getIndexCount(model);
        }
        return instance.GetIndexCount(model);
    }

    @Redirect(
        method = "createFromHandle",
        at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;GetIndices(J)J"),
        remap = false
    )
    private static long redirectGetIndices(NativeFunc instance, long model) {
        if (useBridge(model)) {
            return MMDSyncNativeBridge.getIndices(model);
        }
        return instance.GetIndices(model);
    }

    @Redirect(
        method = "createFromHandle",
        at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;CopyDataToByteBuffer(Ljava/nio/ByteBuffer;JJ)V"),
        remap = false
    )
    private static void redirectCopyDataToByteBuffer(NativeFunc instance, ByteBuffer buffer, long data, long size) {
        if (useBridgeData(data)) {
            MMDSyncNativeBridge.copyDataToByteBuffer(buffer, data, size);
            return;
        }
        instance.CopyDataToByteBuffer(buffer, data, size);
    }

    @Redirect(
        method = "createFromHandle",
        at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;CopyOriginalPositionsToBuffer(JLjava/nio/ByteBuffer;I)I"),
        remap = false
    )
    private static int redirectCopyOriginalPositionsToBuffer(NativeFunc instance, long model, ByteBuffer buffer, int vertexCount) {
        if (useBridge(model)) {
            return MMDSyncNativeBridge.copyOriginalPositionsToBuffer(model, buffer, vertexCount);
        }
        return instance.CopyOriginalPositionsToBuffer(model, buffer, vertexCount);
    }

    @Redirect(
        method = "createFromHandle",
        at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;CopyOriginalNormalsToBuffer(JLjava/nio/ByteBuffer;I)I"),
        remap = false
    )
    private static int redirectCopyOriginalNormalsToBuffer(NativeFunc instance, long model, ByteBuffer buffer, int vertexCount) {
        if (useBridge(model)) {
            return MMDSyncNativeBridge.copyOriginalNormalsToBuffer(model, buffer, vertexCount);
        }
        return instance.CopyOriginalNormalsToBuffer(model, buffer, vertexCount);
    }

    @Redirect(
        method = "createFromHandle",
        at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;GetUVs(J)J"),
        remap = false
    )
    private static long redirectGetUVs(NativeFunc instance, long model) {
        if (useBridge(model)) {
            return MMDSyncNativeBridge.getUVs(model);
        }
        return instance.GetUVs(model);
    }

    @Redirect(
        method = "createFromHandle",
        at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;CopyBoneIndicesToBuffer(JLjava/nio/ByteBuffer;I)I"),
        remap = false
    )
    private static int redirectCopyBoneIndicesToBuffer(NativeFunc instance, long model, ByteBuffer buffer, int vertexCount) {
        if (useBridge(model)) {
            return MMDSyncNativeBridge.copyBoneIndicesToBuffer(model, buffer, vertexCount);
        }
        return instance.CopyBoneIndicesToBuffer(model, buffer, vertexCount);
    }

    @Redirect(
        method = "createFromHandle",
        at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;CopyBoneWeightsToBuffer(JLjava/nio/ByteBuffer;I)I"),
        remap = false
    )
    private static int redirectCopyBoneWeightsToBuffer(NativeFunc instance, long model, ByteBuffer buffer, int vertexCount) {
        if (useBridge(model)) {
            return MMDSyncNativeBridge.copyBoneWeightsToBuffer(model, buffer, vertexCount);
        }
        return instance.CopyBoneWeightsToBuffer(model, buffer, vertexCount);
    }

    @Redirect(
        method = "createFromHandle",
        at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;GetMaterialCount(J)J"),
        remap = false
    )
    private static long redirectGetMaterialCount(NativeFunc instance, long model) {
        if (useBridge(model)) {
            return MMDSyncNativeBridge.getMaterialCount(model);
        }
        return instance.GetMaterialCount(model);
    }

    @Redirect(
        method = "createFromHandle",
        at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;GetMaterialTex(JJ)Ljava/lang/String;"),
        remap = false
    )
    private static String redirectGetMaterialTex(NativeFunc instance, long model, long index) {
        if (useBridge(model)) {
            return MMDSyncNativeBridge.getMaterialTex(model, (int) index);
        }
        return instance.GetMaterialTex(model, index);
    }

    @Redirect(
        method = "createFromHandle",
        at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;InitGpuMorphData(J)V"),
        remap = false
    )
    private static void redirectInitGpuMorphData(NativeFunc instance, long model) {
        if (useBridge(model)) {
            return;
        }
        instance.InitGpuMorphData(model);
    }

    @Redirect(
        method = "createFromHandle",
        at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;GetVertexMorphCount(J)I"),
        remap = false
    )
    private static int redirectGetVertexMorphCount(NativeFunc instance, long model) {
        if (useBridge(model)) {
            return 0;
        }
        return instance.GetVertexMorphCount(model);
    }

    @Redirect(
        method = "createFromHandle",
        at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;InitGpuUvMorphData(J)V"),
        remap = false
    )
    private static void redirectInitGpuUvMorphData(NativeFunc instance, long model) {
        if (useBridge(model)) {
            return;
        }
        instance.InitGpuUvMorphData(model);
    }

    @Redirect(
        method = "createFromHandle",
        at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;GetUvMorphCount(J)I"),
        remap = false
    )
    private static int redirectGetUvMorphCount(NativeFunc instance, long model) {
        if (useBridge(model)) {
            return 0;
        }
        return instance.GetUvMorphCount(model);
    }

    @Redirect(
        method = "createFromHandle",
        at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;GetMaterialMorphResultCount(J)I"),
        remap = false
    )
    private static int redirectGetMaterialMorphResultCount(NativeFunc instance, long model) {
        if (useBridge(model)) {
            return 0;
        }
        return instance.GetMaterialMorphResultCount(model);
    }

    @Redirect(
        method = "createFromHandle",
        at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;GetSubMeshCount(J)J"),
        remap = false
    )
    private static long redirectGetSubMeshCount(NativeFunc instance, long model) {
        if (useBridge(model)) {
            return MMDSyncNativeBridge.getSubMeshCount(model);
        }
        return instance.GetSubMeshCount(model);
    }

    @Redirect(
        method = "createFromHandle",
        at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;SetAutoBlinkEnabled(JZ)V"),
        remap = false
    )
    private static void redirectSetAutoBlinkEnabled(NativeFunc instance, long model, boolean enabled) {
        if (useBridge(model)) {
            return;
        }
        instance.SetAutoBlinkEnabled(model, enabled);
    }

    @Redirect(
        method = "onUpdate",
        at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;UpdateAnimationOnly(JF)V"),
        remap = false
    )
    private static void redirectUpdateAnimationOnly(NativeFunc instance, long model, float deltaTime) {
        if (useBridge(model)) {
            MMDSyncNativeBridge.updateAnimationOnly(model, deltaTime);
            return;
        }
        instance.UpdateAnimationOnly(model, deltaTime);
    }
}
