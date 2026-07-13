package com.opdent.mmdskin.sync.fabric.mixin;

import com.opdent.mmdskin.sync.MMDSyncMod;
import com.tendoarisu.mmdskin.sync.StagePackRefreshCoordinator;
import com.tendoarisu.mmdskin.sync.util.CryptoUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reloads the 1.0.5 stage state once encrypted stage packs become readable. */
@Mixin(targets = "com.shiroha.mmdskin.ui.stage.StageSelectScreen", remap = false)
public abstract class MixinStageSelectScreenFabric {
    private static boolean mmdsync$loggedRuntimeTarget;

    @Invoker(value = "reloadStagePacks", remap = false)
    protected abstract void mmdsync$invokeReloadStagePacks();

    /**
     * {@code remap = false} requires the intermediary runtime name. In the
     * released 1.0.5-1.21.4-1 Fabric JAR, Screen.tick() is method_25393()V.
     */
    @Inject(method = "method_25393()V", at = @At("TAIL"), remap = false)
    private void mmdsync$refreshStagePacksWhenSessionReady(CallbackInfo ci) {
        if (!mmdsync$loggedRuntimeTarget) {
            mmdsync$loggedRuntimeTarget = true;
            MMDSyncMod.LOGGER.info("MMDSync 1.21.4 stage refresh hook applied to StageSelectScreen.method_25393()V");
        }
        if (!StagePackRefreshCoordinator.shouldRefreshWhenSessionReady() || !CryptoUtils.hasSessionMaterial()) {
            return;
        }

        mmdsync$invokeReloadStagePacks();
        StagePackRefreshCoordinator.markRefreshed();
    }
}
