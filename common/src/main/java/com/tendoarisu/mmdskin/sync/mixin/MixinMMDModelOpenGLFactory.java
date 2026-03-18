package com.tendoarisu.mmdskin.sync.mixin;

import com.opdent.mmdskin.sync.MMDSyncMod;
import com.shiroha.mmdskin.NativeFunc;
import com.tendoarisu.mmdskin.sync.util.CryptoUtils;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.File;

@Mixin(targets = "com.shiroha.mmdskin.renderer.runtime.model.opengl.MMDModelOpenGLFactory", remap = false)
public class MixinMMDModelOpenGLFactory {

    private static boolean useBridge(long model) {
        return model != 0L && MMDSyncNativeBridge.isBridgeHandle(model);
    }

    @Redirect(method = "create", at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;LoadModelPMX(Ljava/lang/String;Ljava/lang/String;J)J"), remap = false)
    private static long redirectLoadModelPMX(NativeFunc instance, String filename, String dir, long layerCount) {
        File file = new File(filename);
        if (!CryptoUtils.isEncrypted(file)) {
            return instance.LoadModelPMX(filename, dir, layerCount);
        }

        MMDSyncMod.LOGGER.info("已拦截 OpenGL PMX 加密模型加载: {}", filename);
        long handle = MMDSyncNativeBridge.loadEncryptedModelFromFile(filename, dir, layerCount);
        if (handle == 0L && !CryptoUtils.hasSessionMaterial()) {
            MMDSyncMod.LOGGER.warn("OpenGL 加载加密 PMX 时 SessionKey 尚未就绪，本次跳过阻塞等待: {}", filename);
        }
        return handle;
    }

    @Redirect(method = "create", at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;LoadModelPMD(Ljava/lang/String;Ljava/lang/String;J)J"), remap = false)
    private static long redirectLoadModelPMD(NativeFunc instance, String filename, String dir, long layerCount) {
        File file = new File(filename);
        if (!CryptoUtils.isEncrypted(file)) {
            return instance.LoadModelPMD(filename, dir, layerCount);
        }

        MMDSyncMod.LOGGER.info("已拦截 OpenGL PMD 加密模型加载: {}", filename);
        long handle = MMDSyncNativeBridge.loadEncryptedModelFromFile(filename, dir, layerCount);
        if (handle == 0L && !CryptoUtils.hasSessionMaterial()) {
            MMDSyncMod.LOGGER.warn("OpenGL 加载加密 PMD 时 SessionKey 尚未就绪，本次跳过阻塞等待: {}", filename);
        }
        return handle;
    }

    @Redirect(method = "create", at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;DeleteModel(J)V"), remap = false)
    private static void redirectDeleteModel(NativeFunc instance, long model) {
        if (useBridge(model)) {
            MMDSyncNativeBridge.deleteModel(model);
            return;
        }
        instance.DeleteModel(model);
    }

    @Redirect(method = "createFromHandle", at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;GetVertexCount(J)J"), remap = false)
    private static long redirectGetVertexCount(NativeFunc instance, long model) {
        return useBridge(model) ? MMDSyncNativeBridge.getVertexCount(model) : instance.GetVertexCount(model);
    }

    @Redirect(method = "createFromHandle", at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;GetIndexElementSize(J)J"), remap = false)
    private static long redirectGetIndexElementSize(NativeFunc instance, long model) {
        return useBridge(model) ? MMDSyncNativeBridge.getIndexElementSize(model) : instance.GetIndexElementSize(model);
    }

    @Redirect(method = "createFromHandle", at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;GetIndexCount(J)J"), remap = false)
    private static long redirectGetIndexCount(NativeFunc instance, long model) {
        return useBridge(model) ? MMDSyncNativeBridge.getIndexCount(model) : instance.GetIndexCount(model);
    }

    @Redirect(method = "createFromHandle", at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;GetIndices(J)J"), remap = false)
    private static long redirectGetIndices(NativeFunc instance, long model) {
        return useBridge(model) ? MMDSyncNativeBridge.getIndices(model) : instance.GetIndices(model);
    }

    @Redirect(method = "createFromHandle", at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;GetMaterialCount(J)J"), remap = false)
    private static long redirectGetMaterialCount(NativeFunc instance, long model) {
        return useBridge(model) ? MMDSyncNativeBridge.getMaterialCount(model) : instance.GetMaterialCount(model);
    }

    @Redirect(method = "createFromHandle", at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;GetMaterialTex(JJ)Ljava/lang/String;"), remap = false)
    private static String redirectGetMaterialTex(NativeFunc instance, long model, long index) {
        return useBridge(model) ? MMDSyncNativeBridge.getMaterialTex(model, index) : instance.GetMaterialTex(model, index);
    }

    @Redirect(method = "createFromHandle", at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;GetUVs(J)J"), remap = false)
    private static long redirectGetUVs(NativeFunc instance, long model) {
        return useBridge(model) ? MMDSyncNativeBridge.getUVs(model) : instance.GetUVs(model);
    }

    @Redirect(method = "createFromHandle", at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;GetUvMorphCount(J)I"), remap = false)
    private static int redirectGetUvMorphCount(NativeFunc instance, long model) {
        return useBridge(model) ? 0 : instance.GetUvMorphCount(model);
    }

    @Redirect(method = "createFromHandle", at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;GetMaterialMorphResultCount(J)I"), remap = false)
    private static int redirectGetMaterialMorphResultCount(NativeFunc instance, long model) {
        return useBridge(model) ? 0 : instance.GetMaterialMorphResultCount(model);
    }

    @Redirect(method = "createFromHandle", at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;GetSubMeshCount(J)J"), remap = false)
    private static long redirectGetSubMeshCount(NativeFunc instance, long model) {
        return useBridge(model) ? MMDSyncNativeBridge.getSubMeshCount(model) : instance.GetSubMeshCount(model);
    }

    @Redirect(method = "createFromHandle", at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;SetAutoBlinkEnabled(JZ)V"), remap = false)
    private static void redirectSetAutoBlinkEnabled(NativeFunc instance, long model, boolean enabled) {
        if (useBridge(model)) {
            MMDSyncNativeBridge.setAutoBlinkEnabled(model, enabled);
            return;
        }
        instance.SetAutoBlinkEnabled(model, enabled);
    }
}
