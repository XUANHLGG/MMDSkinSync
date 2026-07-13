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
    private static final Map<String, UploadState> UPLOAD_STATES = new ConcurrentHashMap<>();

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

        if (packet.opCode() == ResourceTransferOpCode.ACK) {
            UPLOAD_STATES.computeIfAbsent(transferId, ignored -> new UploadState())
                    .acceptAck(packet.message());
            return;
        }
        if (packet.opCode() == ResourceTransferOpCode.ABORT) {
            UPLOAD_STATES.computeIfAbsent(transferId, ignored -> new UploadState())
                    .acceptAbort(packet.message());
            SESSIONS.remove(transferId);
            return;
        }

        if (packet.opCode() != ResourceTransferOpCode.MANIFEST
                && packet.opCode() != ResourceTransferOpCode.CHUNK) {
            return;
        }
        TransferSession session = SESSIONS.computeIfAbsent(transferId, id -> new TransferSession(id, packet.serverId()));
        switch (packet.opCode()) {
            case ResourceTransferOpCode.MANIFEST -> session.acceptManifest(packet);
            case ResourceTransferOpCode.CHUNK -> session.acceptChunk(packet);
            default -> {
            }
        }
    }

    public static void prepareUpload(String transferId) {
        if (transferId == null || transferId.isBlank()) {
            return;
        }
        UPLOAD_STATES.put(transferId, new UploadState());
    }

    public static boolean hasUploadAck(String transferId, String expectedMessage) {
        UploadState state = UPLOAD_STATES.get(transferId);
        return state != null && state.hasAck(expectedMessage);
    }

    public static boolean hasUploadResponse(String transferId) {
        UploadState state = UPLOAD_STATES.get(transferId);
        return state != null && state.hasResponse();
    }

    public static boolean isUploadAborted(String transferId) {
        UploadState state = UPLOAD_STATES.get(transferId);
        return state != null && state.isAborted();
    }

    public static String uploadAbortMessage(String transferId) {
        UploadState state = UPLOAD_STATES.get(transferId);
        return state == null ? "" : state.abortMessage();
    }

    public static void clearUploadState(String transferId) {
        if (transferId != null) {
            UPLOAD_STATES.remove(transferId);
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
        UPLOAD_STATES.remove(transferId);
    }

    public static void clearAllSessions() {
        SESSIONS.clear();
        UPLOAD_STATES.clear();
    }

    private static final class UploadState {
        private volatile String ackMessage = "";
        private volatile String abortMessage = "";

        void acceptAck(String message) {
            ackMessage = message == null ? "" : message;
        }

        void acceptAbort(String message) {
            abortMessage = message == null || message.isBlank() ? "server_abort" : message;
        }

        boolean hasAck(String expectedMessage) {
            return expectedMessage != null && expectedMessage.equals(ackMessage);
        }

        boolean hasResponse() {
            return !ackMessage.isBlank() || !abortMessage.isBlank();
        }

        boolean isAborted() {
            return !abortMessage.isBlank();
        }

        String abortMessage() {
            return abortMessage;
        }
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
