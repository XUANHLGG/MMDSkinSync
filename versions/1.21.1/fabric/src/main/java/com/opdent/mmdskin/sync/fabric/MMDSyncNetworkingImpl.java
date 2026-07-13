package com.opdent.mmdskin.sync.fabric;

import com.opdent.mmdskin.sync.network.SyncUrlPacket;
import com.opdent.mmdskin.sync.network.resource.ResourceTransferPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public final class MMDSyncNetworkingImpl {
    private MMDSyncNetworkingImpl() {
    }

    public static void sendSyncUrlPacket(ServerPlayer player, SyncUrlPacket packet) {
        ServerPlayNetworking.send(player, packet);
    }

    public static void sendResourceTransferPacket(ServerPlayer player, ResourceTransferPacket packet) {
        ServerPlayNetworking.send(player, packet);
    }
}
