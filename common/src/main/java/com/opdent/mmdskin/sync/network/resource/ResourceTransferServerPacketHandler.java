package com.opdent.mmdskin.sync.network.resource;

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

        ResourcePacketCodec.ResourcePacket packet = payload.toResourcePacket();
        Path modelRoot = Platform.getGameFolder().resolve("3d-skin");
        String stableServerId = ServerAuthManager.getStableServerId(player);
        try {
            switch (packet.opCode()) {
                case ResourceTransferOpCode.MANIFEST -> sendManifest(player, packet.transferId(), modelRoot, stableServerId);
                case ResourceTransferOpCode.REQUEST_CHUNK -> sendChunks(player, packet, modelRoot, stableServerId);
                case ResourceTransferOpCode.UPLOAD_BEGIN -> {
                    Path target = ResourceTransferServerManager.resolveServerResourceFile(modelRoot, packet.zone(), packet.folderName(), packet.relativePath());
                    if (target == null) {
                        sendAbort(player, packet.transferId(), stableServerId, "invalid_target");
                        break;
                    }
                    if (!ResourceTransferServerManager.beginUpload(packet.transferId(), player.getUUID(), stableServerId, packet.zone(), packet.folderName(), packet.relativePath())) {
                        sendAbort(player, packet.transferId(), stableServerId, "invalid_transfer_id");
                        break;
                    }
                    sendAck(player, packet.transferId(), stableServerId, "upload_begin_ok");
                }
                case ResourceTransferOpCode.UPLOAD_CHUNK -> {
                    if (!ResourceTransferServerManager.appendUploadChunk(packet.transferId(), player.getUUID(), packet.payload())) {
                        sendAbort(player, packet.transferId(), stableServerId, "upload_session_missing");
                        break;
                    }
                    if (packet.chunkIndex() + 1 >= packet.chunkCount()) {
                        sendAck(player, packet.transferId(), stableServerId, "upload_chunks_received");
                    }
                }
                case ResourceTransferOpCode.UPLOAD_FINISH -> {
                    Path committed = ResourceTransferServerManager.commitUpload(packet.transferId(), player.getUUID(), modelRoot);
                    if (committed == null) {
                        sendAbort(player, packet.transferId(), stableServerId, "upload_session_missing");
                        break;
                    }
                    sendAck(player, packet.transferId(), stableServerId, "upload_finish_ok");
                }
                case ResourceTransferOpCode.ABORT -> ResourceTransferServerManager.abortUpload(packet.transferId());
                default -> sendAck(player, packet.transferId(), stableServerId, "ignored");
            }
        } catch (Exception e) {
            sendAbort(player, packet.transferId(), stableServerId, "server_error:" + e.getClass().getSimpleName());
        }
    }

    private static void sendManifest(ServerPlayer player, String transferId, Path modelRoot, String serverId) {
        List<ResourcePacketCodec.ManifestEntry> entries = ResourceTransferServerManager.buildManifestForModelRoot(modelRoot);
        sendPacket(player, new ResourcePacketCodec.ResourcePacket(
                ResourceTransferOpCode.MANIFEST,
                transferId,
                serverId,
                "",
                "",
                "",
                0,
                0,
                0L,
                "",
                new byte[0],
                entries,
                "manifest"
        ));
    }

    private static void sendChunks(ServerPlayer player, ResourcePacketCodec.ResourcePacket packet, Path modelRoot, String serverId) throws IOException {
        Path file = ResourceTransferServerManager.resolveServerResourceFile(modelRoot, packet.zone(), packet.folderName(), packet.relativePath());
        if (file == null || !java.nio.file.Files.isRegularFile(file)) {
            sendAbort(player, packet.transferId(), serverId, "not_found");
            return;
        }
        if (CryptoUtils.isEncrypted(file.toFile())) {
            sendAbort(player, packet.transferId(), serverId, "encrypted_source_blocked");
            return;
        }

        for (ResourcePacketCodec.ResourcePacket chunkPacket : ResourceTransferServerManager.chunkFile(
                packet.transferId(),
                serverId,
                packet.zone(),
                packet.folderName(),
                packet.relativePath(),
                file
        )) {
            sendPacket(player, chunkPacket);
        }
    }

    private static void sendAck(ServerPlayer player, String transferId, String serverId, String message) {
        sendPacket(player, new ResourcePacketCodec.ResourcePacket(
                ResourceTransferOpCode.ACK,
                transferId,
                serverId,
                "",
                "",
                "",
                0,
                0,
                0L,
                "",
                new byte[0],
                List.of(),
                message
        ));
    }

    private static void sendAbort(ServerPlayer player, String transferId, String serverId, String message) {
        sendPacket(player, new ResourcePacketCodec.ResourcePacket(
                ResourceTransferOpCode.ABORT,
                transferId,
                serverId,
                "",
                "",
                "",
                0,
                0,
                0L,
                "",
                new byte[0],
                List.of(),
                message
        ));
    }

    private static void sendPacket(ServerPlayer player, ResourcePacketCodec.ResourcePacket packet) {
        MMDSyncNetworking.sendResourceTransferPacket(player, ResourceTransferPacket.fromResourcePacket(packet));
    }
}
