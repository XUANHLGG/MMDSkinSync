package com.tendoarisu.mmdskin.sync.mixin;

import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.stage.client.playback.DefaultStagePlaybackRuntime;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** 路由默认舞台播放运行时的动画句柄操作，并严格隔离 bridge/核心引擎句柄域。 */
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
    private void mmdsync$routeMergeAnimation(NativeFunc instance, long targetHandle, long sourceHandle) {
        boolean targetBridge = MMDSyncNativeBridge.isBridgeHandle(targetHandle);
        boolean sourceBridge = MMDSyncNativeBridge.isBridgeHandle(sourceHandle);
        if (!targetBridge && !sourceBridge) {
            instance.MergeAnimation(targetHandle, sourceHandle);
        } else if (targetBridge && sourceBridge
                && MMDSyncNativeBridge.isAnimationHandleValid(targetHandle)
                && MMDSyncNativeBridge.isAnimationHandleValid(sourceHandle)) {
            MMDSyncNativeBridge.mergeAnimation(targetHandle, sourceHandle);
        }
        // 混合句柄域的合并无定义，必须拒绝，不能把任一句柄传给另一引擎。
    }
}
