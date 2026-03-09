package com.tendoarisu.mmdskin.sync.mixin;

import com.opdent.mmdskin.sync.MMDSyncMod;
import com.shiroha.mmdskin.NativeFunc;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "com.shiroha.mmdskin.renderer.render.StageAnimSyncHelper", remap = false)
public abstract class MixinStageAnimSyncHelper {

    @Redirect(
            method = "startStageAnim",
            at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;TransitionLayerTo(JJJF)V")
    )
    private static void guardTransitionLayerTo(NativeFunc instance, long modelHandle, long layer, long animHandle, float transitionTime) {
        if (modelHandle != 0L && !MMDSyncNativeBridge.isModelHandleValid(modelHandle)) {
            MMDSyncMod.LOGGER.warn("阻止使用过期加密模型句柄启动舞台动画过渡: model={}, anim={}, layer={}", modelHandle, animHandle, layer);
            return;
        }
        if (animHandle != 0L && !MMDSyncNativeBridge.isAnimationHandleValid(animHandle)) {
            MMDSyncMod.LOGGER.warn("阻止使用过期加密动画句柄启动舞台动画过渡: model={}, anim={}, layer={}", modelHandle, animHandle, layer);
            return;
        }
        instance.TransitionLayerTo(modelHandle, layer, animHandle, transitionTime);
    }
}
