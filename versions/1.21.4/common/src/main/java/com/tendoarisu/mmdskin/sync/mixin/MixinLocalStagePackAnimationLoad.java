package com.tendoarisu.mmdskin.sync.mixin;

import com.opdent.mmdskin.sync.MMDSyncMod;
import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.stage.client.asset.LocalStagePackRepository;
import com.tendoarisu.mmdskin.sync.util.CryptoUtils;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.File;

/** Routes the local stage-pack inspection lambda's temporary animation load. */
@Mixin(value = LocalStagePackRepository.class, remap = false)
public abstract class MixinLocalStagePackAnimationLoad {

    @Redirect(
            method = "lambda$loadStagePacks$0(Ljava/lang/String;)[Z",
            at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;LoadAnimation(JLjava/lang/String;)J"),
            remap = false
    )
    private static long mmdsync$routeLoadAnimation(NativeFunc instance, long model, String filename) {
        File file = new File(filename);
        if (!CryptoUtils.isEncrypted(file)) {
            return instance.LoadAnimation(model, filename);
        }

        long handle = MMDSyncNativeBridge.loadEncryptedVMDFromFile(filename);
        if (handle == 0L && !CryptoUtils.hasSessionMaterial()) {
            MMDSyncMod.LOGGER.warn("扫描舞台包加密动作时 SessionKey 尚未就绪，本次跳过阻塞等待: {}", filename);
        }
        if (handle == 0L) {
            MMDSyncMod.LOGGER.error("扫描舞台包加密动作失败: {}", filename);
        }
        return handle;
    }
}
