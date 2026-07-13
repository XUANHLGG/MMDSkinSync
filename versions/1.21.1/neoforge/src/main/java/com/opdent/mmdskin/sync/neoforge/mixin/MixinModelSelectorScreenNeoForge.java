package com.opdent.mmdskin.sync.neoforge.mixin;

import com.opdent.mmdskin.sync.SyncManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.shiroha.mmdskin.ui.selector.ModelSelectorScreen", remap = false)
public abstract class MixinModelSelectorScreenNeoForge extends Screen {
    private static final int MMDSYNC_PANEL_MARGIN = 10;
    private static final int MMDSYNC_MIN_PANEL_WIDTH = 150;
    private static final int MMDSYNC_MAX_PANEL_WIDTH = 190;
    private static final int MMDSYNC_UPLOAD_WIDTH = 72;
    private static final int MMDSYNC_UPLOAD_HEIGHT = 16;
    private static final int MMDSYNC_UPLOAD_GAP = 4;
    private static final int MMDSYNC_UPLOAD_TOP_OFFSET = 8;
    private static final int MMDSYNC_BUTTON_BACKGROUND = 0x30000000;
    private static final int MMDSYNC_BUTTON_HOVER = 0x4AFFFFFF;
    private static final int MMDSYNC_BUTTON_TEXT = 0xFFF1F5FB;

    protected MixinModelSelectorScreenNeoForge(Component title) {
        super(title);
    }

    @Inject(
            method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            at = @At("TAIL"),
            remap = false
    )
    private void mmdsync$renderUploadAction(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo ci
    ) {
        UploadRect upload = mmdsync$uploadRect();
        boolean hovered = upload.contains(mouseX, mouseY);
        graphics.fill(
                upload.x(),
                upload.y(),
                upload.x() + upload.width(),
                upload.y() + upload.height(),
                hovered ? MMDSYNC_BUTTON_HOVER : MMDSYNC_BUTTON_BACKGROUND
        );
        graphics.drawCenteredString(
                this.font,
                Component.literal("上传资源"),
                upload.x() + upload.width() / 2,
                upload.y() + Math.max(0, (upload.height() - this.font.lineHeight) / 2),
                MMDSYNC_BUTTON_TEXT
        );
    }

    @Inject(method = "mouseClicked(DDI)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void mmdsync$openUpload(
            double mouseX,
            double mouseY,
            int button,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (button == 0 && mmdsync$uploadRect().contains(mouseX, mouseY)) {
            SyncManager.openUploadSelectionScreen(this);
            cir.setReturnValue(true);
        }
    }

    private UploadRect mmdsync$uploadRect() {
        int panelWidth = Math.max(
                MMDSYNC_MIN_PANEL_WIDTH,
                Math.min(MMDSYNC_MAX_PANEL_WIDTH, Math.round(this.width * 0.14f))
        );
        int panelX = this.width - panelWidth - MMDSYNC_PANEL_MARGIN;
        int x = Math.max(4, panelX - MMDSYNC_UPLOAD_GAP - MMDSYNC_UPLOAD_WIDTH);
        return new UploadRect(
                x,
                MMDSYNC_PANEL_MARGIN + MMDSYNC_UPLOAD_TOP_OFFSET,
                MMDSYNC_UPLOAD_WIDTH,
                MMDSYNC_UPLOAD_HEIGHT
        );
    }

    private record UploadRect(int x, int y, int width, int height) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }
}
