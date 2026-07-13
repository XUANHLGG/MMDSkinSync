package com.opdent.mmdskin.sync.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class SyncProgressOverlay {
    private static final int BAR_WIDTH = 150;
    private static final int BAR_HEIGHT = 10;
    private static final int DEFAULT_TOP_OFFSET = 10;
    private static final int YSM_AVOID_OFFSET = 56;

    private SyncProgressOverlay() {
    }

    public static void render(GuiGraphics guiGraphics) {
        SyncProgressTracker.Snapshot snapshot = SyncProgressTracker.snapshot();
        if (!snapshot.visible()) {
            return;
        }

        Font font = Minecraft.getInstance().font;
        int screenWidth = guiGraphics.guiWidth();
        int x = (screenWidth - BAR_WIDTH) / 2;
        int y = isYsmOverlayVisible() ? YSM_AVOID_OFFSET : DEFAULT_TOP_OFFSET;
        int progressWidth = Math.max(0, Math.min(BAR_WIDTH, (int) (BAR_WIDTH * snapshot.progress())));
        int barColor = snapshot.failed() ? 0xFFE06666 : 0xFF60A0D0;

        guiGraphics.drawCenteredString(font, Component.literal(snapshot.statusText()), screenWidth / 2, y, 0xFFE0E0E0);

        guiGraphics.fill(x, y + 14, x + BAR_WIDTH, y + 14 + BAR_HEIGHT, 0xC0202020);
        guiGraphics.fill(x, y + 14, x + progressWidth, y + 14 + BAR_HEIGHT, barColor);

        String detail = snapshot.totalFiles() > 0
                ? (snapshot.completedFiles() + " / " + snapshot.totalFiles())
                : (snapshot.failed() ? "失败" : "等待中");
        guiGraphics.drawCenteredString(font, Component.literal(detail), screenWidth / 2, y + 27, 0xFFFFFFFF);
    }

    private static boolean isYsmOverlayVisible() {
        if (isYsmOverlayVisible(
                "com.elfmcys.yesstevemodel.OO0oo0O0O0o0oooo00OoO000",
                "ooO0000oO0o0o0o000Oooo0O",
                "ooO0000oO0o0o0o000Oooo0O"
        )) {
            return true;
        }
        return isYsmOverlayVisible(
                "com.elfmcys.yesstevemodel.ooo0O00ooOOooO0O0oOO0oOO",
                "Oo000Oo0oo0oOOOo00oOoOoo",
                "Oo000Oo0oo0oOOOo00oOoOoo"
        );
    }

    private static boolean isYsmOverlayVisible(String stateClassName, String outerGetterName, String innerGetterName) {
        try {
            Class<?> stateClass = Class.forName(stateClassName);
            Object stateHolder = stateClass.getMethod(outerGetterName).invoke(null);
            Object state = stateHolder.getClass().getMethod(innerGetterName).invoke(stateHolder);
            if (!(state instanceof Enum<?> value)) {
                return false;
            }
            return switch (value.name()) {
                case "ooO0000oO0o0o0o000Oooo0O", "Oo000Oo0oo0oOOOo00oOoOoo" -> false;
                default -> true;
            };
        } catch (Throwable ignored) {
            return false;
        }
    }
}
