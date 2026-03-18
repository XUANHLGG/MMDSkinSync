package com.opdent.mmdskin.sync;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class MMDSyncServerGuards {
    private MMDSyncServerGuards() {
    }

    public static boolean isPureSingleplayerServer(MinecraftServer server) {
        return server != null && !server.isDedicatedServer() && !server.isPublished();
    }

    public static boolean isPureSingleplayerPlayer(ServerPlayer player) {
        return player != null && isPureSingleplayerServer(player.getServer());
    }
}
