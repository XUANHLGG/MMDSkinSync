package com.tendoarisu.mmdskin.sync.mixin;

import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.stage.client.sync.StageAnimSyncHelper;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** 路由远程舞台动画同步路径的动画句柄操作。 */
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
    private static void mmdsync$routeMergeAnimation(NativeFunc instance, long targetHandle, long sourceHandle) {
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
