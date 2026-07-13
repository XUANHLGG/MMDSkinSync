package com.tendoarisu.mmdskin.sync.mixin;

import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.stage.client.sync.StageAnimSyncHelper;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Routes safe remote-stage animation release and rejects unsupported 79-JNI merges. */
@Mixin(value = StageAnimSyncHelper.class, remap = false)
public abstract class MixinStageAnimSyncAnimationOps {

    @Redirect(
            method = {
                    "onDisconnect()V",
                    "loadAndMergeAnimations(Ljava/io/File;Ljava/util/List;)J",
                    "cleanupRemoteStageAnim(Ljava/util/UUID;)V"
            },
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

    @Redirect(
            method = "loadAndMergeAnimations(Ljava/io/File;Ljava/util/List;)J",
            at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;MergeAnimation(JJ)V"),
            remap = false
    )
    private static void mmdsync$rejectUnsupportedBridgeMerge(
            NativeFunc instance,
            long targetHandle,
            long sourceHandle) {
        if (!MMDSyncNativeBridge.isBridgeHandle(targetHandle)
                && !MMDSyncNativeBridge.isBridgeHandle(sourceHandle)) {
            instance.MergeAnimation(targetHandle, sourceHandle);
        }
        // 79-JNI has no merge export. Mixed or bridge-only handle domains are rejected safely.
    }
}
