package com.tendoarisu.mmdskin.sync.mixin;

import com.shiroha.mmdskin.NativeFunc;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "com.shiroha.mmdskin.renderer.runtime.model.gpu.MMDModelGpuSkinningUploader", remap = false)
public class MixinMMDModelGpuSkinningUploader {

    @Redirect(
        method = "uploadBoneMatrices(Lcom/shiroha/mmdskin/renderer/runtime/model/gpu/MMDModelGpuSkinning;)V",
        at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;CopySkinningMatricesToBuffer(JLjava/nio/ByteBuffer;)I"),
        remap = false
    )
    private static int redirectCopySkinningMatricesToBuffer(NativeFunc instance, long model, java.nio.ByteBuffer buffer) {
        if (MMDSyncNativeBridge.isBridgeHandle(model)) {
            return MMDSyncNativeBridge.copySkinningMatricesToBuffer(model, buffer);
        }
        return instance.CopySkinningMatricesToBuffer(model, buffer);
    }
}
