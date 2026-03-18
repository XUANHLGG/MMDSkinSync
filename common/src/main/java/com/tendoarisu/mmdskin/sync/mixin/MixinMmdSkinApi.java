package com.tendoarisu.mmdskin.sync.mixin;

import com.opdent.mmdskin.sync.MMDSyncMod;
import com.shiroha.mmdskin.NativeFunc;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.nio.ByteBuffer;

@Mixin(targets = "com.shiroha.mmdskin.api.MmdSkinApi", remap = false)
public abstract class MixinMmdSkinApi {

    @Redirect(method = "getModelInfo", at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;GetBoneCount(J)I"), remap = false)
    private static int redirectGetBoneCount(NativeFunc instance, long modelHandle) {
        if (!MMDSyncNativeBridge.isBridgeHandle(modelHandle)) {
            return instance.GetBoneCount(modelHandle);
        }
        return MMDSyncNativeBridge.isModelHandleValid(modelHandle) ? MMDSyncNativeBridge.getBoneCount(modelHandle) : 0;
    }

    @Redirect(method = "getModelInfo", at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;GetVertexCount(J)J"), remap = false)
    private static long redirectGetVertexCount(NativeFunc instance, long modelHandle) {
        if (!MMDSyncNativeBridge.isBridgeHandle(modelHandle)) {
            return instance.GetVertexCount(modelHandle);
        }
        return MMDSyncNativeBridge.isModelHandleValid(modelHandle) ? MMDSyncNativeBridge.getVertexCount(modelHandle) : 0L;
    }

    @Redirect(method = "getModelInfo", at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;GetMaterialCount(J)J"), remap = false)
    private static long redirectGetMaterialCount(NativeFunc instance, long modelHandle) {
        if (!MMDSyncNativeBridge.isBridgeHandle(modelHandle)) {
            return instance.GetMaterialCount(modelHandle);
        }
        return MMDSyncNativeBridge.isModelHandleValid(modelHandle) ? MMDSyncNativeBridge.getMaterialCount(modelHandle) : 0L;
    }

    @Redirect(method = "getModelInfo", at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;GetBoneNames(J)Ljava/lang/String;"), remap = false)
    private static String redirectGetBoneNames(NativeFunc instance, long modelHandle) {
        if (!MMDSyncNativeBridge.isBridgeHandle(modelHandle)) {
            return instance.GetBoneNames(modelHandle);
        }
        if (!MMDSyncNativeBridge.isModelHandleValid(modelHandle)) {
            MMDSyncMod.LOGGER.warn("阻止 API 读取过期加密模型骨骼名称: model={}", modelHandle);
            return "[]";
        }
        return MMDSyncNativeBridge.getBoneNames(modelHandle);
    }

    @Redirect(method = "getModelInfo", at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;CopyBonePositionsToBuffer(JLjava/nio/ByteBuffer;)I"), remap = false)
    private static int redirectCopyBonePositionsToBuffer(NativeFunc instance, long modelHandle, ByteBuffer buffer) {
        if (!MMDSyncNativeBridge.isBridgeHandle(modelHandle)) {
            return instance.CopyBonePositionsToBuffer(modelHandle, buffer);
        }
        if (!MMDSyncNativeBridge.isModelHandleValid(modelHandle)) {
            MMDSyncMod.LOGGER.warn("阻止 API 读取过期加密模型骨骼位置: model={}", modelHandle);
            return 0;
        }
        return MMDSyncNativeBridge.copyBonePositionsToBuffer(modelHandle, buffer);
    }

    @Redirect(method = "getUV", at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;GetVertexCount(J)J"), remap = false)
    private static long redirectGetUVVertexCount(NativeFunc instance, long modelHandle) {
        if (!MMDSyncNativeBridge.isBridgeHandle(modelHandle)) {
            return instance.GetVertexCount(modelHandle);
        }
        return MMDSyncNativeBridge.isModelHandleValid(modelHandle) ? MMDSyncNativeBridge.getVertexCount(modelHandle) : 0L;
    }

    @Redirect(method = "getUV", at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;CopyRealtimeUVsToBuffer(JLjava/nio/ByteBuffer;)I"), remap = false)
    private static int redirectCopyRealtimeUVsToBuffer(NativeFunc instance, long modelHandle, ByteBuffer buffer) {
        if (!MMDSyncNativeBridge.isBridgeHandle(modelHandle)) {
            return instance.CopyRealtimeUVsToBuffer(modelHandle, buffer);
        }
        if (!MMDSyncNativeBridge.isModelHandleValid(modelHandle)) {
            MMDSyncMod.LOGGER.warn("阻止 API 读取过期加密模型实时 UV: model={}", modelHandle);
            return 0;
        }
        return MMDSyncNativeBridge.copyRealtimeUVsToBuffer(modelHandle, buffer);
    }
}
