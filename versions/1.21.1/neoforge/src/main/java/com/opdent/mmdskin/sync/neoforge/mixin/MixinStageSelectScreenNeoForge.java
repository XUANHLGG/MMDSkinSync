package com.opdent.mmdskin.sync.neoforge.mixin;

import com.opdent.mmdskin.sync.MMDSyncMod;
import com.tendoarisu.mmdskin.sync.StagePackRefreshCoordinator;
import com.tendoarisu.mmdskin.sync.util.CryptoUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.shiroha.mmdskin.ui.stage.StageSelectScreen", remap = false)
public abstract class MixinStageSelectScreenNeoForge {
    @Invoker(value = "reloadStagePacks", remap = false)
    protected abstract void mmdsync$invokeReloadStagePacks();

    @Inject(method = "tick()V", at = @At("TAIL"), remap = false)
    private void mmdsync$refreshStagePacksWhenSessionReady(CallbackInfo ci) {
        if (!StagePackRefreshCoordinator.shouldRefreshWhenSessionReady() || !CryptoUtils.hasSessionMaterial()) {
            return;
        }

        try {
            mmdsync$invokeReloadStagePacks();
            StagePackRefreshCoordinator.markRefreshed();
        } catch (Throwable error) {
            MMDSyncMod.LOGGER.warn("刷新 MmdSkin 舞台资源包失败，将保留待刷新状态", error);
        }
    }
}
