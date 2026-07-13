package com.opdent.mmdskin.sync;

import com.mojang.brigadier.CommandDispatcher;
import com.tendoarisu.mmdskin.sync.Config;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class CommandHandler {
    private static byte[] serverSyncKey = null;

    public static synchronized byte[] getServerSyncKey() {
        if (serverSyncKey == null) {
            serverSyncKey = new byte[32];
            new java.security.SecureRandom().nextBytes(serverSyncKey);
        }
        return serverSyncKey;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mmdsync")
            .requires(source -> source.hasPermission(2))
            .executes(context -> executeSync(context.getSource()))
            .then(Commands.literal("reload")
                .executes(context -> {
                    context.getSource().sendSuccess(() -> Component.literal("§a正在重新加载配置并同步到所有玩家..."), true);

                    Config.load();

                    return executeSync(context.getSource(), "§a配置重载完成，已重新下发资源握手。");
                })
            )
        );
    }

    private static int executeSync(CommandSourceStack source) {
        return executeSync(source, null);
    }

    private static int executeSync(CommandSourceStack source, String successPrefix) {
        source.sendSuccess(() -> Component.literal("§a正在全服同步模型..."), true);
        try {
            if (!source.getLevel().isClientSide) {
                if (MMDSyncServerGuards.isPureSingleplayerServer(source.getServer())) {
                    source.sendSuccess(() -> Component.literal("§e当前为纯单人世界，本地模型不需要 MMDSync 同步。"), true);
                    return 1;
                }
                for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
                    MMDSyncNetworking.sendSyncUrlPacket(player, ServerAuthManager.buildChallengePacket(player));
                }
                if (successPrefix != null && !successPrefix.isEmpty()) {
                    source.sendSuccess(() -> Component.literal(successPrefix), true);
                }
                source.sendSuccess(() -> Component.literal("§a已向所有在线玩家下发新的握手挑战。"), true);
            }

            source.sendSuccess(() -> Component.literal("§a已为全服下发新握手挑战。"), true);
        } catch (Exception e) {
            source.sendFailure(Component.literal("§c同步失败: " + e.getMessage()));
            MMDSyncMod.LOGGER.error("执行 /mmdsync 失败", e);
        }
        return 1;
    }
}
