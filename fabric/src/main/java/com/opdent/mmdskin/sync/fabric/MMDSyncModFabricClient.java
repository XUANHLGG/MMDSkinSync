package com.opdent.mmdskin.sync.fabric;

import com.opdent.mmdskin.sync.MMDSyncMod;
import com.tendoarisu.mmdskin.sync.MMDSync;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.opdent.mmdskin.sync.SyncManager;
import com.tendoarisu.mmdskin.sync.util.CryptoUtils;
import com.opdent.mmdskin.sync.network.HandshakePacket;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import java.util.concurrent.atomic.AtomicBoolean;

public class MMDSyncModFabricClient implements ClientModInitializer {
    private static final AtomicBoolean nativeFingerprintLogged = new AtomicBoolean(false);
    private static final AtomicBoolean handshakeRetryScheduled = new AtomicBoolean(false);
    @Override
    public void onInitializeClient() {
        MMDSync.initClient(); // 调用 Common 初始化 (但 ClientGameEvents 被我们禁用了)
        
        // 1. 注册 Fabric 原生网络包接收器 (S2C SyncUrlPacket)
        ClientPlayNetworking.registerGlobalReceiver(
            com.opdent.mmdskin.sync.network.SyncUrlPacket.TYPE, 
            (payload, context) -> {
                context.client().execute(() -> {
                    SyncManager.setServerUrlOverride(payload.url());
                    SyncManager.setServerSecret(payload.serverSecret());
                    SyncManager.setCurrentServerId(payload.serverId());

                    if (payload.encryptedKey() != null && !payload.encryptedKey().isEmpty()) {
                        CryptoUtils.installSessionMaterial(payload.encryptedKey(), SyncManager.getLastServerSecret());
                        if (CryptoUtils.hasSessionMaterial()) {
                            SyncManager.onSessionKeyReady();
                            if (payload.url() != null && !payload.url().isEmpty()) {
                                SyncManager.startSync();
                            }
                        } else {
                            MMDSyncMod.LOGGER.warn("客户端处理握手响应后会话材料仍未就绪。");
                        }
                    } else {
                        if (payload.serverSecret() != null && payload.serverSecret().contains("|")) {
                            trySendOrScheduleHandshake(context.client());
                        }
                        if ((payload.serverSecret() == null || !payload.serverSecret().contains("|")) && payload.url() != null && !payload.url().isEmpty()) {
                            SyncManager.startSync();
                        }
                    }

                });
            }
        );

        // 2. 注册 Fabric 客户端连接事件 (替代 ClientGameEvents.CLIENT_PLAYER_JOIN)
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> trySendOrScheduleHandshake(client));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            handshakeRetryScheduled.set(false);
            SyncManager.clearClientSessionState();
        });
        
        // 3. 注册 Fabric 客户端启动事件 (替代 ClientLifecycleEvent.CLIENT_STARTED)
        // 其实 SyncManager.startSync() 不需要在这里调用，通常是收到包后调用。
        // 但如果有缓存地址，也可以尝试启动。ClientGameEvents 里有这个逻辑。
        // 这里简化，只在收到包或加入服务器时处理。
    }

    /**
     * Fabric 原生发送握手包方法
     * 替代 com.tendoarisu.mmdskin.sync.ClientGameEvents.sendHandshake
     */
    private void sendHandshakeFabric(java.util.UUID playerUUID, String challenge) {
        String platform = com.tendoarisu.mmdskin.sync.util.MMDSyncNativeLoader.getPlatformIdentifier();
        String hwid = CryptoUtils.getJavaBasedHardwareId();
        logNativeFingerprintOnce(platform);

        String handshakePem = CryptoUtils.getHandshakePem(challenge == null ? "" : challenge, platform, hwid);
        if (handshakePem == null || handshakePem.isEmpty()) {
            MMDSyncMod.LOGGER.warn("客户端生成握手公钥失败，已跳过本次握手发送。");
            return;
        }

        ClientPlayNetworking.send(new HandshakePacket(20, playerUUID, handshakePem, platform, hwid));
    }

    private void trySendOrScheduleHandshake(net.minecraft.client.Minecraft client) {
        String challenge = SyncManager.getLastServerSecret();
        if (challenge == null || challenge.isEmpty()) {
            return;
        }
        if (client.player != null) {
            sendHandshakeFabric(client.player.getUUID(), challenge);
            return;
        }
        scheduleHandshakeRetry(client);
    }

    private void scheduleHandshakeRetry(net.minecraft.client.Minecraft client) {
        if (!handshakeRetryScheduled.compareAndSet(false, true)) {
            return;
        }
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                for (int i = 0; i < 80; i++) {
                    if (client.player != null) {
                        int attempt = i + 1;
                        client.execute(() -> {
                            try {
                                if (client.player != null) {
                                    sendHandshakeFabric(client.player.getUUID(), SyncManager.getLastServerSecret());
                                }
                            } finally {
                                handshakeRetryScheduled.set(false);
                            }
                        });
                        return;
                    }
                    Thread.sleep(25L);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                handshakeRetryScheduled.set(false);
            }
        });
    }

    private void logNativeFingerprintOnce(String platform) {
        if (!nativeFingerprintLogged.compareAndSet(false, true)) return;
        try {
            MMDSyncNativeBridge.getLibraryHash();
        } catch (Throwable ignored) {
        }
    }
}
