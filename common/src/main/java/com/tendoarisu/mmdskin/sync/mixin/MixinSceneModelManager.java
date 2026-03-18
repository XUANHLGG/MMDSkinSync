package com.tendoarisu.mmdskin.sync.mixin;

import com.opdent.mmdskin.sync.MMDSyncMod;
import com.shiroha.mmdskin.NativeFunc;
import com.tendoarisu.mmdskin.sync.util.CryptoUtils;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.File;

@Mixin(targets = "com.shiroha.mmdskin.scene.client.SceneModelManager", remap = false)
public class MixinSceneModelManager {

    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;LoadModelPMX(Ljava/lang/String;Ljava/lang/String;J)J"), remap = false)
    private long redirectLoadModelPMX(NativeFunc instance, String filename, String dir, long layerCount) {
        File file = new File(filename);
        if (!CryptoUtils.isEncrypted(file)) {
            return instance.LoadModelPMX(filename, dir, layerCount);
        }
        long handle = MMDSyncNativeBridge.loadEncryptedModelFromFile(filename, dir, layerCount);
        if (handle == 0L && !CryptoUtils.hasSessionMaterial()) {
            CryptoUtils.waitForSessionMaterial(2000L);
            handle = MMDSyncNativeBridge.loadEncryptedModelFromFile(filename, dir, layerCount);
        }
        return handle;
    }

    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;LoadModelPMD(Ljava/lang/String;Ljava/lang/String;J)J"), remap = false)
    private long redirectLoadModelPMD(NativeFunc instance, String filename, String dir, long layerCount) {
        File file = new File(filename);
        if (!CryptoUtils.isEncrypted(file)) {
            return instance.LoadModelPMD(filename, dir, layerCount);
        }
        long handle = MMDSyncNativeBridge.loadEncryptedModelFromFile(filename, dir, layerCount);
        if (handle == 0L && !CryptoUtils.hasSessionMaterial()) {
            CryptoUtils.waitForSessionMaterial(2000L);
            handle = MMDSyncNativeBridge.loadEncryptedModelFromFile(filename, dir, layerCount);
        }
        return handle;
    }

    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;LoadModelVRM(Ljava/lang/String;Ljava/lang/String;J)J"), remap = false)
    private long redirectLoadModelVRM(NativeFunc instance, String filename, String dir, long layerCount) {
        File file = new File(filename);
        if (!CryptoUtils.isEncrypted(file)) {
            return instance.LoadModelVRM(filename, dir, layerCount);
        }
        long handle = MMDSyncNativeBridge.loadEncryptedModelFromFile(filename, dir, layerCount);
        if (handle == 0L && !CryptoUtils.hasSessionMaterial()) {
            CryptoUtils.waitForSessionMaterial(2000L);
            handle = MMDSyncNativeBridge.loadEncryptedModelFromFile(filename, dir, layerCount);
        }
        return handle;
    }

    @Redirect(method = {"render", "checkPendingLoad"}, at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;SetModelPositionAndYaw(JFFFF)V"), remap = false)
    private void redirectSetModelPositionAndYaw(NativeFunc instance, long modelHandle, float posX, float posY, float posZ, float yaw) {
        if (!MMDSyncNativeBridge.isBridgeHandle(modelHandle)) {
            instance.SetModelPositionAndYaw(modelHandle, posX, posY, posZ, yaw);
            return;
        }
        if (!MMDSyncNativeBridge.isModelHandleValid(modelHandle)) {
            MMDSyncMod.LOGGER.warn("阻止场景模型使用过期加密句柄设置位置与朝向: model={}", modelHandle);
            return;
        }
        MMDSyncNativeBridge.setModelPositionAndYaw(modelHandle, posX, posY, posZ, yaw);
    }

    @Redirect(method = "preloadTextures", at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;GetMaterialCount(J)J"), remap = false)
    private long redirectGetMaterialCount(NativeFunc instance, long modelHandle) {
        if (!MMDSyncNativeBridge.isBridgeHandle(modelHandle)) {
            return instance.GetMaterialCount(modelHandle);
        }
        if (!MMDSyncNativeBridge.isModelHandleValid(modelHandle)) {
            MMDSyncMod.LOGGER.warn("阻止场景模型读取过期加密材质数量: model={}", modelHandle);
            return 0L;
        }
        return MMDSyncNativeBridge.getMaterialCount(modelHandle);
    }

    @Redirect(method = "preloadTextures", at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;GetMaterialTex(JJ)Ljava/lang/String;"), remap = false)
    private String redirectGetMaterialTex(NativeFunc instance, long modelHandle, long pos) {
        if (!MMDSyncNativeBridge.isBridgeHandle(modelHandle)) {
            return instance.GetMaterialTex(modelHandle, pos);
        }
        if (!MMDSyncNativeBridge.isModelHandleValid(modelHandle)) {
            MMDSyncMod.LOGGER.warn("阻止场景模型读取过期加密材质贴图: model={}, pos={}", modelHandle, pos);
            return "";
        }
        return MMDSyncNativeBridge.getMaterialTex(modelHandle, pos);
    }

    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;DeleteModel(J)V"), remap = false)
    private void redirectDeleteModel(NativeFunc instance, long modelHandle) {
        if (!MMDSyncNativeBridge.isBridgeHandle(modelHandle)) {
            instance.DeleteModel(modelHandle);
            return;
        }
        MMDSyncNativeBridge.deleteModel(modelHandle);
    }
}
