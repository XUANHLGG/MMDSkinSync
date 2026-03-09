package com.tendoarisu.mmdskin.sync.mixin;

import com.opdent.mmdskin.sync.MMDSyncMod;
import com.shiroha.mmdskin.NativeFunc;
import com.tendoarisu.mmdskin.sync.util.CryptoUtils;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;

@Mixin(targets = "com.shiroha.mmdskin.renderer.model.MMDModelManager", remap = false)
public class MixinMMDModelManager {

    /**
     * MMDModelManager 的后台异步加载直接调用 NativeFunc.LoadModelPMX，
     * 不会经过 MMDModelNativeRender.LoadModel，因此也必须在这里拦截。
     */
    @Redirect(
        method = "lambda$startBackgroundLoad$1",
        at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;LoadModelPMX(Ljava/lang/String;Ljava/lang/String;J)J")
    )
    private static long redirectBackgroundLoadModelPMX(NativeFunc instance, String filename, String dir, long layerCount) {
        File file = new File(filename);
        if (!CryptoUtils.isEncrypted(file)) {
            return instance.LoadModelPMX(filename, dir, layerCount);
        }

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

    @Inject(method = "preloadModelTextures", at = @At("HEAD"), cancellable = true)
    private static void guardPreloadModelTextures(NativeFunc nf, long modelHandle, String modelDir, CallbackInfo ci) {
        if (modelHandle != 0L && !MMDSyncNativeBridge.isModelHandleValid(modelHandle)) {
            MMDSyncMod.LOGGER.warn("阻止对过期加密模型句柄预加载贴图: model={}, dir={}", modelHandle, modelDir);
            ci.cancel();
        }
    }

    @Inject(method = "applyMaterialVisibility", at = @At("HEAD"), cancellable = true)
    private static void guardApplyMaterialVisibility(long modelHandle, String modelName, CallbackInfo ci) {
        if (modelHandle != 0L && !MMDSyncNativeBridge.isModelHandleValid(modelHandle)) {
            MMDSyncMod.LOGGER.warn("阻止对过期加密模型句柄应用材质可见性: model={}, name={}", modelHandle, modelName);
            ci.cancel();
        }
    }
}
