package com.tendoarisu.mmdskin.sync.mixin;

import com.opdent.mmdskin.sync.MMDSyncMod;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.shiroha.mmdskin.renderer.model.AbstractMMDModel", remap = false)
public abstract class MixinAbstractMMDModel {

    @Shadow protected long model;

    @Inject(method = "changeAnim", at = @At("HEAD"), cancellable = true)
    private void guardChangeAnim(long anim, long layer, CallbackInfo ci) {
        if (model != 0L && !MMDSyncNativeBridge.isModelHandleValid(model)) {
            MMDSyncMod.LOGGER.warn("阻止抽象模型对过期加密模型句柄切换动画: model={}, anim={}, layer={}", model, anim, layer);
            ci.cancel();
            return;
        }
        if (anim != 0L && !MMDSyncNativeBridge.isAnimationHandleValid(anim)) {
            MMDSyncMod.LOGGER.warn("阻止抽象模型对过期加密动画句柄切换动画: model={}, anim={}, layer={}", model, anim, layer);
            ci.cancel();
        }
    }

    @Inject(method = "transitionAnim", at = @At("HEAD"), cancellable = true)
    private void guardTransitionAnim(long anim, long layer, float transitionTime, CallbackInfo ci) {
        if (model != 0L && !MMDSyncNativeBridge.isModelHandleValid(model)) {
            MMDSyncMod.LOGGER.warn("阻止抽象模型对过期加密模型句柄做动画过渡: model={}, anim={}, layer={}", model, anim, layer);
            ci.cancel();
            return;
        }
        if (anim != 0L && !MMDSyncNativeBridge.isAnimationHandleValid(anim)) {
            MMDSyncMod.LOGGER.warn("阻止抽象模型对过期加密动画句柄做动画过渡: model={}, anim={}, layer={}", model, anim, layer);
            ci.cancel();
        }
    }

    @Inject(method = "setLayerLoop", at = @At("HEAD"), cancellable = true)
    private void guardSetLayerLoop(long layer, boolean loop, CallbackInfo ci) {
        if (model != 0L && !MMDSyncNativeBridge.isModelHandleValid(model)) {
            MMDSyncMod.LOGGER.warn("阻止对过期加密模型句柄设置图层循环: model={}, layer={}, loop={}", model, layer, loop);
            ci.cancel();
        }
    }

    @Inject(method = "resetPhysics", at = @At("HEAD"), cancellable = true)
    private void guardResetPhysics(CallbackInfo ci) {
        if (model != 0L && !MMDSyncNativeBridge.isModelHandleValid(model)) {
            MMDSyncMod.LOGGER.warn("阻止对过期加密模型句柄重置物理: model={}", model);
            ci.cancel();
        }
    }
}
