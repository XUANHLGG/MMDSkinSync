package com.tendoarisu.mmdskin.sync.mixin;

import com.opdent.mmdskin.sync.MMDSyncMod;
import com.shiroha.mmdskin.renderer.runtime.model.AbstractMMDModel;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AbstractMMDModel.class, remap = false)
public abstract class MixinAbstractMMDModel {

    @Shadow protected long model;

    @Inject(method = "changeAnim(JJ)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void guardChangeAnim(long anim, long layer, CallbackInfo ci) {
        if (MMDSyncNativeBridge.isBridgeHandle(model)) {
            if (!MMDSyncNativeBridge.isModelHandleValid(model)) {
                MMDSyncMod.LOGGER.warn("阻止抽象模型对过期加密模型句柄切换动画: model={}, anim={}, layer={}", model, anim, layer);
                ci.cancel();
                return;
            }
            if (MMDSyncNativeBridge.isBridgeHandle(anim) && !MMDSyncNativeBridge.isAnimationHandleValid(anim)) {
                MMDSyncMod.LOGGER.warn("阻止抽象模型对过期加密动画句柄切换动画: model={}, anim={}, layer={}", model, anim, layer);
                ci.cancel();
                return;
            }
            MMDSyncNativeBridge.changeModelAnim(model, anim, layer);
            ci.cancel();
            return;
        }
        if (MMDSyncNativeBridge.isBridgeHandle(anim)) {
            MMDSyncMod.LOGGER.warn("阻止普通模型使用桥接动画句柄: model={}, anim={}, layer={}", model, anim, layer);
            ci.cancel();
        }
    }

    @Inject(method = "transitionAnim(JJF)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void guardTransitionAnim(long anim, long layer, float transitionTime, CallbackInfo ci) {
        if (MMDSyncNativeBridge.isBridgeHandle(model)) {
            if (!MMDSyncNativeBridge.isModelHandleValid(model)) {
                MMDSyncMod.LOGGER.warn("阻止抽象模型对过期加密模型句柄做动画过渡: model={}, anim={}, layer={}", model, anim, layer);
                ci.cancel();
                return;
            }
            if (MMDSyncNativeBridge.isBridgeHandle(anim) && !MMDSyncNativeBridge.isAnimationHandleValid(anim)) {
                MMDSyncMod.LOGGER.warn("阻止抽象模型对过期加密动画句柄做动画过渡: model={}, anim={}, layer={}", model, anim, layer);
                ci.cancel();
                return;
            }
            MMDSyncNativeBridge.transitionLayerTo(model, anim, layer, transitionTime);
            ci.cancel();
            return;
        }
        if (MMDSyncNativeBridge.isBridgeHandle(anim)) {
            MMDSyncMod.LOGGER.warn("阻止普通模型使用桥接动画句柄过渡动画: model={}, anim={}, layer={}", model, anim, layer);
            ci.cancel();
        }
    }

    @Inject(method = "setLayerLoop(JZ)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void guardSetLayerLoop(long layer, boolean loop, CallbackInfo ci) {
        if (MMDSyncNativeBridge.isBridgeHandle(model)) {
            if (!MMDSyncNativeBridge.isModelHandleValid(model)) {
                MMDSyncMod.LOGGER.warn("阻止对过期加密模型句柄设置图层循环: model={}, layer={}, loop={}", model, layer, loop);
                ci.cancel();
                return;
            }
            MMDSyncNativeBridge.setLayerLoop(model, layer, loop);
            ci.cancel();
        }
    }

    @Inject(method = "resetPhysics()V", at = @At("HEAD"), cancellable = true, remap = false)
    private void guardResetPhysics(CallbackInfo ci) {
        if (MMDSyncNativeBridge.isBridgeHandle(model)) {
            if (!MMDSyncNativeBridge.isModelHandleValid(model)) {
                MMDSyncMod.LOGGER.warn("阻止对过期加密模型句柄重置物理: model={}", model);
                ci.cancel();
                return;
            }
            MMDSyncNativeBridge.resetPhysics(model);
            ci.cancel();
        }
    }
}
