package com.tendoarisu.mmdskin.sync.mixin;

import com.opdent.mmdskin.sync.MMDSyncMod;
import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.stage.client.sync.StageAnimSyncHelper;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = StageAnimSyncHelper.class, remap = false)
public abstract class MixinStageAnimSyncHelperNew {

    @Redirect(
            method = {
                    "syncAllRemoteStageFrame(F)V",
                    "syncLocalStageFrame(F)V",
                    "applyStageAnim(Ljava/util/UUID;Lcom/shiroha/mmdskin/player/model/PlayerModelResolver$Result;Lcom/shiroha/mmdskin/stage/domain/model/StageDescriptor;)V"
            },
            at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;SeekLayer(JJF)V"),
            remap = false
    )
    private static void guardSeekLayer(NativeFunc instance, long modelHandle, long layer, float frame) {
        if (!MMDSyncNativeBridge.isBridgeHandle(modelHandle)) {
            instance.SeekLayer(modelHandle, layer, frame);
            return;
        }
        if (!MMDSyncNativeBridge.isModelHandleValid(modelHandle)) {
            MMDSyncMod.LOGGER.warn("阻止使用过期加密模型句柄同步舞台帧(新包名): model={}, layer={}, frame={}", modelHandle, layer, frame);
            return;
        }
        MMDSyncNativeBridge.seekLayer(modelHandle, layer, frame);
    }
}
