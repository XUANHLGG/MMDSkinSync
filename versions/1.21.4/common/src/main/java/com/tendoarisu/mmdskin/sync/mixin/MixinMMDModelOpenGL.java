package com.tendoarisu.mmdskin.sync.mixin;

import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.renderer.runtime.model.opengl.MMDModelOpenGL;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = MMDModelOpenGL.class, remap = false)
public class MixinMMDModelOpenGL {

    @Redirect(method = "onUpdate(F)V", at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;UpdateModel(JF)V"), remap = false)
    private void redirectUpdateModel(NativeFunc instance, long model, float deltaTime) {
        if (model != 0L && MMDSyncNativeBridge.isBridgeHandle(model)) {
            MMDSyncNativeBridge.updateModel(model, deltaTime);
            return;
        }
        instance.UpdateModel(model, deltaTime);
    }
}
