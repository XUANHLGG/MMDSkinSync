package com.opdent.mmdskin.sync;

import com.opdent.mmdskin.sync.network.HandshakePacket;
import com.opdent.mmdskin.sync.network.SyncUrlPacket;
import com.tendoarisu.mmdskin.sync.Config;
import com.tendoarisu.mmdskin.sync.util.CryptoUtils;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import com.sun.net.httpserver.HttpExchange;
import net.minecraft.server.level.ServerPlayer;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;

public final class ServerAuthManager {
    private static final Map<UUID, PendingHandshake> pendingHandshakes = new ConcurrentHashMap<>();
    private static final Map<String, DownloadSession> downloadSessions = new ConcurrentHashMap<>();
    private static final long HANDSHAKE_TTL_MS = 60_000L;
    private static final long DOWNLOAD_TOKEN_TTL_MS = 90_000L;
    private static final long REQUEST_TS_WINDOW_SEC = 5L;
    private static final long NONCE_RETENTION_MS = 15_000L;
    private static final int NONCE_HEX_LENGTH = 32;
    private static final String NEXT_TOKEN_ATTR = "mmdsync.nextToken";
    private static final AtomicBoolean nativeFingerprintLogged = new AtomicBoolean(false);

    private static final class PendingHandshake {
        final String challenge;
        final long expireAt;

        PendingHandshake(String challenge, long expireAt) {
            this.challenge = challenge;
            this.expireAt = expireAt;
        }
    }

    private static final class DownloadSession {
        final UUID playerUuid;
        final long expireAt;
        final Map<String, Long> usedNonces = new ConcurrentHashMap<>();
        final AtomicBoolean downloadConsumed = new AtomicBoolean(false);

        DownloadSession(UUID playerUuid, long expireAt) {
            this.playerUuid = playerUuid;
            this.expireAt = expireAt;
        }

        boolean tryUseNonce(String nonce) {
            long now = System.currentTimeMillis();
            usedNonces.entrySet().removeIf(entry -> now - entry.getValue() > NONCE_RETENTION_MS);
            return usedNonces.putIfAbsent(nonce, now) == null;
        }

        boolean tryConsumeDownload() {
            return downloadConsumed.compareAndSet(false, true);
        }

        DownloadSession renew() {
            return new DownloadSession(playerUuid, expireAt);
        }
    }

    private ServerAuthManager() {}

    public static SyncUrlPacket buildChallengePacket(ServerPlayer player) {
        String serverUrl = Config.SERVER_URL == null ? "" : Config.SERVER_URL.trim();
        if (serverUrl.isEmpty()) {
            serverUrl = ":" + Config.SERVER_PORT;
        }
        String challenge = randomUrlSafeToken(24);
        String serverId = buildStableServerId();
        pendingHandshakes.put(player.getUUID(), new PendingHandshake(challenge, System.currentTimeMillis() + HANDSHAKE_TTL_MS));
        return new SyncUrlPacket(serverUrl, "", challenge + "|", serverId);
    }

    public static SyncUrlPacket handleHandshake(ServerPlayer player, HandshakePacket packet) {
        if (packet == null) {
            MMDSyncMod.LOGGER.warn("[握手链路] handleHandshake 失败：packet 为 null。");
            return null;
        }
        if (player == null) {
            MMDSyncMod.LOGGER.warn("[握手链路] handleHandshake 失败：player 为 null。opCode={}", packet.opCode());
            return null;
        }
        if (packet.opCode() != 20) {
            MMDSyncMod.LOGGER.warn("[握手链路] handleHandshake 失败：opCode 非 20。player={}, opCode={}", player.getUUID(), packet.opCode());
            return null;
        }
        if (packet.playerUUID() == null || !packet.playerUUID().equals(player.getUUID())) {
            MMDSyncMod.LOGGER.warn("[握手链路] handleHandshake 失败：playerUUID 不匹配。serverPlayer={}, packetPlayer={}", player.getUUID(), packet.playerUUID());
            return null;
        }
        PendingHandshake pending = pendingHandshakes.get(player.getUUID());
        if (pending == null) {
            MMDSyncMod.LOGGER.warn("[握手链路] handleHandshake 失败：未找到 pending challenge。player={}, pendingCount={}", player.getUUID(), pendingHandshakes.size());
            return null;
        }
        if (System.currentTimeMillis() > pending.expireAt) {
            pendingHandshakes.remove(player.getUUID());
            MMDSyncMod.LOGGER.warn("[握手链路] handleHandshake 失败：challenge 已过期。player={}", player.getUUID());
            return null;
        }
        byte[] targetHash = getNativeResourceHash(packet.platform());
        if (targetHash == null) {
            MMDSyncMod.LOGGER.warn("[握手链路] handleHandshake 失败：无法获取平台 native hash。player={}, platform={}", player.getUUID(), packet.platform());
            return null;
        }
        logNativeFingerprintOnce(packet.platform());
        String expectedPem = MMDSyncNativeBridge.deriveHandshakePem(pending.challenge, targetHash, packet.hwid());
        if (expectedPem == null || expectedPem.isEmpty()) {
            MMDSyncMod.LOGGER.warn("[握手链路] handleHandshake 失败：expectedPem 为空。player={}", player.getUUID());
            return null;
        }
        if (!normalizePem(expectedPem).equals(normalizePem(packet.publicKey()))) {
            MMDSyncMod.LOGGER.warn("[握手链路] handleHandshake 失败：客户端公钥校验不一致。player={}, expectedLen={}, actualLen={}",
                    player.getUUID(),
                    expectedPem.length(),
                    packet.publicKey() != null ? packet.publicKey().length() : 0);
            return null;
        }
        String encryptedAesKey;
        try {
            encryptedAesKey = CryptoUtils.rsaEncryptJava(getServerSyncKey(), expectedPem);
        } catch (Exception e) {
            MMDSyncMod.LOGGER.warn("[握手链路] handleHandshake 失败：加密 SessionKey 异常: {}", e.toString());
            return null;
        }
        pendingHandshakes.remove(player.getUUID());
        String token = randomUrlSafeToken(32);
        downloadSessions.put(token, new DownloadSession(player.getUUID(), System.currentTimeMillis() + DOWNLOAD_TOKEN_TTL_MS));
        String serverUrl = Config.SERVER_URL == null ? "" : Config.SERVER_URL.trim();
        if (serverUrl.isEmpty()) {
            serverUrl = ":" + Config.SERVER_PORT;
        }
        String serverId = buildStableServerId();
        return new SyncUrlPacket(serverUrl, encryptedAesKey, pending.challenge + "|" + token, serverId);
    }

    public static boolean isAuthorized(HttpExchange exchange) {
        String query = exchange.getRequestURI().getQuery();
        if (query == null || query.isEmpty()) return false;
        String token = null;
        for (String param : query.split("&")) {
            int idx = param.indexOf('=');
            if (idx <= 0) continue;
            if (!"token".equals(param.substring(0, idx))) continue;
            token = URLDecoder.decode(param.substring(idx + 1), StandardCharsets.UTF_8);
            break;
        }
        if (token == null || token.isEmpty()) return false;
        DownloadSession session = downloadSessions.get(token);
        if (session == null) return false;
        if (System.currentTimeMillis() > session.expireAt) {
            downloadSessions.remove(token);
            return false;
        }
        String ts = exchange.getRequestHeaders().getFirst("X-MMDSync-Ts");
        String nonce = exchange.getRequestHeaders().getFirst("X-MMDSync-Nonce");
        String sign = exchange.getRequestHeaders().getFirst("X-MMDSync-Sign");
        if (ts == null || nonce == null || sign == null) return false;
        if (!isValidNonce(nonce)) return false;
        long tsSec;
        try {
            tsSec = Long.parseLong(ts);
        } catch (NumberFormatException e) {
            return false;
        }
        long nowSec = System.currentTimeMillis() / 1000L;
        if (Math.abs(nowSec - tsSec) > REQUEST_TS_WINDOW_SEC) return false;
        String rawPath = exchange.getRequestURI().getRawPath();
        if (!session.tryUseNonce(nonce)) return false;
        String expected = calcRequestSignature(token, ts, nonce, exchange.getRequestMethod(), rawPath);
        if (expected.isEmpty() || !expected.equalsIgnoreCase(sign)) {
            session.usedNonces.remove(nonce);
            return false;
        }
        if (rawPath != null && rawPath.startsWith("/download/")) {
            if (!session.tryConsumeDownload()) {
                return false;
            }
            String nextToken = randomUrlSafeToken(32);
            downloadSessions.put(nextToken, session.renew());
            downloadSessions.remove(token, session);
            exchange.setAttribute(NEXT_TOKEN_ATTR, nextToken);
        }
        return true;
    }

    public static byte[] getServerSyncKey() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest((Config.SERVER_SECRET == null ? "" : Config.SERVER_SECRET).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return new byte[32];
        }
    }

    private static String buildStableServerId() {
        byte[] key = getServerSyncKey();
        if (key == null || key.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder("srv-");
        for (byte b : key) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String calcRequestSignature(String token, String ts, String nonce, String method, String rawPath) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(getServerSyncKey(), "HmacSHA256"));
            String payload = token + "|" + ts + "|" + nonce + "|" + method + "|" + rawPath;
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String randomUrlSafeToken(int byteLen) {
        byte[] bytes = new byte[byteLen];
        new java.security.SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String consumeIssuedNextToken(HttpExchange exchange) {
        Object value = exchange.getAttribute(NEXT_TOKEN_ATTR);
        if (!(value instanceof String nextToken) || nextToken.isEmpty()) {
            return "";
        }
        exchange.setAttribute(NEXT_TOKEN_ATTR, null);
        return nextToken;
    }

    private static boolean isValidNonce(String nonce) {
        if (nonce.length() != NONCE_HEX_LENGTH) {
            return false;
        }
        for (int i = 0; i < nonce.length(); i++) {
            char c = nonce.charAt(i);
            boolean isHex = (c >= '0' && c <= '9')
                    || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F');
            if (!isHex) {
                return false;
            }
        }
        return true;
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
