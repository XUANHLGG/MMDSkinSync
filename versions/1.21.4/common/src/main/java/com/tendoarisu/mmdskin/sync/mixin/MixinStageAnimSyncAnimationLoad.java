package com.tendoarisu.mmdskin.sync.mixin;

import com.opdent.mmdskin.sync.MMDSyncMod;
import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.stage.client.sync.StageAnimSyncHelper;
import com.tendoarisu.mmdskin.sync.util.CryptoUtils;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/** Routes remote-stage animation loading without claiming unsupported 79-JNI merge support. */
@Mixin(value = StageAnimSyncHelper.class, remap = false)
public abstract class MixinStageAnimSyncAnimationLoad {

    @Redirect(
            method = "loadAndMergeAnimations(Ljava/io/File;Ljava/util/List;)J",
            at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;LoadAnimation(JLjava/lang/String;)J"),
            remap = false
    )
    private static long mmdsync$routeLoadAnimation(NativeFunc instance, long model, String filename) {
        if (MMDSyncNativeBridge.isBridgeHandle(model) && !MMDSyncNativeBridge.isModelHandleValid(model)) {
            MMDSyncMod.LOGGER.warn("阻止舞台同步对过期加密模型句柄加载动画: model={}, file={}", model, filename);
            return 0L;
        }

        File file = new File(filename);
        if (CryptoUtils.isEncrypted(file)) {
            long handle = MMDSyncNativeBridge.loadEncryptedVMDFromFile(filename);
            if (handle == 0L && !CryptoUtils.hasSessionMaterial()) {
                MMDSyncMod.LOGGER.warn("舞台同步加载加密动作时 SessionKey 尚未就绪，本次跳过阻塞等待: {}", filename);
            }
            if (handle == 0L) {
                MMDSyncMod.LOGGER.error("舞台同步加载加密动作失败: {}", filename);
            }
            return handle;
        }

        if (MMDSyncNativeBridge.isBridgeHandle(model)) {
            if (!file.exists()) {
                MMDSyncMod.LOGGER.warn("舞台同步桥接模型加载非加密动作失败: 文件不存在: {}", filename);
                return 0L;
            }
            try {
                long handle = MMDSyncNativeBridge.loadVMDFromMemory(Files.readAllBytes(file.toPath()));
                if (handle == 0L) {
                    MMDSyncMod.LOGGER.error("舞台同步桥接模型加载非加密动作失败: 内存加载返回空句柄: {}", filename);
                }
                return handle;
            } catch (IOException exception) {
                MMDSyncMod.LOGGER.error("舞台同步桥接模型读取非加密动作失败: {}", filename, exception);
                return 0L;
            }
        }

        return instance.LoadAnimation(model, filename);
    }
}
