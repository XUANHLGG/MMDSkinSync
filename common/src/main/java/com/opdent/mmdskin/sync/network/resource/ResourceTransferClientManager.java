package com.opdent.mmdskin.sync.network.resource;

import com.opdent.mmdskin.sync.MMDSyncMod;
import com.opdent.mmdskin.sync.ui.SyncProgressTracker;
import dev.architectury.platform.Platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ResourceTransferClientManager {
    private static final Map<String, TransferSession> SESSIONS = new ConcurrentHashMap<>();

    private ResourceTransferClientManager() {
    }

    public static void acceptPacket(ResourcePacketCodec.ResourcePacket packet) {
        if (packet == null) {
            return;
        }

        String transferId = packet.transferId();
        if (transferId == null || transferId.isBlank()) {
            return;
        }

        TransferSession session = SESSIONS.computeIfAbsent(transferId, id -> new TransferSession(id, packet.serverId()));
        switch (packet.opCode()) {
            case ResourceTransferOpCode.MANIFEST -> session.acceptManifest(packet);
            case ResourceTransferOpCode.CHUNK -> session.acceptChunk(packet);
            case ResourceTransferOpCode.ABORT -> SESSIONS.remove(transferId);
            default -> {
            }
        }
    }

    public static Map<String, Path> snapshotCompletedFiles(String transferId) {
        TransferSession session = SESSIONS.get(transferId);
        if (session == null) {
            return Map.of();
        }
        return session.snapshotCompletedFiles();
    }

    public static List<ResourcePacketCodec.ManifestEntry> snapshotManifestEntries(String transferId) {
        TransferSession session = SESSIONS.get(transferId);
        if (session == null) {
            return List.of();
        }
        return session.snapshotManifestEntries();
    }

    public static boolean hasManifest(String transferId) {
        TransferSession session = SESSIONS.get(transferId);
        return session != null && session.hasManifest();
    }

    public static void clearSession(String transferId) {
        if (transferId == null || transferId.isBlank()) {
            return;
        }
        SESSIONS.remove(transferId);
    }

    static final class TransferSession {
        private final String transferId;
        private final String serverId;
        private final Map<String, ResourcePacketCodec.ManifestEntry> manifestEntries = new HashMap<>();
        private final Map<String, Path> completedFiles = new HashMap<>();
        private boolean manifestReceived;

        TransferSession(String transferId, String serverId) {
            this.transferId = transferId;
            this.serverId = serverId == null ? "" : serverId;
        }

        void acceptManifest(ResourcePacketCodec.ResourcePacket packet) {
            manifestEntries.clear();
            manifestReceived = true;
            if (packet.manifestEntries() == null) {
                SyncProgressTracker.onManifest(transferId, 0);
                return;
            }
            for (ResourcePacketCodec.ManifestEntry entry : packet.manifestEntries()) {
                manifestEntries.put(buildKey(entry.zone(), entry.folderName(), entry.relativePath()), entry);
            }
            SyncProgressTracker.onManifest(transferId, manifestEntries.size());
        }

        void acceptChunk(ResourcePacketCodec.ResourcePacket packet) {
            try {
                String key = buildKey(packet.zone(), packet.folderName(), packet.relativePath());
                Path chunkFile = getCiphertextCacheRoot()
                        .resolve(serverId.isBlank() ? "unknown" : serverId)
                        .resolve(packet.zone())
                        .resolve(packet.folderName())
                        .resolve(packet.relativePath());

                Files.createDirectories(chunkFile.getParent());
                if (packet.chunkIndex() == 0) {
                    Files.deleteIfExists(chunkFile);
                }
                Files.write(chunkFile, packet.payload(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);

                if (packet.chunkIndex() + 1 >= packet.chunkCount()) {
                    completedFiles.put(key, chunkFile);
                    SyncProgressTracker.onFileCompleted(transferId);
                }
            } catch (IOException e) {
                MMDSyncMod.LOGGER.warn("资源分块写入失败(transferId={}, file={}): {}",
                        transferId,
                        buildKey(packet.zone(), packet.folderName(), packet.relativePath()),
                        e.toString());
            }
        }

        Map<String, Path> snapshotCompletedFiles() {
            return new HashMap<>(completedFiles);
        }

        List<ResourcePacketCodec.ManifestEntry> snapshotManifestEntries() {
            return new ArrayList<>(manifestEntries.values());
        }

        boolean hasManifest() {
            return manifestReceived;
        }

        private static String buildKey(String zone, String folderName, String relativePath) {
            return (zone == null ? "" : zone) + "/" + (folderName == null ? "" : folderName) + "/" + (relativePath == null ? "" : relativePath);
        }

        private static Path getCiphertextCacheRoot() {
            return Platform.getGameFolder().resolve("mmdsync-transfer-cache");
        }
    }
}
