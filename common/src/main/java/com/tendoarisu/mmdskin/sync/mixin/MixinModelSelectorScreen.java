package com.tendoarisu.mmdskin.sync.mixin;

import com.opdent.mmdskin.sync.MMDSyncMod;
import com.opdent.mmdskin.sync.SyncManager;
import com.shiroha.mmdskin.ui.selector.ModelSelectorScreen;
import net.minecraft.Util;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.List;

@Mixin(ModelSelectorScreen.class)
public abstract class MixinModelSelectorScreen extends Screen {
    private static final Field MODEL_CARD_DISPLAY_NAME_FIELD;
    private static final Field MODEL_CARD_MODEL_INFO_FIELD;

    static {
        try {
            Class<?> cardClass = Class.forName("com.shiroha.mmdskin.ui.selector.ModelSelectorScreen$ModelCardEntry");
            MODEL_CARD_DISPLAY_NAME_FIELD = cardClass.getDeclaredField("displayName");
            MODEL_CARD_DISPLAY_NAME_FIELD.setAccessible(true);
            MODEL_CARD_MODEL_INFO_FIELD = cardClass.getDeclaredField("modelInfo");
            MODEL_CARD_MODEL_INFO_FIELD.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Shadow(remap = false) @Final private List<Object> modelCards;

    protected MixinModelSelectorScreen(Component title) {
        super(title);
    }

    @Inject(method = "loadAvailableModels", at = @At("RETURN"), remap = false)
    private void onLoadAvailableModels(CallbackInfo ci) {
        int before = modelCards.size();
        Iterator<Object> iterator = modelCards.iterator();
        while (iterator.hasNext()) {
            Object card = iterator.next();
            try {
                Object modelInfo = MODEL_CARD_MODEL_INFO_FIELD.get(card);
                if (modelInfo == null) {
                    continue;
                }
                String displayName = (String) MODEL_CARD_DISPLAY_NAME_FIELD.get(card);
                if (!SyncManager.shouldDisplayModelFolder(displayName)) {
                    iterator.remove();
                }
            } catch (IllegalAccessException e) {
                MMDSyncMod.LOGGER.warn("过滤服务器限定模型列表失败: {}", e.toString());
                return;
            }
        }
    }

    @Inject(method = "init", at = @At("RETURN"), require = 0)
    private void onInit(CallbackInfo ci) {
        // 获取原始界面的关键布局参数 (参考 ModelSelectorScreen 源码)
        int panelWidth = 140; // PANEL_WIDTH
        int panelMargin = 4;  // PANEL_MARGIN
        int footerHeight = 20; // FOOTER_HEIGHT
        
        // 计算面板基准位置
        int panelX = this.width - panelWidth - panelMargin;
        int panelY = panelMargin;
        int panelH = this.height - panelMargin * 2;
        int listBottom = panelY + panelH - footerHeight;

        // "上传模型"按钮布局
        int btnW = panelWidth - 8;
        int btnH = 14;
        // 放在列表底部边缘上方 2 像素 (原版刷新按钮在 listBottom + 4)
        int btnY = listBottom - btnH - 2;

        this.addRenderableWidget(Button.builder(Component.literal("§6上传模型资源"), btn -> {
            String url = SyncManager.getServerUrl();
            if (url != null && !url.isEmpty()) {
                Util.getPlatform().openUri(url);
            }
        }).bounds(panelX + 4, btnY, btnW, btnH).build());
    }
}
