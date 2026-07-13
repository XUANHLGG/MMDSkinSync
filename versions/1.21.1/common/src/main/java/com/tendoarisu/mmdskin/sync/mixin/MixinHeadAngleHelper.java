package com.tendoarisu.mmdskin.sync.mixin;

import com.opdent.mmdskin.sync.MMDSyncMod;
import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.renderer.runtime.model.helper.HeadAngleHelper;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = HeadAngleHelper.class, remap = false)
public class MixinHeadAngleHelper {

    @Redirect(
            method = "updateHeadAngle",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/shiroha/mmdskin/NativeFunc;SetHeadAngle(JFFFZ)V"
            ),
            remap = false
    )
    private static void redirectSetHeadAngle(NativeFunc instance, long modelHandle, float headX, float headY, float headZ, boolean isHeadInSync) {
        if (!MMDSyncNativeBridge.isBridgeHandle(modelHandle)) {
            instance.SetHeadAngle(modelHandle, headX, headY, headZ, isHeadInSync);
            return;
        }
        if (!MMDSyncNativeBridge.isModelHandleValid(modelHandle)) {
            MMDSyncMod.LOGGER.warn("阻止对过期加密模型句柄设置头部角度: model={}", modelHandle);
            return;
        }
        MMDSyncNativeBridge.setHeadAngle(modelHandle, headX, headY, headZ, isHeadInSync);
    }
}
