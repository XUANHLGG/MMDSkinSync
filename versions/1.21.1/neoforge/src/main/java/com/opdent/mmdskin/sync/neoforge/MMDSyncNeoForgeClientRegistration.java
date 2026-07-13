package com.opdent.mmdskin.sync.neoforge;

import com.opdent.mmdskin.sync.MMDSyncMod;
import com.opdent.mmdskin.sync.SyncManager;
import com.opdent.mmdskin.sync.network.HandshakePacket;
import com.tendoarisu.mmdskin.sync.MMDSync;
import com.tendoarisu.mmdskin.sync.util.CryptoUtils;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Client-only setup, lifecycle, handshake, and MC-MMD 1.0.5 network bindings. */
@EventBusSubscriber(value = Dist.CLIENT, modid = MMDSyncMod.MODID)
public final class MMDSyncNeoForgeClientRegistration {
    private static final AtomicBoolean NATIVE_FINGERPRINT_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean HANDSHAKE_RETRY_SCHEDULED = new AtomicBoolean();

    private MMDSyncNeoForgeClientRegistration() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MMDSync.initClient();
            bindUpstreamModelSelectionNetworking();
            NeoForge.EVENT_BUS.addListener(MMDSyncNeoForgeClientRegistration::onClientLogin);
            NeoForge.EVENT_BUS.addListener(MMDSyncNeoForgeClientRegistration::onClientLogout);
        });
    }

    private static void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        trySendOrScheduleHandshake();
    }

    private static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        HANDSHAKE_RETRY_SCHEDULED.set(false);
        SyncManager.clearClientSessionState();
    }

    static void trySendOrScheduleHandshake() {
        String challenge = SyncManager.getLastServerSecret();
        if (challenge == null || challenge.isEmpty()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            sendHandshake(client.player, challenge);
            return;
        }
        scheduleHandshakeRetry(client);
    }

    private static void sendHandshake(LocalPlayer player, String challenge) {
        String platform = com.tendoarisu.mmdskin.sync.util.MMDSyncNativeLoader.getPlatformIdentifier();
        String hwid = CryptoUtils.getJavaBasedHardwareId();
        logNativeFingerprintOnce();
        String pem = CryptoUtils.getHandshakePem(challenge == null ? "" : challenge, platform, hwid);
        if (pem == null || pem.isEmpty()) {
            MMDSyncMod.LOGGER.warn("NeoForge 客户端未能生成握手材料，跳过本次握手");
            return;
        }
        PacketDistributor.sendToServer(new HandshakePacket(20, player.getUUID(), pem, platform, hwid));
    }

    private static void scheduleHandshakeRetry(Minecraft client) {
        if (!HANDSHAKE_RETRY_SCHEDULED.compareAndSet(false, true)) {
            return;
        }
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                for (int i = 0; i < 80; i++) {
                    if (client.player != null) {
                        client.execute(() -> {
                            try {
                                LocalPlayer player = client.player;
                                String challenge = SyncManager.getLastServerSecret();
                                if (player != null && challenge != null && !challenge.isEmpty()) {
                                    sendHandshake(player, challenge);
                                }
                            } finally {
                                HANDSHAKE_RETRY_SCHEDULED.set(false);
                            }
                        });
                        return;
                    }
                    Thread.sleep(25L);
                }
                MMDSyncMod.LOGGER.warn("NeoForge 客户端握手等待玩家对象超时");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                HANDSHAKE_RETRY_SCHEDULED.set(false);
            }
        });
    }

    private static void logNativeFingerprintOnce() {
        if (!NATIVE_FINGERPRINT_LOGGED.compareAndSet(false, true)) {
            return;
        }
        try {
            MMDSyncNativeBridge.getLibraryHash();
        } catch (Throwable throwable) {
            MMDSyncMod.LOGGER.warn("读取 MMDSync 原生库指纹失败，将继续使用 Java 降级路径: {}", throwable.toString());
        }
    }

    @SuppressWarnings("unchecked")
    private static void bindUpstreamModelSelectionNetworking() {
        try {
            Class<?> selectorHandlerClass = Class.forName("com.shiroha.mmdskin.ui.network.ModelSelectorNetworkHandler");
            Object selectorHandler = selectorHandlerClass.getMethod("getInstance").invoke(null);
            selectorHandlerClass.getMethod("setNetworkSender", Consumer.class)
                    .invoke(selectorHandler, (Consumer<String>) MMDSyncNeoForgeClientRegistration::sendModelSelectToServer);

            Class<?> syncManagerClass = Class.forName("com.shiroha.mmdskin.ui.network.PlayerModelSyncManager");
            syncManagerClass.getMethod("setNetworkBroadcaster", BiConsumer.class)
                    .invoke(null, (BiConsumer<UUID, String>) MMDSyncNeoForgeClientRegistration::sendModelSelectToServer);
        } catch (Throwable throwable) {
            MMDSyncMod.LOGGER.warn(
                    "绑定 MmdSkin 1.0.5 模型选择网络发送器失败；模型 UI 保持可用但不会经此兼容绑定发送: {}",
                    throwable.toString()
            );
        }
    }

    private static void sendModelSelectToServer(String modelName) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            sendModelSelectToServer(player.getUUID(), modelName);
        }
    }

    private static void sendModelSelectToServer(UUID playerUuid, String modelName) {
        try {
            Class<?> packClass = Class.forName("com.shiroha.mmdskin.neoforge.network.MmdSkinNetworkPack");
            Object payload = packClass.getMethod("withAnimId", int.class, UUID.class, String.class)
                    .invoke(null, 3, playerUuid, modelName);
            if (!(payload instanceof CustomPacketPayload customPacketPayload)) {
                MMDSyncMod.LOGGER.warn("MmdSkin 1.0.5 模型选择工厂未返回 CustomPacketPayload，已安全跳过发送");
                return;
            }
            PacketDistributor.sendToServer(customPacketPayload);
        } catch (Throwable throwable) {
            MMDSyncMod.LOGGER.warn("发送 MmdSkin 1.0.5 模型选择包失败，已安全跳过: {}", throwable.toString());
        }
    }
}
