package com.tendoarisu.mmdskin.sync.mixin;

import com.opdent.mmdskin.sync.MMDSyncMod;
import com.shiroha.mmdskin.NativeFunc;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.shiroha.mmdskin.ui.selector.adapter.DefaultModelSettingsRuntimeGateway", remap = false)
public abstract class MixinModelSettingsScreen {

    @Redirect(
            method = "applyConfigIfSelected",
            at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;SetEyeTrackingEnabled(JZ)V"),
            remap = false
    )
    private static void guardSetEyeTrackingEnabled(NativeFunc instance, long modelHandle, boolean enabled) {
        if (!MMDSyncNativeBridge.isBridgeHandle(modelHandle)) {
            instance.SetEyeTrackingEnabled(modelHandle, enabled);
            return;
        }
        if (!MMDSyncNativeBridge.isModelHandleValid(modelHandle)) {
            MMDSyncMod.LOGGER.warn("阻止使用过期加密模型句柄设置眼球追踪: model={}, enabled={}", modelHandle, enabled);
            return;
        }
        MMDSyncNativeBridge.setEyeTrackingEnabled(modelHandle, enabled);
    }

    @Redirect(
            method = "applyConfigIfSelected",
            at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;SetEyeMaxAngle(JF)V"),
            remap = false
    )
    private static void guardSetEyeMaxAngle(NativeFunc instance, long modelHandle, float angle) {
        if (!MMDSyncNativeBridge.isBridgeHandle(modelHandle)) {
            instance.SetEyeMaxAngle(modelHandle, angle);
            return;
        }
        if (!MMDSyncNativeBridge.isModelHandleValid(modelHandle)) {
            MMDSyncMod.LOGGER.warn("阻止使用过期加密模型句柄设置眼球角度: model={}, angle={}", modelHandle, angle);
            return;
        }
        MMDSyncNativeBridge.setEyeMaxAngle(modelHandle, angle);
    }
}
