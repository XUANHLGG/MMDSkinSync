package com.opdent.mmdskin.sync.ui;

import com.opdent.mmdskin.sync.MMDSyncMod;
import com.opdent.mmdskin.sync.SyncManager;
import com.shiroha.mmdskin.ui.selector.ModelSelectorScreen;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.List;

public class SyncModelSelectorScreen extends ModelSelectorScreen {

    public SyncModelSelectorScreen() {
        super();
    }

    @Override
    protected void init() {
        filterModelCards();
        super.init();
    }

    @SuppressWarnings("unchecked")
    private void filterModelCards() {
        try {
            Field modelCardsField = ModelSelectorScreen.class.getDeclaredField("modelCards");
            modelCardsField.setAccessible(true);
            Object value = modelCardsField.get(this);
            if (!(value instanceof List<?> rawCards)) {
                return;
            }

            List<Object> modelCards = (List<Object>) rawCards;
            Iterator<Object> iterator = modelCards.iterator();
            while (iterator.hasNext()) {
                Object card = iterator.next();
                String displayName = readDisplayName(card);
                if (displayName != null && !SyncManager.shouldDisplayModelFolder(displayName)) {
                    iterator.remove();
                }
            }
            updateModelCount(modelCards.size());
        } catch (Throwable t) {
            MMDSyncMod.LOGGER.warn("过滤模型列表失败: {}", t.toString());
        }
    }

    private String readDisplayName(Object card) {
        if (card == null) {
            return null;
        }
        try {
            Field displayNameField = card.getClass().getDeclaredField("displayName");
            displayNameField.setAccessible(true);
            Object value = displayNameField.get(card);
            return value instanceof String str ? str : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void updateModelCount(int size) {
        try {
            Field countField = ModelSelectorScreen.class.getDeclaredField("modelCount");
            countField.setAccessible(true);
            countField.set(this, size);
        } catch (Throwable ignored) {
        }
    }
}
