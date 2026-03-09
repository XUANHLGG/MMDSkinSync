package com.tendoarisu.mmdskin.sync.mixin;

import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.renderer.animation.MMDAnimManager;
import com.opdent.mmdskin.sync.MMDSyncMod;
import com.tendoarisu.mmdskin.sync.util.CryptoUtils;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;

@Mixin(value = MMDAnimManager.class, remap = false)
public class MixinMMDAnimManager {

    /**
     * 拦截所有对 NativeFunc.LoadAnimation 的调用
     * 如果是加密文件，则解密后通过内存加载
     */
    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;LoadAnimation(JLjava/lang/String;)J"))
    private static long redirectLoadAnimation(NativeFunc instance, long model, String filename) {
        if (model != 0L && !MMDSyncNativeBridge.isModelHandleValid(model)) {
            MMDSyncMod.LOGGER.warn("阻止对过期加密模型句柄加载动画: model={}, file={}", model, filename);
            return 0L;
        }
        File file = new File(filename);
        if (CryptoUtils.isEncrypted(file)) {
            long handle = MMDSyncNativeBridge.loadEncryptedVMDFromFile(filename);
            if (handle == 0L && !CryptoUtils.hasSessionMaterial()) {
                if (!CryptoUtils.waitForSessionMaterial(2000L)) {
                    MMDSyncMod.LOGGER.warn("加载加密动作时 Native SessionKey 在补等待后仍未就绪: {}", filename);
                }
                handle = MMDSyncNativeBridge.loadEncryptedVMDFromFile(filename);
            }
            if (handle == 0L) {
                MMDSyncMod.LOGGER.error("加载加密动作失败: Native 直接文件解密/加载返回空句柄: {}", filename);
            }
            return handle;
        }
        return instance.LoadAnimation(model, filename);
    }

    @Inject(method = "GetAnimModel", at = @At("RETURN"), cancellable = true)
    private static void invalidateStaleTrackedAnimHandle(com.shiroha.mmdskin.renderer.core.IMMDModel model, String animName, CallbackInfoReturnable<Long> cir) {
        Long handle = cir.getReturnValue();
        if (handle != null && handle != 0L && !MMDSyncNativeBridge.isAnimationHandleValid(handle)) {
            MMDSyncMod.LOGGER.warn("阻止返回过期加密动画句柄: anim={}, handle={}", animName, handle);
            cir.setReturnValue(0L);
        }
    }
}
