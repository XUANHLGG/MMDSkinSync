package com.opdent.mmdskin.sync;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import com.tendoarisu.mmdskin.sync.Config;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.PlayerEvent;
import net.minecraft.server.level.ServerPlayer;

public class MMDSyncMod {
    public static final String MODID = "mmdsync";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static void init() {
        Config.load();
        registerPayloads();
        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) -> {
            CommandHandler.register(dispatcher);
        });
        PlayerEvent.PLAYER_JOIN.register(player -> {
            if (player instanceof ServerPlayer serverPlayer) {
                onPlayerLoggedIn(serverPlayer);
            }
        });
    }

    public static void registerPayloads() {
    }

    private static void onPlayerLoggedIn(ServerPlayer player) {
        if (MMDSyncServerGuards.isPureSingleplayerPlayer(player)) {
            return;
        }
        MMDSyncNetworking.sendSyncUrlPacket(player, ServerAuthManager.buildChallengePacket(player));
    }
}
