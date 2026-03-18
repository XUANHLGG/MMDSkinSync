package com.tendoarisu.mmdskin.sync.mixin;

import com.opdent.mmdskin.sync.MMDSyncMod;
import com.shiroha.mmdskin.NativeFunc;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Arrays;

@Mixin(targets = "com.shiroha.mmdskin.player.runtime.FirstPersonManager", remap = false)
public class MixinFirstPersonManager {

    @Redirect(
            method = "*",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/shiroha/mmdskin/NativeFunc;SetFirstPersonMode(JZ)V"
            ),
            remap = false
    )
    private static void redirectSetFirstPersonMode(NativeFunc instance, long modelHandle, boolean enabled) {
        if (!MMDSyncNativeBridge.isBridgeHandle(modelHandle)) {
            instance.SetFirstPersonMode(modelHandle, enabled);
            return;
        }
        if (!MMDSyncNativeBridge.isModelHandleValid(modelHandle)) {
            MMDSyncMod.LOGGER.warn("阻止对过期加密模型句柄切换第一人称模式: model={}, enabled={}", modelHandle, enabled);
            return;
        }
        MMDSyncNativeBridge.setFirstPersonMode(modelHandle, enabled);
    }

    @Redirect(
            method = "postRender",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/shiroha/mmdskin/NativeFunc;GetEyeBonePosition(J[F)V"
            ),
            remap = false
    )
    private static void redirectGetEyeBonePosition(NativeFunc instance, long modelHandle, float[] out) {
        if (!MMDSyncNativeBridge.isBridgeHandle(modelHandle)) {
            instance.GetEyeBonePosition(modelHandle, out);
            return;
        }
        if (!MMDSyncNativeBridge.isModelHandleValid(modelHandle)) {
            MMDSyncMod.LOGGER.warn("阻止读取过期加密模型眼骨位置: model={}", modelHandle);
            Arrays.fill(out, 0.0F);
            return;
        }
        MMDSyncNativeBridge.getEyeBonePosition(modelHandle, out);
    }
}
