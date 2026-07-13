package com.tendoarisu.mmdskin.sync.mixin;

import com.shiroha.mmdskin.NativeFunc;
import com.tendoarisu.mmdskin.sync.runtime.HybridNativeRuntime;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Routes supported GPU lifecycle metrics and reports unsupported 79-JNI values as safe defaults. */
@Mixin(targets = "com.shiroha.mmdskin.renderer.runtime.model.gpu.MMDModelGpuSkinningLifecycle", remap = false)
public abstract class MixinModelLifecycleMetrics {

    @Redirect(
            method = "getVramUsage(Lcom/shiroha/mmdskin/renderer/runtime/model/gpu/MMDModelGpuSkinning;)J",
            at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;GetIndexCount(J)J"),
            remap = false
    )
    private static long mmdsync$routeIndexCount(NativeFunc instance, long modelHandle) {
        return MMDSyncNativeBridge.isBridgeHandle(modelHandle)
                ? HybridNativeRuntime.queryPort().getIndexCount(modelHandle)
                : instance.GetIndexCount(modelHandle);
    }

    @Redirect(
            method = "getRamUsage(Lcom/shiroha/mmdskin/renderer/runtime/model/gpu/MMDModelGpuSkinning;)J",
            at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;GetModelMemoryUsage(J)J"),
            remap = false
    )
    private static long mmdsync$routeModelMemoryUsage(NativeFunc instance, long modelHandle) {
        return MMDSyncNativeBridge.isBridgeHandle(modelHandle)
                ? HybridNativeRuntime.modelPort().getModelMemoryUsage(modelHandle)
                : instance.GetModelMemoryUsage(modelHandle);
    }

    @Redirect(
            method = "getVramUsage(Lcom/shiroha/mmdskin/renderer/runtime/model/gpu/MMDModelGpuSkinning;)J",
            at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;GetGpuMorphOffsetsSize(J)J"),
            remap = false
    )
    private static long mmdsync$routeGpuMorphOffsetsSize(NativeFunc instance, long modelHandle) {
        return MMDSyncNativeBridge.isBridgeHandle(modelHandle)
                ? HybridNativeRuntime.queryPort().getGpuMorphOffsetsSize(modelHandle)
                : instance.GetGpuMorphOffsetsSize(modelHandle);
    }

    @Redirect(
            method = "getVramUsage(Lcom/shiroha/mmdskin/renderer/runtime/model/gpu/MMDModelGpuSkinning;)J",
            at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;GetGpuUvMorphOffsetsSize(J)J"),
            remap = false
    )
    private static long mmdsync$routeGpuUvMorphOffsetsSize(NativeFunc instance, long modelHandle) {
        return MMDSyncNativeBridge.isBridgeHandle(modelHandle)
                ? HybridNativeRuntime.queryPort().getGpuUvMorphOffsetsSize(modelHandle)
                : instance.GetGpuUvMorphOffsetsSize(modelHandle);
    }
}
