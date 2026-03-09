package com.opdent.mmdskin.sync;

import com.mojang.brigadier.CommandDispatcher;
import com.tendoarisu.mmdskin.sync.Config;
import com.tendoarisu.mmdskin.sync.EmbeddedServer;
import dev.architectury.networking.NetworkManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;

public class CommandHandler {
    private static byte[] serverSyncKey = null;

    /**
     * 获取或生成服务器同步 AES 密钥
     */
    public static synchronized byte[] getServerSyncKey() {
        if (serverSyncKey == null) {
            // 生成一个随机的 32 字节 AES 密钥
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
                    EmbeddedServer.stop();
                    EmbeddedServer.start();

                    return executeSync(context.getSource(), "§a配置重载完成，内置服务器已重启。");
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
                for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
                    NetworkManager.sendToPlayer(player, ServerAuthManager.buildChallengePacket(player));
                }
                if (successPrefix != null && !successPrefix.isEmpty()) {
                    source.sendSuccess(() -> Component.literal(successPrefix), true);
                }
                source.sendSuccess(() -> Component.literal("§a已向所有在线玩家下发新的握手挑战。"), true);
            }

            int count = 0;
            try {
                Object attachmentType = Class.forName("com.shiroha.mmdskin.neoforge.register.MmdSkinAttachments")
                        .getField("PLAYER_MMD_MODEL").get(null);
                java.util.function.Supplier<?> supplier = (java.util.function.Supplier<?>) attachmentType;
                Class<?> attachmentTypeClass = Class.forName("net.neoforged.neoforge.attachment.AttachmentType");
                Class<?> packetClass = Class.forName("com.shiroha.mmdskin.neoforge.network.MmdSkinNetworkPack");
                java.lang.reflect.Method withAnimId = packetClass.getMethod("withAnimId", int.class, UUID.class, String.class);

                for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
                    java.lang.reflect.Method getDataMethod = player.getClass().getMethod("getData", attachmentTypeClass);
                    String modelName = (String) getDataMethod.invoke(player, supplier.get());
                    if (modelName != null && !modelName.isEmpty()) {
                        Object packet = withAnimId.invoke(null, 3, player.getUUID(), modelName);
                        NetworkManager.sendToPlayers(source.getServer().getPlayerList().getPlayers(), (CustomPacketPayload) packet);
                        count++;
                    }
                }
            } catch (ClassNotFoundException ignored) {
            }

            final int finalCount = count;
            source.sendSuccess(() -> Component.literal("§a已为全服下发新握手挑战，并为 " + finalCount + " 名玩家广播模型状态。"), true);
        } catch (Exception e) {
            source.sendFailure(Component.literal("§c同步失败: " + e.getMessage()));
            MMDSyncMod.LOGGER.error("执行 /mmdsync 失败", e);
        }
        return 1;
    }
}
