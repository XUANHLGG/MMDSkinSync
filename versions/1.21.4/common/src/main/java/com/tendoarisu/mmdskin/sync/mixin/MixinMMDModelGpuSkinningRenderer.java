package com.tendoarisu.mmdskin.sync.mixin;

import com.shiroha.mmdskin.NativeFunc;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "com.shiroha.mmdskin.renderer.runtime.model.gpu.MMDModelGpuSkinningRenderer", remap = false)
public class MixinMMDModelGpuSkinningRenderer {

    @Redirect(
        method = "render",
        at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;BatchGetSubMeshData(JLjava/nio/ByteBuffer;)I"),
        remap = false
    )
    private static int redirectBatchGetSubMeshData(NativeFunc instance, long model, java.nio.ByteBuffer buffer) {
        if (MMDSyncNativeBridge.isBridgeHandle(model)) {
            return MMDSyncNativeBridge.batchGetSubMeshData(model, buffer);
        }
        return instance.BatchGetSubMeshData(model, buffer);
    }
}
