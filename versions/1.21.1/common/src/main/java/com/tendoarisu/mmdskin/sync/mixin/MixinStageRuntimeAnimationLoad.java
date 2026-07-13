package com.tendoarisu.mmdskin.sync.mixin;

import com.opdent.mmdskin.sync.MMDSyncMod;
import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.stage.client.playback.DefaultStagePlaybackRuntime;
import com.tendoarisu.mmdskin.sync.util.CryptoUtils;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/** 路由默认舞台播放运行时的动作加载，并隔离 bridge/核心引擎句柄域。 */
@Mixin(value = DefaultStagePlaybackRuntime.class, remap = false)
public abstract class MixinStageRuntimeAnimationLoad {

    @Redirect(
            method = {
                    "startHostPlayback(Lcom/shiroha/mmdskin/config/StagePack;ZFLjava/lang/String;)Lcom/shiroha/mmdskin/stage/client/playback/port/StagePlaybackRuntimePort$HostStartResult;",
                    "startGuestPlayback(Ljava/util/UUID;Lcom/shiroha/mmdskin/stage/client/playback/StagePlaybackStartRequest;Z)Lcom/shiroha/mmdskin/stage/client/playback/port/StagePlaybackRuntimePort$GuestStartResult;"
            },
            at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;LoadAnimation(JLjava/lang/String;)J"),
            remap = false
    )
    private long mmdsync$routeLoadAnimation(NativeFunc instance, long model, String filename) {
        if (MMDSyncNativeBridge.isBridgeHandle(model) && !MMDSyncNativeBridge.isModelHandleValid(model)) {
            MMDSyncMod.LOGGER.warn("阻止舞台运行时对过期加密模型句柄加载动画: model={}, file={}", model, filename);
            return 0L;
        }

        File file = new File(filename);
        if (CryptoUtils.isEncrypted(file)) {
            long handle = MMDSyncNativeBridge.loadEncryptedVMDFromFile(filename);
            if (handle == 0L && !CryptoUtils.hasSessionMaterial()) {
                MMDSyncMod.LOGGER.warn("舞台运行时加载加密动作时 SessionKey 尚未就绪，本次跳过阻塞等待: {}", filename);
            }
            if (handle == 0L) {
                MMDSyncMod.LOGGER.error("舞台运行时加载加密动作失败: {}", filename);
            }
            return handle;
        }

        if (MMDSyncNativeBridge.isBridgeHandle(model)) {
            if (!file.exists()) {
                MMDSyncMod.LOGGER.warn("舞台运行时桥接模型加载非加密动作失败: 文件不存在: {}", filename);
                return 0L;
            }
            try {
                byte[] data = Files.readAllBytes(file.toPath());
                long handle = MMDSyncNativeBridge.loadVMDFromMemory(data);
                if (handle == 0L) {
                    MMDSyncMod.LOGGER.error("舞台运行时桥接模型加载非加密动作失败: 内存加载返回空句柄: {}", filename);
                }
                return handle;
            } catch (IOException ex) {
                MMDSyncMod.LOGGER.error("舞台运行时桥接模型读取非加密动作失败: {}", filename, ex);
                return 0L;
            }
        }

        return instance.LoadAnimation(model, filename);
    }
}
