package com.tendoarisu.mmdskin.sync.mixin;

import com.opdent.mmdskin.sync.MMDSyncMod;
import com.shiroha.mmdskin.NativeFunc;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "com.shiroha.mmdskin.renderer.runtime.model.helper.EyeTrackingHelper", remap = false)
public class MixinEyeTrackingHelper {

    @Redirect(
            method = "*",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/shiroha/mmdskin/NativeFunc;SetEyeTrackingEnabled(JZ)V"
            ),
            remap = false
    )
    private static void redirectSetEyeTrackingEnabled(NativeFunc instance, long modelHandle, boolean enabled) {
        if (!MMDSyncNativeBridge.isBridgeHandle(modelHandle)) {
            instance.SetEyeTrackingEnabled(modelHandle, enabled);
            return;
        }
        if (!MMDSyncNativeBridge.isModelHandleValid(modelHandle)) {
            MMDSyncMod.LOGGER.warn("阻止对过期加密模型句柄设置眼球追踪: model={}, enabled={}", modelHandle, enabled);
            return;
        }
        MMDSyncNativeBridge.setEyeTrackingEnabled(modelHandle, enabled);
    }

    @Redirect(
            method = "*",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/shiroha/mmdskin/NativeFunc;SetEyeMaxAngle(JF)V"
            ),
            remap = false
    )
    private static void redirectSetEyeMaxAngle(NativeFunc instance, long modelHandle, float maxAngle) {
        if (!MMDSyncNativeBridge.isBridgeHandle(modelHandle)) {
            instance.SetEyeMaxAngle(modelHandle, maxAngle);
            return;
        }
        if (!MMDSyncNativeBridge.isModelHandleValid(modelHandle)) {
            MMDSyncMod.LOGGER.warn("阻止对过期加密模型句柄设置眼球最大角度: model={}, maxAngle={}", modelHandle, maxAngle);
            return;
        }
        MMDSyncNativeBridge.setEyeMaxAngle(modelHandle, maxAngle);
    }

    @Redirect(
            method = "*",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/shiroha/mmdskin/NativeFunc;SetEyeAngle(JFF)V"
            ),
            remap = false
    )
    private static void redirectSetEyeAngle(NativeFunc instance, long modelHandle, float eyeX, float eyeY) {
        if (!MMDSyncNativeBridge.isBridgeHandle(modelHandle)) {
            instance.SetEyeAngle(modelHandle, eyeX, eyeY);
            return;
        }
        if (!MMDSyncNativeBridge.isModelHandleValid(modelHandle)) {
            MMDSyncMod.LOGGER.warn("阻止对过期加密模型句柄设置眼球角度: model={}, eyeX={}, eyeY={}", modelHandle, eyeX, eyeY);
            return;
        }
        MMDSyncNativeBridge.setEyeAngle(modelHandle, eyeX, eyeY);
    }
}
