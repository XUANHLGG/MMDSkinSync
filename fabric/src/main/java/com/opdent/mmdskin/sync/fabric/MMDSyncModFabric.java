package com.opdent.mmdskin.sync.fabric;

import com.opdent.mmdskin.sync.MMDSyncMod;
import com.opdent.mmdskin.sync.ServerAuthManager;
import com.opdent.mmdskin.sync.network.HandshakePacket;
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
            // 同时注册 C2S 握手包，确保双端都认识这个包
            PayloadTypeRegistry.playC2S().register(HandshakePacket.TYPE, HandshakePacket.STREAM_CODEC);
            ServerPlayNetworking.registerGlobalReceiver(HandshakePacket.TYPE, (payload, context) -> {
                net.minecraft.server.level.ServerPlayer player = context.player();
                context.server().execute(() -> {
                    SyncUrlPacket response = ServerAuthManager.handleHandshake(player, payload);
                    if (response != null) {
                        ServerPlayNetworking.send(player, response);
                    }
                });
            });
            
        } catch (IllegalArgumentException ignored) {
        } catch (Throwable t) {
            MMDSyncMod.LOGGER.error("注册 Fabric Payload Codec 失败", t);
        }
    }
}
