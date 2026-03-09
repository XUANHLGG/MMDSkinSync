package com.tendoarisu.mmdskin.sync;

import com.opdent.mmdskin.sync.MMDSyncMod;
import com.opdent.mmdskin.sync.SyncManager;
import com.opdent.mmdskin.sync.network.HandshakePacket;
import com.tendoarisu.mmdskin.sync.util.CryptoUtils;
import dev.architectury.event.events.client.ClientLifecycleEvent;
import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;

public class ClientGameEvents {
    public static void init() {
        // 客户端启动后开始同步
        ClientLifecycleEvent.CLIENT_STARTED.register(client -> {
            SyncManager.startSync();
        });

        // 监听玩家加入远程服务器
        ClientPlayerEvent.CLIENT_PLAYER_JOIN.register(player -> {
            if (player != null && Minecraft.getInstance().player != null) {
                // 如果已经有 serverSecret，立即握手
                String secret = SyncManager.getLastServerSecret();
                if (secret != null && !secret.isEmpty()) {
                    sendHandshake(player.getUUID(), secret);
                }
            }
        });
    }

    public static void sendHandshake(java.util.UUID playerUUID, String serverSecret) {
        String platform = com.tendoarisu.mmdskin.sync.util.MMDSyncNativeLoader.getPlatformIdentifier();
        String hwid = CryptoUtils.getJavaBasedHardwareId();

        String handshakePem = CryptoUtils.getHandshakePem(serverSecret, platform, hwid);
        if (handshakePem == null || handshakePem.isEmpty()) {
            return;
        }

        NetworkManager.sendToServer(new HandshakePacket(20, playerUUID, handshakePem, platform, hwid));
    }
}
