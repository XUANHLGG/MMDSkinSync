package com.opdent.mmdskin.sync;

import com.opdent.mmdskin.sync.network.resource.ResourcePacketCodec;
import com.opdent.mmdskin.sync.resource.SafeUploadCollector;
import com.tendoarisu.mmdskin.sync.util.CryptoUtils;
import com.opdent.mmdskin.sync.network.resource.ResourceTransferClientManager;
import com.opdent.mmdskin.sync.network.resource.ResourceTransferOpCode;
import com.opdent.mmdskin.sync.network.resource.ResourceTransferPacket;
import com.opdent.mmdskin.sync.ui.ResourceUploadScreen;
import com.opdent.mmdskin.sync.ui.SyncProgressTracker;
import com.tendoarisu.mmdskin.sync.Config;
import com.tendoarisu.mmdskin.sync.StagePackRefreshCoordinator;
import com.tendoarisu.mmdskin.sync.util.CryptoUtils;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import com.tendoarisu.mmdskin.sync.util.MMDSyncRuntimePorts;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

public class SyncManager {
    public enum UploadZone {
        PMX("pmx", "MMD模型"),
        VMD("vmd", "舞蹈动作");

        private final String zoneId;
        private final String displayName;

        UploadZone(String zoneId, String displayName) {
            this.zoneId = zoneId;
            this.displayName = displayName;
        }

        public String zoneId() {
            return zoneId;
        }

        public String displayName() {
            return displayName;
        }
    }

    public enum UploadSourceKind {
        ZIP("ZIP 压缩包"),
        SINGLE_FILE("单文件"),
        DIRECTORY("文件夹");

        private final String displayName;

        UploadSourceKind(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    private static final Gson GSON = new Gson();
    private static final String SERVER_BINDING_ROOT_DIR = "mmdsync-server-bindings";
    private static final String MODEL_BINDING_DIR = "models";
    private static final String STAGE_ANIM_BINDING_DIR = "stage-anim";
    private static final String MODEL_SELECTION_BINDING_DIR = "selected-models";
    private static final String MODEL_SERVER_ID_FILE = ".mmdsync-server-id";
    private static final String STAGE_ANIM_SERVER_ID_FILE = ".mmdsync-stage-server-id";
    private static String currentServerId = "";
    private static String handshakeChallenge = "";

    public static void setServerSecret(String secret) {
        handshakeChallenge = secret == null ? "" : secret;
    }

    public static String getLastServerSecret() {
        return handshakeChallenge;
    }

    public static void setCurrentServerId(String serverId) {
        String nextServerId = serverId == null ? "" : serverId.trim();
        boolean changed = !nextServerId.equals(currentServerId);
        currentServerId = nextServerId;
        restoreRememberedModelSelection(currentServerId);
        if (changed) {
            refreshModelCatalog("服务器上下文已切换，已触发模型缓存刷新。", "切换服务器后刷新模型缓存失败: {}");
        }
    }

    public static String getCurrentServerId() {
        return currentServerId;
    }

    private static Path getModelServerIdFile(String folderName) {
        return Platform.getConfigFolder()
                .resolve(SERVER_BINDING_ROOT_DIR)
                .resolve(MODEL_BINDING_DIR)
                .resolve(folderName + ".txt");
    }

    private static Path getStageAnimServerIdFile(String folderName) {
        return Platform.getConfigFolder()
                .resolve(SERVER_BINDING_ROOT_DIR)
                .resolve(STAGE_ANIM_BINDING_DIR)
                .resolve(folderName + ".txt");
    }

    public static void bindStageAnimFolderToServer(String folderName, String serverId) {
        if (folderName == null || folderName.isBlank() || serverId == null || serverId.isBlank()) {
            return;
        }
        try {
            Path metadataFile = getStageAnimServerIdFile(folderName);
            Files.createDirectories(metadataFile.getParent());
            Files.writeString(metadataFile, serverId.trim(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            MMDSyncMod.LOGGER.warn("写入舞蹈动作包服务器标识失败(folder={}): {}", folderName, e.toString());
        }
    }

    private static String getStageAnimBoundServerId(String folderName) {
        if (folderName == null || folderName.isBlank()) {
            return "";
        }
        Path metadataFile = getStageAnimServerIdFile(folderName);
        try {
            if (Files.exists(metadataFile)) {
                return Files.readString(metadataFile, StandardCharsets.UTF_8).trim();
            }
            return "";
        } catch (IOException e) {
            MMDSyncMod.LOGGER.warn("读取舞蹈动作包服务器标识失败(folder={}): {}", folderName, e.toString());
            return "";
        }
    }

    public static boolean shouldDisplayStageAnimFolder(String folderName) {
        String boundServerId = getStageAnimBoundServerId(folderName);
        return !currentServerId.isBlank() && currentServerId.equals(boundServerId);
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
        try {
            if (Files.exists(metadataFile)) {
                return Files.readString(metadataFile, StandardCharsets.UTF_8).trim();
            }
            return "";
        } catch (IOException e) {
            MMDSyncMod.LOGGER.warn("读取模型服务器标识失败(folder={}): {}", folderName, e.toString());
            return "";
        }
    }

    public static boolean shouldDisplayModelFolder(String folderName) {
        if (isDefaultModelName(folderName)) {
            return true;
        }
        String boundServerId = getBoundServerId(folderName);
        if (currentServerId == null || currentServerId.isBlank()) {
            return boundServerId == null || boundServerId.isBlank();
        }
        if (boundServerId == null || boundServerId.isBlank()) {
            return false;
        }
        return currentServerId.equals(boundServerId);
    }

    private static boolean isDefaultModelName(String folderName) {
        if (folderName == null || folderName.isBlank()) {
            return false;
        }
        try {
            Class<?> uiConstClass = Class.forName("com.shiroha.mmdskin.config.UIConstants");
            String defaultName = (String) uiConstClass.getField("DEFAULT_MODEL_NAME").get(null);
            if (defaultName != null && !defaultName.isBlank()) {
                return defaultName.equals(folderName);
            }
        } catch (Throwable ignored) {
        }
        return "默认 (原版渲染)".equals(folderName) || "默认模型".equals(folderName);
    }

    public static void onSessionKeyReady() {
        refreshModelCatalog("SessionKey 已就绪，已触发 MMD 模型缓存强制重载。", "SessionKey 就绪后触发模型重载失败: {}");
    }

    public static void clearClientSessionState() {
        rememberCurrentModelSelection(currentServerId);
        currentServerId = "";
        handshakeChallenge = "";
        StagePackRefreshCoordinator.reset();
        ResourceTransferClientManager.clearAllSessions();

        resetUpstreamModelSelectionToDefault();

        try {
            MMDSyncNativeBridge.clearSessionMaterial();
        } catch (Throwable e) {
            MMDSyncMod.LOGGER.warn("断服清理 Native 会话材料时发生异常: {}", e.toString());
        }

        callUpstreamStageAnimOnDisconnect();

        refreshModelCatalog("客户端连接已断开，已清理 MMDSync 会话态并强制清空旧模型缓存。",
                "断服后触发模型缓存清理失败: {}");
    }

    private static Path getSelectedModelMemoryFile(String serverId) {
        return Platform.getConfigFolder()
                .resolve(SERVER_BINDING_ROOT_DIR)
                .resolve(MODEL_SELECTION_BINDING_DIR)
                .resolve(serverId + ".txt");
    }

    private static void rememberCurrentModelSelection(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return;
        }

        try {
            Class<?> configClass = Class.forName("com.shiroha.mmdskin.ui.config.ModelSelectorConfig");
            Object instance = configClass.getMethod("getInstance").invoke(null);
            String selectedModel = (String) configClass.getMethod("getSelectedModel").invoke(instance);
            if (selectedModel == null || selectedModel.isBlank()) {
                return;
            }

            Path memoryFile = getSelectedModelMemoryFile(serverId);
            Files.createDirectories(memoryFile.getParent());
            Files.writeString(memoryFile, selectedModel, StandardCharsets.UTF_8);
        } catch (Throwable e) {
            MMDSyncMod.LOGGER.warn("记录分服务器模型选择失败(serverId={}): {}", serverId, e.toString());
        }
    }

    private static void restoreRememberedModelSelection(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return;
        }

        try {
            Path memoryFile = getSelectedModelMemoryFile(serverId);
            if (!Files.exists(memoryFile)) {
                return;
            }

            String selectedModel = Files.readString(memoryFile, StandardCharsets.UTF_8).trim();
            if (selectedModel.isBlank()) {
                return;
            }

            Class<?> configClass = Class.forName("com.shiroha.mmdskin.ui.config.ModelSelectorConfig");
            Object instance = configClass.getMethod("getInstance").invoke(null);
            configClass.getMethod("setSelectedModel", String.class).invoke(instance, selectedModel);
        } catch (Throwable e) {
            MMDSyncMod.LOGGER.warn("恢复分服务器模型选择失败(serverId={}): {}", serverId, e.toString());
        }
    }

    private static boolean reloadAllModels(String successLog, String failureLog) {
        try {
            Class<?> managerClass = Class.forName("com.shiroha.mmdskin.renderer.runtime.model.MMDModelManager");
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

    private static void refreshModelCatalog(String successLog, String failureLog) {
        invalidateModelScanCache();
        reloadAllModels(successLog, failureLog);
    }

    private static void resetUpstreamModelSelectionToDefault() {
        try {
            Class<?> configClass = Class.forName("com.shiroha.mmdskin.ui.config.ModelSelectorConfig");
            Object instance = configClass.getMethod("getInstance").invoke(null);

            Class<?> uiConstClass = Class.forName("com.shiroha.mmdskin.config.UIConstants");
            String defaultName = (String) uiConstClass.getField("DEFAULT_MODEL_NAME").get(null);
            if (defaultName == null || defaultName.isBlank()) {
                defaultName = "默认 (原版渲染)";
            }

            configClass.getMethod("setSelectedModel", String.class).invoke(instance, defaultName);
        } catch (Throwable ignored) {
        }
    }

    private static void callUpstreamStageAnimOnDisconnect() {
        try {
            Class<?> helper = Class.forName("com.shiroha.mmdskin.stage.client.sync.StageAnimSyncHelper");
            helper.getMethod("onDisconnect").invoke(null);
        } catch (Throwable ignored) {
        }
    }

    public static void startSync() {
        CompletableFuture.runAsync(() -> {
            try {
                Path gameDir = Platform.getGameFolder();
                Path pmxDir = gameDir.resolve("3d-skin/EntityPlayer");
                Path vmdDir = gameDir.resolve("3d-skin/StageAnim");

                if (trySyncViaResourcePackets(pmxDir, vmdDir)) {
                    return;
                }
                notifyUser("当前服务器未提供可用的游戏内资源传输通道。", true);

            } catch (Exception e) {
                MMDSyncMod.LOGGER.error("同步文件失败", e);
                notifyUser("MMD 资源同步出错: " + e.getMessage(), true);
            }
        });
    }

    private static boolean trySyncViaResourcePackets(Path pmxDir, Path vmdDir) {
        String transferId = "resource-sync-" + UUID.randomUUID();
        try {
            SyncProgressTracker.begin(transferId);
            ResourceTransferClientManager.clearSession(transferId);
            sendResourcePacket(new ResourcePacketCodec.ResourcePacket(
                    ResourceTransferOpCode.MANIFEST,
                    transferId,
                    currentServerId,
                    "",
                    "",
                    "",
                    0,
                    0,
                    0L,
                    "",
                    new byte[0],
                    List.of(),
                    "manifest_request"
            ));

            if (!waitForCondition(() -> ResourceTransferClientManager.hasManifest(transferId), 5000L)) {
                SyncProgressTracker.fail(transferId, "未收到资源清单");
                return false;
            }

            List<ResourcePacketCodec.ManifestEntry> manifestEntries = ResourceTransferClientManager.snapshotManifestEntries(transferId);
            List<ResourcePacketCodec.ManifestEntry> pmxEntries = filterManifestEntries(manifestEntries, "pmx");
            List<ResourcePacketCodec.ManifestEntry> vmdEntries = filterManifestEntries(manifestEntries, "vmd");

            int downloadedCount = 0;
            int deletedCount = 0;
            downloadedCount += syncZoneViaPackets(transferId, pmxDir, pmxEntries, true);
            downloadedCount += syncZoneViaPackets(transferId, vmdDir, vmdEntries, false);
            deletedCount += pruneStalePacketFiles(pmxDir, pmxEntries, transferId);
            deletedCount += pruneStalePacketFiles(vmdDir, vmdEntries, transferId);

            refreshModelCatalog(
                    downloadedCount > 0 || deletedCount > 0
                            ? "模型资源变更后已触发模型缓存重载。"
                            : "资源清单无文件变更，已刷新模型缓存与列表可见性。",
                    downloadedCount > 0 || deletedCount > 0
                            ? "同步完成后触发模型重载失败: {}"
                            : "零变更同步后触发模型重载失败: {}"
            );
            if (downloadedCount > 0 || deletedCount > 0) {
                SyncProgressTracker.finish(transferId, "MMD 资源同步完成");
            } else {
                SyncProgressTracker.finish(transferId, "MMD 资源文件已是最新");
            }
            return true;
        } catch (Exception e) {
            MMDSyncMod.LOGGER.warn("游戏内资源同步失败: {}", e.toString());
            SyncProgressTracker.fail(transferId, "同步失败: " + e.getMessage());
            return false;
        } finally {
            ResourceTransferClientManager.clearSession(transferId);
        }
    }

    private static int syncZoneViaPackets(
            String transferId,
            Path localDir,
            List<ResourcePacketCodec.ManifestEntry> entries,
            boolean modelZone
    ) throws IOException {
        int count = 0;
        if (entries == null || entries.isEmpty()) {
            return 0;
        }

        Files.createDirectories(localDir);
        for (ResourcePacketCodec.ManifestEntry entry : entries) {
            Path target = localDir.resolve(entry.folderName()).resolve(entry.relativePath().replace("/", File.separator));
            Files.createDirectories(target.getParent());

            if (Files.exists(target)) {
                boolean match = isContentMatch(target, entry.sha256());
                if (!match) {
                    MMDSyncMod.LOGGER.info("资源校验不匹配, zone={}, folder={}, path={}, expectedHash={}, localHash={}",
                            entry.zone(), entry.folderName(), entry.relativePath(), entry.sha256(), fileHashQuiet(target));
                }
                if (match) {
                    if (modelZone) {
                        bindModelFolderToServer(entry.folderName(), currentServerId);
                    } else {
                        bindStageAnimFolderToServer(entry.folderName(), currentServerId);
                    }
                    continue;
                }
            }

            sendResourcePacket(new ResourcePacketCodec.ResourcePacket(
                    ResourceTransferOpCode.REQUEST_CHUNK,
                    transferId,
                    currentServerId,
                    entry.zone(),
                    entry.folderName(),
                    entry.relativePath(),
                    0,
                    0,
                    0L,
                    "",
                    new byte[0],
                    List.of(),
                    "chunk_request"
            ));

            String key = buildTransferKey(entry.zone(), entry.folderName(), entry.relativePath());
            long timeoutSeconds = Config.RESOURCE_PACKET_WAIT_TIMEOUT_SECONDS;
            long timeoutMs = timeoutSeconds <= 0L ? 0L : timeoutSeconds * 1000L;
            if (!waitForCondition(() -> ResourceTransferClientManager.snapshotCompletedFiles(transferId).containsKey(key), timeoutMs)) {
                throw new IOException("等待资源分块完成超时: " + key);
            }

            Path cachedFile = ResourceTransferClientManager.snapshotCompletedFiles(transferId).get(key);
            if (cachedFile == null || !Files.exists(cachedFile)) {
                throw new IOException("资源分块缓存缺失: " + key);
            }

            Files.copy(cachedFile, target, StandardCopyOption.REPLACE_EXISTING);
            if (modelZone) {
                bindModelFolderToServer(entry.folderName(), currentServerId);
            } else {
                bindStageAnimFolderToServer(entry.folderName(), currentServerId);
            }
            count++;
        }
        return count;
    }

    private static boolean isContentMatch(Path path, String expected) {
        if (expected == null || expected.isBlank()) {
            return false;
        }
        String actual = fileHashQuiet(path);
        if (actual.isEmpty()) {
            return false;
        }
        return expected.equalsIgnoreCase(actual);
    }

    private static String fileHashQuiet(Path path) {
        if (path == null) {
            return "";
        }
        return getFileMD5(path.toFile());
    }

    private static boolean isEncryptedArchive(Path path) {
        try {
            return CryptoUtils.isEncrypted(path.toFile());
        } catch (Exception e) {
            return false;
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static int pruneStalePacketFiles(Path localDir, List<ResourcePacketCodec.ManifestEntry> entries, String transferId) {
        if (localDir == null || !Files.isDirectory(localDir)) {
            return 0;
        }

        Set<String> expectedFiles = new HashSet<>();
        Map<String, String> expectedHashes = new HashMap<>();
        for (ResourcePacketCodec.ManifestEntry entry : entries) {
            String relative = (entry.folderName() + "/" + entry.relativePath()).replace('\\', '/');
            expectedFiles.add(relative);
            if (entry.sha256() != null && !entry.sha256().isBlank()) {
                expectedHashes.put(relative, entry.sha256());
            }
        }

        int deleted = 0;
        List<Path> candidates;
        try (Stream<Path> stream = Files.walk(localDir)) {
            candidates = stream.filter(Files::isRegularFile).toList();
        } catch (IOException e) {
            MMDSyncMod.LOGGER.warn("扫描过期资源文件失败(dir={}): {}", localDir, e.toString());
            return 0;
        }

        for (Path path : candidates) {
            String relative = localDir.relativize(path).toString().replace('\\', '/');
            boolean shouldManage = shouldManageStalePacketFile(localDir, relative);
            if (!expectedFiles.contains(relative)) {
                if (!shouldManage) {
                    continue;
                }
                try {
                    Files.deleteIfExists(path);
                    deleted++;
                } catch (IOException e) {
                    MMDSyncMod.LOGGER.warn("删除过期资源文件失败(file={}): {}", path, e.toString());
                }
                continue;
            }

            String expected = expectedHashes.get(relative);
            if (expected == null || expected.isBlank()) {
                continue;
            }
            if (!shouldManage) {
                continue;
            }
            if (!isContentMatch(path, expected)) {
                try {
                    ResourcePacketCodec.ManifestEntry entry = findManifestEntry(entries, relative);
                    if (entry == null) {
                        continue;
                    }
                    requestFileRefresh(transferId, entry, path);
                    deleted++;
                } catch (IOException e) {
                    MMDSyncMod.LOGGER.warn("刷新资源失败(file={}): {}", path, e.toString());
                }
            }
        }

        try (Stream<Path> stream = Files.walk(localDir)) {
            stream.sorted(Comparator.reverseOrder())
                    .filter(Files::isDirectory)
                    .filter(path -> !path.equals(localDir))
                    .forEach(path -> {
                        try (Stream<Path> child = Files.list(path)) {
                            if (child.findAny().isEmpty()) {
                                Files.deleteIfExists(path);
                            }
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }

        return deleted;
    }

    private static boolean shouldManageStalePacketFile(Path localDir, String relative) {
        if (currentServerId == null || currentServerId.isBlank() || relative == null || relative.isBlank()) {
            return false;
        }
        String folderName = relative.split("/", 2)[0];
        String zoneName = localDir.getFileName() == null ? "" : localDir.getFileName().toString();
        if ("EntityPlayer".equalsIgnoreCase(zoneName)) {
            String boundServerId = getBoundServerId(folderName);
            return currentServerId.equals(boundServerId);
        }
        if ("StageAnim".equalsIgnoreCase(zoneName)) {
            String boundServerId = getStageAnimBoundServerId(folderName);
            return currentServerId.equals(boundServerId);
        }
        return false;
    }

    private static ResourcePacketCodec.ManifestEntry findManifestEntry(List<ResourcePacketCodec.ManifestEntry> entries, String relative) {
        if (entries == null || relative == null) {
            return null;
        }
        for (ResourcePacketCodec.ManifestEntry entry : entries) {
            String key = (entry.folderName() + "/" + entry.relativePath()).replace('\\', '/');
            if (key.equals(relative)) {
                return entry;
            }
        }
        return null;
    }

    private static void requestFileRefresh(String transferId, ResourcePacketCodec.ManifestEntry entry, Path target) throws IOException {
        sendResourcePacket(new ResourcePacketCodec.ResourcePacket(
                ResourceTransferOpCode.REQUEST_CHUNK,
                transferId,
                currentServerId,
                entry.zone(),
                entry.folderName(),
                entry.relativePath(),
                0,
                0,
                0L,
                "",
                new byte[0],
                List.of(),
                "chunk_request"
        ));

        String key = buildTransferKey(entry.zone(), entry.folderName(), entry.relativePath());
        long timeoutSeconds = Config.RESOURCE_PACKET_WAIT_TIMEOUT_SECONDS;
        long timeoutMs = timeoutSeconds <= 0L ? 0L : timeoutSeconds * 1000L;
        if (!waitForCondition(() -> ResourceTransferClientManager.snapshotCompletedFiles(transferId).containsKey(key), timeoutMs)) {
            throw new IOException("等待资源分块完成超时: " + key);
        }

        Path cachedFile = ResourceTransferClientManager.snapshotCompletedFiles(transferId).get(key);
        if (cachedFile == null || !Files.exists(cachedFile)) {
            throw new IOException("资源分块缓存缺失: " + key);
        }

        Files.copy(cachedFile, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private static List<ResourcePacketCodec.ManifestEntry> filterManifestEntries(List<ResourcePacketCodec.ManifestEntry> entries, String zone) {
        List<ResourcePacketCodec.ManifestEntry> result = new java.util.ArrayList<>();
        if (entries == null) {
            return result;
        }
        for (ResourcePacketCodec.ManifestEntry entry : entries) {
            if (zone.equalsIgnoreCase(entry.zone())) {
                result.add(entry);
            }
        }
        return result;
    }

    private static String buildTransferKey(String zone, String folderName, String relativePath) {
        return (zone == null ? "" : zone) + "/"
                + (folderName == null ? "" : folderName) + "/"
                + (relativePath == null ? "" : relativePath);
    }

    private static void sendResourcePacket(ResourcePacketCodec.ResourcePacket packet) throws IOException {
        Minecraft client = Minecraft.getInstance();
        ResourceTransferPacket payload = ResourceTransferPacket.fromResourcePacket(packet);
        int encodedBytes = measureResourcePacketBytes(payload);
        if (encodedBytes > 32_766) {
            MMDSyncMod.LOGGER.warn(
                    "拒绝发送超过 Bukkit plugin-message 上限的资源包: opcode={}, transferId={}, chunk={}/{}, bytes={}, folderUtf8={}, pathUtf8={}",
                    packet.opCode(), packet.transferId(), packet.chunkIndex(), packet.chunkCount(), encodedBytes,
                    utf8Length(packet.folderName()), utf8Length(packet.relativePath()));
            throw new IOException("资源传输包编码后超过 32,766 字节安全上限");
        } else if (packet.opCode() == ResourceTransferOpCode.UPLOAD_BEGIN
                || packet.opCode() == ResourceTransferOpCode.UPLOAD_FINISH
                || (packet.opCode() == ResourceTransferOpCode.UPLOAD_CHUNK && packet.chunkIndex() == 0)) {
            MMDSyncMod.LOGGER.info(
                    "资源上传诊断: opcode={}, transferId={}, chunk={}/{}, payloadBytes={}, encodedBytes={}, totalBytes={}, folder={}, path={}",
                    packet.opCode(), packet.transferId(), packet.chunkIndex(), packet.chunkCount(),
                    packet.payload() == null ? 0 : packet.payload().length, encodedBytes, packet.totalSize(),
                    packet.folderName(), packet.relativePath());
        }
        if (client != null && !client.isSameThread()) {
            java.util.concurrent.CompletableFuture<Void> future = new java.util.concurrent.CompletableFuture<>();
            client.execute(() -> {
                try {
                    dispatchResourcePacket(payload);
                    future.complete(null);
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                }
            });
            try {
                future.join();
            } catch (java.util.concurrent.CompletionException e) {
                throw new IOException("发送资源传输包失败", e.getCause());
            }
            return;
        }
        dispatchResourcePacket(payload);
    }

    private static int measureResourcePacketBytes(ResourceTransferPacket payload) {
        net.minecraft.network.FriendlyByteBuf buffer =
                new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            ResourceTransferPacket.STREAM_CODEC.encode(buffer, payload);
            return buffer.readableBytes();
        } finally {
            buffer.release();
        }
    }

    private static int utf8Length(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }

    private static void dispatchResourcePacket(ResourceTransferPacket payload) throws IOException {
        if (trySendViaFabric(payload)) {
            return;
        }
        if (trySendViaNeoForge(payload)) {
            return;
        }
        try {
            NetworkManager.sendToServer(payload);
        } catch (Throwable t) {
            throw new IOException("发送资源传输包失败", t);
        }
    }

    private static boolean trySendViaFabric(ResourceTransferPacket payload) throws IOException {
        try {
            Class<?> networkingClass = Class.forName("net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking");
            for (java.lang.reflect.Method method : networkingClass.getMethods()) {
                if (!method.getName().equals("send") || method.getParameterCount() != 1) {
                    continue;
                }
                Class<?> parameterType = method.getParameterTypes()[0];
                if (parameterType.isInstance(payload)
                        || parameterType.getName().equals("net.minecraft.network.protocol.common.custom.CustomPacketPayload")) {
                    method.invoke(null, payload);
                    return true;
                }
            }
            return false;
        } catch (ClassNotFoundException ignored) {
            return false;
        } catch (Throwable t) {
            throw new IOException("Fabric 发送资源传输包失败", t);
        }
    }

    private static boolean trySendViaNeoForge(ResourceTransferPacket payload) throws IOException {
        try {
            Class<?> distributorClass = Class.forName("net.neoforged.neoforge.network.PacketDistributor");
            Class<?> customPayloadClass = Class.forName("net.minecraft.network.protocol.common.custom.CustomPacketPayload");
            for (java.lang.reflect.Method method : distributorClass.getMethods()) {
                if (!method.getName().equals("sendToServer")) {
                    continue;
                }

                Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length == 1) {
                    Class<?> parameterType = parameterTypes[0];
                    if (!parameterType.isInstance(payload)
                            && !parameterType.getName().equals(customPayloadClass.getName())) {
                        continue;
                    }
                    method.invoke(null, payload);
                    return true;
                }

                if (parameterTypes.length == 2 && method.isVarArgs()) {
                    Class<?> firstType = parameterTypes[0];
                    Class<?> secondType = parameterTypes[1];
                    if ((!firstType.isInstance(payload) && !firstType.getName().equals(customPayloadClass.getName()))
                            || !secondType.isArray()
                            || !secondType.getComponentType().getName().equals(customPayloadClass.getName())) {
                        continue;
                    }
                    Object emptyPayloads = java.lang.reflect.Array.newInstance(secondType.getComponentType(), 0);
                    method.invoke(null, payload, emptyPayloads);
                    return true;
                }
            }
            return false;
        } catch (ClassNotFoundException ignored) {
            return false;
        } catch (Throwable t) {
            throw new IOException("NeoForge 发送资源传输包失败", t);
        }
    }

    private static boolean waitForCondition(BooleanSupplier predicate, long timeoutMs) {
        if (timeoutMs <= 0L) {
            while (true) {
                if (predicate.getAsBoolean()) {
                    return true;
                }
                try {
                    Thread.sleep(50L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (predicate.getAsBoolean()) {
                return true;
            }
            try {
                Thread.sleep(50L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return predicate.getAsBoolean();
    }

    public static void openUploadSelectionScreen(Screen parent) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return;
        }
        client.setScreen(new ResourceUploadScreen(parent));
    }

    public static void openUploadDialogAndUpload(Screen parent, UploadZone zone, UploadSourceKind sourceKind) {
        try {
            Path selected = chooseUploadPath(zone, sourceKind);
            if (selected == null) {
                return;
            }

            Minecraft client = Minecraft.getInstance();
            if (client != null) {
                client.setScreen(parent);
            }

            CompletableFuture.runAsync(() -> {
                try {
                    notifyUser("正在上传资源: " + selected.getFileName(), false);
                    int uploaded = uploadSelectedResource(selected, zone);
                    if (uploaded > 0) {
                        notifyUser("服务器已确认完成 " + uploaded + " 个资源文件上传。", false);
                    } else {
                        notifyUser("未找到可上传的资源文件。", true);
                    }
                } catch (Exception e) {
                    MMDSyncMod.LOGGER.error("上传模型资源失败", e);
                    notifyUser("上传模型资源失败: " + e.getMessage(), true);
                }
            });
        } catch (Exception e) {
            MMDSyncMod.LOGGER.error("上传模型资源失败", e);
            notifyUser("上传模型资源失败: " + e.getMessage(), true);
        }
    }

    private static Path chooseUploadPath(UploadZone zone, UploadSourceKind sourceKind) throws IOException {
        return switch (sourceKind) {
            case DIRECTORY -> chooseDirectory(zone);
            case ZIP -> chooseFile("选择 ZIP 压缩包", new String[]{"*.zip"}, "ZIP 压缩包");
            case SINGLE_FILE -> chooseSingleResourceFile(zone);
        };
    }

    private static Path chooseDirectory(UploadZone zone) throws IOException {
        String selected = TinyFileDialogs.tinyfd_selectFolderDialog("选择" + zone.displayName() + "文件夹", "");
        if (selected == null || selected.isBlank()) {
            return null;
        }
        return Path.of(selected);
    }

    private static Path chooseSingleResourceFile(UploadZone zone) throws IOException {
        if (zone == UploadZone.PMX) {
            return chooseFile("选择 MMD 模型文件", new String[]{"*.pmx", "*.pmd", "*.vrm", "*.fbx"}, "MMD 模型文件");
        }
        return chooseFile("选择舞蹈动作文件", new String[]{"*.vmd", "*.fbx"}, "舞蹈动作文件");
    }

    private static Path chooseFile(String title, String[] filters, String description) throws IOException {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer patterns = stack.mallocPointer(filters.length);
            for (String filter : filters) {
                patterns.put(stack.UTF8(filter));
            }
            patterns.flip();
            String selected = TinyFileDialogs.tinyfd_openFileDialog(title, "", patterns, description, false);
            if (selected == null || selected.isBlank()) {
                return null;
            }
            return Path.of(selected);
        } catch (Throwable t) {
            throw new IOException("打开文件选择器失败", t);
        }
    }

    private static int uploadSelectedResource(Path selected, UploadZone zone) throws IOException {
        List<UploadEntry> entries = collectUploadEntries(selected);
        if (entries.isEmpty()) {
            return 0;
        }

        int uploaded = 0;
        for (UploadEntry entry : entries) {
            uploadSingleResourceEntry(zone.zoneId(), entry);
            uploaded++;
        }
        return uploaded;
    }

    private static void uploadSingleResourceEntry(String zone, UploadEntry entry) throws IOException {
        String transferId = "upload-" + UUID.randomUUID();
        byte[] bytes = entry.payload();
        String digest = sha256Hex(bytes);
        int chunkSize = calculateUploadChunkSize(
                transferId, currentServerId, zone, entry.folderName(), entry.relativePath(), bytes.length, digest);
        int chunkCount = Math.max(1, (bytes.length + chunkSize - 1) / chunkSize);
        ResourceTransferClientManager.prepareUpload(transferId);

        try {
            sendResourcePacket(new ResourcePacketCodec.ResourcePacket(
                    ResourceTransferOpCode.UPLOAD_BEGIN,
                    transferId,
                    currentServerId,
                    zone,
                    entry.folderName(),
                    entry.relativePath(),
                    0,
                    chunkCount,
                    bytes.length,
                    digest,
                    new byte[0],
                    List.of(),
                    "upload_begin;cap=ack-v1"
            ));

            waitForCondition(() -> ResourceTransferClientManager.hasUploadResponse(transferId), 1_500L);
            throwIfUploadAborted(transferId);
            boolean ackV1 = ResourceTransferClientManager.hasUploadAck(transferId, "upload_begin_ok;ack-v1");
            MMDSyncMod.LOGGER.info("资源上传流控协商: transferId={}, mode={}", transferId,
                    ackV1 ? "ack-v1" : "legacy-paced");

            for (int i = 0; i < chunkCount; i++) {
                int start = i * chunkSize;
                int end = Math.min(bytes.length, start + chunkSize);
                byte[] chunk = java.util.Arrays.copyOfRange(bytes, start, end);
                sendResourcePacket(new ResourcePacketCodec.ResourcePacket(
                        ResourceTransferOpCode.UPLOAD_CHUNK,
                        transferId,
                        currentServerId,
                        zone,
                        entry.folderName(),
                        entry.relativePath(),
                        i,
                        chunkCount,
                        bytes.length,
                        digest,
                        chunk,
                        List.of(),
                        "upload_chunk"
                ));

                if (ackV1) {
                    String expectedAck = "upload_chunk_ok:" + i;
                    if (!waitForCondition(
                            () -> ResourceTransferClientManager.hasUploadAck(transferId, expectedAck)
                                    || ResourceTransferClientManager.isUploadAborted(transferId),
                            15_000L)) {
                        throw new IOException("等待服务器确认上传分块超时: " + i + "/" + chunkCount);
                    }
                    throwIfUploadAborted(transferId);
                } else {
                    throwIfUploadAborted(transferId);
                    sleepUploadPacing();
                }
            }

            sendResourcePacket(new ResourcePacketCodec.ResourcePacket(
                    ResourceTransferOpCode.UPLOAD_FINISH,
                    transferId,
                    currentServerId,
                    zone,
                    entry.folderName(),
                    entry.relativePath(),
                    chunkCount - 1,
                    chunkCount,
                    bytes.length,
                    digest,
                    new byte[0],
                    List.of(),
                    "upload_finish"
            ));
            if (!waitForCondition(
                    () -> ResourceTransferClientManager.hasUploadAck(transferId, "upload_finish_ok")
                            || ResourceTransferClientManager.isUploadAborted(transferId),
                    30_000L)) {
                throw new IOException("等待服务器确认上传完成超时");
            }
            throwIfUploadAborted(transferId);
        } catch (IOException | RuntimeException e) {
            abortUploadQuietly(transferId);
            throw e;
        } finally {
            ResourceTransferClientManager.clearUploadState(transferId);
        }
    }

    private static void throwIfUploadAborted(String transferId) throws IOException {
        if (ResourceTransferClientManager.isUploadAborted(transferId)) {
            throw new IOException("服务器中止上传: " + ResourceTransferClientManager.uploadAbortMessage(transferId));
        }
    }

    private static void sleepUploadPacing() throws IOException {
        try {
            Thread.sleep(15L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("上传线程已中断", e);
        }
    }

    private static void abortUploadQuietly(String transferId) {
        try {
            sendResourcePacket(new ResourcePacketCodec.ResourcePacket(
                    ResourceTransferOpCode.ABORT, transferId, currentServerId,
                    "", "", "", 0, 0, 0L, "", new byte[0], List.of(), "client_abort"));
        } catch (Exception ignored) {
        }
    }

    private static int calculateUploadChunkSize(
            String transferId,
            String serverId,
            String zone,
            String folderName,
            String relativePath,
            long totalSize,
            String digest) throws IOException {
        ResourceTransferPacket emptyChunk = ResourceTransferPacket.fromResourcePacket(new ResourcePacketCodec.ResourcePacket(
                ResourceTransferOpCode.UPLOAD_CHUNK,
                transferId,
                serverId,
                zone,
                folderName,
                relativePath,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                totalSize,
                digest,
                new byte[0],
                List.of(),
                "upload_chunk"
        ));
        int metadataBytes = measureResourcePacketBytes(emptyChunk);
        int dynamicLimit = 32_766 - metadataBytes;
        if (dynamicLimit <= 0) {
            throw new IOException("上传路径或元数据过长，无法在 32,766 字节 plugin-message 上限内编码");
        }
        int chunkSize = Math.min(24 * 1024, dynamicLimit);
        while (chunkSize > 0) {
            ResourceTransferPacket candidate = ResourceTransferPacket.fromResourcePacket(
                    new ResourcePacketCodec.ResourcePacket(
                            ResourceTransferOpCode.UPLOAD_CHUNK, transferId, serverId, zone, folderName,
                            relativePath, Integer.MAX_VALUE, Integer.MAX_VALUE, totalSize, digest,
                            new byte[chunkSize], List.of(), "upload_chunk"));
            if (measureResourcePacketBytes(candidate) <= 32_766) {
                return chunkSize;
            }
            chunkSize--;
        }
        throw new IOException("上传路径或元数据过长，无法为分块保留有效载荷空间");
    }

    private static List<UploadEntry> collectUploadEntries(Path selected) throws IOException {
        if (Files.isDirectory(selected, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            String folderName = buildUploadFolderName(selected.getFileName().toString());
            return SafeUploadCollector.collectDirectory(selected).stream()
                    .map(entry -> new UploadEntry(folderName, entry.relativePath(), entry.payload()))
                    .toList();
        }

        String lowerName = selected.getFileName().toString().toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".zip")) {
            SafeUploadCollector.ArchiveContents archive = SafeUploadCollector.collectZip(selected);
            String folderSeed = archive.commonRoot().isEmpty()
                    ? stripExtension(selected.getFileName().toString())
                    : archive.commonRoot();
            String folderName = buildUploadFolderName(folderSeed);
            List<UploadEntry> entries = new ArrayList<>(archive.entries().size());
            for (SafeUploadCollector.Entry entry : archive.entries()) {
                String relative = entry.relativePath();
                if (!archive.commonRoot().isEmpty() && relative.startsWith(archive.commonRoot() + "/")) {
                    relative = relative.substring(archive.commonRoot().length() + 1);
                }
                if (!relative.isEmpty()) {
                    entries.add(new UploadEntry(folderName, relative, entry.payload()));
                }
            }
            return entries;
        }

        SafeUploadCollector.Entry entry = SafeUploadCollector.collectSingleFile(selected);
        return List.of(new UploadEntry(
                buildUploadFolderName(stripExtension(selected.getFileName().toString())),
                entry.relativePath(),
                entry.payload()
        ));
    }

    private static String buildUploadFolderName(String baseName) {
        String clean = baseName == null ? "upload" : baseName.replaceAll("[^A-Za-z0-9._-]", "_");
        if (clean.isBlank()) {
            clean = "upload";
        }
        return new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + "_" + clean;
    }

    private static String stripExtension(String fileName) {
        if (fileName == null) {
            return "upload";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0) {
            return fileName;
        }
        return fileName.substring(0, dot);
    }

    private static String sha256Hex(byte[] data) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest.digest()) {
                sb.append(String.format(Locale.ROOT, "%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IOException("计算 SHA-256 失败", e);
        }
    }

    private record UploadEntry(String folderName, String relativePath, byte[] payload) {
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
            Class<?> modelInfoClass = Class.forName("com.shiroha.mmdskin.asset.catalog.ModelInfo");
            java.lang.reflect.Method invalidateMethod = modelInfoClass.getMethod("invalidateCache");
            invalidateMethod.invoke(null);
        } catch (Throwable ignored) {
        }
    }

    private static String getFolderMD5(Path folder) {
        try (Stream<Path> stream = Files.walk(folder)) {
            StringBuilder combined = new StringBuilder();
            stream.filter(Files::isRegularFile)
                  .filter(p -> !shouldIgnoreMd5File(p))
                  .sorted()
                  .forEach(p -> {
                      String fileMd5 = getFileMD5(p.toFile());
                      if (fileMd5.isEmpty()) {
                          MMDSyncMod.LOGGER.warn("文件 MD5 为空: {}", p);
                      }
                      combined.append(fileMd5);
                  });
            
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(combined.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static boolean shouldIgnoreMd5File(Path file) {
        String name = file.getFileName() == null ? "" : file.getFileName().toString();
        return MODEL_SERVER_ID_FILE.equals(name) || STAGE_ANIM_SERVER_ID_FILE.equals(name);
    }

    private static String getFileMD5(File file) {
        if (file == null || !file.exists()) {
            return "";
        }
        boolean encrypted = CryptoUtils.isEncrypted(file);
        if (encrypted && !CryptoUtils.hasSessionMaterial()) {
            CryptoUtils.waitForSessionMaterial(0);
        }

        try {
            String nativeMd5 = MMDSyncNativeBridge.getPlaintextMd5FromFile(file.getAbsolutePath());
            if (nativeMd5 != null && !nativeMd5.isEmpty()) {
                return nativeMd5;
            }
            MMDSyncMod.LOGGER.warn("本地文件明文 MD5 计算失败，Native 返回空结果: {}", file.getAbsolutePath());
        } catch (Throwable e) {
            MMDSyncMod.LOGGER.warn("本地文件明文 MD5 计算异常，准备使用 Java fallback: {}", e.toString());
        }

        if (encrypted) {
            MMDSyncMod.LOGGER.warn("加密文件未能获得明文 MD5，已跳过 Java 原文 fallback: {}", file.getAbsolutePath());
            return "";
        }

        try (InputStream in = Files.newInputStream(file.toPath())) {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : digest.digest()) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            MMDSyncMod.LOGGER.warn("本地文件 MD5 计算失败（Java fallback）: {}", e.toString());
            return "";
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
