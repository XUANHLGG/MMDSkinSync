package com.tendoarisu.mmdskin.sync.mixin;

import com.opdent.mmdskin.sync.MMDSyncMod;
import com.shiroha.mmdskin.config.ModelConfigData;
import com.shiroha.mmdskin.config.ModelConfigManager;
import com.shiroha.mmdskin.renderer.runtime.model.MMDModelManager;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MMDModelManager.class, remap = false)
public abstract class MixinRuntimeMMDModelManager {

    @Inject(
            method = "applyMaterialVisibility(JLjava/lang/String;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void mmdsync$applyBridgeMaterialVisibility(long modelHandle, String modelName, CallbackInfo ci) {
        if (!MMDSyncNativeBridge.isBridgeHandle(modelHandle)) {
            return;
        }

        try {
            if (!MMDSyncNativeBridge.isModelHandleValid(modelHandle)) {
                MMDSyncMod.LOGGER.warn(
                        "阻止对过期加密模型句柄应用材质可见性: model={}, name={}",
                        modelHandle,
                        modelName
                );
                return;
            }

            ModelConfigData config = ModelConfigManager.getLiveConfig(modelName);
            if (config.hiddenMaterials == null || config.hiddenMaterials.isEmpty()) {
                return;
            }

            long materialCount = MMDSyncNativeBridge.getMaterialCount(modelHandle);
            for (int index : config.hiddenMaterials) {
                if (index >= 0 && index < materialCount) {
                    MMDSyncNativeBridge.setMaterialVisible(modelHandle, index, false);
                }
            }
        } catch (LinkageError | RuntimeException exception) {
            MMDSyncMod.LOGGER.warn(
                    "桥接模型材质可见性恢复失败: model={}, name={}",
                    modelHandle,
                    modelName,
                    exception
            );
        } finally {
            ci.cancel();
        }
    }
}
