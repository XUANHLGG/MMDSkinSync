package com.opdent.mmdskin.sync.neoforge;

import com.opdent.mmdskin.sync.ServerAuthManager;
import com.opdent.mmdskin.sync.SyncManager;
import com.opdent.mmdskin.sync.network.HandshakePacket;
import com.opdent.mmdskin.sync.network.SyncUrlPacket;
import com.opdent.mmdskin.sync.network.resource.ResourceTransferClientManager;
import com.opdent.mmdskin.sync.network.resource.ResourceTransferPacket;
import com.opdent.mmdskin.sync.network.resource.ResourceTransferServerPacketHandler;
import com.tendoarisu.mmdskin.sync.util.CryptoUtils;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import com.tendoarisu.mmdskin.sync.MMDSync;
import com.opdent.mmdskin.sync.MMDSyncMod;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.payload.MinecraftRegisterPayload;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Mod(MMDSyncMod.MODID)
public class MMDSyncModNeoForge {
    private static final AtomicBoolean nativeFingerprintLogged = new AtomicBoolean(false);
    private static final AtomicBoolean handshakeRetryScheduled = new AtomicBoolean(false);
    public MMDSyncModNeoForge(IEventBus modEventBus) {
        MMDSync.init();
        modEventBus.addListener(this::registerPayloads);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            MMDSync.initClient();
            bindUpstreamModelSelectionNetworking();
            NeoForge.EVENT_BUS.addListener(this::onClientLogin);
            NeoForge.EVENT_BUS.addListener(this::onClientLogout);
        }
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(MMDSyncMod.MODID).optional();
        registrar.playBidirectional(HandshakePacket.TYPE, HandshakePacket.STREAM_CODEC, MMDSyncModNeoForge::handleHandshakePacket);
        registrar.playBidirectional(ResourceTransferPacket.TYPE, ResourceTransferPacket.STREAM_CODEC, MMDSyncModNeoForge::handleResourceTransferPacket);
        registrar.playToClient(SyncUrlPacket.TYPE, SyncUrlPacket.STREAM_CODEC, (payload, context) -> {
            context.enqueueWork(() -> {
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
                        trySendOrScheduleHandshake();
                    } else {
                        SyncManager.startSync();
                    }
                }
            });
        });
        registerPluginChannels();
    }

    private void registerPluginChannels() {
        net.minecraft.client.multiplayer.ClientPacketListener connection = net.minecraft.client.Minecraft.getInstance().getConnection();
        if (connection == null || net.minecraft.client.Minecraft.getInstance().player == null) {
            return;
        }
        try {
            ResourceLocation syncUrl = ResourceLocation.fromNamespaceAndPath(MMDSyncMod.MODID, "sync_url");
            ResourceLocation resourceTransfer = ResourceLocation.fromNamespaceAndPath(MMDSyncMod.MODID, "resource_transfer");
            Set<ResourceLocation> channels = Set.of(syncUrl, resourceTransfer);
            NetworkRegistry.onMinecraftRegister(connection.getConnection(), channels);
            PacketDistributor.sendToServer(new MinecraftRegisterPayload(channels));
        } catch (Throwable t) {
            MMDSyncMod.LOGGER.warn("[NeoForge] 发送插件通道注册失败: {}", t.toString());
        }
    }

    private static void handleResourceTransferPacket(ResourceTransferPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.flow().isClientbound()) {
                var decoded = packet.toResourcePacket();
                ResourceTransferClientManager.acceptPacket(decoded);
            } else {
                if (context.player() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                    ResourceTransferServerPacketHandler.handleServerboundPacket(serverPlayer, packet);
                }
            }
        });
    }

    private static void handleHandshakePacket(HandshakePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                SyncUrlPacket response = ServerAuthManager.handleHandshake(serverPlayer, packet);
                if (response != null) {
                    PacketDistributor.sendToPlayer(serverPlayer, response);
                }
            }
        });
    }

    private void sendHandshakeNeoForge(String challenge) {
        String platform = com.tendoarisu.mmdskin.sync.util.MMDSyncNativeLoader.getPlatformIdentifier();
        String hwid = CryptoUtils.getJavaBasedHardwareId();
        logNativeFingerprintOnce(platform);
        String handshakePem = CryptoUtils.getHandshakePem(challenge == null ? "" : challenge, platform, hwid);
        if (handshakePem == null || handshakePem.isEmpty()) {
            return;
        }
        if (net.minecraft.client.Minecraft.getInstance().player != null) {
            PacketDistributor.sendToServer(new HandshakePacket(20, net.minecraft.client.Minecraft.getInstance().player.getUUID(), handshakePem, platform, hwid));
        }
    }

    private void trySendOrScheduleHandshake() {
        String challenge = SyncManager.getLastServerSecret();
        if (challenge == null || challenge.isEmpty()) {
            return;
        }
        if (net.minecraft.client.Minecraft.getInstance().player != null) {
            sendHandshakeNeoForge(challenge);
            return;
        }
        scheduleHandshakeRetry();
    }

    private void scheduleHandshakeRetry() {
        if (!handshakeRetryScheduled.compareAndSet(false, true)) {
            return;
        }
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                for (int i = 0; i < 80; i++) {
                    if (net.minecraft.client.Minecraft.getInstance().player != null) {
                        net.minecraft.client.Minecraft.getInstance().execute(() -> {
                            try {
                                if (net.minecraft.client.Minecraft.getInstance().player != null) {
                                    sendHandshakeNeoForge(SyncManager.getLastServerSecret());
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

    private void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        registerPluginChannels();
        trySendOrScheduleHandshake();
    }

    private void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        handshakeRetryScheduled.set(false);
        SyncManager.clearClientSessionState();
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
            setNetworkSender.invoke(selectorHandler, (Consumer<String>) this::sendModelSelectToServer);

            Class<?> syncManagerClass = Class.forName("com.shiroha.mmdskin.ui.network.PlayerModelSyncManager");
            java.lang.reflect.Method setBroadcaster = syncManagerClass.getMethod("setNetworkBroadcaster", BiConsumer.class);
            setBroadcaster.invoke(null, (BiConsumer<java.util.UUID, String>) this::sendModelSelectToServer);
        } catch (Throwable e) {
            MMDSyncMod.LOGGER.warn("绑定上游模型选择网络发送器失败(NeoForge): {}", e.toString());
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
            Class<?> packClass = Class.forName("com.shiroha.mmdskin.neoforge.network.MmdSkinNetworkPack");
            java.lang.reflect.Method withAnimId = packClass.getMethod("withAnimId", int.class, java.util.UUID.class, String.class);
            Object payload = withAnimId.invoke(null, 3, playerUUID, modelName);
            if (payload instanceof CustomPacketPayload customPacketPayload) {
                PacketDistributor.sendToServer(customPacketPayload);
            }
        } catch (Throwable e) {
            MMDSyncMod.LOGGER.warn("发送上游模型选择包失败(NeoForge): {}", e.toString());
        }
    }
}
