package com.tendoarisu.mmdskin.sync.mixin;

import com.shiroha.mmdskin.bridge.runtime.NativeModelPort;
import com.shiroha.mmdskin.bridge.runtime.NativeModelQueryPort;
import com.shiroha.mmdskin.ui.selector.adapter.DefaultMaterialVisibilityGateway;
import com.tendoarisu.mmdskin.sync.runtime.HybridNativeRuntime;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 1.0.5 的材质网关在构造时保存上游端口，早于 MMDSync 安装中心路由。
 * 因此仅对 bridge 句柄改走 MMDSync 路由；普通句柄仍调用网关原有端口。
 */
@Mixin(value = DefaultMaterialVisibilityGateway.class, remap = false)
public class MixinMaterialVisibilityGateway {

    @Redirect(
            method = "loadMaterials(Lcom/shiroha/mmdskin/ui/selector/application/MaterialVisibilityApplicationService$MaterialScreenContext;)Ljava/util/List;",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/shiroha/mmdskin/bridge/runtime/NativeModelPort;getMaterialCount(J)I"
            ),
            remap = false
    )
    private int mmdsync$routeMaterialCount(NativeModelPort port, long modelHandle) {
        return MMDSyncNativeBridge.isBridgeHandle(modelHandle)
                ? HybridNativeRuntime.modelPort().getMaterialCount(modelHandle)
                : port.getMaterialCount(modelHandle);
    }

    @Redirect(
            method = "loadMaterials(Lcom/shiroha/mmdskin/ui/selector/application/MaterialVisibilityApplicationService$MaterialScreenContext;)Ljava/util/List;",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/shiroha/mmdskin/bridge/runtime/NativeModelQueryPort;getMaterialName(JI)Ljava/lang/String;"
            ),
            remap = false
    )
    private String mmdsync$routeMaterialName(NativeModelQueryPort port, long modelHandle, int materialIndex) {
        return MMDSyncNativeBridge.isBridgeHandle(modelHandle)
                ? HybridNativeRuntime.queryPort().getMaterialName(modelHandle, materialIndex)
                : port.getMaterialName(modelHandle, materialIndex);
    }

    @Redirect(
            method = "loadMaterials(Lcom/shiroha/mmdskin/ui/selector/application/MaterialVisibilityApplicationService$MaterialScreenContext;)Ljava/util/List;",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/shiroha/mmdskin/bridge/runtime/NativeModelQueryPort;isMaterialVisible(JI)Z"
            ),
            remap = false
    )
    private boolean mmdsync$routeMaterialVisibleQuery(
            NativeModelQueryPort port,
            long modelHandle,
            int materialIndex) {
        return MMDSyncNativeBridge.isBridgeHandle(modelHandle)
                ? HybridNativeRuntime.queryPort().isMaterialVisible(modelHandle, materialIndex)
                : port.isMaterialVisible(modelHandle, materialIndex);
    }

    @Redirect(
            method = "setAllVisible(JZ)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/shiroha/mmdskin/bridge/runtime/NativeModelPort;setAllMaterialsVisible(JZ)V"
            ),
            remap = false
    )
    private void mmdsync$routeAllMaterialsVisible(NativeModelPort port, long modelHandle, boolean visible) {
        if (MMDSyncNativeBridge.isBridgeHandle(modelHandle)) {
            HybridNativeRuntime.modelPort().setAllMaterialsVisible(modelHandle, visible);
            return;
        }
        port.setAllMaterialsVisible(modelHandle, visible);
    }

    @Redirect(
            method = "setMaterialVisible(JIZ)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/shiroha/mmdskin/bridge/runtime/NativeModelPort;setMaterialVisible(JIZ)V"
            ),
            remap = false
    )
    private void mmdsync$routeMaterialVisible(
            NativeModelPort port,
            long modelHandle,
            int materialIndex,
            boolean visible) {
        if (MMDSyncNativeBridge.isBridgeHandle(modelHandle)) {
            HybridNativeRuntime.modelPort().setMaterialVisible(modelHandle, materialIndex, visible);
            return;
        }
        port.setMaterialVisible(modelHandle, materialIndex, visible);
    }
}
