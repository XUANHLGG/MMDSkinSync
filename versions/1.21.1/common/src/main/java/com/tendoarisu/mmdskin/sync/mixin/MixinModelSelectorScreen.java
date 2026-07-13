package com.tendoarisu.mmdskin.sync.mixin;

import com.opdent.mmdskin.sync.SyncManager;
import com.shiroha.mmdskin.ui.selector.application.ModelSelectionApplicationService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** 过滤 1.0.5 应用服务返回的模型卡片，避免依赖已删除的 UI 内部类。 */
@Mixin(value = ModelSelectionApplicationService.class, remap = false)
public abstract class MixinModelSelectorScreen {
    @Inject(method = "loadModelCards()Ljava/util/List;", at = @At("RETURN"), cancellable = true, remap = false)
    private void mmdsync$filterModelCards(CallbackInfoReturnable<List<ModelSelectionApplicationService.ModelCard>> cir) {
        List<ModelSelectionApplicationService.ModelCard> filtered = cir.getReturnValue().stream()
                .filter(card -> !card.configurable() || SyncManager.shouldDisplayModelFolder(card.displayName()))
                .toList();
        cir.setReturnValue(filtered);
    }
}
