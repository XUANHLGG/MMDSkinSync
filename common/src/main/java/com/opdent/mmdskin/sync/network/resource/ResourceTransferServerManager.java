package com.opdent.mmdskin.sync.network.resource;

import com.opdent.mmdskin.sync.ServerAuthManager;
import com.opdent.mmdskin.sync.MMDSyncMod;
import com.tendoarisu.mmdskin.sync.util.CryptoUtils;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import dev.architectury.platform.Platform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ResourceTransferServerManager {
    public static final int DEFAULT_CHUNK_SIZE = 24 * 1024;
    private static final Map<String, UploadSession> UPLOAD_SESSIONS = new ConcurrentHashMap<>();
    private static final byte[] MMDARC_HEADER = "MMDARC".getBytes(StandardCharsets.UTF_8);
    private static final byte MMDARC_VERSION = 0x01;

    private ResourceTransferServerManager() {
    }

    public static List<ResourcePacketCodec.ManifestEntry> buildManifest(Path rootDir) {
        List<ResourcePacketCodec.ManifestEntry> entries = new ArrayList<>();
        if (rootDir == null || !Files.isDirectory(rootDir)) {
            return entries;
        }

        try (var stream = Files.walk(rootDir)) {
            stream.filter(Files::isRegularFile)
                    .sorted(Comparator.naturalOrder())
                    .forEach(path -> {
                        try {
                            String relative = rootDir.relativize(path).toString().replace('\\', '/');
                            String[] parts = relative.split("/", 3);
                            if (parts.length < 3) {
                                return;
                            }
                            entries.add(new ResourcePacketCodec.ManifestEntry(
                                    parts[0],
                                    parts[1],
                                    parts[2],
                                    Files.size(path),
                                    md5(path)
                            ));
                        } catch (Exception e) {
                            MMDSyncMod.LOGGER.warn("构建资源清单时跳过文件(path={}): {}", path, e.toString());
                        }
                    });
        } catch (IOException e) {
            MMDSyncMod.LOGGER.warn("扫描资源清单失败(root={}): {}", rootDir, e.toString());
        }
        return entries;
    }

    public static List<ResourcePacketCodec.ManifestEntry> buildManifestForModelRoot(Path modelRoot) {
        List<ResourcePacketCodec.ManifestEntry> entries = new ArrayList<>();
        appendZoneEntries(entries, modelRoot, "pmx");
        appendZoneEntries(entries, modelRoot, "vmd");
        return entries;
    }

    public static Path resolveServerResourceFile(Path modelRoot, String zone, String folderName, String relativePath) {
        Path zoneRoot = resolveZoneRoot(modelRoot, zone);
        if (zoneRoot == null) {
            return null;
        }

        String safeFolder = folderName == null ? "" : folderName.strip();
        String safeRelativePath = sanitizeRelativePath(relativePath);
        if (safeFolder.isEmpty() || safeRelativePath.isEmpty()) {
            return null;
        }

        Path resolved = zoneRoot.resolve(safeFolder).resolve(safeRelativePath).normalize();
        if (!resolved.startsWith(zoneRoot.normalize())) {
            return null;
        }
        return resolved;
    }

    public static Path commitUpload(String transferId, Path modelRoot) throws IOException {
        UploadSession session = UPLOAD_SESSIONS.remove(transferId);
        if (session == null) {
            return null;
        }

        Path target = resolveServerResourceFile(modelRoot, session.zone(), session.folderName(), session.relativePath());
        if (target == null) {
            return null;
        }

        Files.createDirectories(target.getParent());
        Files.move(session.tempFile(), target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    public static Path commitUpload(String transferId, UUID playerUuid, Path modelRoot) throws IOException {
        UploadSession session = UPLOAD_SESSIONS.get(transferId);
        if (session == null || playerUuid == null || !playerUuid.equals(session.playerUuid())) {
            return null;
        }
        return commitUpload(transferId, modelRoot);
    }

    public static List<ResourcePacketCodec.ResourcePacket> chunkFile(
            String transferId,
            String serverId,
            String zone,
            String folderName,
            String relativePath,
            Path file
    ) throws IOException {
        byte[] rawData = Files.readAllBytes(file);
        byte[] all = prepareTransferPayload(rawData, relativePath);
        int chunkCount = Math.max(1, (all.length + DEFAULT_CHUNK_SIZE - 1) / DEFAULT_CHUNK_SIZE);
        List<ResourcePacketCodec.ResourcePacket> packets = new ArrayList<>(chunkCount);
        String digest;
        try {
            digest = md5(rawData);
        } catch (Exception e) {
            throw new IOException("计算资源文件摘要失败: " + file, e);
        }

        for (int i = 0; i < chunkCount; i++) {
            int start = i * DEFAULT_CHUNK_SIZE;
            int end = Math.min(all.length, start + DEFAULT_CHUNK_SIZE);
            byte[] payload = java.util.Arrays.copyOfRange(all, start, end);
            packets.add(new ResourcePacketCodec.ResourcePacket(
                    ResourceTransferOpCode.CHUNK,
                    transferId,
                    serverId,
                    zone,
                    folderName,
                    relativePath,
                    i,
                    chunkCount,
                    rawData.length,
                    digest,
                    payload,
                    List.of(),
                    ""
            ));
        }
        return packets;
    }

    public static boolean beginUpload(String transferId, UUID playerUuid, String serverId, String zone, String folderName, String relativePath) {
        if (transferId == null || transferId.isBlank() || playerUuid == null) {
            return false;
        }
        UPLOAD_SESSIONS.put(transferId, new UploadSession(playerUuid, serverId, zone, folderName, relativePath));
        return true;
    }

    public static boolean appendUploadChunk(String transferId, UUID playerUuid, byte[] payload) throws IOException {
        UploadSession session = UPLOAD_SESSIONS.get(transferId);
        if (session == null || playerUuid == null || !playerUuid.equals(session.playerUuid())) {
            return false;
        }
        Files.createDirectories(session.tempFile.getParent());
        Files.write(session.tempFile, payload, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        return true;
    }

    public static Path finishUpload(String transferId) {
        UploadSession session = UPLOAD_SESSIONS.remove(transferId);
        return session == null ? null : session.tempFile;
    }

    public static void abortUpload(String transferId) {
        UploadSession session = UPLOAD_SESSIONS.remove(transferId);
        if (session == null) {
            return;
        }
        try {
            Files.deleteIfExists(session.tempFile());
        } catch (IOException ignored) {
        }
    }

    private static void appendZoneEntries(List<ResourcePacketCodec.ManifestEntry> entries, Path modelRoot, String zone) {
        Path zoneRoot = resolveZoneRoot(modelRoot, zone);
        if (zoneRoot == null || !Files.isDirectory(zoneRoot)) {
            return;
        }

        try (var stream = Files.walk(zoneRoot)) {
            stream.filter(Files::isRegularFile)
                    .sorted(Comparator.naturalOrder())
                    .forEach(path -> {
                        try {
                            if (CryptoUtils.isEncrypted(path.toFile())) {
                                return;
                            }
                            String relative = zoneRoot.relativize(path).toString().replace('\\', '/');
                            String[] parts = relative.split("/", 2);
                            if (parts.length < 2) {
                                return;
                            }
                            entries.add(new ResourcePacketCodec.ManifestEntry(
                                    zone,
                                    parts[0],
                                    parts[1],
                                    Files.size(path),
                                    md5(path)
                            ));
                        } catch (Exception e) {
                            MMDSyncMod.LOGGER.warn("构建模型资源清单时跳过文件(path={}): {}", path, e.toString());
                        }
                    });
        } catch (IOException e) {
            MMDSyncMod.LOGGER.warn("扫描模型资源目录失败(root={}, zone={}): {}", modelRoot, zone, e.toString());
        }
    }

    private static Path resolveZoneRoot(Path modelRoot, String zone) {
        if (modelRoot == null || zone == null) {
            return null;
        }
        return switch (zone) {
            case "pmx" -> modelRoot.resolve("EntityPlayer");
            case "vmd" -> modelRoot.resolve("StageAnim");
            default -> null;
        };
    }

    private static String sanitizeRelativePath(String relativePath) {
        if (relativePath == null) {
            return "";
        }
        return relativePath.replace('\\', '/').replace("..", "").replaceFirst("^/+", "");
    }

    private static byte[] prepareTransferPayload(byte[] data, String relativePath) {
        String lowerName = relativePath == null ? "" : relativePath.toLowerCase(Locale.ROOT);
        byte[] serverSyncKey = ServerAuthManager.getServerSyncKey();
        if (serverSyncKey == null || serverSyncKey.length != 32 || !shouldEncryptTransferredFile(lowerName) || isEncryptedArchive(data)) {
            return data;
        }

        byte[] encrypted = MMDSyncNativeBridge.aesEncrypt(data, serverSyncKey);
        if (encrypted == null) {
            return data;
        }

        byte[] result = new byte[MMDARC_HEADER.length + 1 + encrypted.length];
        System.arraycopy(MMDARC_HEADER, 0, result, 0, MMDARC_HEADER.length);
        result[MMDARC_HEADER.length] = MMDARC_VERSION;
        System.arraycopy(encrypted, 0, result, MMDARC_HEADER.length + 1, encrypted.length);
        return result;
    }

    private static boolean shouldEncryptTransferredFile(String lowerName) {
        return lowerName.endsWith(".pmx") || lowerName.endsWith(".pmd") || lowerName.endsWith(".vrm")
                || lowerName.endsWith(".vmd") || lowerName.endsWith(".fbx")
                || lowerName.endsWith(".png") || lowerName.endsWith(".jpg")
                || lowerName.endsWith(".jpeg") || lowerName.endsWith(".tga");
    }

    private static boolean isEncryptedArchive(byte[] data) {
        return data.length >= MMDARC_HEADER.length + 1
                && data[0] == 'M' && data[1] == 'M' && data[2] == 'D'
                && data[3] == 'A' && data[4] == 'R' && data[5] == 'C';
    }

    private static String md5(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        digest.update(Files.readAllBytes(path));
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String md5(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        digest.update(data);
        return HexFormat.of().formatHex(digest.digest());
    }

    private record UploadSession(UUID playerUuid, String serverId, String zone, String folderName, String relativePath, Path tempFile) {
        private UploadSession(UUID playerUuid, String serverId, String zone, String folderName, String relativePath) {
            this(
                    playerUuid,
                    serverId,
                    zone,
                    folderName,
                    relativePath,
                    Platform.getGameFolder()
                            .resolve("mmdsync-upload-staging")
                            .resolve(serverId == null || serverId.isBlank() ? "unknown" : serverId)
                            .resolve(zone == null ? "" : zone)
                            .resolve(folderName == null ? "" : folderName)
                            .resolve(relativePath == null || relativePath.isBlank() ? "payload.bin" : relativePath)
            );
        }
    }
}
