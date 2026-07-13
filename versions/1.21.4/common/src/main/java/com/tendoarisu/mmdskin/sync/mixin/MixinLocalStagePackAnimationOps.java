package com.tendoarisu.mmdskin.sync.mixin;

import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.stage.client.asset.LocalStagePackRepository;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Routes local stage-pack inspection queries and release across the animation handle domains. */
@Mixin(value = LocalStagePackRepository.class, remap = false)
public abstract class MixinLocalStagePackAnimationOps {

    @Redirect(
            method = "lambda$loadStagePacks$0(Ljava/lang/String;)[Z",
            at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;HasCameraData(J)Z"),
            remap = false
    )
    private static boolean mmdsync$routeHasCameraData(NativeFunc instance, long animationHandle) {
        if (!MMDSyncNativeBridge.isBridgeHandle(animationHandle)) {
            return instance.HasCameraData(animationHandle);
        }
        return MMDSyncNativeBridge.isAnimationHandleValid(animationHandle)
                && MMDSyncNativeBridge.hasCameraData(animationHandle);
    }

    @Redirect(
            method = "lambda$loadStagePacks$0(Ljava/lang/String;)[Z",
            at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;HasBoneData(J)Z"),
            remap = false
    )
    private static boolean mmdsync$routeHasBoneData(NativeFunc instance, long animationHandle) {
        if (!MMDSyncNativeBridge.isBridgeHandle(animationHandle)) {
            return instance.HasBoneData(animationHandle);
        }
        return MMDSyncNativeBridge.isAnimationHandleValid(animationHandle)
                && MMDSyncNativeBridge.hasBoneData(animationHandle);
    }

    @Redirect(
            method = "lambda$loadStagePacks$0(Ljava/lang/String;)[Z",
            at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;HasMorphData(J)Z"),
            remap = false
    )
    private static boolean mmdsync$routeHasMorphData(NativeFunc instance, long animationHandle) {
        if (!MMDSyncNativeBridge.isBridgeHandle(animationHandle)) {
            return instance.HasMorphData(animationHandle);
        }
        return MMDSyncNativeBridge.isAnimationHandleValid(animationHandle)
                && MMDSyncNativeBridge.hasMorphData(animationHandle);
    }

    @Redirect(
            method = "lambda$loadStagePacks$0(Ljava/lang/String;)[Z",
            at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;DeleteAnimation(J)V"),
            remap = false
    )
    private static void mmdsync$routeDeleteAnimation(NativeFunc instance, long animationHandle) {
        if (!MMDSyncNativeBridge.isBridgeHandle(animationHandle)) {
            instance.DeleteAnimation(animationHandle);
            return;
        }
        if (MMDSyncNativeBridge.isAnimationHandleValid(animationHandle)) {
            MMDSyncNativeBridge.deleteAnimation(animationHandle);
        }
    }
}
