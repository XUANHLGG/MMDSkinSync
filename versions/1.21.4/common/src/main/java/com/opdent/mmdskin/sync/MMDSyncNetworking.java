package com.opdent.mmdskin.sync;

import com.opdent.mmdskin.sync.network.SyncUrlPacket;
import com.opdent.mmdskin.sync.network.resource.ResourceTransferPacket;
import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.server.level.ServerPlayer;

public final class MMDSyncNetworking {
    private MMDSyncNetworking() {
    }

    @ExpectPlatform
    public static void sendSyncUrlPacket(ServerPlayer player, SyncUrlPacket packet) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void sendResourceTransferPacket(ServerPlayer player, ResourceTransferPacket packet) {
        throw new AssertionError();
    }
}
