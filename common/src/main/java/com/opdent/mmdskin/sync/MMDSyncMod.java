package com.opdent.mmdskin.sync;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import com.tendoarisu.mmdskin.sync.Config;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.networking.NetworkManager;
import net.minecraft.server.level.ServerPlayer;

public class MMDSyncMod {
    public static final String MODID = "mmdsync";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static void init() {
        // 加载自定义配置
        Config.load();
        
        // 注册网络包
        registerPayloads();
        
        // 注册命令
        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) -> {
            CommandHandler.register(dispatcher);
        });
        
        // 监听玩家加入 (服务端)
        PlayerEvent.PLAYER_JOIN.register(player -> {
            if (player instanceof ServerPlayer serverPlayer) {
                onPlayerLoggedIn(serverPlayer);
            }
        });
    }

    public static void registerPayloads() {
        // 在 Common 层不再进行网络包注册
        // 注册逻辑完全下沉到 Fabric/NeoForge 平台层
        // 以避免 Architectury 可能存在的 Codec 缓存或版本不一致问题
    }

    private static void onPlayerLoggedIn(ServerPlayer player) {
        NetworkManager.sendToPlayer(player, ServerAuthManager.buildChallengePacket(player));
    }
}
