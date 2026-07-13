package com.tendoarisu.mmdskin.sync.mixin;

import com.opdent.mmdskin.sync.MMDSyncMod;
import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.renderer.runtime.model.loading.ModelLoadCoordinator;
import com.tendoarisu.mmdskin.sync.util.CryptoUtils;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;

@Mixin(value = ModelLoadCoordinator.class, remap = false)
public class MixinMMDModelManager {

    @Redirect(
        method = "loadModelHandle(Ljava/lang/String;Lcom/shiroha/mmdskin/asset/catalog/ModelInfo;Ljava/lang/String;J)Lcom/shiroha/mmdskin/renderer/runtime/model/loading/ModelLoadCoordinator$AsyncLoadResult;",
        at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;LoadModelPMX(Ljava/lang/String;Ljava/lang/String;J)J"),
        remap = false
    )
    private static long redirectBackgroundLoadModelPMX(NativeFunc instance, String filename, String dir, long layerCount) {
        File file = new File(filename);
        if (!CryptoUtils.isEncrypted(file)) {
            return instance.LoadModelPMX(filename, dir, layerCount);
        }

        MMDSyncMod.LOGGER.info("已拦截运行时加密 PMX 模型加载: {}", filename);

        long handle = MMDSyncNativeBridge.loadEncryptedModelFromFile(filename, dir, layerCount);
        if (handle == 0L && !CryptoUtils.hasSessionMaterial()) {
            MMDSyncMod.LOGGER.warn("后台加载加密模型时 Native SessionKey 尚未就绪，本次跳过阻塞等待: {}", filename);
        }
        if (handle == 0L) {
            MMDSyncMod.LOGGER.error("后台加载加密模型失败: Native 直接文件解密/加载返回空句柄: {}", filename);
        }
        return handle;
    }

    @Redirect(
        method = "loadModelHandle(Ljava/lang/String;Lcom/shiroha/mmdskin/asset/catalog/ModelInfo;Ljava/lang/String;J)Lcom/shiroha/mmdskin/renderer/runtime/model/loading/ModelLoadCoordinator$AsyncLoadResult;",
        at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;LoadModelPMD(Ljava/lang/String;Ljava/lang/String;J)J"),
        remap = false
    )
    private static long redirectBackgroundLoadModelPMD(NativeFunc instance, String filename, String dir, long layerCount) {
        File file = new File(filename);
        if (!CryptoUtils.isEncrypted(file)) {
            return instance.LoadModelPMD(filename, dir, layerCount);
        }

        MMDSyncMod.LOGGER.error("加密 PMD 暂不受 bridge 支持：1.0.5 引擎未公开 PMD 内存加载 API，拒绝错误按 PMX 解析: {}", filename);
        return 0L;
    }

    @Redirect(
        method = "loadModelHandle(Ljava/lang/String;Lcom/shiroha/mmdskin/asset/catalog/ModelInfo;Ljava/lang/String;J)Lcom/shiroha/mmdskin/renderer/runtime/model/loading/ModelLoadCoordinator$AsyncLoadResult;",
        at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;LoadModelVRM(Ljava/lang/String;Ljava/lang/String;J)J"),
        remap = false
    )
    private static long redirectBackgroundLoadModelVRM(NativeFunc instance, String filename, String dir, long layerCount) {
        File file = new File(filename);
        if (!CryptoUtils.isEncrypted(file)) {
            return instance.LoadModelVRM(filename, dir, layerCount);
        }

        MMDSyncMod.LOGGER.error("加密 VRM 暂不受 bridge 支持：1.0.5 引擎未公开 VRM 内存加载 API，拒绝错误按 PMX 解析: {}", filename);
        return 0L;
    }

    @Redirect(
        method = "loadModelHandle(Ljava/lang/String;Lcom/shiroha/mmdskin/asset/catalog/ModelInfo;Ljava/lang/String;J)Lcom/shiroha/mmdskin/renderer/runtime/model/loading/ModelLoadCoordinator$AsyncLoadResult;",
        at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;DeleteModel(J)V"),
        remap = false
    )
    private static void redirectBackgroundDeleteModel(NativeFunc instance, long modelHandle) {
        if (MMDSyncNativeBridge.isBridgeHandle(modelHandle)) {
            MMDSyncNativeBridge.deleteModel(modelHandle);
            return;
        }
        instance.DeleteModel(modelHandle);
    }

    @Inject(
        method = "preloadModelTextures(Lcom/shiroha/mmdskin/NativeFunc;JLjava/lang/String;)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private static void guardPreloadModelTextures(NativeFunc nf, long modelHandle, String modelDir, CallbackInfo ci) {
        if (MMDSyncNativeBridge.isBridgeHandle(modelHandle)
            && !MMDSyncNativeBridge.isModelHandleValid(modelHandle)) {
            MMDSyncMod.LOGGER.warn("阻止对过期加密模型句柄预加载贴图: model={}, dir={}", modelHandle, modelDir);
            ci.cancel();
        }
    }

    @Redirect(
        method = "preloadModelTextures(Lcom/shiroha/mmdskin/NativeFunc;JLjava/lang/String;)V",
        at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;GetMaterialCount(J)J"),
        remap = false
    )
    private static long redirectBackgroundGetMaterialCount(NativeFunc instance, long modelHandle) {
        return MMDSyncNativeBridge.isBridgeHandle(modelHandle)
                ? MMDSyncNativeBridge.getMaterialCount(modelHandle)
                : instance.GetMaterialCount(modelHandle);
    }

    @Redirect(
        method = "preloadModelTextures(Lcom/shiroha/mmdskin/NativeFunc;JLjava/lang/String;)V",
        at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;GetMaterialTex(JJ)Ljava/lang/String;"),
        remap = false
    )
    private static String redirectBackgroundGetMaterialTex(NativeFunc instance, long modelHandle, long index) {
        return MMDSyncNativeBridge.isBridgeHandle(modelHandle)
                ? MMDSyncNativeBridge.getMaterialTex(modelHandle, index)
                : instance.GetMaterialTex(modelHandle, index);
    }
}

