package com.tendoarisu.mmdskin.sync.mixin;

import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.renderer.model.MMDModelNativeRender;
import com.opdent.mmdskin.sync.MMDSyncMod;
import com.tendoarisu.mmdskin.sync.util.CryptoUtils;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;

@Mixin(value = MMDModelNativeRender.class, remap = false)
public class MixinMMDModelNativeRender {

    /**
     * 拦截 LoadModel 中对 NativeFunc.LoadModelPMX 的调用
     * 如果是加密文件，则解密后通过内存加载
     */
    @Redirect(method = "LoadModel", at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;LoadModelPMX(Ljava/lang/String;Ljava/lang/String;J)J"))
    private static long redirectLoadModelPMX(NativeFunc instance, String filename, String dir, long layerCount) {
        File file = new File(filename);
        if (CryptoUtils.isEncrypted(file)) {
            long handle = MMDSyncNativeBridge.loadEncryptedModelFromFile(filename, dir, layerCount);
            if (handle == 0L && !CryptoUtils.hasSessionMaterial()) {
                if (!CryptoUtils.waitForSessionMaterial(2000L)) {
                    MMDSyncMod.LOGGER.warn("加载加密模型时 Native SessionKey 在补等待后仍未就绪: {}", filename);
                }
                handle = MMDSyncNativeBridge.loadEncryptedModelFromFile(filename, dir, layerCount);
            }
            if (handle == 0L) {
                MMDSyncMod.LOGGER.error("加载加密模型失败: Native 直接文件解密/加载返回空句柄: {}", filename);
            }
            return handle;
        }
        // 如果不是加密文件，调用原版逻辑
        return instance.LoadModelPMX(filename, dir, layerCount);
    }

    @Inject(method = "createFromHandle", at = @At("HEAD"), cancellable = true)
    private static void guardCreateFromHandle(long model, String modelDirectory, CallbackInfoReturnable<MMDModelNativeRender> cir) {
        if (model != 0L && !MMDSyncNativeBridge.isModelHandleValid(model)) {
            MMDSyncMod.LOGGER.warn("阻止从过期加密模型句柄创建渲染对象: model={}, dir={}", model, modelDirectory);
            cir.setReturnValue(null);
        }
    }

    @Redirect(method = "changeAnim", at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;ChangeModelAnim(JJJ)V"))
    private void guardChangeAnim(NativeFunc instance, long model, long anim, long layer) {
        if (model != 0L && !MMDSyncNativeBridge.isModelHandleValid(model)) {
            MMDSyncMod.LOGGER.warn("阻止对过期加密模型句柄切换动画: model={}, anim={}, layer={}", model, anim, layer);
            return;
        }
        if (anim != 0L && !MMDSyncNativeBridge.isAnimationHandleValid(anim)) {
            MMDSyncMod.LOGGER.warn("阻止对过期加密动画句柄切换动画: model={}, anim={}, layer={}", model, anim, layer);
            return;
        }
        instance.ChangeModelAnim(model, anim, layer);
    }

    @Redirect(method = "transitionAnim", at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;TransitionLayerTo(JJJF)V"))
    private void guardTransitionAnim(NativeFunc instance, long model, long layer, long anim, float transitionTime) {
        if (model != 0L && !MMDSyncNativeBridge.isModelHandleValid(model)) {
            MMDSyncMod.LOGGER.warn("阻止对过期加密模型句柄做动画过渡: model={}, anim={}, layer={}", model, anim, layer);
            return;
        }
        if (anim != 0L && !MMDSyncNativeBridge.isAnimationHandleValid(anim)) {
            MMDSyncMod.LOGGER.warn("阻止对过期加密动画句柄做动画过渡: model={}, anim={}, layer={}", model, anim, layer);
            return;
        }
        instance.TransitionLayerTo(model, layer, anim, transitionTime);
    }
}
