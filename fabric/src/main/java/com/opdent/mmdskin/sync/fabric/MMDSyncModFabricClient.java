package com.opdent.mmdskin.sync.fabric;

import com.opdent.mmdskin.sync.MMDSyncMod;
import com.opdent.mmdskin.sync.network.resource.ResourceTransferClientManager;
import com.opdent.mmdskin.sync.network.resource.ResourceTransferPacket;
import com.tendoarisu.mmdskin.sync.MMDSync;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.opdent.mmdskin.sync.SyncManager;
import com.tendoarisu.mmdskin.sync.util.CryptoUtils;
import com.opdent.mmdskin.sync.network.HandshakePacket;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import net.minecraft.client.player.LocalPlayer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class MMDSyncModFabricClient implements ClientModInitializer {
    private static final AtomicBoolean nativeFingerprintLogged = new AtomicBoolean(false);
    private static final AtomicBoolean handshakeRetryScheduled = new AtomicBoolean(false);
    @Override
    public void onInitializeClient() {
        MMDSync.initClient();
        bindUpstreamModelSelectionNetworking();

        try {
            PayloadTypeRegistry.playS2C().register(com.opdent.mmdskin.sync.network.SyncUrlPacket.TYPE, com.opdent.mmdskin.sync.network.SyncUrlPacket.STREAM_CODEC);
        } catch (IllegalArgumentException ignored) {
        }
        try {
            PayloadTypeRegistry.playS2C().register(ResourceTransferPacket.TYPE, ResourceTransferPacket.STREAM_CODEC);
        } catch (IllegalArgumentException ignored) {
        }
        try {
            PayloadTypeRegistry.playC2S().register(HandshakePacket.TYPE, HandshakePacket.STREAM_CODEC);
        } catch (IllegalArgumentException ignored) {
        }
        try {
            PayloadTypeRegistry.playC2S().register(ResourceTransferPacket.TYPE, ResourceTransferPacket.STREAM_CODEC);
        } catch (IllegalArgumentException ignored) {
        }

        ClientPlayNetworking.registerGlobalReceiver(ResourceTransferPacket.TYPE, (payload, context) -> {
            context.client().execute(() -> ResourceTransferClientManager.acceptPacket(payload.toResourcePacket()));
        });
        
        ClientPlayNetworking.registerGlobalReceiver(
            com.opdent.mmdskin.sync.network.SyncUrlPacket.TYPE, 
            (payload, context) -> {
                context.client().execute(() -> {
                    SyncManager.setServerSecret(payload.serverSecret());
                    SyncManager.setCurrentServerId(payload.serverId());

                    if (payload.encryptedKey() != null && !payload.encryptedKey().isEmpty()) {
                        CryptoUtils.installSessionMaterial(payload.encryptedKey(), SyncManager.getLastServerSecret());
                        if (CryptoUtils.hasSessionMaterial()) {
                            SyncManager.onSessionKeyReady();
                            SyncManager.startSync();
                        }
                    } else {
                        if (payload.serverSecret() != null && !payload.serverSecret().isEmpty()) {
                            trySendOrScheduleHandshake(context.client());
                        } else {
                            SyncManager.startSync();
                        }
                    }

                });
            }
        );

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> trySendOrScheduleHandshake(client));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            handshakeRetryScheduled.set(false);
            SyncManager.clearClientSessionState();
        });
    }

    private void sendHandshakeFabric(java.util.UUID playerUUID, String challenge) {
        String platform = com.tendoarisu.mmdskin.sync.util.MMDSyncNativeLoader.getPlatformIdentifier();
        String hwid = CryptoUtils.getJavaBasedHardwareId();
        logNativeFingerprintOnce(platform);

        String handshakePem = CryptoUtils.getHandshakePem(challenge == null ? "" : challenge, platform, hwid);
        if (handshakePem == null || handshakePem.isEmpty()) {
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

    @SuppressWarnings("unchecked")
    private void bindUpstreamModelSelectionNetworking() {
        try {
            Class<?> selectorHandlerClass = Class.forName("com.shiroha.mmdskin.ui.network.ModelSelectorNetworkHandler");
            Object selectorHandler = selectorHandlerClass.getMethod("getInstance").invoke(null);
            java.lang.reflect.Method setNetworkSender = selectorHandlerClass.getMethod("setNetworkSender", Consumer.class);
            setNetworkSender.invoke(selectorHandler, (Consumer<String>) modelName -> sendModelSelectToServer(modelName));

            Class<?> syncManagerClass = Class.forName("com.shiroha.mmdskin.ui.network.PlayerModelSyncManager");
            java.lang.reflect.Method setBroadcaster = syncManagerClass.getMethod("setNetworkBroadcaster", BiConsumer.class);
            setBroadcaster.invoke(null, (BiConsumer<java.util.UUID, String>) (playerUUID, modelName) -> sendModelSelectToServer(playerUUID, modelName));
        } catch (Throwable e) {
            MMDSyncMod.LOGGER.warn("绑定上游模型选择网络发送器失败(Fabric): {}", e.toString());
        }
    }

    private void sendModelSelectToServer(String modelName) {
        LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
        if (player != null) {
            sendModelSelectToServer(player.getUUID(), modelName);
        }
    }

    private void sendModelSelectToServer(java.util.UUID playerUUID, String modelName) {
        try {
            Class<?> packClass = Class.forName("com.shiroha.mmdskin.fabric.network.MmdSkinNetworkPack");
            java.lang.reflect.Method sendMethod = packClass.getMethod("sendToServer", int.class, java.util.UUID.class, String.class);
            sendMethod.invoke(null, 3, playerUUID, modelName);
        } catch (Throwable e) {
            MMDSyncMod.LOGGER.warn("发送上游模型选择包失败(Fabric): {}", e.toString());
        }
    }
}
