package com.opdent.mmdskin.sync;

import com.opdent.mmdskin.sync.network.HandshakePacket;
import com.opdent.mmdskin.sync.network.SyncUrlPacket;
import com.tendoarisu.mmdskin.sync.Config;
import com.tendoarisu.mmdskin.sync.util.CryptoUtils;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;

public final class ServerAuthManager {
    private static final Map<UUID, PendingHandshake> pendingHandshakes = new ConcurrentHashMap<>();
    private static final long HANDSHAKE_TTL_MS = 60_000L;
    private static final AtomicBoolean nativeFingerprintLogged = new AtomicBoolean(false);

    private static final class PendingHandshake {
        final String challenge;
        final long expireAt;

        PendingHandshake(String challenge, long expireAt) {
            this.challenge = challenge;
            this.expireAt = expireAt;
        }
    }

    public static SyncUrlPacket buildChallengePacket(ServerPlayer player) {
        String challenge = randomUrlSafeToken(24);
        String serverId = buildStableServerId(player);
        pendingHandshakes.put(player.getUUID(), new PendingHandshake(challenge, System.currentTimeMillis() + HANDSHAKE_TTL_MS));
        return new SyncUrlPacket("", challenge, serverId);
    }

    public static SyncUrlPacket handleHandshake(ServerPlayer player, HandshakePacket packet) {
        if (packet == null || player == null) {
            return null;
        }
        if (packet.opCode() != 20) {
            return null;
        }
        if (packet.playerUUID() == null || !packet.playerUUID().equals(player.getUUID())) {
            return null;
        }
        PendingHandshake pending = pendingHandshakes.get(player.getUUID());
        if (pending == null) {
            return null;
        }
        if (System.currentTimeMillis() > pending.expireAt) {
            pendingHandshakes.remove(player.getUUID());
            return null;
        }
        byte[] targetHash = getNativeResourceHash(packet.platform());
        if (targetHash == null) {
            return null;
        }
        logNativeFingerprintOnce(packet.platform());
        String expectedPem = MMDSyncNativeBridge.deriveHandshakePem(pending.challenge, targetHash, packet.hwid());
        if (expectedPem == null || expectedPem.isEmpty()) {
            return null;
        }
        if (!normalizePem(expectedPem).equals(normalizePem(packet.publicKey()))) {
            return null;
        }
        String encryptedAesKey;
        try {
            encryptedAesKey = CryptoUtils.rsaEncryptJava(getServerSyncKey(), expectedPem);
        } catch (Exception e) {
            return null;
        }
        pendingHandshakes.remove(player.getUUID());
        String serverId = buildStableServerId(player);
        return new SyncUrlPacket(encryptedAesKey, pending.challenge, serverId);
    }

    public static byte[] getServerSyncKey() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest((Config.SERVER_SECRET == null ? "" : Config.SERVER_SECRET).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return new byte[32];
        }
    }

    public static String getStableServerId(ServerPlayer player) {
        return buildStableServerId(player);
    }

    private static String buildStableServerId(ServerPlayer player) {
        byte[] key = getServerSyncKey();
        if (key == null || key.length == 0) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(key);
            digest.update(resolveServerScope(player).getBytes(StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder("srv-");
            for (byte b : digest.digest()) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String resolveServerScope(ServerPlayer player) {
        if (player == null) {
            return "unknown-server-root";
        }

        net.minecraft.server.MinecraftServer server = player.getServer();
        if (server == null) {
            return "unknown-server-root";
        }

        Path rootPath = server.getWorldPath(LevelResource.ROOT);
        if (rootPath == null) {
            return "unknown-server-root";
        }
        return rootPath.toAbsolutePath().normalize().toString();
    }

    private static String randomUrlSafeToken(int byteLen) {
        byte[] bytes = new byte[byteLen];
        new java.security.SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String normalizePem(String pem) {
        if (pem == null) return "";
        return pem.replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
    }

    private static byte[] getNativeResourceHash(String platform) {
        if (platform == null || platform.isEmpty()) return null;
        String fileName;
        if (platform.startsWith("windows")) {
            fileName = "mmdsync_bridge.dll";
        } else if (platform.startsWith("macos")) {
            fileName = "libmmdsync_bridge.dylib";
        } else if (platform.startsWith("linux")) {
            fileName = "libmmdsync_bridge.so";
        } else {
            return null;
        }
        String resourcePath = "/natives/" + platform + "/" + fileName;
        try (java.io.InputStream is = ServerAuthManager.class.getResourceAsStream(resourcePath)) {
            if (is == null) return null;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int n;
            while ((n = is.read(buffer)) != -1) {
                digest.update(buffer, 0, n);
            }
            return digest.digest();
        } catch (Exception e) {
            return null;
        }
    }

    private static void logNativeFingerprintOnce(String platform) {
        if (!nativeFingerprintLogged.compareAndSet(false, true)) return;
        try {
            MMDSyncNativeBridge.getLibraryHash();
        } catch (Throwable ignored) {
        }
    }
}
