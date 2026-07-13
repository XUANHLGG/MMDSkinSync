package com.tendoarisu.mmdskin.sync.mixin;

import com.shiroha.mmdskin.renderer.runtime.model.helper.EyeTrackingHelper;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = EyeTrackingHelper.class, remap = false)
public class MixinEyeTrackingHelper {
    @Redirect(
            method = {
                    "updateEyeTracking",
                    "updateEyeTrackingInternal",
                    "disableEyeTracking"
            },
            at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;SetEyeTrackingEnabled(JZ)V"),
            remap = false
    )
    private static void redirectSetEyeTrackingEnabled(com.shiroha.mmdskin.NativeFunc instance, long modelHandle, boolean enabled) {
        if (MMDSyncNativeBridge.isBridgeHandle(modelHandle)) {
            if (MMDSyncNativeBridge.isModelHandleValid(modelHandle)) MMDSyncNativeBridge.setEyeTrackingEnabled(modelHandle, enabled);
            return;
        }
        instance.SetEyeTrackingEnabled(modelHandle, enabled);
    }

    @Redirect(
            method = "updateEyeTrackingInternal",
            at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;SetEyeMaxAngle(JF)V"),
            remap = false
    )
    private static void redirectSetEyeMaxAngle(com.shiroha.mmdskin.NativeFunc instance, long modelHandle, float maxAngle) {
        if (MMDSyncNativeBridge.isBridgeHandle(modelHandle)) {
            if (MMDSyncNativeBridge.isModelHandleValid(modelHandle)) MMDSyncNativeBridge.setEyeMaxAngle(modelHandle, maxAngle);
            return;
        }
        instance.SetEyeMaxAngle(modelHandle, maxAngle);
    }

    @Redirect(
            method = "updateEyeTrackingInternal",
            at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;SetEyeAngle(JFF)V"),
            remap = false
    )
    private static void redirectSetEyeAngle(com.shiroha.mmdskin.NativeFunc instance, long modelHandle, float eyeX, float eyeY) {
        if (MMDSyncNativeBridge.isBridgeHandle(modelHandle)) {
            if (MMDSyncNativeBridge.isModelHandleValid(modelHandle)) MMDSyncNativeBridge.setEyeAngle(modelHandle, eyeX, eyeY);
            return;
        }
        instance.SetEyeAngle(modelHandle, eyeX, eyeY);
    }
}
