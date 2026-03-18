package com.tendoarisu.mmdskin.sync.mixin;

import com.opdent.mmdskin.sync.MMDSyncMod;
import com.shiroha.mmdskin.NativeFunc;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "com.shiroha.mmdskin.stage.client.camera.MMDCameraController", remap = false)
public abstract class MixinMMDCameraController {

    @Redirect(
            method = "*",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/shiroha/mmdskin/NativeFunc;SetAutoBlinkEnabled(JZ)V"
            ),
            remap = false
    )
    private void redirectSetAutoBlinkEnabled(NativeFunc instance, long modelHandle, boolean enabled) {
        if (!MMDSyncNativeBridge.isBridgeHandle(modelHandle)) {
            instance.SetAutoBlinkEnabled(modelHandle, enabled);
            return;
        }
        if (!MMDSyncNativeBridge.isModelHandleValid(modelHandle)) {
            MMDSyncMod.LOGGER.warn("阻止舞台模式对过期加密模型句柄设置自动眨眼: model={}, enabled={}", modelHandle, enabled);
            return;
        }
        MMDSyncNativeBridge.setAutoBlinkEnabled(modelHandle, enabled);
    }

    @Redirect(
            method = "*",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/shiroha/mmdskin/NativeFunc;SetEyeTrackingEnabled(JZ)V"
            ),
            remap = false
    )
    private void redirectSetEyeTrackingEnabled(NativeFunc instance, long modelHandle, boolean enabled) {
        if (!MMDSyncNativeBridge.isBridgeHandle(modelHandle)) {
            instance.SetEyeTrackingEnabled(modelHandle, enabled);
            return;
        }
        if (!MMDSyncNativeBridge.isModelHandleValid(modelHandle)) {
            MMDSyncMod.LOGGER.warn("阻止舞台模式对过期加密模型句柄设置眼球追踪: model={}, enabled={}", modelHandle, enabled);
            return;
        }
        MMDSyncNativeBridge.setEyeTrackingEnabled(modelHandle, enabled);
    }

    @Redirect(
            method = "*",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/shiroha/mmdskin/NativeFunc;DeleteAnimation(J)V"
            ),
            remap = false
    )
    private void redirectDeleteAnimation(NativeFunc instance, long animHandle) {
        if (!MMDSyncNativeBridge.isBridgeHandle(animHandle)) {
            instance.DeleteAnimation(animHandle);
            return;
        }
        if (!MMDSyncNativeBridge.isAnimationHandleValid(animHandle)) {
            MMDSyncMod.LOGGER.warn("阻止删除过期加密镜头动画句柄: anim={}", animHandle);
            return;
        }
        MMDSyncNativeBridge.deleteAnimation(animHandle);
    }

    @Redirect(
            method = "*",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/shiroha/mmdskin/NativeFunc;HasCameraData(J)Z"
            ),
            remap = false
    )
    private boolean redirectHasCameraData(NativeFunc instance, long animHandle) {
        if (!MMDSyncNativeBridge.isBridgeHandle(animHandle)) {
            return instance.HasCameraData(animHandle);
        }
        if (!MMDSyncNativeBridge.isAnimationHandleValid(animHandle)) {
            MMDSyncMod.LOGGER.warn("阻止读取过期加密镜头动画相机轨道标记: anim={}", animHandle);
            return false;
        }
        return MMDSyncNativeBridge.hasCameraData(animHandle);
    }

    @Redirect(
            method = "*",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/shiroha/mmdskin/NativeFunc;GetAnimMaxFrame(J)F"
            ),
            remap = false
    )
    private float redirectGetAnimMaxFrame(NativeFunc instance, long animHandle) {
        if (!MMDSyncNativeBridge.isBridgeHandle(animHandle)) {
            return instance.GetAnimMaxFrame(animHandle);
        }
        if (!MMDSyncNativeBridge.isAnimationHandleValid(animHandle)) {
            MMDSyncMod.LOGGER.warn("阻止读取过期加密镜头动画最大帧: anim={}", animHandle);
            return 0.0F;
        }
        return MMDSyncNativeBridge.getAnimMaxFrame(animHandle);
    }
}
