package com.opdent.mmdskin.sync.fabric;

import com.opdent.mmdskin.sync.MMDSyncMod;
import com.opdent.mmdskin.sync.ServerAuthManager;
import com.opdent.mmdskin.sync.network.HandshakePacket;
import com.opdent.mmdskin.sync.network.resource.ResourceTransferPacket;
import com.opdent.mmdskin.sync.network.resource.ResourceTransferServerPacketHandler;
import com.tendoarisu.mmdskin.sync.MMDSync;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import com.opdent.mmdskin.sync.network.SyncUrlPacket;

public class MMDSyncModFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        MMDSync.init();

        try {
            PayloadTypeRegistry.playS2C().register(SyncUrlPacket.TYPE, SyncUrlPacket.STREAM_CODEC);
        } catch (IllegalArgumentException ignored) {
        } catch (Throwable t) {
            MMDSyncMod.LOGGER.error("注册 Fabric SyncUrlPacket 失败", t);
        }

        try {
            PayloadTypeRegistry.playS2C().register(ResourceTransferPacket.TYPE, ResourceTransferPacket.STREAM_CODEC);
        } catch (IllegalArgumentException ignored) {
        } catch (Throwable t) {
            MMDSyncMod.LOGGER.error("注册 Fabric ResourceTransferPacket(S2C) 失败", t);
        }

        try {
            PayloadTypeRegistry.playC2S().register(HandshakePacket.TYPE, HandshakePacket.STREAM_CODEC);
        } catch (IllegalArgumentException ignored) {
        } catch (Throwable t) {
            MMDSyncMod.LOGGER.error("注册 Fabric HandshakePacket(C2S) 失败", t);
        }

        try {
            PayloadTypeRegistry.playC2S().register(ResourceTransferPacket.TYPE, ResourceTransferPacket.STREAM_CODEC);
        } catch (IllegalArgumentException ignored) {
        } catch (Throwable t) {
            MMDSyncMod.LOGGER.error("注册 Fabric ResourceTransferPacket(C2S) 失败", t);
        }

        ServerPlayNetworking.registerGlobalReceiver(HandshakePacket.TYPE, (payload, context) -> {
            net.minecraft.server.level.ServerPlayer player = context.player();
            context.server().execute(() -> {
                SyncUrlPacket response = ServerAuthManager.handleHandshake(player, payload);
                if (response != null) {
                    ServerPlayNetworking.send(player, response);
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(ResourceTransferPacket.TYPE, (payload, context) -> {
            context.server().execute(() -> ResourceTransferServerPacketHandler.handleServerboundPacket(context.player(), payload));
        });
    }
}
