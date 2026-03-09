package com.tendoarisu.mmdskin.sync.mixin;

import com.opdent.mmdskin.sync.MMDSyncMod;
import com.shiroha.mmdskin.NativeFunc;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "com.shiroha.mmdskin.ui.selector.ModelSettingsScreen", remap = false)
public abstract class MixinModelSettingsScreen {

    @Redirect(
            method = "applyConfigToModel",
            at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;SetEyeTrackingEnabled(JZ)V")
    )
    private void guardSetEyeTrackingEnabled(NativeFunc instance, long modelHandle, boolean enabled) {
        if (modelHandle != 0L && !MMDSyncNativeBridge.isModelHandleValid(modelHandle)) {
            MMDSyncMod.LOGGER.warn("阻止使用过期加密模型句柄设置眼球追踪: model={}, enabled={}", modelHandle, enabled);
            return;
        }
        instance.SetEyeTrackingEnabled(modelHandle, enabled);
    }

    @Redirect(
            method = "applyConfigToModel",
            at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;SetEyeMaxAngle(JF)V")
    )
    private void guardSetEyeMaxAngle(NativeFunc instance, long modelHandle, float angle) {
        if (modelHandle != 0L && !MMDSyncNativeBridge.isModelHandleValid(modelHandle)) {
            MMDSyncMod.LOGGER.warn("阻止使用过期加密模型句柄设置眼球角度: model={}, angle={}", modelHandle, angle);
            return;
        }
        instance.SetEyeMaxAngle(modelHandle, angle);
    }
}
