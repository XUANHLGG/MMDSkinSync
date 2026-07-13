package com.tendoarisu.mmdskin.sync.mixin;

import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.renderer.runtime.model.helper.LivingEntityModelStateHelper;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** 路由实体姿态同步中尚未端口化的模型状态调用。 */
@Mixin(value = LivingEntityModelStateHelper.class, remap = false)
public abstract class MixinLivingEntityModelStateHelper {

    @Redirect(
            method = "syncModelState",
            at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;SetHeadAngle(JFFFZ)V"),
            remap = false
    )
    private static void mmdsync$routeHeadAngle(
            NativeFunc instance,
            long modelHandle,
            float pitch,
            float yaw,
            float roll,
            boolean worldScene) {
        if (!MMDSyncNativeBridge.isBridgeHandle(modelHandle)) {
            instance.SetHeadAngle(modelHandle, pitch, yaw, roll, worldScene);
            return;
        }
        if (MMDSyncNativeBridge.isModelHandleValid(modelHandle)) {
            MMDSyncNativeBridge.setHeadAngle(modelHandle, pitch, yaw, roll, worldScene);
        }
    }

    @Redirect(
            method = "syncModelState",
            at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;SetModelPositionAndYaw(JFFFF)V"),
            remap = false
    )
    private static void mmdsync$routeModelPosition(
            NativeFunc instance,
            long modelHandle,
            float x,
            float y,
            float z,
            float yaw) {
        if (!MMDSyncNativeBridge.isBridgeHandle(modelHandle)) {
            instance.SetModelPositionAndYaw(modelHandle, x, y, z, yaw);
            return;
        }
        if (MMDSyncNativeBridge.isModelHandleValid(modelHandle)) {
            MMDSyncNativeBridge.setModelPositionAndYaw(modelHandle, x, y, z, yaw);
        }
    }
}
