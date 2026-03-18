package com.opdent.mmdskin.sync.fabric.mixin;

import com.opdent.mmdskin.sync.SyncManager;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.shiroha.mmdskin.ui.selector.ModelSelectorScreen", remap = false)
public abstract class MixinModelSelectorScreenFabric extends Screen {
    private static final int PANEL_WIDTH = 140;
    private static final int PANEL_MARGIN = 4;
    private static final int FOOTER_HEIGHT = 20;

    protected MixinModelSelectorScreenFabric(Component title) {
        super(title);
    }

    @Inject(method = "method_25426", at = @At("TAIL"), remap = false)
    private void mmdsync$addUploadButton(CallbackInfo ci) {
        addUploadButton();
    }

    private void addUploadButton() {
        int panelX = this.width - PANEL_WIDTH - PANEL_MARGIN;
        int panelY = PANEL_MARGIN;
        int panelH = this.height - PANEL_MARGIN * 2;
        int listBottom = panelY + panelH - FOOTER_HEIGHT;

        int btnW = PANEL_WIDTH - 8;
        int btnH = 14;
        int btnY = listBottom - btnH - 2;

        this.addRenderableWidget(Button.builder(Component.literal("§6上传模型资源"), btn ->
            SyncManager.openUploadSelectionScreen(this)
        ).bounds(panelX + 4, btnY, btnW, btnH).build());
    }
}
