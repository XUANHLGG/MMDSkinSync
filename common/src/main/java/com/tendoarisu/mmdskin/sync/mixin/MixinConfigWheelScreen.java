package com.tendoarisu.mmdskin.sync.mixin;

import com.opdent.mmdskin.sync.ui.SyncModelSelectorScreen;
import com.shiroha.mmdskin.ui.wheel.ConfigWheelScreen;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ConfigWheelScreen.class, remap = false)
public abstract class MixinConfigWheelScreen {
    @Inject(method = "openModelSelector", at = @At("HEAD"), cancellable = true, remap = false)
    private void mmdsync$openSyncModelSelector(CallbackInfo ci) {
        Minecraft.getInstance().setScreen(new SyncModelSelectorScreen());
        ci.cancel();
    }
}
