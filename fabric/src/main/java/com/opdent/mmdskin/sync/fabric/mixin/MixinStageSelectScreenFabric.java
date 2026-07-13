package com.opdent.mmdskin.sync.fabric.mixin;

import com.tendoarisu.mmdskin.sync.StagePackRefreshCoordinator;
import com.tendoarisu.mmdskin.sync.util.CryptoUtils;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Pseudo
@Mixin(targets = "com.shiroha.mmdskin.ui.stage.StageSelectScreen", remap = false)
public abstract class MixinStageSelectScreenFabric extends Screen {
    @Shadow(remap = false) private List<Object> stagePacks;
    @Shadow(remap = false) private int selectedPackIndex;
    @Shadow(remap = false) private String selectedHostMotionFileName;

    protected MixinStageSelectScreenFabric(Component title) {
        super(title);
    }

    @Inject(method = "method_25393", at = @At("HEAD"), remap = false)
    private void mmdsync$refreshStagePacksWhenSessionReady(CallbackInfo ci) {
        if (!StagePackRefreshCoordinator.shouldRefreshWhenSessionReady() || !CryptoUtils.hasSessionMaterial()) {
            return;
        }
        StagePackRefreshCoordinator.markRefreshed();
        mmdsync$reloadStagePacks();
    }

    @SuppressWarnings("unchecked")
    private void mmdsync$reloadStagePacks() {
        try {
            Class<?> repositoryClass = Class.forName("com.shiroha.mmdskin.stage.client.asset.LocalStagePackRepository");
            Object repository = repositoryClass.getMethod("getInstance").invoke(null);
            Object loaded = repositoryClass.getMethod("loadStagePacks").invoke(repository);
            if (!(loaded instanceof List<?> rawList)) {
                return;
            }

            this.stagePacks = (List<Object>) rawList;
            if (this.stagePacks.isEmpty()) {
                this.selectedPackIndex = -1;
                this.selectedHostMotionFileName = null;
                return;
            }
            if (this.selectedPackIndex < 0 || this.selectedPackIndex >= this.stagePacks.size()) {
                this.selectedPackIndex = 0;
            }
        } catch (Throwable ignored) {
        }
    }
}
