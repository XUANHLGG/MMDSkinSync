package com.opdent.mmdskin.sync.network.resource;

import dev.architectury.networking.NetworkManager;
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

        ResourcePacketCodec.ResourcePacket packet = payload.toResourcePacket();
        Path modelRoot = Platform.getGameFolder().resolve("3d-skin");
        try {
            switch (packet.opCode()) {
                case ResourceTransferOpCode.MANIFEST -> sendManifest(player, packet.transferId(), modelRoot);
                case ResourceTransferOpCode.REQUEST_CHUNK -> sendChunks(player, packet, modelRoot);
                case ResourceTransferOpCode.UPLOAD_BEGIN -> {
                    ResourceTransferServerManager.beginUpload(packet.transferId(), packet.serverId(), packet.zone(), packet.folderName(), packet.relativePath());
                    sendAck(player, packet.transferId(), "upload_begin_ok");
                }
                case ResourceTransferOpCode.UPLOAD_CHUNK -> {
                    ResourceTransferServerManager.appendUploadChunk(packet.transferId(), packet.payload());
                    if (packet.chunkIndex() + 1 >= packet.chunkCount()) {
                        sendAck(player, packet.transferId(), "upload_chunks_received");
                    }
                }
                case ResourceTransferOpCode.UPLOAD_FINISH -> {
                    ResourceTransferServerManager.commitUpload(packet.transferId(), modelRoot);
                    sendAck(player, packet.transferId(), "upload_finish_ok");
                }
                case ResourceTransferOpCode.ABORT -> ResourceTransferServerManager.abortUpload(packet.transferId());
                default -> sendAck(player, packet.transferId(), "ignored");
            }
        } catch (Exception e) {
            sendAbort(player, packet.transferId(), "server_error:" + e.getClass().getSimpleName());
        }
    }

    private static void sendManifest(ServerPlayer player, String transferId, Path modelRoot) {
        List<ResourcePacketCodec.ManifestEntry> entries = ResourceTransferServerManager.buildManifestForModelRoot(modelRoot);
        sendPacket(player, new ResourcePacketCodec.ResourcePacket(
                ResourceTransferOpCode.MANIFEST,
                transferId,
                "",
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

    private static void sendChunks(ServerPlayer player, ResourcePacketCodec.ResourcePacket packet, Path modelRoot) throws IOException {
        Path file = ResourceTransferServerManager.resolveServerResourceFile(modelRoot, packet.zone(), packet.folderName(), packet.relativePath());
        if (file == null || !java.nio.file.Files.isRegularFile(file)) {
            sendAbort(player, packet.transferId(), "not_found");
            return;
        }

        for (ResourcePacketCodec.ResourcePacket chunkPacket : ResourceTransferServerManager.chunkFile(
                packet.transferId(),
                packet.serverId(),
                packet.zone(),
                packet.folderName(),
                packet.relativePath(),
                file
        )) {
            sendPacket(player, chunkPacket);
        }
    }

    private static void sendAck(ServerPlayer player, String transferId, String message) {
        sendPacket(player, new ResourcePacketCodec.ResourcePacket(
                ResourceTransferOpCode.ACK,
                transferId,
                "",
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

    private static void sendAbort(ServerPlayer player, String transferId, String message) {
        sendPacket(player, new ResourcePacketCodec.ResourcePacket(
                ResourceTransferOpCode.ABORT,
                transferId,
                "",
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
        NetworkManager.sendToPlayer(player, ResourceTransferPacket.fromResourcePacket(packet));
    }
}
