package com.tendoarisu.mmdskin.sync.mixin;

import com.opdent.mmdskin.sync.MMDSyncMod;
import com.shiroha.mmdskin.ui.selector.MaterialVisibilityScreen;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MaterialVisibilityScreen.class, remap = false)
public abstract class MixinMaterialVisibilityScreen {

    @Shadow @Final private com.shiroha.mmdskin.ui.selector.application.MaterialVisibilityApplicationService.MaterialScreenContext context;

    private long mmdsync$modelHandle() {
        try {
            return context != null ? context.modelHandle() : 0L;
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    @Inject(method = "loadMaterials()V", at = @At("HEAD"), cancellable = true, remap = false)
    private void guardLoadMaterials(CallbackInfo ci) {
        long modelHandle = mmdsync$modelHandle();
        if (MMDSyncNativeBridge.isBridgeHandle(modelHandle) && !MMDSyncNativeBridge.isModelHandleValid(modelHandle)) {
            MMDSyncMod.LOGGER.warn("阻止使用过期加密模型句柄读取材质列表: model={}", modelHandle);
            ci.cancel();
        }
    }

}
