package com.tendoarisu.mmdskin.sync.mixin;

import com.opdent.mmdskin.sync.MMDSyncMod;
import com.shiroha.mmdskin.bridge.runtime.NativeModelPort;
import com.shiroha.mmdskin.bridge.runtime.NativeModelQueryPort;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "com.shiroha.mmdskin.ui.selector.adapter.DefaultMaterialVisibilityGateway", remap = false)
public abstract class MixinMaterialVisibilityGateway {

    @Redirect(
            method = "loadMaterials",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/shiroha/mmdskin/bridge/runtime/NativeModelPort;getMaterialCount(J)I"
            ),
            remap = false
    )
    private int mmdsync$getMaterialCount(NativeModelPort port, long modelHandle) {
        if (!MMDSyncNativeBridge.isBridgeHandle(modelHandle)) {
            return port.getMaterialCount(modelHandle);
        }
        if (!MMDSyncNativeBridge.isModelHandleValid(modelHandle)) {
            MMDSyncMod.LOGGER.warn("阻止读取过期加密模型材质数量: model={}", modelHandle);
            return 0;
        }
        long count = MMDSyncNativeBridge.getMaterialCount(modelHandle);
        return count > 0L && count <= Integer.MAX_VALUE ? (int) count : 0;
    }

    @Redirect(
            method = "loadMaterials",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/shiroha/mmdskin/bridge/runtime/NativeModelQueryPort;getMaterialName(JI)Ljava/lang/String;"
            ),
            remap = false
    )
    private String mmdsync$getMaterialName(NativeModelQueryPort port, long modelHandle, int index) {
        if (!MMDSyncNativeBridge.isBridgeHandle(modelHandle)) {
            return port.getMaterialName(modelHandle, index);
        }
        if (!MMDSyncNativeBridge.isModelHandleValid(modelHandle)) {
            MMDSyncMod.LOGGER.warn("阻止读取过期加密模型材质名称: model={}, index={}", modelHandle, index);
            return "";
        }
        String name = MMDSyncNativeBridge.getMaterialName(modelHandle, index);
        return name != null ? name : "";
    }

    @Redirect(
            method = "loadMaterials",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/shiroha/mmdskin/bridge/runtime/NativeModelQueryPort;isMaterialVisible(JI)Z"
            ),
            remap = false
    )
    private boolean mmdsync$isMaterialVisible(NativeModelQueryPort port, long modelHandle, int index) {
        if (!MMDSyncNativeBridge.isBridgeHandle(modelHandle)) {
            return port.isMaterialVisible(modelHandle, index);
        }
        return MMDSyncNativeBridge.isModelHandleValid(modelHandle)
                && MMDSyncNativeBridge.isMaterialVisible(modelHandle, index);
    }

    @Redirect(
            method = "setAllVisible",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/shiroha/mmdskin/bridge/runtime/NativeModelPort;setAllMaterialsVisible(JZ)V"
            ),
            remap = false
    )
    private void mmdsync$setAllMaterialsVisible(NativeModelPort port, long modelHandle, boolean visible) {
        if (!MMDSyncNativeBridge.isBridgeHandle(modelHandle)) {
            port.setAllMaterialsVisible(modelHandle, visible);
            return;
        }
        if (MMDSyncNativeBridge.isModelHandleValid(modelHandle)) {
            MMDSyncNativeBridge.setAllMaterialsVisible(modelHandle, visible);
        }
    }

    @Redirect(
            method = "setMaterialVisible",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/shiroha/mmdskin/bridge/runtime/NativeModelPort;setMaterialVisible(JIZ)V"
            ),
            remap = false
    )
    private void mmdsync$setMaterialVisible(NativeModelPort port, long modelHandle, int index, boolean visible) {
        if (!MMDSyncNativeBridge.isBridgeHandle(modelHandle)) {
            port.setMaterialVisible(modelHandle, index, visible);
            return;
        }
        if (MMDSyncNativeBridge.isModelHandleValid(modelHandle)) {
            MMDSyncNativeBridge.setMaterialVisible(modelHandle, index, visible);
        }
    }
}
