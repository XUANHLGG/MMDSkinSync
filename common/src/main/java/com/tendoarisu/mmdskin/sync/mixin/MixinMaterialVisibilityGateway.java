package com.tendoarisu.mmdskin.sync.mixin;

import com.opdent.mmdskin.sync.MMDSyncMod;
import com.shiroha.mmdskin.NativeFunc;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "com.shiroha.mmdskin.ui.selector.adapter.DefaultMaterialVisibilityGateway", remap = false)
public class MixinMaterialVisibilityGateway {

    @Redirect(
            method = "loadMaterials",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/shiroha/mmdskin/NativeFunc;GetMaterialCount(J)J"
            ),
            remap = false
    )
    private long redirectGetMaterialCount(NativeFunc instance, long modelHandle) {
        if (!MMDSyncNativeBridge.isBridgeHandle(modelHandle)) {
            return instance.GetMaterialCount(modelHandle);
        }
        if (!MMDSyncNativeBridge.isModelHandleValid(modelHandle)) {
            MMDSyncMod.LOGGER.warn("阻止读取过期加密模型材质数量: model={}", modelHandle);
            return 0L;
        }
        return MMDSyncNativeBridge.getMaterialCount(modelHandle);
    }

    @Redirect(
            method = "loadMaterials",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/shiroha/mmdskin/NativeFunc;GetMaterialName(JI)Ljava/lang/String;"
            ),
            remap = false
    )
    private String redirectGetMaterialName(NativeFunc instance, long modelHandle, int index) {
        if (!MMDSyncNativeBridge.isBridgeHandle(modelHandle)) {
            return instance.GetMaterialName(modelHandle, index);
        }
        if (!MMDSyncNativeBridge.isModelHandleValid(modelHandle)) {
            MMDSyncMod.LOGGER.warn("阻止读取过期加密模型材质名称: model={}, index={}", modelHandle, index);
            return "";
        }
        return MMDSyncNativeBridge.getMaterialName(modelHandle, index);
    }

    @Redirect(
            method = "loadMaterials",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/shiroha/mmdskin/NativeFunc;IsMaterialVisible(JI)Z"
            ),
            remap = false
    )
    private boolean redirectIsMaterialVisible(NativeFunc instance, long modelHandle, int index) {
        if (!MMDSyncNativeBridge.isBridgeHandle(modelHandle)) {
            return instance.IsMaterialVisible(modelHandle, index);
        }
        if (!MMDSyncNativeBridge.isModelHandleValid(modelHandle)) {
            MMDSyncMod.LOGGER.warn("阻止读取过期加密模型材质可见性: model={}, index={}", modelHandle, index);
            return false;
        }
        return MMDSyncNativeBridge.isMaterialVisible(modelHandle, index);
    }

    @Redirect(
            method = "setAllVisible",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/shiroha/mmdskin/NativeFunc;SetAllMaterialsVisible(JZ)V"
            ),
            remap = false
    )
    private void redirectSetAllMaterialsVisible(NativeFunc instance, long modelHandle, boolean visible) {
        if (!MMDSyncNativeBridge.isBridgeHandle(modelHandle)) {
            instance.SetAllMaterialsVisible(modelHandle, visible);
            return;
        }
        if (!MMDSyncNativeBridge.isModelHandleValid(modelHandle)) {
            MMDSyncMod.LOGGER.warn("阻止批量设置过期加密模型材质可见性: model={}, visible={}", modelHandle, visible);
            return;
        }
        MMDSyncNativeBridge.setAllMaterialsVisible(modelHandle, visible);
    }

    @Redirect(
            method = "setMaterialVisible",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/shiroha/mmdskin/NativeFunc;SetMaterialVisible(JIZ)V"
            ),
            remap = false
    )
    private void redirectSetMaterialVisible(NativeFunc instance, long modelHandle, int index, boolean visible) {
        if (!MMDSyncNativeBridge.isBridgeHandle(modelHandle)) {
            instance.SetMaterialVisible(modelHandle, index, visible);
            return;
        }
        if (!MMDSyncNativeBridge.isModelHandleValid(modelHandle)) {
            MMDSyncMod.LOGGER.warn("阻止设置过期加密模型单个材质可见性: model={}, index={}, visible={}", modelHandle, index, visible);
            return;
        }
        MMDSyncNativeBridge.setMaterialVisible(modelHandle, index, visible);
    }
}
