package com.tendoarisu.mmdskin.sync.mixin;

import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.debug.client.PerformanceHud;
import com.tendoarisu.mmdskin.sync.runtime.HybridNativeRuntime;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keeps the performance HUD out of the upstream native handle domain for bridge models. */
@Mixin(value = PerformanceHud.class, remap = false)
public abstract class MixinPerformanceHud {

    @Redirect(method = "rebuildLines", at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;GetIndexCount(J)J"), remap = false)
    private static long mmdsync$routeIndexCount(NativeFunc instance, long handle) {
        return MMDSyncNativeBridge.isBridgeHandle(handle)
                ? HybridNativeRuntime.queryPort().getIndexCount(handle)
                : instance.GetIndexCount(handle);
    }

    @Redirect(method = "rebuildLines", at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;GetVertexCount(J)J"), remap = false)
    private static long mmdsync$routeVertexCount(NativeFunc instance, long handle) {
        return MMDSyncNativeBridge.isBridgeHandle(handle)
                ? HybridNativeRuntime.queryPort().getVertexCount(handle)
                : instance.GetVertexCount(handle);
    }

    @Redirect(method = "rebuildLines", at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;GetBoneCount(J)I"), remap = false)
    private static int mmdsync$routeBoneCount(NativeFunc instance, long handle) {
        return MMDSyncNativeBridge.isBridgeHandle(handle)
                ? HybridNativeRuntime.queryPort().getBoneCount(handle)
                : instance.GetBoneCount(handle);
    }

    @Redirect(method = "rebuildLines", at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;GetMaterialCount(J)J"), remap = false)
    private static long mmdsync$routeMaterialCount(NativeFunc instance, long handle) {
        return MMDSyncNativeBridge.isBridgeHandle(handle)
                ? HybridNativeRuntime.queryPort().getMaterialCount(handle)
                : instance.GetMaterialCount(handle);
    }

    @Redirect(method = "rebuildLines", at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;GetVertexMorphCount(J)I"), remap = false)
    private static int mmdsync$routeVertexMorphCount(NativeFunc instance, long handle) {
        return MMDSyncNativeBridge.isBridgeHandle(handle)
                ? HybridNativeRuntime.queryPort().getVertexMorphCount(handle)
                : instance.GetVertexMorphCount(handle);
    }

    @Redirect(method = "rebuildLines", at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;GetUvMorphCount(J)I"), remap = false)
    private static int mmdsync$routeUvMorphCount(NativeFunc instance, long handle) {
        return MMDSyncNativeBridge.isBridgeHandle(handle)
                ? HybridNativeRuntime.queryPort().getUvMorphCount(handle)
                : instance.GetUvMorphCount(handle);
    }
}
