package com.tendoarisu.mmdskin.sync.util;

import com.opdent.mmdskin.sync.MMDSyncMod;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;

import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class CryptoUtils {
    private static final byte[] CONTAINER_MAGIC = "MMDARC".getBytes(StandardCharsets.UTF_8);
    private static final byte CONTAINER_VERSION = 0x01;

    public static byte[] getNativeResourceHash(String platform) {
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
        try (InputStream is = CryptoUtils.class.getResourceAsStream(resourcePath)) {
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

    public static String getHandshakePem(String serverSecret, String targetPlatform, String clientHwid) {
        try {
            String safeHwid = (clientHwid != null && !clientHwid.isEmpty()) ? clientHwid : "fallback-hwid";
            byte[] targetHash = (targetPlatform != null) ? getNativeResourceHash(targetPlatform) : null;

            return MMDSyncNativeBridge.deriveHandshakePem(serverSecret == null ? "" : serverSecret, targetHash != null ? targetHash : new byte[0], safeHwid);
        } catch (Throwable e) {
            MMDSyncMod.LOGGER.error("获取客户端握手材料失败", e);
            return "";
        }
    }

    public static String getHandshakePem(String serverSecret) {
        return getHandshakePem(serverSecret, null, null);
    }

    public static void installSessionMaterial(String encryptedAesKeyBase64, String serverSecret) {
        try {
            String safeHwid = getJavaBasedHardwareId();
            String platform = MMDSyncNativeLoader.getPlatformIdentifier();
            byte[] targetHash = getNativeResourceHash(platform);
            if (targetHash == null) {
                targetHash = new byte[0];
            }

            boolean stored = MMDSyncNativeBridge.installSessionMaterial(
                encryptedAesKeyBase64,
                serverSecret == null ? "" : serverSecret,
                targetHash,
                safeHwid
            );
            if (!stored) {
                com.opdent.mmdskin.sync.MMDSyncMod.LOGGER.error("会话材料安装失败: Native 存储失败, platform={}, hashLen={}", platform, targetHash.length);
            }
        } catch (Throwable e) {
            MMDSyncMod.LOGGER.error("会话材料安装异常", e);
        }
    }

    public static boolean hasSessionMaterial() {
        try {
            return MMDSyncNativeBridge.hasSessionMaterial();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean waitForSessionMaterial(long timeoutMs) {
        if (hasSessionMaterial()) {
            return true;
        }

        long deadline = timeoutMs > 0 ? System.currentTimeMillis() + timeoutMs : Long.MAX_VALUE;
        while (System.currentTimeMillis() < deadline) {
            if (hasSessionMaterial()) {
                return true;
            }
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return hasSessionMaterial();
            }
        }
        return hasSessionMaterial();
    }

    public static String signSyncEnvelope(String token, String ts, String nonce, String method, String rawPath) {
        if (!hasSessionMaterial() || token == null || token.isEmpty()) return "";
        try {
            String signed = MMDSyncNativeBridge.signSyncEnvelope(token, ts, nonce, method, rawPath);
            return signed != null ? signed : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    public static String signSyncRequestWithSecret(String secret, String token, String ts, String nonce, String method, String rawPath) {
        if (secret == null || secret.isEmpty() || token == null || token.isEmpty()) return "";
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
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
    


    private static String javaHwidCache = null;
    public static String getJavaBasedHardwareId() {
        if (javaHwidCache != null) return javaHwidCache;
        
        try {
            StringBuilder sb = new StringBuilder();
            sb.append(System.getProperty("os.name"));
            sb.append(System.getProperty("os.arch"));
            sb.append(System.getProperty("os.version"));
            sb.append(Runtime.getRuntime().availableProcessors());
            sb.append(System.getenv("PROCESSOR_IDENTIFIER"));
            sb.append(System.getenv("COMPUTERNAME"));
            sb.append(System.getProperty("user.name"));
            
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            javaHwidCache = hexString.toString();
        } catch (Exception e) {
            javaHwidCache = "JAVA-FALLBACK-HWID-" + System.currentTimeMillis();
            MMDSyncMod.LOGGER.warn("生成 Java HWID 失败，已回退到临时标识: {}", e.getMessage());
        }
        return javaHwidCache;
    }

    public static String rsaEncryptJava(byte[] aesKey, String publicKeyPem) throws Exception {
        String publicKeyString = publicKeyPem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replaceAll("\\s", "");
        
        byte[] encoded = Base64.getDecoder().decode(publicKeyString);
        java.security.spec.X509EncodedKeySpec keySpec = new java.security.spec.X509EncodedKeySpec(encoded);
        java.security.KeyFactory keyFactory = java.security.KeyFactory.getInstance("RSA");
        java.security.PublicKey publicKey = keyFactory.generatePublic(keySpec);

        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        byte[] encryptedBytes = cipher.doFinal(aesKey);
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }

    public static boolean isEncrypted(File file) {
        if (!file.exists() || file.length() < CONTAINER_MAGIC.length + 1) {
            return false;
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = new byte[CONTAINER_MAGIC.length];
            if (fis.read(header) != CONTAINER_MAGIC.length) return false;
            if (!Arrays.equals(header, CONTAINER_MAGIC)) return false;
            int version = fis.read();
            return version == (CONTAINER_VERSION & 0xFF);
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean isEncrypted(byte[] data) {
        if (data == null || data.length < CONTAINER_MAGIC.length + 1) {
            return false;
        }
        for (int i = 0; i < CONTAINER_MAGIC.length; i++) {
            if (data[i] != CONTAINER_MAGIC[i]) return false;
        }
        return data[CONTAINER_MAGIC.length] == CONTAINER_VERSION;
    }

    public static String getIntegrityHash() {
        try {
            String nativeHash = MMDSyncNativeBridge.getLibraryHash();
            
            String classPath = MMDSyncNativeBridge.class.getName().replace('.', '/') + ".class";
            byte[] classBytes = null;
            try (InputStream is = MMDSyncNativeBridge.class.getClassLoader().getResourceAsStream(classPath)) {
                if (is != null) {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = is.read(buffer)) != -1) {
                        bos.write(buffer, 0, n);
                    }
                    classBytes = bos.toByteArray();
                }
            }
            
            String javaHash = classBytes != null ? MMDSyncNativeBridge.getClassHash(classBytes) : "JAVA_HASH_ERROR";
            return "NATIVE:" + nativeHash + "|JAVA:" + javaHash;
        } catch (Throwable e) {
            return "INTEGRITY_CHECK_FAILED:" + e.getMessage();
        }
    }
}
