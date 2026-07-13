package com.tendoarisu.mmdskin.sync.mixin;

import com.shiroha.mmdskin.NativeFunc;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.nio.ByteBuffer;

@Mixin(targets = "com.shiroha.mmdskin.renderer.runtime.model.opengl.MMDModelOpenGLRenderer", remap = false)
public class MixinMMDModelOpenGLRenderer {

    private static boolean useBridge(long model) {
        return model != 0L && MMDSyncNativeBridge.isBridgeHandle(model);
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;BatchGetSubMeshData(JLjava/nio/ByteBuffer;)I"), remap = false)
    private static int redirectBatchGetSubMeshData(NativeFunc instance, long model, ByteBuffer buffer) {
        return useBridge(model) ? MMDSyncNativeBridge.batchGetSubMeshData(model, buffer) : instance.BatchGetSubMeshData(model, buffer);
    }

    @Redirect(
            method = {
                    "uploadDynamicBuffers",
                    "renderToon"
            },
            at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;GetPoss(J)J"),
            remap = false
    )
    private static long redirectGetPoss(NativeFunc instance, long model) {
        return useBridge(model) ? MMDSyncNativeBridge.getPoss(model) : instance.GetPoss(model);
    }

    @Redirect(
            method = {
                    "uploadDynamicBuffers",
                    "renderToon"
            },
            at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;GetNormals(J)J"),
            remap = false
    )
    private static long redirectGetNormals(NativeFunc instance, long model) {
        return useBridge(model) ? MMDSyncNativeBridge.getNormals(model) : instance.GetNormals(model);
    }

    @Redirect(
            method = {
                    "uploadDynamicBuffers",
                    "renderToon"
            },
            at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;GetUVs(J)J"),
            remap = false
    )
    private static long redirectGetUVs(NativeFunc instance, long model) {
        return useBridge(model) ? MMDSyncNativeBridge.getUVs(model) : instance.GetUVs(model);
    }
}
