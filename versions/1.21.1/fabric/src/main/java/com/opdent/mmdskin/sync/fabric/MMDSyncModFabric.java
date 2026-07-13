package com.opdent.mmdskin.sync.fabric;

import com.opdent.mmdskin.sync.ServerAuthManager;
import com.opdent.mmdskin.sync.network.HandshakePacket;
import com.opdent.mmdskin.sync.network.resource.ResourceTransferPacket;
import com.opdent.mmdskin.sync.network.resource.ResourceTransferServerManager;
import com.opdent.mmdskin.sync.network.resource.ResourceTransferServerPacketHandler;
import com.tendoarisu.mmdskin.sync.MMDSync;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import com.opdent.mmdskin.sync.network.SyncUrlPacket;

public class MMDSyncModFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        registerPayloadTypes();
        MMDSync.init();

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
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                ResourceTransferServerManager.abortUploadsFor(handler.player.getUUID()));
    }

    private static void registerPayloadTypes() {
        PayloadTypeRegistry.playS2C().register(SyncUrlPacket.TYPE, SyncUrlPacket.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(ResourceTransferPacket.TYPE, ResourceTransferPacket.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(HandshakePacket.TYPE, HandshakePacket.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(ResourceTransferPacket.TYPE, ResourceTransferPacket.STREAM_CODEC);
    }
}
