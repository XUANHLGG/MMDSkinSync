package com.tendoarisu.mmdskin.sync.mixin;

import com.opdent.mmdskin.sync.MMDSyncMod;
import com.shiroha.mmdskin.NativeFunc;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "com.shiroha.mmdskin.compat.vr.VRBoneDriver", remap = false)
public abstract class MixinVRBoneDriver {

    @Redirect(
            method = "driveModel",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/shiroha/mmdskin/NativeFunc;SetVRTrackingData(J[F)V"
            ),
            remap = false
    )
    private static void redirectSetVRTrackingData(NativeFunc instance, long modelHandle, float[] trackingData) {
        if (!MMDSyncNativeBridge.isBridgeHandle(modelHandle)) {
            instance.SetVRTrackingData(modelHandle, trackingData);
            return;
        }
        if (!MMDSyncNativeBridge.isModelHandleValid(modelHandle)) {
            MMDSyncMod.LOGGER.warn("阻止对过期加密模型句柄设置 VR 追踪数据: model={}", modelHandle);
            return;
        }
        MMDSyncNativeBridge.setVRTrackingData(modelHandle, trackingData);
    }

    @Redirect(
            method = "setVREnabled",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/shiroha/mmdskin/NativeFunc;SetVREnabled(JZ)V"
            ),
            remap = false
    )
    private static void redirectSetVREnabled(NativeFunc instance, long modelHandle, boolean enabled) {
        if (!MMDSyncNativeBridge.isBridgeHandle(modelHandle)) {
            instance.SetVREnabled(modelHandle, enabled);
            return;
        }
        if (!MMDSyncNativeBridge.isModelHandleValid(modelHandle)) {
            MMDSyncMod.LOGGER.warn("阻止对过期加密模型句柄设置 VR 模式: model={}, enabled={}", modelHandle, enabled);
            return;
        }
        MMDSyncNativeBridge.setVREnabled(modelHandle, enabled);
    }

    @Redirect(
            method = "setVRIKParams",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/shiroha/mmdskin/NativeFunc;SetVRIKParams(JF)V"
            ),
            remap = false
    )
    private static void redirectSetVRIKParams(NativeFunc instance, long modelHandle, float armIKStrength) {
        if (!MMDSyncNativeBridge.isBridgeHandle(modelHandle)) {
            instance.SetVRIKParams(modelHandle, armIKStrength);
            return;
        }
        if (!MMDSyncNativeBridge.isModelHandleValid(modelHandle)) {
            MMDSyncMod.LOGGER.warn("阻止对过期加密模型句柄设置 VR IK 参数: model={}, strength={}", modelHandle, armIKStrength);
            return;
        }
        MMDSyncNativeBridge.setVRIKParams(modelHandle, armIKStrength);
    }
}
