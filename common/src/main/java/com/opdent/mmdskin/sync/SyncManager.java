package com.opdent.mmdskin.sync;

import com.tendoarisu.mmdskin.sync.Config;
import com.tendoarisu.mmdskin.sync.util.CryptoUtils;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.architectury.platform.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class SyncManager {
    private static final Gson GSON = new Gson();
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .executor(java.util.concurrent.Executors.newFixedThreadPool(4)) // 限制 HTTP 客户端内部线程池
            .build();
    private static final String MODEL_SERVER_ID_FILE = ".mmdsync-server-id";
    private static String serverUrlOverride = null;
    private static String handshakeChallenge = "";
    private static String downloadToken = "";
    private static String currentServerId = "";

    public static void setServerUrlOverride(String url) {
        serverUrlOverride = url;
    }

    public static void setServerSecret(String secret) {
        if (secret == null) return;
        if (secret.contains("|")) {
            String[] parts = secret.split("\\|", 2);
            handshakeChallenge = parts.length > 0 ? parts[0] : "";
            downloadToken = parts.length > 1 ? parts[1] : "";
        } else {
            handshakeChallenge = secret;
            downloadToken = secret;
        }
    }

    public static void setCurrentServerId(String serverId) {
        currentServerId = serverId == null ? "" : serverId.trim();
    }

    public static String getCurrentServerId() {
        return currentServerId;
    }

    public static String getLastServerSecret() {
        return handshakeChallenge;
    }

    public static String getDownloadToken() {
        return downloadToken;
    }

    private static String appendToken(String url) {
        if (downloadToken == null || downloadToken.isEmpty()) return url;
        String sep = url.contains("?") ? "&" : "?";
        return url + sep + "token=" + URLEncoder.encode(downloadToken, StandardCharsets.UTF_8);
    }

    private static HttpRequest.Builder applyRequestAuth(HttpRequest.Builder builder, String method, String rawPath) {
        if (downloadToken == null || downloadToken.isEmpty()) return builder;
        String ts = String.valueOf(System.currentTimeMillis() / 1000L);
        String nonce = java.util.UUID.randomUUID().toString().replace("-", "");
        String sign;
        if (CryptoUtils.hasSessionMaterial()) {
            sign = CryptoUtils.signSyncEnvelope(downloadToken, ts, nonce, method, rawPath);
        } else {
            if (handshakeChallenge == null || !handshakeChallenge.equals(downloadToken)) return builder;
            sign = CryptoUtils.signSyncRequestWithSecret(handshakeChallenge, downloadToken, ts, nonce, method, rawPath);
        }
        if (sign.isEmpty()) return builder;

        return builder.header("X-MMDSync-Ts", ts)
                .header("X-MMDSync-Nonce", nonce)
                .header("X-MMDSync-Sign", sign);
    }

    public static String getServerUrl() {
        String serverUrl = serverUrlOverride;
        
        // 如果没有服务器下发的地址，则进入自动检测逻辑（视为留空）
        if (serverUrl == null || serverUrl.isEmpty()) {
            serverUrl = ""; // 强制进入下方的自动检测逻辑
        }
        
        // 如果留空或只填了端口（如 :5000），尝试获取当前连接的服务器 IP
        if (serverUrl.isEmpty() || serverUrl.startsWith(":")) {
            net.minecraft.client.multiplayer.ServerData serverData = Minecraft.getInstance().getConnection() != null 
                    ? Minecraft.getInstance().getConnection().getServerData() 
                    : null;
            if (serverData != null) {
                String ip = serverData.ip;
                // 去掉 IP 中原有的端口号
                if (ip.contains(":")) {
                    ip = ip.substring(0, ip.indexOf(":"));
                }
                
                if (serverUrl.startsWith(":")) {
                    // 如果用户只填了 :端口，则使用用户填的端口
                    serverUrl = ip + serverUrl;
                } else {
                    // 如果完全留空，使用配置的端口
                    serverUrl = ip + ":" + Config.SERVER_PORT;
                }
            } else {
                return null;
            }
        }
        
        // 自动处理协议前缀
        if (!serverUrl.toLowerCase().startsWith("http://") && !serverUrl.toLowerCase().startsWith("https://")) {
            serverUrl = "http://" + serverUrl;
        }
        
        if (serverUrl.endsWith("/")) {
            serverUrl = serverUrl.substring(0, serverUrl.length() - 1);
        }
        return serverUrl;
    }

    private static Path getModelServerIdFile(String folderName) {
        return Platform.getGameFolder()
                .resolve("3d-skin")
                .resolve("EntityPlayer")
                .resolve(folderName)
                .resolve(MODEL_SERVER_ID_FILE);
    }

    public static void bindModelFolderToServer(String folderName, String serverId) {
        if (folderName == null || folderName.isBlank() || serverId == null || serverId.isBlank()) {
            return;
        }
        try {
            Path metadataFile = getModelServerIdFile(folderName);
            Files.createDirectories(metadataFile.getParent());
            Files.writeString(metadataFile, serverId.trim(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            MMDSyncMod.LOGGER.warn("写入模型服务器标识失败(folder={}): {}", folderName, e.toString());
        }
    }

    private static String getBoundServerId(String folderName) {
        if (folderName == null || folderName.isBlank()) {
            return "";
        }
        Path metadataFile = getModelServerIdFile(folderName);
        if (!Files.exists(metadataFile)) {
            return "";
        }
        try {
            return Files.readString(metadataFile, StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            MMDSyncMod.LOGGER.warn("读取模型服务器标识失败(folder={}): {}", folderName, e.toString());
            return "";
        }
    }

    public static boolean shouldDisplayModelFolder(String folderName) {
        String boundServerId = getBoundServerId(folderName);
        return !currentServerId.isBlank() && currentServerId.equals(boundServerId);
    }

    public static void onSessionKeyReady() {
        if (reloadAllModels("SessionKey 已就绪，已触发 MMD 模型缓存强制重载。", "SessionKey 就绪后触发模型重载失败: {}")) {
            return;
        }
    }

    public static void clearClientSessionState() {
        serverUrlOverride = null;
        handshakeChallenge = "";
        downloadToken = "";
        currentServerId = "";

        boolean cleared = false;
        try {
            cleared = MMDSyncNativeBridge.clearSessionMaterial();
        } catch (Throwable e) {
            MMDSyncMod.LOGGER.warn("断服清理 Native 会话材料时发生异常: {}", e.toString());
        }

        reloadAllModels("客户端连接已断开，已清理 MMDSync 会话态并强制清空旧模型缓存。",
                "断服后触发模型缓存清理失败: {}");
    }

    private static boolean reloadAllModels(String successLog, String failureLog) {
        try {
            Class<?> managerClass = Class.forName("com.shiroha.mmdskin.renderer.model.MMDModelManager");
            java.lang.reflect.Method reloadMethod = managerClass.getMethod("forceReloadAllModels");
            Minecraft client = Minecraft.getInstance();
            Runnable reloadTask = () -> {
                try {
                    reloadMethod.invoke(null);
                } catch (Throwable e) {
                    MMDSyncMod.LOGGER.warn(failureLog, e.toString());
                }
            };

            if (client != null && !client.isSameThread()) {
                client.execute(reloadTask);
            } else {
                reloadTask.run();
            }
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        } catch (Throwable e) {
            MMDSyncMod.LOGGER.warn(failureLog, e.toString());
            return false;
        }
    }

    public static void startSync() {
        final String baseUrl = getServerUrl();
        if (baseUrl == null) {
            MMDSyncMod.LOGGER.warn("配置 serverUrl 为空且未连接到服务器，跳过同步。");
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                notifyUser("正在从服务器同步 MMD 模型资源...", false);

                // Fetch manifest
                String syncPath = "/api/sync";
                HttpRequest request = applyRequestAuth(
                        HttpRequest.newBuilder().uri(URI.create(appendToken(baseUrl + syncPath))),
                        "GET",
                        syncPath
                ).GET().build();

                HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    notifyUser("连接资源服务器失败: " + response.statusCode(), true);
                    return;
                }

                JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
                JsonArray pmxFiles = json.getAsJsonArray("pmx");
                JsonArray vmdFiles = json.getAsJsonArray("vmd");

                Path gameDir = Platform.getGameFolder();
                Path pmxDir = gameDir.resolve("3d-skin/EntityPlayer");
                Path vmdDir = gameDir.resolve("3d-skin/StageAnim");

                int downloadedCount = 0;
                int deletedCount = 0;
                downloadedCount += syncZone(baseUrl, "pmx", pmxDir, pmxFiles);
                downloadedCount += syncZone(baseUrl, "vmd", vmdDir, vmdFiles);
                deletedCount += pruneStaleFolders(pmxDir, pmxFiles, true);
                deletedCount += pruneStaleFolders(vmdDir, vmdFiles, false);

                if (downloadedCount > 0 || deletedCount > 0) {
                    notifyUser("MMD 资源同步完成，共更新 " + downloadedCount + " 个文件，删除 " + deletedCount + " 个过期模型/动作目录。", false);
                } else {
                    notifyUser("MMD 资源文件已是最新。", false);
                }

            } catch (Exception e) {
                MMDSyncMod.LOGGER.error("同步文件失败", e);
                notifyUser("MMD 资源同步出错: " + e.getMessage(), true);
            }
        });
    }

    private static int syncZone(String baseUrl, String zone, Path localDir, JsonArray folders) throws IOException, InterruptedException {
        int count = 0;
        if (folders == null) return 0;

        for (JsonElement element : folders) {
            if (!element.isJsonObject()) continue;
            JsonObject folderObj = element.getAsJsonObject();
            
            // 安全获取字段，防止 NPE
            JsonElement nameElem = folderObj.get("name");
            JsonElement md5Elem = folderObj.get("md5");
            if (nameElem == null || md5Elem == null) continue;
            
            String folderName = nameElem.getAsString();
            String serverMd5 = md5Elem.getAsString();
            
            Path folderPath = localDir.resolve(folderName);
            
            boolean needsDownload = false;
            if (!Files.exists(folderPath)) {
                needsDownload = true;
            } else {
                // 计算本地文件夹 MD5
                String localMd5 = getFolderMD5(folderPath);
                if (!serverMd5.equalsIgnoreCase(localMd5)) {
                    needsDownload = true;
                }
            }

            if (needsDownload) {
                // 对文件夹名进行编码
                String encodedName = URLEncoder.encode(folderName, StandardCharsets.UTF_8).replace("+", "%20");
                String downloadPath = "/download/" + zone + "/" + encodedName;
                String downloadUrl = appendToken(baseUrl + downloadPath);
                
                try {
                    HttpRequest request = applyRequestAuth(
                            HttpRequest.newBuilder().uri(URI.create(downloadUrl)),
                            "GET",
                            downloadPath
                    ).GET().build();
                    
                    HttpResponse<InputStream> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
                    if (response.statusCode() == 200) {
                        rotateDownloadToken(response);
                        InputStream responseStream = response.body();
                        String contentEncoding = response.headers().firstValue("Content-Encoding").orElse("");
                        if ("gzip".equalsIgnoreCase(contentEncoding)) {
                            responseStream = new GZIPInputStream(responseStream);
                        }
                        // 下载的是 ZIP，直接解压到目标目录
                        try (InputStream in = responseStream; ZipInputStream zis = new ZipInputStream(in)) {
                            ZipEntry entry;
                            Path normalizedFolderPath = folderPath.normalize();
                            while ((entry = zis.getNextEntry()) != null) {
                                Path target = folderPath.resolve(entry.getName().replace("/", File.separator)).normalize();
                                if (!target.startsWith(normalizedFolderPath)) {
                                    zis.closeEntry();
                                    continue;
                                }
                                if (entry.isDirectory()) {
                                    Files.createDirectories(target);
                                } else {
                                    Files.createDirectories(target.getParent());
                                    // 检查是否需要加密存储 (仅针对 pmx/vmd/纹理)
                                    String fileName = entry.getName().toLowerCase();
                                    if (fileName.endsWith(".pmx") || fileName.endsWith(".vmd") || 
                                        fileName.endsWith(".png") || fileName.endsWith(".jpg") || fileName.endsWith(".tga")) {
                                        byte[] data = zis.readAllBytes();
                                        // 客户端现在直接保存从服务端接收到的加密流
                                        Files.write(target, data);
                                    } else {
                                        Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
                                    }
                                }
                                zis.closeEntry();
                            }
                        }
                        if ("pmx".equalsIgnoreCase(zone)) {
                            bindModelFolderToServer(folderName, currentServerId);
                        }
                        count++;
                    } else {
                        MMDSyncMod.LOGGER.error("无法下载资源包 {}: {}", downloadUrl, response.statusCode());
                    }
                } catch (Exception e) {
                    MMDSyncMod.LOGGER.error("同步资源包异常: " + downloadUrl, e);
                }
            }
        }
        return count;
    }

    private static int pruneStaleFolders(Path localDir, JsonArray folders, boolean invalidateModelCache) {
        if (localDir == null || !Files.isDirectory(localDir)) {
            return 0;
        }

        Set<String> serverFolders = new HashSet<>();
        if (folders != null) {
            for (JsonElement element : folders) {
                if (!element.isJsonObject()) continue;
                JsonElement nameElem = element.getAsJsonObject().get("name");
                if (nameElem != null) {
                    String name = nameElem.getAsString();
                    if (name != null && !name.isBlank()) {
                        serverFolders.add(name);
                    }
                }
            }
        }

        int deleted = 0;
        try (Stream<Path> stream = Files.list(localDir)) {
            for (Path folderPath : (Iterable<Path>) stream.filter(Files::isDirectory)::iterator) {
                String folderName = folderPath.getFileName().toString();
                if (serverFolders.contains(folderName)) {
                    continue;
                }
                deleteDirectoryRecursively(folderPath);
                deleted++;
            }
        } catch (Exception e) {
            MMDSyncMod.LOGGER.error("清理本地过期同步目录失败: {}", localDir, e);
        }

        if (deleted > 0 && invalidateModelCache) {
            invalidateModelScanCache();
        }
        return deleted;
    }

    private static void deleteDirectoryRecursively(Path dir) throws IOException {
        try (Stream<Path> stream = Files.walk(dir)) {
            for (Path path : (Iterable<Path>) stream.sorted((a, b) -> b.getNameCount() - a.getNameCount())::iterator) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static void invalidateModelScanCache() {
        try {
            Class<?> modelInfoClass = Class.forName("com.shiroha.mmdskin.renderer.model.ModelInfo");
            java.lang.reflect.Method invalidateMethod = modelInfoClass.getMethod("invalidateCache");
            invalidateMethod.invoke(null);
        } catch (Throwable ignored) {
        }
    }

    private static String getFolderMD5(Path folder) {
        try (Stream<Path> stream = Files.walk(folder)) {
            StringBuilder combined = new StringBuilder();
            stream.filter(Files::isRegularFile)
                  .sorted()
                  .forEach(p -> combined.append(getFileMD5(p.toFile())));
            
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(combined.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String getFileMD5(File file) {
        try {
            String nativeMd5 = MMDSyncNativeBridge.getPlaintextMd5FromFile(file.getAbsolutePath());
            if (nativeMd5 != null && !nativeMd5.isEmpty()) {
                return nativeMd5;
            }
            MMDSyncMod.LOGGER.warn("本地文件明文 MD5 计算失败，Native 返回空结果: {}", file.getAbsolutePath());
        } catch (Throwable e) {
            MMDSyncMod.LOGGER.warn("本地文件明文 MD5 计算异常，已放弃 Java fallback: {}", e.toString());
        }
        return "";
    }

    private static void rotateDownloadToken(HttpResponse<?> response) {
        if (response == null) {
            return;
        }
        String nextToken = response.headers().firstValue("X-MMDSync-Next-Token").orElse("");
        if (!nextToken.isEmpty()) {
            downloadToken = nextToken;
        }
    }

    private static void notifyUser(String message, boolean isError) {
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(
                    Component.literal((isError ? "§c" : "§a") + "[MMDSync] " + message), 
                    false
                );
            }
        });
    }
}
