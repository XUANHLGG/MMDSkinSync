package com.tendoarisu.mmdskin.sync.mixin;

import com.opdent.mmdskin.sync.MMDSyncMod;
import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.stage.client.camera.MMDCameraData;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.nio.ByteBuffer;

@Mixin(value = MMDCameraData.class, remap = false)
public abstract class MixinMMDCameraData {

    @Redirect(
            method = "update(F)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/shiroha/mmdskin/NativeFunc;GetCameraTransform(JFLjava/nio/ByteBuffer;)V"
            ),
            remap = false
    )
    private void redirectGetCameraTransform(NativeFunc instance, long animHandle, float frame, ByteBuffer buffer) {
        if (!MMDSyncNativeBridge.isBridgeHandle(animHandle)) {
            instance.GetCameraTransform(animHandle, frame, buffer);
            return;
        }
        if (!MMDSyncNativeBridge.isAnimationHandleValid(animHandle)) {
            MMDSyncMod.LOGGER.warn("阻止读取过期加密镜头动画数据: anim={}, frame={}", animHandle, frame);
            return;
        }
        MMDSyncNativeBridge.getCameraTransform(animHandle, frame, buffer);
    }
}
