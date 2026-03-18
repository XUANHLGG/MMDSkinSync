package com.tendoarisu.mmdskin.sync.mixin;

import com.opdent.mmdskin.sync.MMDSyncMod;
import com.shiroha.mmdskin.NativeFunc;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 新上游（已观察到的包名迁移）：com.shiroha.mmdskin.stage.client.sync.StageAnimSyncHelper
 *
 * 注意：该类在旧上游依赖中不存在，因此这里用 @Pseudo 避免编译期/运行期因 target 不存在而失败。
 */
@Pseudo
@Mixin(targets = "com.shiroha.mmdskin.stage.client.sync.StageAnimSyncHelper", remap = false)
public abstract class MixinStageAnimSyncHelperNew {

    /**
     * 上游 1.0.4 的 StageAnimSyncHelper 不再直接调用 TransitionLayerTo（改为走 IMMDModel#transitionAnim），
     * 因此这里改为守卫其仍然会直接调用的 SeekLayer。
     */
    @Redirect(
            method = {"syncAllRemoteStageFrame", "syncLocalStageFrame", "applyStageAnim"},
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
