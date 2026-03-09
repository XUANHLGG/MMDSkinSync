package com.tendoarisu.mmdskin.sync.mixin;

import com.opdent.mmdskin.sync.MMDSyncMod;
import com.shiroha.mmdskin.NativeFunc;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "com.shiroha.mmdskin.renderer.render.MorphSyncHelper", remap = false)
public abstract class MixinMorphSyncHelper {

    @Redirect(
            method = {"applyRemoteMorph", "applyMorphFromFile"},
            at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;ResetAllMorphs(J)V")
    )
    private static void guardResetAllMorphs(NativeFunc instance, long modelHandle) {
        if (modelHandle != 0L && !MMDSyncNativeBridge.isModelHandleValid(modelHandle)) {
            MMDSyncMod.LOGGER.warn("阻止使用过期加密模型句柄执行远程表情重置: model={}", modelHandle);
            return;
        }
        instance.ResetAllMorphs(modelHandle);
    }

    @Redirect(
            method = "applyMorphFromFile",
            at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;ApplyVpdMorph(JLjava/lang/String;)I")
    )
    private static int guardApplyVpdMorph(NativeFunc instance, long modelHandle, String filePath) {
        if (modelHandle != 0L && !MMDSyncNativeBridge.isModelHandleValid(modelHandle)) {
            MMDSyncMod.LOGGER.warn("阻止使用过期加密模型句柄执行远程表情 VPD: model={}, file={}", modelHandle, filePath);
            return -2;
        }
        return instance.ApplyVpdMorph(modelHandle, filePath);
    }
}
