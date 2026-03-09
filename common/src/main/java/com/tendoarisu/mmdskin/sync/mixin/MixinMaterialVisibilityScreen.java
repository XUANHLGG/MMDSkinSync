package com.tendoarisu.mmdskin.sync.mixin;

import com.opdent.mmdskin.sync.MMDSyncMod;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.shiroha.mmdskin.ui.selector.MaterialVisibilityScreen", remap = false)
public abstract class MixinMaterialVisibilityScreen {

    @Shadow @Final private long modelHandle;

    @Inject(method = "loadMaterials", at = @At("HEAD"), cancellable = true)
    private void guardLoadMaterials(CallbackInfo ci) {
        if (modelHandle != 0L && !MMDSyncNativeBridge.isModelHandleValid(modelHandle)) {
            MMDSyncMod.LOGGER.warn("阻止使用过期加密模型句柄读取材质列表: model={}", modelHandle);
            ci.cancel();
        }
    }

    @Inject(method = "setAllVisible", at = @At("HEAD"), cancellable = true)
    private void guardSetAllVisible(boolean visible, CallbackInfo ci) {
        if (modelHandle != 0L && !MMDSyncNativeBridge.isModelHandleValid(modelHandle)) {
            MMDSyncMod.LOGGER.warn("阻止使用过期加密模型句柄批量设置材质可见性: model={}, visible={}", modelHandle, visible);
            ci.cancel();
        }
    }

    @Inject(method = "invertSelection", at = @At("HEAD"), cancellable = true)
    private void guardInvertSelection(CallbackInfo ci) {
        if (modelHandle != 0L && !MMDSyncNativeBridge.isModelHandleValid(modelHandle)) {
            MMDSyncMod.LOGGER.warn("阻止使用过期加密模型句柄反选材质可见性: model={}", modelHandle);
            ci.cancel();
        }
    }

    @Inject(method = "toggleMaterial", at = @At("HEAD"), cancellable = true)
    private void guardToggleMaterial(int index, CallbackInfo ci) {
        if (modelHandle != 0L && !MMDSyncNativeBridge.isModelHandleValid(modelHandle)) {
            MMDSyncMod.LOGGER.warn("阻止使用过期加密模型句柄切换单个材质可见性: model={}, index={}", modelHandle, index);
            ci.cancel();
        }
    }
}
