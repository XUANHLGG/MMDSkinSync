package com.opdent.mmdskin.sync.ui;

import com.opdent.mmdskin.sync.SyncManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class ResourceUploadScreen extends Screen {
    private final Screen parent;
    private SyncManager.UploadZone selectedZone = SyncManager.UploadZone.PMX;
    private SyncManager.UploadSourceKind selectedSourceKind = SyncManager.UploadSourceKind.ZIP;

    public ResourceUploadScreen(Screen parent) {
        super(Component.literal("上传模型资源"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rebuildLayout();
    }

    private void rebuildLayout() {
        this.clearWidgets();
        int centerX = this.width / 2;
        int startY = this.height / 4;
        int buttonWidth = 120;
        int buttonHeight = 20;
        int gap = 6;

        Button pmxButton = Button.builder(buildZoneLabel(SyncManager.UploadZone.PMX), btn -> {
                    selectedZone = SyncManager.UploadZone.PMX;
                    rebuildLayout();
                })
                .bounds(centerX - buttonWidth - gap / 2, startY + 20, buttonWidth, buttonHeight)
                .build();
        pmxButton.active = selectedZone != SyncManager.UploadZone.PMX;
        this.addRenderableWidget(pmxButton);

        Button vmdButton = Button.builder(buildZoneLabel(SyncManager.UploadZone.VMD), btn -> {
                    selectedZone = SyncManager.UploadZone.VMD;
                    rebuildLayout();
                })
                .bounds(centerX + gap / 2, startY + 20, buttonWidth, buttonHeight)
                .build();
        vmdButton.active = selectedZone != SyncManager.UploadZone.VMD;
        this.addRenderableWidget(vmdButton);

        int sourceButtonWidth = 78;
        int sourceRowY = startY + 64;
        int totalWidth = sourceButtonWidth * 3 + gap * 2;
        int sourceStartX = centerX - totalWidth / 2;

        Button zipButton = Button.builder(buildSourceLabel(SyncManager.UploadSourceKind.ZIP), btn -> {
                    selectedSourceKind = SyncManager.UploadSourceKind.ZIP;
                    rebuildLayout();
                })
                .bounds(sourceStartX, sourceRowY, sourceButtonWidth, buttonHeight)
                .build();
        zipButton.active = selectedSourceKind != SyncManager.UploadSourceKind.ZIP;
        this.addRenderableWidget(zipButton);

        Button singleFileButton = Button.builder(buildSourceLabel(SyncManager.UploadSourceKind.SINGLE_FILE), btn -> {
                    selectedSourceKind = SyncManager.UploadSourceKind.SINGLE_FILE;
                    rebuildLayout();
                })
                .bounds(sourceStartX + sourceButtonWidth + gap, sourceRowY, sourceButtonWidth, buttonHeight)
                .build();
        singleFileButton.active = selectedSourceKind != SyncManager.UploadSourceKind.SINGLE_FILE;
        this.addRenderableWidget(singleFileButton);

        Button directoryButton = Button.builder(buildSourceLabel(SyncManager.UploadSourceKind.DIRECTORY), btn -> {
                    selectedSourceKind = SyncManager.UploadSourceKind.DIRECTORY;
                    rebuildLayout();
                })
                .bounds(sourceStartX + (sourceButtonWidth + gap) * 2, sourceRowY, sourceButtonWidth, buttonHeight)
                .build();
        directoryButton.active = selectedSourceKind != SyncManager.UploadSourceKind.DIRECTORY;
        this.addRenderableWidget(directoryButton);

        this.addRenderableWidget(Button.builder(Component.literal("选择并上传"), btn -> SyncManager.openUploadDialogAndUpload(this.parent, selectedZone, selectedSourceKind))
                .bounds(centerX - 75, startY + 108, 150, buttonHeight)
                .build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, btn -> this.minecraft.setScreen(this.parent))
                .bounds(centerX - 75, startY + 134, 150, buttonHeight)
                .build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        int centerX = this.width / 2;
        int startY = this.height / 4;
        guiGraphics.drawCenteredString(this.font, this.title, centerX, startY - 18, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font,
                Component.literal("资源类型: " + selectedZone.displayName()),
                centerX,
                startY,
                0xA0E0FF);
        guiGraphics.drawCenteredString(this.font,
                Component.literal("上传方式: " + selectedSourceKind.displayName()),
                centerX,
                startY + 10,
                0xA0E0FF);
    }

    private Component buildZoneLabel(SyncManager.UploadZone zone) {
        if (selectedZone == zone) {
            return Component.literal("§8● " + zone.displayName());
        }
        return Component.literal(zone.displayName());
    }

    private Component buildSourceLabel(SyncManager.UploadSourceKind sourceKind) {
        if (selectedSourceKind == sourceKind) {
            return Component.literal("§8● " + sourceKind.displayName());
        }
        return Component.literal(sourceKind.displayName());
    }
}
