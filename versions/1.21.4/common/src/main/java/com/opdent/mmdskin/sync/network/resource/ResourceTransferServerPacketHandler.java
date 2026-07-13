package com.opdent.mmdskin.sync.network.resource;

import com.opdent.mmdskin.sync.MMDSyncMod;
import com.opdent.mmdskin.sync.MMDSyncNetworking;
import com.opdent.mmdskin.sync.MMDSyncServerGuards;
import com.opdent.mmdskin.sync.ServerAuthManager;
import com.tendoarisu.mmdskin.sync.util.CryptoUtils;
import dev.architectury.platform.Platform;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public final class ResourceTransferServerPacketHandler {
    private ResourceTransferServerPacketHandler() {
    }

    public static void handleServerboundPacket(ServerPlayer player, ResourceTransferPacket payload) {
        if (player == null || payload == null) {
            return;
        }
        if (MMDSyncServerGuards.isPureSingleplayerPlayer(player)) {
            return;
        }

        ResourcePacketCodec.ResourcePacket packet;
        try {
            packet = payload.toResourcePacket();
        } catch (RuntimeException e) {
            MMDSyncMod.LOGGER.warn("拒绝无法转换的资源传输包(player={}): {}",
                    player.getUUID(), e.toString());
            return;
        }
        Path modelRoot = Platform.getGameFolder().resolve("3d-skin");
        String stableServerId = ServerAuthManager.getStableServerId(player);
        try {
            switch (packet.opCode()) {
                case ResourceTransferOpCode.MANIFEST ->
                        sendManifest(player, packet.transferId(), modelRoot, stableServerId);
                case ResourceTransferOpCode.REQUEST_CHUNK ->
                        sendChunks(player, packet, modelRoot, stableServerId);
                case ResourceTransferOpCode.UPLOAD_BEGIN -> {
                    ResourceTransferServerManager.beginUpload(
                            packet, player.getUUID(), stableServerId, modelRoot);
                    boolean ackV1 = ResourceTransferServerManager.usesAckV1(
                            packet.transferId(), player.getUUID());
                    sendAck(player, packet.transferId(), stableServerId,
                            ackV1 ? "upload_begin_ok;ack-v1" : "upload_begin_ok");
                }
                case ResourceTransferOpCode.UPLOAD_CHUNK -> {
                    int nextIndex = ResourceTransferServerManager.appendUploadChunk(
                            packet, player.getUUID());
                    if (ResourceTransferServerManager.usesAckV1(packet.transferId(), player.getUUID())) {
                        sendAck(player, packet.transferId(), stableServerId,
                                "upload_chunk_ok:" + (nextIndex - 1));
                    } else if (nextIndex >= packet.chunkCount()) {
                        sendAck(player, packet.transferId(), stableServerId,
                                "upload_chunks_received");
                    }
                }
                case ResourceTransferOpCode.UPLOAD_FINISH -> {
                    Path committed = ResourceTransferServerManager.commitUpload(
                            packet, player.getUUID(), modelRoot);
                    MMDSyncMod.LOGGER.info(
                            "资源上传提交完成(player={}, transferId={}, target={})",
                            player.getUUID(), packet.transferId(), committed);
                    sendAck(player, packet.transferId(), stableServerId, "upload_finish_ok");
                }
                case ResourceTransferOpCode.ABORT ->
                        ResourceTransferServerManager.abortUpload(packet.transferId(), player.getUUID());
                default -> {
                    ResourceTransferServerManager.abortUpload(packet.transferId(), player.getUUID());
                    sendAbort(player, packet.transferId(), stableServerId, "unsupported_opcode");
                }
            }
        } catch (Exception e) {
            ResourceTransferServerManager.abortUpload(packet.transferId(), player.getUUID());
            MMDSyncMod.LOGGER.warn(
                    "资源传输包处理失败(player={}, transferId={}, opcode={}): {}",
                    player.getUUID(), packet.transferId(), packet.opCode(), e.toString());
            sendAbort(player, packet.transferId(), stableServerId,
                    "invalid_packet:" + e.getClass().getSimpleName());
        }
    }

    private static void sendManifest(ServerPlayer player, String transferId,
                                     Path modelRoot, String serverId) {
        List<ResourcePacketCodec.ManifestEntry> entries =
                ResourceTransferServerManager.buildManifestForModelRoot(modelRoot);
        sendPacket(player, new ResourcePacketCodec.ResourcePacket(
                ResourceTransferOpCode.MANIFEST, transferId, serverId,
                "", "", "", 0, 0, 0L, "", new byte[0], entries, "manifest"));
    }

    private static void sendChunks(ServerPlayer player, ResourcePacketCodec.ResourcePacket packet,
                                   Path modelRoot, String serverId) throws IOException {
        Path file = ResourceTransferServerManager.resolveServerResourceFile(
                modelRoot, packet.zone(), packet.folderName(), packet.relativePath());
        if (file == null || !java.nio.file.Files.isRegularFile(
                file, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            sendAbort(player, packet.transferId(), serverId, "not_found");
            return;
        }
        if (CryptoUtils.isEncrypted(file.toFile())) {
            sendAbort(player, packet.transferId(), serverId, "encrypted_source_blocked");
            return;
        }

        for (ResourcePacketCodec.ResourcePacket chunkPacket :
                ResourceTransferServerManager.chunkFile(
                        packet.transferId(), serverId, packet.zone(), packet.folderName(),
                        packet.relativePath(), file)) {
            sendPacket(player, chunkPacket);
        }
    }

    private static void sendAck(ServerPlayer player, String transferId,
                                String serverId, String message) {
        sendPacket(player, new ResourcePacketCodec.ResourcePacket(
                ResourceTransferOpCode.ACK, transferId, serverId,
                "", "", "", 0, 0, 0L, "", new byte[0], List.of(), message));
    }

    private static void sendAbort(ServerPlayer player, String transferId,
                                  String serverId, String message) {
        sendPacket(player, new ResourcePacketCodec.ResourcePacket(
                ResourceTransferOpCode.ABORT, transferId, serverId,
                "", "", "", 0, 0, 0L, "", new byte[0], List.of(), message));
    }

    private static void sendPacket(ServerPlayer player, ResourcePacketCodec.ResourcePacket packet) {
        try {
            MMDSyncNetworking.sendResourceTransferPacket(
                    player, ResourceTransferPacket.fromResourcePacket(packet));
        } catch (RuntimeException e) {
            MMDSyncMod.LOGGER.warn(
                    "发送资源传输响应失败(player={}, transferId={}, opcode={}): {}",
                    player.getUUID(), packet.transferId(), packet.opCode(), e.toString());
        }
    }
}
