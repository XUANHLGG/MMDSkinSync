package com.tendoarisu.mmdskin.sync.mixin;

import com.opdent.mmdskin.sync.SyncManager;
import com.opdent.mmdskin.sync.MMDSyncMod;
import com.shiroha.mmdskin.config.StagePack;
import com.tendoarisu.mmdskin.sync.StagePackRefreshCoordinator;
import com.tendoarisu.mmdskin.sync.util.CryptoUtils;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

@Mixin(value = StagePack.class, remap = false)
public abstract class MixinStagePack {

    @Redirect(
            method = "scanVmdFiles(Ljava/io/File;Lcom/shiroha/mmdskin/config/StagePack$VmdFileInspector;)Ljava/util/List;",
            at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/config/StagePack$VmdFileInspector;inspect(Ljava/lang/String;)[Z"),
            remap = false
    )
    private static boolean[] redirectInspectEncryptedVmd(StagePack.VmdFileInspector inspector, String path) {
        File file = new File(path);
        if (!CryptoUtils.isEncrypted(file)) {
            return inspector.inspect(path);
        }

        if (!CryptoUtils.hasSessionMaterial()) {
            String serverId = SyncManager.getCurrentServerId();
            if (serverId == null || serverId.isBlank()) {
                return null;
            }
            StagePackRefreshCoordinator.requestRefreshWhenSessionReady();
            return null;
        }

        long handle = MMDSyncNativeBridge.loadEncryptedVMDFromFile(path);
        if (handle == 0L) {
            MMDSyncMod.LOGGER.warn("扫描加密舞蹈动作失败，无法生成 StagePack 条目: {}", path);
            return null;
        }

        try {
            return new boolean[]{
                    MMDSyncNativeBridge.hasCameraData(handle),
                    MMDSyncNativeBridge.hasBoneData(handle),
                    MMDSyncNativeBridge.hasMorphData(handle)
            };
        } finally {
            MMDSyncNativeBridge.deleteAnimation(handle);
        }
    }

    @Inject(
            method = "scan(Ljava/io/File;Lcom/shiroha/mmdskin/config/StagePack$VmdFileInspector;)Ljava/util/List;",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private static void filterStagePacksByServerId(File dir, StagePack.VmdFileInspector inspector, CallbackInfoReturnable<List<StagePack>> cir) {
        List<StagePack> list = cir.getReturnValue();
        if (list == null || list.isEmpty()) {
            return;
        }
        String serverId = SyncManager.getCurrentServerId();
        if (serverId == null || serverId.isBlank()) {
            return;
        }

        List<StagePack> filtered = list.stream()
                .filter(pack -> {
                    try {
                        String path = pack.getFolderPath();
                        if (path == null || path.isBlank()) return false;
                        String folderName = new File(path).getName();
                        return SyncManager.shouldDisplayStageAnimFolder(folderName);
                    } catch (Throwable ignored) {
                        return false;
                    }
                })
                .collect(Collectors.toList());

        cir.setReturnValue(filtered);
    }
}
