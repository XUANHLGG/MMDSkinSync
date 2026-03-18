package com.tendoarisu.mmdskin.sync.mixin;

import com.opdent.mmdskin.sync.MMDSyncMod;
import com.shiroha.mmdskin.NativeFunc;
import com.tendoarisu.mmdskin.sync.util.CryptoUtils;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;

@Mixin(targets = "com.shiroha.mmdskin.renderer.runtime.model.loading.ModelLoadCoordinator", remap = false)
public class MixinMMDModelManager {

    @Redirect(
        method = "loadModelHandle",
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
            if (!CryptoUtils.waitForSessionMaterial(2000L)) {
                MMDSyncMod.LOGGER.warn("后台加载加密模型时 Native SessionKey 在补等待后仍未就绪: {}", filename);
            }
            handle = MMDSyncNativeBridge.loadEncryptedModelFromFile(filename, dir, layerCount);
        }
        if (handle == 0L) {
            MMDSyncMod.LOGGER.error("后台加载加密模型失败: Native 直接文件解密/加载返回空句柄: {}", filename);
        }
        return handle;
    }

    @Redirect(
        method = "loadModelHandle",
        at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;LoadModelPMD(Ljava/lang/String;Ljava/lang/String;J)J"),
        remap = false
    )
    private static long redirectBackgroundLoadModelPMD(NativeFunc instance, String filename, String dir, long layerCount) {
        File file = new File(filename);
        if (!CryptoUtils.isEncrypted(file)) {
            return instance.LoadModelPMD(filename, dir, layerCount);
        }

        MMDSyncMod.LOGGER.info("已拦截运行时加密 PMD 模型加载: {}", filename);

        long handle = MMDSyncNativeBridge.loadEncryptedModelFromFile(filename, dir, layerCount);
        if (handle == 0L && !CryptoUtils.hasSessionMaterial()) {
            if (!CryptoUtils.waitForSessionMaterial(2000L)) {
                MMDSyncMod.LOGGER.warn("后台加载加密 PMD 模型时 Native SessionKey 在补等待后仍未就绪: {}", filename);
            }
            handle = MMDSyncNativeBridge.loadEncryptedModelFromFile(filename, dir, layerCount);
        }
        if (handle == 0L) {
            MMDSyncMod.LOGGER.error("后台加载加密 PMD 模型失败: Native 直接文件解密/加载返回空句柄: {}", filename);
        }
        return handle;
    }

    @Redirect(
        method = "loadModelHandle",
        at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;LoadModelVRM(Ljava/lang/String;Ljava/lang/String;J)J"),
        remap = false
    )
    private static long redirectBackgroundLoadModelVRM(NativeFunc instance, String filename, String dir, long layerCount) {
        File file = new File(filename);
        if (!CryptoUtils.isEncrypted(file)) {
            return instance.LoadModelVRM(filename, dir, layerCount);
        }

        MMDSyncMod.LOGGER.info("已拦截运行时加密 VRM 模型加载: {}", filename);

        long handle = MMDSyncNativeBridge.loadEncryptedModelFromFile(filename, dir, layerCount);
        if (handle == 0L && !CryptoUtils.hasSessionMaterial()) {
            if (!CryptoUtils.waitForSessionMaterial(2000L)) {
                MMDSyncMod.LOGGER.warn("后台加载加密 VRM 模型时 Native SessionKey 在补等待后仍未就绪: {}", filename);
            }
            handle = MMDSyncNativeBridge.loadEncryptedModelFromFile(filename, dir, layerCount);
        }
        if (handle == 0L) {
            MMDSyncMod.LOGGER.error("后台加载加密 VRM 模型失败: Native 直接文件解密/加载返回空句柄: {}", filename);
        }
        return handle;
    }

    @Inject(method = "preloadModelTextures", at = @At("HEAD"), cancellable = true, remap = false)
    private static void guardPreloadModelTextures(NativeFunc nf, long modelHandle, String modelDir, CallbackInfo ci) {
        if (MMDSyncNativeBridge.isBridgeHandle(modelHandle)
            && !MMDSyncNativeBridge.isModelHandleValid(modelHandle)) {
            MMDSyncMod.LOGGER.warn("阻止对过期加密模型句柄预加载贴图: model={}, dir={}", modelHandle, modelDir);
            ci.cancel();
        }
    }
}

@Mixin(targets = "com.shiroha.mmdskin.renderer.runtime.model.MMDModelManager", remap = false)
class MixinRuntimeMMDModelManager {
    @Inject(method = "applyMaterialVisibility", at = @At("HEAD"), cancellable = true, remap = false)
    private static void guardApplyMaterialVisibility(long modelHandle, String modelName, CallbackInfo ci) {
        if (MMDSyncNativeBridge.isBridgeHandle(modelHandle)
            && !MMDSyncNativeBridge.isModelHandleValid(modelHandle)) {
            MMDSyncMod.LOGGER.warn("阻止对过期加密模型句柄应用材质可见性: model={}, name={}", modelHandle, modelName);
            ci.cancel();
        }
    }
}

