package com.opdent.mmdskin.sync.neoforge;

import com.opdent.mmdskin.sync.ServerAuthManager;
import com.opdent.mmdskin.sync.SyncManager;
import com.opdent.mmdskin.sync.network.HandshakePacket;
import com.opdent.mmdskin.sync.network.SyncUrlPacket;
import com.tendoarisu.mmdskin.sync.util.CryptoUtils;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import com.tendoarisu.mmdskin.sync.MMDSync;
import com.opdent.mmdskin.sync.MMDSyncMod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import java.util.concurrent.atomic.AtomicBoolean;

@Mod(MMDSyncMod.MODID)
public class MMDSyncModNeoForge {
    private static final AtomicBoolean nativeFingerprintLogged = new AtomicBoolean(false);
    public MMDSyncModNeoForge(IEventBus modEventBus) {
        MMDSync.init();
        modEventBus.addListener(this::registerPayloads);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            MMDSync.initClient();
            NeoForge.EVENT_BUS.addListener(this::onClientLogout);
        }
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(MMDSyncMod.MODID);
        registrar.playBidirectional(HandshakePacket.TYPE, HandshakePacket.STREAM_CODEC, MMDSyncModNeoForge::handleHandshakePacket);
        registrar.commonToClient(SyncUrlPacket.TYPE, SyncUrlPacket.STREAM_CODEC, (payload, context) -> {
            context.enqueueWork(() -> {
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
                    }
                } else {
                    if (payload.serverSecret() != null && payload.serverSecret().contains("|")) {
                        sendHandshakeNeoForge(SyncManager.getLastServerSecret());
                    } else if (payload.url() != null && !payload.url().isEmpty()) {
                        SyncManager.startSync();
                    }
                }
            });
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

    private void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        SyncManager.clearClientSessionState();
    }

    private void logNativeFingerprintOnce(String platform) {
        if (!nativeFingerprintLogged.compareAndSet(false, true)) return;
        try {
            MMDSyncNativeBridge.getLibraryHash();
        } catch (Throwable ignored) {
        }
    }
}
