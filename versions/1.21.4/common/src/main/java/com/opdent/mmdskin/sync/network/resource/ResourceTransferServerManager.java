package com.opdent.mmdskin.sync.network.resource;

import com.opdent.mmdskin.sync.MMDSyncMod;
import com.opdent.mmdskin.sync.ServerAuthManager;
import com.tendoarisu.mmdskin.sync.util.CryptoUtils;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import dev.architectury.platform.Platform;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ResourceTransferServerManager {
    public static final int DEFAULT_CHUNK_SIZE = 24 * 1024;
    public static final long MAX_UPLOAD_BYTES = 512L * 1024L * 1024L;
    public static final int MAX_UPLOAD_CHUNKS = 65_536;
    private static final int MAX_TRANSFER_ID_CHARS = 128;
    private static final int MAX_RELATIVE_PATH_UTF8 = 2_048;
    private static final Map<String, UploadSession> UPLOAD_SESSIONS = new ConcurrentHashMap<>();
    private static final byte[] MMDARC_HEADER = "MMDARC".getBytes(StandardCharsets.UTF_8);
    private static final byte MMDARC_VERSION = 0x01;

    private ResourceTransferServerManager() {
    }

    public static List<ResourcePacketCodec.ManifestEntry> buildManifest(Path rootDir) {
        List<ResourcePacketCodec.ManifestEntry> entries = new ArrayList<>();
        if (rootDir == null || !Files.isDirectory(rootDir, LinkOption.NOFOLLOW_LINKS)) {
            return entries;
        }

        try (var stream = Files.walk(rootDir)) {
            stream.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .sorted(Comparator.naturalOrder())
                    .forEach(path -> {
                        try {
                            String relative = rootDir.relativize(path).toString().replace('\\', '/');
                            String[] parts = relative.split("/", 3);
                            if (parts.length < 3) {
                                return;
                            }
                            entries.add(new ResourcePacketCodec.ManifestEntry(
                                    parts[0], parts[1], parts[2], Files.size(path), md5(path)));
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

    public static Path resolveServerResourceFile(Path modelRoot, String zone, String folderName,
                                                 String relativePath) {
        try {
            return resolveServerResourceFileStrict(modelRoot, zone, folderName, relativePath);
        } catch (IOException ignored) {
            return null;
        }
    }

    public static void beginUpload(ResourcePacketCodec.ResourcePacket packet, UUID playerUuid,
                                   String stableServerId, Path modelRoot) throws IOException {
        Objects.requireNonNull(packet, "packet");
        Objects.requireNonNull(playerUuid, "playerUuid");
        validateTransferId(packet.transferId());
        if (stableServerId == null || stableServerId.isBlank()
                || !stableServerId.equals(packet.serverId())) {
            throw new IOException("upload server binding mismatch");
        }
        resolveServerResourceFileStrict(modelRoot, packet.zone(), packet.folderName(), packet.relativePath());

        Path stagingRoot = Platform.getGameFolder().resolve("mmdsync-upload-staging")
                .toAbsolutePath().normalize();
        Files.createDirectories(stagingRoot);
        rejectSymbolicLink(stagingRoot);
        Path tempFile = Files.createTempFile(stagingRoot, "upload-", ".part");
        UploadSession session;
        try {
            session = new UploadSession(playerUuid, packet, tempFile);
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(tempFile);
            throw e;
        }
        UploadSession raced = UPLOAD_SESSIONS.putIfAbsent(packet.transferId(), session);
        if (raced != null) {
            session.cleanup();
            throw new IOException("upload session already exists");
        }
    }

    public static int appendUploadChunk(ResourcePacketCodec.ResourcePacket packet, UUID playerUuid)
            throws IOException {
        UploadSession session = requireSession(packet.transferId(), playerUuid);
        try {
            session.append(playerUuid, packet);
            return session.nextChunkIndex();
        } catch (IOException | RuntimeException e) {
            if (UPLOAD_SESSIONS.remove(packet.transferId(), session)) {
                session.cleanup();
            }
            throw e;
        }
    }

    public static Path commitUpload(ResourcePacketCodec.ResourcePacket packet, UUID playerUuid,
                                    Path modelRoot) throws IOException {
        UploadSession session = requireSession(packet.transferId(), playerUuid);
        try {
            Path target = resolveServerResourceFileStrict(
                    modelRoot, session.zone, session.folderName, session.relativePath);
            session.finish(playerUuid, packet, resolveZoneRoot(modelRoot, session.zone), target);
            UPLOAD_SESSIONS.remove(packet.transferId(), session);
            return target;
        } catch (IOException | RuntimeException e) {
            if (UPLOAD_SESSIONS.remove(packet.transferId(), session)) {
                session.cleanup();
            }
            throw e;
        }
    }

    public static void abortUpload(String transferId, UUID playerUuid) {
        UploadSession session = UPLOAD_SESSIONS.get(transferId);
        if (session == null || playerUuid == null || !playerUuid.equals(session.playerUuid)) {
            return;
        }
        if (UPLOAD_SESSIONS.remove(transferId, session)) {
            session.cleanup();
        }
    }

    public static void abortUploadsFor(UUID playerUuid) {
        if (playerUuid == null) {
            return;
        }
        UPLOAD_SESSIONS.entrySet().removeIf(entry -> {
            if (!playerUuid.equals(entry.getValue().playerUuid)) {
                return false;
            }
            entry.getValue().cleanup();
            return true;
        });
    }

    public static boolean usesAckV1(String transferId, UUID playerUuid) {
        UploadSession session = UPLOAD_SESSIONS.get(transferId);
        return session != null && playerUuid != null && playerUuid.equals(session.playerUuid)
                && session.ackV1;
    }

    static int activeUploadCount() {
        return UPLOAD_SESSIONS.size();
    }

    public static List<ResourcePacketCodec.ResourcePacket> chunkFile(
            String transferId, String serverId, String zone, String folderName,
            String relativePath, Path file) throws IOException {
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
                    ResourceTransferOpCode.CHUNK, transferId, serverId, zone, folderName,
                    relativePath, i, chunkCount, rawData.length, digest, payload, List.of(), ""));
        }
        return packets;
    }

    private static UploadSession requireSession(String transferId, UUID playerUuid) throws IOException {
        UploadSession session = UPLOAD_SESSIONS.get(transferId);
        if (session == null) {
            throw new IOException("upload session is missing");
        }
        session.requireOwner(playerUuid);
        return session;
    }

    private static Path resolveServerResourceFileStrict(Path modelRoot, String zone, String folderName,
                                                        String relativePath) throws IOException {
        Path zoneRoot = resolveZoneRoot(modelRoot, zone);
        if (zoneRoot == null) {
            throw new IOException("unsupported upload zone");
        }
        String safeFolder = normalizeFolderName(folderName);
        String safeRelative = normalizeRelativePath(relativePath);
        Path normalizedRoot = zoneRoot.toAbsolutePath().normalize();
        Path target = normalizedRoot.resolve(safeFolder).resolve(safeRelative).normalize();
        if (!target.startsWith(normalizedRoot)) {
            throw new IOException("upload target escapes zone root");
        }
        verifyNoSymbolicLinks(normalizedRoot, target);
        return target;
    }

    private static String normalizeFolderName(String folderName) throws IOException {
        if (folderName == null || folderName.isBlank() || !folderName.equals(folderName.strip())
                || folderName.indexOf('\0') >= 0 || folderName.indexOf('/') >= 0
                || folderName.indexOf('\\') >= 0 || folderName.indexOf(':') >= 0
                || folderName.equals(".") || folderName.equals("..")) {
            throw new IOException("invalid upload folder name");
        }
        return folderName;
    }

    static String normalizeRelativePath(String relativePath) throws IOException {
        if (relativePath == null || relativePath.isBlank() || relativePath.indexOf('\0') >= 0) {
            throw new IOException("invalid upload relative path");
        }
        String candidate = relativePath.replace('\\', '/');
        if (candidate.startsWith("/") || candidate.startsWith("//")
                || candidate.matches("(?i)^[a-z]:.*")
                || candidate.getBytes(StandardCharsets.UTF_8).length > MAX_RELATIVE_PATH_UTF8) {
            throw new IOException("absolute or oversized upload path is not allowed");
        }
        StringBuilder normalized = new StringBuilder();
        for (String segment : candidate.split("/", -1)) {
            if (segment.isBlank() || segment.equals(".") || segment.equals("..")
                    || segment.indexOf(':') >= 0 || segment.indexOf('\0') >= 0) {
                throw new IOException("invalid upload path segment");
            }
            if (!normalized.isEmpty()) {
                normalized.append('/');
            }
            normalized.append(segment);
        }
        return normalized.toString();
    }

    private static void validateTransferId(String transferId) throws IOException {
        if (transferId == null || transferId.isBlank() || !transferId.equals(transferId.strip())
                || transferId.length() > MAX_TRANSFER_ID_CHARS || transferId.indexOf('\0') >= 0
                || !transferId.matches("[A-Za-z0-9._-]+")) {
            throw new IOException("invalid transfer id");
        }
    }

    private static void verifyNoSymbolicLinks(Path zoneRoot, Path target) throws IOException {
        Path current = zoneRoot;
        if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
            rejectSymbolicLink(current);
        }
        Path relative = zoneRoot.relativize(target);
        for (Path component : relative) {
            current = current.resolve(component);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                rejectSymbolicLink(current);
            }
        }
    }

    private static void rejectSymbolicLink(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) {
            throw new IOException("symbolic link is not allowed in upload target: " + path);
        }
    }

    private static void appendZoneEntries(List<ResourcePacketCodec.ManifestEntry> entries,
                                          Path modelRoot, String zone) {
        Path zoneRoot = resolveZoneRoot(modelRoot, zone);
        if (zoneRoot == null || !Files.isDirectory(zoneRoot, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }

        try (var stream = Files.walk(zoneRoot)) {
            stream.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
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
                                    zone, parts[0], parts[1], Files.size(path), md5(path)));
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

    private static byte[] prepareTransferPayload(byte[] data, String relativePath) {
        String lowerName = relativePath == null ? "" : relativePath.toLowerCase(Locale.ROOT);
        byte[] serverSyncKey = ServerAuthManager.getServerSyncKey();
        if (serverSyncKey == null || serverSyncKey.length != 32
                || !shouldEncryptTransferredFile(lowerName) || isEncryptedArchive(data)) {
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
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String md5(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        digest.update(data);
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read > 0) {
                        digest.update(buffer, 0, read);
                    }
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 is unavailable", e);
        }
    }

    private static final class UploadSession {
        private final UUID playerUuid;
        private final String transferId;
        private final String serverId;
        private final String zone;
        private final String folderName;
        private final String relativePath;
        private final Path tempFile;
        private final int expectedChunkCount;
        private final long expectedSize;
        private final String expectedDigest;
        private final MessageDigest digest;
        private final boolean ackV1;
        private int nextChunkIndex;
        private long receivedBytes;

        private UploadSession(UUID playerUuid, ResourcePacketCodec.ResourcePacket begin, Path tempFile)
                throws IOException {
            this.playerUuid = playerUuid;
            this.transferId = begin.transferId();
            this.serverId = requireText(begin.serverId(), "serverId");
            this.zone = requireText(begin.zone(), "zone");
            this.folderName = requireText(begin.folderName(), "folderName");
            this.relativePath = requireText(begin.relativePath(), "relativePath");
            this.tempFile = tempFile.toAbsolutePath().normalize();
            this.expectedSize = begin.totalSize();
            this.expectedChunkCount = begin.chunkCount();
            if (expectedSize < 0 || expectedSize > MAX_UPLOAD_BYTES) {
                throw new IOException("upload size is outside the allowed range");
            }
            long minimumCount = expectedSize == 0 ? 1L
                    : (expectedSize + DEFAULT_CHUNK_SIZE - 1L) / DEFAULT_CHUNK_SIZE;
            long maximumUsefulCount = expectedSize == 0 ? 1L
                    : Math.min(expectedSize, MAX_UPLOAD_CHUNKS);
            if (expectedChunkCount < minimumCount || expectedChunkCount > maximumUsefulCount) {
                throw new IOException("upload chunk count cannot represent declared size");
            }
            if (begin.digest() == null || !begin.digest().matches("(?i)[0-9a-f]{64}")) {
                throw new IOException("upload digest must be SHA-256 hex");
            }
            this.expectedDigest = begin.digest().toLowerCase(Locale.ROOT);
            this.ackV1 = begin.message() != null && begin.message().contains("cap=ack-v1");
            try {
                this.digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new IOException("SHA-256 is unavailable", e);
            }
        }

        private synchronized void append(UUID senderUuid, ResourcePacketCodec.ResourcePacket packet)
                throws IOException {
            requireOwner(senderUuid);
            requireBoundPacket(packet);
            if (packet.chunkIndex() != nextChunkIndex) {
                throw new IOException("upload chunk is out of order");
            }
            if (packet.chunkCount() != expectedChunkCount || packet.totalSize() != expectedSize
                    || !expectedDigest.equalsIgnoreCase(packet.digest())) {
                throw new IOException("upload chunk metadata does not match BEGIN");
            }
            byte[] payload = packet.payload() == null ? new byte[0] : packet.payload();
            long remaining = expectedSize - receivedBytes;
            int chunksAfterThis = expectedChunkCount - nextChunkIndex - 1;
            if (payload.length > DEFAULT_CHUNK_SIZE || (expectedSize > 0 && payload.length == 0)
                    || payload.length > remaining) {
                throw new IOException("upload chunk size is outside allowed range");
            }
            long remainingAfter = remaining - payload.length;
            long maximumRemaining = (long) chunksAfterThis * DEFAULT_CHUNK_SIZE;
            long minimumRemaining = expectedSize == 0 ? 0 : chunksAfterThis;
            if (remainingAfter < minimumRemaining || remainingAfter > maximumRemaining) {
                throw new IOException("upload chunks cannot satisfy BEGIN metadata");
            }
            Files.write(tempFile, payload, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
            digest.update(payload);
            receivedBytes += payload.length;
            nextChunkIndex++;
        }

        private synchronized void finish(UUID senderUuid, ResourcePacketCodec.ResourcePacket packet,
                                         Path zoneRoot, Path target) throws IOException {
            requireOwner(senderUuid);
            requireBoundPacket(packet);
            if (packet.chunkCount() != expectedChunkCount || packet.totalSize() != expectedSize
                    || !expectedDigest.equalsIgnoreCase(packet.digest())
                    || packet.chunkIndex() != expectedChunkCount - 1) {
                throw new IOException("upload FINISH metadata does not match BEGIN");
            }
            if (nextChunkIndex != expectedChunkCount || receivedBytes != expectedSize) {
                throw new IOException("upload is incomplete");
            }
            String actual = HexFormat.of().formatHex(digest.digest());
            if (!MessageDigest.isEqual(actual.getBytes(StandardCharsets.US_ASCII),
                    expectedDigest.getBytes(StandardCharsets.US_ASCII))
                    || Files.size(tempFile) != expectedSize
                    || !expectedDigest.equals(sha256(tempFile))) {
                throw new IOException("upload SHA-256 integrity mismatch");
            }
            verifyNoSymbolicLinks(zoneRoot.toAbsolutePath().normalize(), target);
            Files.createDirectories(target.getParent());
            verifyNoSymbolicLinks(zoneRoot.toAbsolutePath().normalize(), target);
            try {
                Files.move(tempFile, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
            }
            verifyNoSymbolicLinks(zoneRoot.toAbsolutePath().normalize(), target);
        }

        private void requireOwner(UUID senderUuid) throws IOException {
            if (!playerUuid.equals(senderUuid)) {
                throw new IOException("upload owner mismatch");
            }
        }

        private void requireBoundPacket(ResourcePacketCodec.ResourcePacket packet) throws IOException {
            if (packet == null || !transferId.equals(packet.transferId())
                    || !serverId.equals(packet.serverId()) || !zone.equals(packet.zone())
                    || !folderName.equals(packet.folderName())
                    || !relativePath.equals(packet.relativePath())) {
                throw new IOException("upload packet binding does not match BEGIN");
            }
        }

        private synchronized int nextChunkIndex() {
            return nextChunkIndex;
        }

        private void cleanup() {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {
            }
        }

        private static String requireText(String value, String field) throws IOException {
            if (value == null || value.isBlank()) {
                throw new IOException(field + " is required");
            }
            return value;
        }
    }
}
