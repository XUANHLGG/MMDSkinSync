package com.opdent.mmdskin.sync.neoforge;

import com.opdent.mmdskin.sync.MMDSyncMod;
import com.opdent.mmdskin.sync.ServerAuthManager;
import com.opdent.mmdskin.sync.network.HandshakePacket;
import com.opdent.mmdskin.sync.network.SyncUrlPacket;
import com.opdent.mmdskin.sync.network.resource.ResourceTransferPacket;
import com.opdent.mmdskin.sync.network.resource.ResourceTransferServerPacketHandler;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.lang.reflect.InvocationTargetException;

/** Server-safe payload dispatch. Client handlers are resolved only for clientbound traffic. */
final class MMDSyncNeoForgePayloadHandlers {
    private static final String CLIENT_HANDLERS =
            "com.opdent.mmdskin.sync.neoforge.MMDSyncNeoForgeClientPayloadHandlers";

    private MMDSyncNeoForgePayloadHandlers() {
    }

    static void handleHandshakePacket(HandshakePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer serverPlayer)) {
                MMDSyncMod.LOGGER.warn("忽略缺少服务端玩家上下文的 NeoForge 握手包");
                return;
            }
            SyncUrlPacket response = ServerAuthManager.handleHandshake(serverPlayer, packet);
            if (response != null) {
                PacketDistributor.sendToPlayer(serverPlayer, response);
            }
        });
    }

    static void handleSyncUrlPacket(SyncUrlPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> invokeClientHandler("handleSyncUrlPacket", SyncUrlPacket.class, packet));
    }

    static void handleResourceTransferPacket(ResourceTransferPacket packet, IPayloadContext context) {
        if (context.flow().isClientbound()) {
            context.enqueueWork(() -> invokeClientHandler(
                    "handleResourceTransferPacket",
                    ResourceTransferPacket.class,
                    packet
            ));
            return;
        }

        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer serverPlayer)) {
                MMDSyncMod.LOGGER.warn("忽略缺少服务端玩家上下文的 NeoForge 资源传输包");
                return;
            }
            ResourceTransferServerPacketHandler.handleServerboundPacket(serverPlayer, packet);
        });
    }

    private static void invokeClientHandler(String methodName, Class<?> packetType, Object packet) {
        try {
            Class.forName(CLIENT_HANDLERS, true, MMDSyncNeoForgePayloadHandlers.class.getClassLoader())
                    .getDeclaredMethod(methodName, packetType)
                    .invoke(null, packet);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            throw new IllegalStateException("NeoForge 客户端 payload 处理失败: " + methodName, cause);
        } catch (ReflectiveOperationException | LinkageError exception) {
            throw new IllegalStateException("无法装载 NeoForge 客户端 payload 处理器: " + methodName, exception);
        }
    }
}
