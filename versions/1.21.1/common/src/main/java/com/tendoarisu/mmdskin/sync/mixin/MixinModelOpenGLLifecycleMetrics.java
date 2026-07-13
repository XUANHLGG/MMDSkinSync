package com.tendoarisu.mmdskin.sync.mixin;

import com.shiroha.mmdskin.NativeFunc;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import com.tendoarisu.mmdskin.sync.util.MMDSyncRuntimePorts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** 防止 OpenGL 模型资源统计路径把 bridge 模型句柄传入上游 native。 */
@Mixin(targets = "com.shiroha.mmdskin.renderer.runtime.model.opengl.MMDModelOpenGLLifecycle", remap = false)
public abstract class MixinModelOpenGLLifecycleMetrics {

    @Redirect(
            method = "getVramUsage(Lcom/shiroha/mmdskin/renderer/runtime/model/opengl/MMDModelOpenGL;)J",
            at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;GetIndexCount(J)J"),
            remap = false
    )
    private static long mmdsync$routeIndexCount(NativeFunc instance, long modelHandle) {
        return MMDSyncNativeBridge.isBridgeHandle(modelHandle)
                ? MMDSyncRuntimePorts.queryPort().getIndexCount(modelHandle)
                : instance.GetIndexCount(modelHandle);
    }

    @Redirect(
            method = "getRamUsage(Lcom/shiroha/mmdskin/renderer/runtime/model/opengl/MMDModelOpenGL;)J",
            at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;GetModelMemoryUsage(J)J"),
            remap = false
    )
    private static long mmdsync$routeModelMemoryUsage(NativeFunc instance, long modelHandle) {
        return MMDSyncNativeBridge.isBridgeHandle(modelHandle)
                ? MMDSyncRuntimePorts.modelPort().getModelMemoryUsage(modelHandle)
                : instance.GetModelMemoryUsage(modelHandle);
    }
}
