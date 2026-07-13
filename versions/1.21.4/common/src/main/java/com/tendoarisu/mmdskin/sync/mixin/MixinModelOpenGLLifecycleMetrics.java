package com.tendoarisu.mmdskin.sync.mixin;

import com.shiroha.mmdskin.NativeFunc;
import com.tendoarisu.mmdskin.sync.runtime.HybridNativeRuntime;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Routes supported OpenGL lifecycle metrics and reports unsupported 79-JNI RAM usage as zero. */
@Mixin(targets = "com.shiroha.mmdskin.renderer.runtime.model.opengl.MMDModelOpenGLLifecycle", remap = false)
public abstract class MixinModelOpenGLLifecycleMetrics {

    @Redirect(
            method = "getVramUsage(Lcom/shiroha/mmdskin/renderer/runtime/model/opengl/MMDModelOpenGL;)J",
            at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;GetIndexCount(J)J"),
            remap = false
    )
    private static long mmdsync$routeIndexCount(NativeFunc instance, long modelHandle) {
        return MMDSyncNativeBridge.isBridgeHandle(modelHandle)
                ? HybridNativeRuntime.queryPort().getIndexCount(modelHandle)
                : instance.GetIndexCount(modelHandle);
    }

    @Redirect(
            method = "getRamUsage(Lcom/shiroha/mmdskin/renderer/runtime/model/opengl/MMDModelOpenGL;)J",
            at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;GetModelMemoryUsage(J)J"),
            remap = false
    )
    private static long mmdsync$routeModelMemoryUsage(NativeFunc instance, long modelHandle) {
        return MMDSyncNativeBridge.isBridgeHandle(modelHandle)
                ? HybridNativeRuntime.modelPort().getModelMemoryUsage(modelHandle)
                : instance.GetModelMemoryUsage(modelHandle);
    }
}
