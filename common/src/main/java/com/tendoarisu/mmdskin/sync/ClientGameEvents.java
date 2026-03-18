package com.tendoarisu.mmdskin.sync;

import com.opdent.mmdskin.sync.MMDSyncMod;
import com.opdent.mmdskin.sync.SyncManager;
import com.opdent.mmdskin.sync.network.HandshakePacket;
import com.tendoarisu.mmdskin.sync.util.CryptoUtils;
import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;

public class ClientGameEvents {
    public static void init() {
        ClientPlayerEvent.CLIENT_PLAYER_JOIN.register(player -> {
            if (player != null && Minecraft.getInstance().player != null) {
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
