package com.tendoarisu.mmdskin.sync.mixin;

import com.opdent.mmdskin.sync.SyncManager;
import com.shiroha.mmdskin.ui.selector.ModelSelectorScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Iterator;
import java.util.List;

@Mixin(value = ModelSelectorScreen.class, remap = false)
public abstract class MixinModelSelectorScreen extends Screen {
    @Shadow(remap = false) @Final private List<Object> modelCards;

    protected MixinModelSelectorScreen(Component title) {
        super(title);
    }

    @Inject(method = "loadAvailableModels", at = @At("HEAD"), remap = false)
    private void onLoadAvailableModelsHead(CallbackInfo ci) {
        modelCards.clear();
        updateModelCount();
    }

    @Inject(method = "loadAvailableModels", at = @At("RETURN"), remap = false)
    private void onLoadAvailableModels(CallbackInfo ci) {
        Iterator<Object> iterator = modelCards.iterator();
        while (iterator.hasNext()) {
            Object card = iterator.next();
            if (!(card instanceof ModelCardEntryAccessor accessor)) {
                continue;
            }
            String displayName = accessor.mmdsync$getDisplayName();
            if (!SyncManager.shouldDisplayModelFolder(displayName)) {
                iterator.remove();
            }
        }
        updateModelCount();
    }

    private void updateModelCount() {
        try {
            java.lang.reflect.Field countField = ModelSelectorScreen.class.getDeclaredField("modelCount");
            countField.setAccessible(true);
            countField.set(this, modelCards.size());
        } catch (Throwable ignored) {
        }
    }
}
