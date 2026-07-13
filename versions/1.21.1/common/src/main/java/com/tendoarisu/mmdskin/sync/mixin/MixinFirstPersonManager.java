package com.tendoarisu.mmdskin.sync.mixin;

import com.opdent.mmdskin.sync.MMDSyncMod;
import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.player.runtime.FirstPersonManager;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

@Mixin(value = FirstPersonManager.class, remap = false)
public class MixinFirstPersonManager {
    @Shadow private static boolean activeDesktopFirstPerson;
    @Shadow private static long trackedModelHandle;

    @Inject(method = "reset()V", at = @At("HEAD"), remap = false)
    private static void mmdsync$disableBridgeFirstPersonBeforeReset(CallbackInfo ci) {
        if (!activeDesktopFirstPerson || !MMDSyncNativeBridge.isBridgeHandle(trackedModelHandle)) {
            return;
        }
        if (!MMDSyncNativeBridge.isModelHandleValid(trackedModelHandle)) {
            MMDSyncMod.LOGGER.warn("F3 视角恢复时 bridge 模型句柄已过期: model={}", trackedModelHandle);
            activeDesktopFirstPerson = false;
            return;
        }
        MMDSyncNativeBridge.setFirstPersonMode(trackedModelHandle, false);
        // The upstream disableTrackedModel() call that follows targets its own
        // native domain. Clear the flag so it becomes a no-op for this bridge handle.
        activeDesktopFirstPerson = false;
        MMDSyncMod.LOGGER.info("F3 视角生命周期已恢复 bridge 模型第三人称材质: model={}", trackedModelHandle);
    }

    @Redirect(
            method = "preRender(Lcom/shiroha/mmdskin/NativeFunc;JFZ)V",
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
        MMDSyncMod.LOGGER.info("第一人称生命周期诊断: 通过 preRender 路由 bridge 模型状态: model={}, enabled={}", modelHandle, enabled);
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
