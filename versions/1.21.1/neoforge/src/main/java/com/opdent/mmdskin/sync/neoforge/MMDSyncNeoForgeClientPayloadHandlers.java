package com.opdent.mmdskin.sync.neoforge;

import com.opdent.mmdskin.sync.SyncManager;
import com.opdent.mmdskin.sync.network.SyncUrlPacket;
import com.opdent.mmdskin.sync.network.resource.ResourceTransferClientManager;
import com.opdent.mmdskin.sync.network.resource.ResourceTransferPacket;
import com.tendoarisu.mmdskin.sync.util.CryptoUtils;

/** Client-only payload logic, loaded reflectively only after receiving clientbound traffic. */
final class MMDSyncNeoForgeClientPayloadHandlers {
    private MMDSyncNeoForgeClientPayloadHandlers() {
    }

    static void handleResourceTransferPacket(ResourceTransferPacket packet) {
        ResourceTransferClientManager.acceptPacket(packet.toResourcePacket());
    }

    static void handleSyncUrlPacket(SyncUrlPacket payload) {
        SyncManager.setServerSecret(payload.serverSecret());
        SyncManager.setCurrentServerId(payload.serverId());

        if (payload.encryptedKey() != null && !payload.encryptedKey().isEmpty()) {
            CryptoUtils.installSessionMaterial(payload.encryptedKey(), SyncManager.getLastServerSecret());
            if (CryptoUtils.hasSessionMaterial()) {
                SyncManager.onSessionKeyReady();
                SyncManager.startSync();
            }
        } else if (payload.serverSecret() == null || payload.serverSecret().isEmpty()) {
            SyncManager.startSync();
        } else {
            MMDSyncNeoForgeClientRegistration.trySendOrScheduleHandshake();
        }
    }
}
