package com.tendoarisu.mmdskin.sync.mixin;

import com.opdent.mmdskin.sync.MMDSyncMod;
import com.shiroha.mmdskin.NativeFunc;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 1.0.4 起 MorphWheelScreen 不再直接调用 NativeFunc，而是委托给 DefaultMorphWheelService 的 runtimePort。
 * 因此需要把注入点迁移到 DefaultMorphWheelService$MinecraftMorphRuntimePort 中。
 */
@Mixin(targets = "com.shiroha.mmdskin.ui.wheel.service.DefaultMorphWheelService$MinecraftMorphRuntimePort", remap = false)
public abstract class MixinMorphWheelScreen {

    @Redirect(
            method = "resetCurrentPlayerMorphs",
            at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;ResetAllMorphs(J)V"),
            remap = false
    )
    private static void guardResetAllMorphs(NativeFunc instance, long modelHandle) {
        if (!MMDSyncNativeBridge.isBridgeHandle(modelHandle)) {
            instance.ResetAllMorphs(modelHandle);
            return;
        }
        if (!MMDSyncNativeBridge.isModelHandleValid(modelHandle)) {
            MMDSyncMod.LOGGER.warn("阻止使用过期加密模型句柄重置表情轮盘 Morph: model={}", modelHandle);
            return;
        }
        MMDSyncNativeBridge.resetAllMorphs(modelHandle);
    }

    @Redirect(
            method = "applyCurrentPlayerMorph",
            at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;ApplyVpdMorph(JLjava/lang/String;)I"),
            remap = false
    )
    private static int guardApplyVpdMorph(NativeFunc instance, long modelHandle, String filePath) {
        if (!MMDSyncNativeBridge.isBridgeHandle(modelHandle)) {
            return instance.ApplyVpdMorph(modelHandle, filePath);
        }
        if (!MMDSyncNativeBridge.isModelHandleValid(modelHandle)) {
            MMDSyncMod.LOGGER.warn("阻止使用过期加密模型句柄应用表情轮盘 VPD: model={}, file={}", modelHandle, filePath);
            return -2;
        }
        return MMDSyncNativeBridge.applyVpdMorph(modelHandle, filePath);
    }
}
