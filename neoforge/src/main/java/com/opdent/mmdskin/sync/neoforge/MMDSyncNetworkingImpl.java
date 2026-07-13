package com.opdent.mmdskin.sync.neoforge;

import com.opdent.mmdskin.sync.network.SyncUrlPacket;
import com.opdent.mmdskin.sync.network.resource.ResourceTransferPacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class MMDSyncNetworkingImpl {
    private MMDSyncNetworkingImpl() {
    }

    public static void sendSyncUrlPacket(ServerPlayer player, SyncUrlPacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendResourceTransferPacket(ServerPlayer player, ResourceTransferPacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }
}
