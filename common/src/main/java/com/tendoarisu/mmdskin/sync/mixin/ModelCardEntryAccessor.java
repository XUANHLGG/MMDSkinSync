package com.tendoarisu.mmdskin.sync.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "com.shiroha.mmdskin.ui.selector.ModelSelectorScreen$ModelCardEntry", remap = false)
public interface ModelCardEntryAccessor {
    @Accessor("displayName")
    String mmdsync$getDisplayName();
}
