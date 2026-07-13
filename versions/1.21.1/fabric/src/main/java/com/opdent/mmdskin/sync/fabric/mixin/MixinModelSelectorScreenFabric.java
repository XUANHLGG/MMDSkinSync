package com.opdent.mmdskin.sync.fabric.mixin;

import com.opdent.mmdskin.sync.MMDSyncMod;
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
public abstract class MixinModelSelectorScreenFabric extends Screen {
    private static boolean mmdsync$loggedRuntimeTargets;
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

    protected MixinModelSelectorScreenFabric(Component title) {
        super(title);
    }

    /** Fabric production uses intermediary names for methods inherited from Screen. */
    @Inject(
            method = "method_25394(Lnet/minecraft/class_332;IIF)V",
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
        if (!mmdsync$loggedRuntimeTargets) {
            mmdsync$loggedRuntimeTargets = true;
            MMDSyncMod.LOGGER.info(
                    "MMDSync 1.21.1 Fabric selector hooks resolved to method_25394(class_332,I,I,F) and method_25402(D,D,I)"
            );
        }
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

    @Inject(method = "method_25402(DDI)Z", at = @At("HEAD"), cancellable = true, remap = false)
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
