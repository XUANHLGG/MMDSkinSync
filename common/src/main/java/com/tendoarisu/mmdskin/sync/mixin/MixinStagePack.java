package com.tendoarisu.mmdskin.sync.mixin;

import com.opdent.mmdskin.sync.SyncManager;
import com.shiroha.mmdskin.config.StagePack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 让 StageAnim（舞蹈动作包）也具备“按服务器隔离显示”的能力。
 *
 * StagePack.scan 会扫描 StageAnim 目录下的动作包，注入后在 RETURN 时过滤：
 * 仅保留绑定到当前 serverId 的动作包目录。
 */
@Mixin(value = StagePack.class, remap = false)
public abstract class MixinStagePack {

    @Inject(method = "scan", at = @At("RETURN"), cancellable = true, remap = false)
    private static void filterStagePacksByServerId(File dir, StagePack.VmdFileInspector inspector, CallbackInfoReturnable<List<StagePack>> cir) {
        List<StagePack> list = cir.getReturnValue();
        if (list == null || list.isEmpty()) {
            return;
        }
        String serverId = SyncManager.getCurrentServerId();
        if (serverId == null || serverId.isBlank()) {
            // 未连接服务器（或尚未拿到 serverId）时保持原样
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
