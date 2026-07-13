package com.tendoarisu.mmdskin.sync.mixin;

import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.renderer.runtime.animation.MMDAnimManager;
import com.opdent.mmdskin.sync.MMDSyncMod;
import com.tendoarisu.mmdskin.sync.util.CryptoUtils;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

@Mixin(value = MMDAnimManager.class, remap = false)
public class MixinMMDAnimManager {

    @Redirect(
            method = {
                    "tryLoadFromDir(Lcom/shiroha/mmdskin/renderer/api/IMMDModel;Ljava/lang/String;Ljava/lang/String;)J",
                    "tryLoadAnimation(Lcom/shiroha/mmdskin/renderer/api/IMMDModel;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)J"
            },
            at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;LoadAnimation(JLjava/lang/String;)J"),
            remap = false
    )
    private static long redirectLoadAnimation(NativeFunc instance, long model, String filename) {
        if (MMDSyncNativeBridge.isBridgeHandle(model) && !MMDSyncNativeBridge.isModelHandleValid(model)) {
            MMDSyncMod.LOGGER.warn("阻止对过期加密模型句柄加载动画: model={}, file={}", model, filename);
            return 0L;
        }
        File file = new File(filename);
        if (CryptoUtils.isEncrypted(file)) {
            long handle = MMDSyncNativeBridge.loadEncryptedVMDFromFile(filename);
            if (handle == 0L && !CryptoUtils.hasSessionMaterial()) {
                MMDSyncMod.LOGGER.warn("加载加密动作时 Native SessionKey 尚未就绪，本次跳过阻塞等待: {}", filename);
            }
            if (handle == 0L) {
                MMDSyncMod.LOGGER.error("加载加密动作失败: Native 直接文件解密/加载返回空句柄: {}", filename);
            }
            return handle;
        }
        if (MMDSyncNativeBridge.isBridgeHandle(model)) {
            if (!file.exists()) {
                MMDSyncMod.LOGGER.warn("桥接模型加载非加密动作失败: 文件不存在: {}", filename);
                return 0L;
            }
            try {
                byte[] data = Files.readAllBytes(file.toPath());
                long handle = MMDSyncNativeBridge.loadVMDFromMemory(data);
                if (handle == 0L) {
                    MMDSyncMod.LOGGER.error("桥接模型加载非加密动作失败: 内存加载返回空句柄: {}", filename);
                }
                return handle;
            } catch (IOException ex) {
                MMDSyncMod.LOGGER.error("桥接模型读取非加密动作失败: {}", filename, ex);
                return 0L;
            }
        }
        return instance.LoadAnimation(model, filename);
    }

    @Redirect(
            method = {
                    "DeleteModel(Lcom/shiroha/mmdskin/renderer/api/IMMDModel;)V",
                    "invalidateAnimCache(Lcom/shiroha/mmdskin/renderer/api/IMMDModel;)V"
            },
            at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;DeleteAnimation(J)V"),
            remap = false
    )
    private static void redirectDeleteAnimation(NativeFunc instance, long animationHandle) {
        if (MMDSyncNativeBridge.isBridgeHandle(animationHandle)) {
            if (MMDSyncNativeBridge.isAnimationHandleValid(animationHandle)) {
                MMDSyncNativeBridge.deleteAnimation(animationHandle);
            }
            return;
        }
        instance.DeleteAnimation(animationHandle);
    }

    @Inject(
            method = "GetAnimModel(Lcom/shiroha/mmdskin/renderer/api/IMMDModel;Ljava/lang/String;)J",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private static void invalidateStaleTrackedAnimHandle(com.shiroha.mmdskin.renderer.api.IMMDModel model, String animName, CallbackInfoReturnable<Long> cir) {
        Long handle = cir.getReturnValue();
        if (handle != null && MMDSyncNativeBridge.isBridgeHandle(handle) && !MMDSyncNativeBridge.isAnimationHandleValid(handle)) {
            MMDSyncMod.LOGGER.warn("阻止返回过期加密动画句柄: anim={}, handle={}", animName, handle);
            cir.setReturnValue(0L);
        }
    }
}
