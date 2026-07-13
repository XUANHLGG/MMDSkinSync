package com.tendoarisu.mmdskin.sync.mixin;

import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.stage.client.playback.DefaultStagePlaybackRuntime;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Routes safe default-stage animation operations and rejects unsupported 79-JNI merges. */
@Mixin(value = DefaultStagePlaybackRuntime.class, remap = false)
public abstract class MixinStageRuntimeAnimationOps {

    @Redirect(
            method = {
                    "startHostPlayback(Lcom/shiroha/mmdskin/config/StagePack;ZFLjava/lang/String;)Lcom/shiroha/mmdskin/stage/client/playback/port/StagePlaybackRuntimePort$HostStartResult;",
                    "startGuestPlayback(Ljava/util/UUID;Lcom/shiroha/mmdskin/stage/client/playback/StagePlaybackStartRequest;Z)Lcom/shiroha/mmdskin/stage/client/playback/port/StagePlaybackRuntimePort$GuestStartResult;",
                    "cleanupHandles(Lcom/shiroha/mmdskin/NativeFunc;JJ)V"
            },
            at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;DeleteAnimation(J)V"),
            remap = false
    )
    private void mmdsync$routeDeleteAnimation(NativeFunc instance, long animationHandle) {
        if (!MMDSyncNativeBridge.isBridgeHandle(animationHandle)) {
            instance.DeleteAnimation(animationHandle);
            return;
        }
        if (MMDSyncNativeBridge.isAnimationHandleValid(animationHandle)) {
            MMDSyncNativeBridge.deleteAnimation(animationHandle);
        }
    }

    @Redirect(
            method = "startGuestPlayback(Ljava/util/UUID;Lcom/shiroha/mmdskin/stage/client/playback/StagePlaybackStartRequest;Z)Lcom/shiroha/mmdskin/stage/client/playback/port/StagePlaybackRuntimePort$GuestStartResult;",
            at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;HasBoneData(J)Z"),
            remap = false
    )
    private boolean mmdsync$routeHasBoneData(NativeFunc instance, long animationHandle) {
        if (!MMDSyncNativeBridge.isBridgeHandle(animationHandle)) {
            return instance.HasBoneData(animationHandle);
        }
        return MMDSyncNativeBridge.isAnimationHandleValid(animationHandle)
                && MMDSyncNativeBridge.hasBoneData(animationHandle);
    }

    @Redirect(
            method = "startGuestPlayback(Ljava/util/UUID;Lcom/shiroha/mmdskin/stage/client/playback/StagePlaybackStartRequest;Z)Lcom/shiroha/mmdskin/stage/client/playback/port/StagePlaybackRuntimePort$GuestStartResult;",
            at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;HasMorphData(J)Z"),
            remap = false
    )
    private boolean mmdsync$routeHasMorphData(NativeFunc instance, long animationHandle) {
        if (!MMDSyncNativeBridge.isBridgeHandle(animationHandle)) {
            return instance.HasMorphData(animationHandle);
        }
        return MMDSyncNativeBridge.isAnimationHandleValid(animationHandle)
                && MMDSyncNativeBridge.hasMorphData(animationHandle);
    }

    @Redirect(
            method = {
                    "startHostPlayback(Lcom/shiroha/mmdskin/config/StagePack;ZFLjava/lang/String;)Lcom/shiroha/mmdskin/stage/client/playback/port/StagePlaybackRuntimePort$HostStartResult;",
                    "startGuestPlayback(Ljava/util/UUID;Lcom/shiroha/mmdskin/stage/client/playback/StagePlaybackStartRequest;Z)Lcom/shiroha/mmdskin/stage/client/playback/port/StagePlaybackRuntimePort$GuestStartResult;"
            },
            at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;MergeAnimation(JJ)V"),
            remap = false
    )
    private void mmdsync$rejectUnsupportedBridgeMerge(
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
