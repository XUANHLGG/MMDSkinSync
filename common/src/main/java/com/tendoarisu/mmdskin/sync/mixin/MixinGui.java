package com.tendoarisu.mmdskin.sync.mixin;

import com.opdent.mmdskin.sync.ui.SyncProgressOverlay;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class MixinGui {
    @Inject(method = "render", at = @At("TAIL"))
    private void mmdsync$renderSyncProgress(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        SyncProgressOverlay.render(guiGraphics);
    }
}
