package com.tendoarisu.mmdskin.sync;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.opdent.mmdskin.sync.ServerAuthManager;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import dev.architectury.platform.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class EmbeddedServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(EmbeddedServer.class);
    private static HttpServer server;
    private static ExecutorService serverExecutor;
    private static final Map<Path, CacheEntry> MD5_CACHE = Collections.synchronizedMap(new HashMap<>());

    static class CacheEntry {
        String md5;
        long lastModified;

        CacheEntry(String md5, long lastModified) {
            this.md5 = md5;
            this.lastModified = lastModified;
        }
    }

    public static void start() {
        if (!Config.ENABLE_SERVER) return;
        CompletableFuture.runAsync(() -> {
            loadCache();
            try {
                int port = Config.SERVER_PORT;
                String bindHost = "0.0.0.0";
                server = HttpServer.create(new InetSocketAddress(bindHost, port), 0);
                server.createContext("/", new IndexHandler());
                server.createContext("/api/sync", new SyncHandler());
                server.createContext("/download/", new DownloadHandler());
                server.createContext("/upload", new UploadHandler());
                serverExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
                server.setExecutor(serverExecutor);
                server.start();
            } catch (IOException e) {
                LOGGER.error("启动内置服务器失败", e);
            }
        });
    }

    public static void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
            if (serverExecutor != null) {
                serverExecutor.shutdown();
                serverExecutor = null;
            }
            saveCache();
        }
    }

    // 静态资源处理器 (首页)
    static class IndexHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String resourcePath = "/".equals(path) ? "/assets/mmdsync/web/index.html" : "/assets/mmdsync/web" + path;
            InputStream is = EmbeddedServer.class.getResourceAsStream(resourcePath);
            if (is == null) {
                if (!"/".equals(path)) {
                    sendResponse(exchange, 404, "404 Not Found".getBytes(StandardCharsets.UTF_8), "text/plain");
                    return;
                }
                String error = "404 Not Found";
                sendResponse(exchange, 404, error.getBytes(StandardCharsets.UTF_8), "text/plain");
                return;
            }
            byte[] response = is.readAllBytes();
            sendResponse(exchange, 200, response, getContentType(path));
        }

        private String getContentType(String path) {
            String lower = path.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".html") || "/".equals(path)) return "text/html; charset=UTF-8";
            if (lower.endsWith(".woff2")) return "font/woff2";
            if (lower.endsWith(".txt")) return "text/plain; charset=UTF-8";
            if (lower.endsWith(".webp")) return "image/webp";
            if (lower.endsWith(".png")) return "image/png";
            if (lower.endsWith(".ico")) return "image/x-icon";
            return "application/octet-stream";
        }
    }

    // 资源列表处理器
    static class SyncHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthorized(exchange)) {
                sendResponse(exchange, 403, "403 Forbidden".getBytes(StandardCharsets.UTF_8), "text/plain");
                return;
            }
            Path gameDir = Platform.getGameFolder();
            JsonObject response = new JsonObject();
            response.add("pmx", scanFolders(gameDir.resolve("3d-skin/EntityPlayer"), "pmx"));
            response.add("vmd", scanFolders(gameDir.resolve("3d-skin/StageAnim"), "vmd"));
            sendResponse(exchange, 200, response.toString().getBytes(StandardCharsets.UTF_8), "application/json");
        }

        private JsonArray scanFolders(Path root, String zone) {
            JsonArray array = new JsonArray();
            if (!Files.exists(root)) return array;
            try (Stream<Path> stream = Files.list(root)) {
                stream.filter(Files::isDirectory).forEach(p -> {
                    if (hasTargetFile(p, zone)) {
                        JsonObject obj = new JsonObject();
                        obj.addProperty("name", p.getFileName().toString());
                        obj.addProperty("md5", calculateFolderMd5(p));
                        array.add(obj);
                    }
                });
            } catch (IOException ignored) {}
            return array;
        }

        private boolean hasTargetFile(Path dir, String zone) {
            String ext = "vmd".equalsIgnoreCase(zone) ? ".vmd" : ".pmx";
            try (Stream<Path> stream = Files.walk(dir, 1)) {
                return stream.anyMatch(p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(ext));
            } catch (IOException ignored) {}
            return false;
        }
    }

    // 下载处理器 (支持 ZIP 打包和 MD5 缓存)
    static class DownloadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthorized(exchange)) {
                sendResponse(exchange, 403, "403 Forbidden".getBytes(StandardCharsets.UTF_8), "text/plain");
                return;
            }
            String nextToken = ServerAuthManager.consumeIssuedNextToken(exchange);
            if (!nextToken.isEmpty()) {
                exchange.getResponseHeaders().set("X-MMDSync-Next-Token", nextToken);
            }
            String path = URLDecoder.decode(exchange.getRequestURI().getPath(), StandardCharsets.UTF_8);
            String[] parts = path.split("/", 4);
            if (parts.length < 4) {
                sendResponse(exchange, 404, "Folder not found".getBytes(), "text/plain");
                return;
            }

            String zone = parts[2];
            String folderName = parts[3];
            Path baseDir = Platform.getGameFolder().resolve("3d-skin").resolve("vmd".equalsIgnoreCase(zone) ? "StageAnim" : "EntityPlayer");
            Path folderPath = baseDir.resolve(folderName);

            if (!Files.exists(folderPath) || !Files.isDirectory(folderPath)) {
                sendResponse(exchange, 404, "Folder not found".getBytes(), "text/plain");
                return;
            }

            // 计算文件夹 MD5 (基于所有文件的 MD5)
            String etag = calculateFolderMd5(folderPath);
            String ifNoneMatch = exchange.getRequestHeaders().getFirst("If-None-Match");

            if (etag.equals(ifNoneMatch)) {
                exchange.sendResponseHeaders(304, -1);
                exchange.close();
                return;
            }

            exchange.getResponseHeaders().set("ETag", etag);
            exchange.getResponseHeaders().set("Content-Type", "application/zip");
            exchange.sendResponseHeaders(200, 0);

            try (OutputStream os = exchange.getResponseBody();
                 ZipOutputStream zos = new ZipOutputStream(os)) {
                Files.walk(folderPath).forEach(p -> {
                    if (Files.isRegularFile(p)) {
                        try {
                            String name = folderPath.relativize(p).toString().replace("\\", "/");
                            zos.putNextEntry(new ZipEntry(name));

                            byte[] data = Files.readAllBytes(p);
                            String lower = name.toLowerCase(Locale.ROOT);
                            byte[] serverSyncKey = ServerAuthManager.getServerSyncKey();
                            if (serverSyncKey != null && (lower.endsWith(".pmx") || lower.endsWith(".vmd") ||
                                lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".tga"))) {
                                if (!com.tendoarisu.mmdskin.sync.util.CryptoUtils.isEncrypted(data)) {
                                    byte[] encrypted = com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge.aesEncrypt(data, serverSyncKey);
                                    if (encrypted != null) {
                                        byte[] header = "MMDARC".getBytes(StandardCharsets.UTF_8);
                                        byte version = 0x01;
                                        byte[] result = new byte[header.length + 1 + encrypted.length];
                                        System.arraycopy(header, 0, result, 0, header.length);
                                        result[header.length] = version;
                                        System.arraycopy(encrypted, 0, result, header.length + 1, encrypted.length);
                                        data = result;
                                    }
                                }
                            }

                            zos.write(data);
                            zos.closeEntry();
                        } catch (IOException ignored) {}
                    }
                });
            }
        }
    }

    // 上传处理器 (仅供管理员手动上传模型使用，支持 ZIP 自动解压)
    static class UploadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthorized(exchange)) {
                sendResponse(exchange, 403, "403 Forbidden".getBytes(StandardCharsets.UTF_8), "text/plain");
                return;
            }
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed".getBytes(), "text/plain");
                return;
            }

            // 此处简化处理：假设上传的是整个 ZIP 流，并带上模型名称 Header
            String modelName = exchange.getRequestHeaders().getFirst("X-Model-Name");
            if (modelName == null || modelName.isEmpty()) {
                sendResponse(exchange, 400, "Missing X-Model-Name header".getBytes(), "text/plain");
                return;
            }

            Path targetDir = Platform.getGameFolder().resolve("3d-skin/EntityPlayer").resolve(modelName);
            if (!Files.exists(targetDir)) Files.createDirectories(targetDir);

            try (ZipInputStream zis = new ZipInputStream(exchange.getRequestBody())) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    Path filePath = targetDir.resolve(entry.getName()).normalize();
                    if (!filePath.startsWith(targetDir.normalize())) {
                        zis.closeEntry();
                        continue;
                    }
                    if (entry.isDirectory()) {
                        Files.createDirectories(filePath);
                    } else {
                        Files.createDirectories(filePath.getParent());
                        Files.copy(zis, filePath, StandardCopyOption.REPLACE_EXISTING);
                    }
                    zis.closeEntry();
                }
            }

            sendResponse(exchange, 200, "Upload successful".getBytes(), "text/plain");
        }
    }

    private static boolean isAuthorized(HttpExchange exchange) {
        return ServerAuthManager.isAuthorized(exchange);
    }

    private static void sendResponse(HttpExchange exchange, int code, byte[] response, String contentType) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        // 如果开启 GZIP
        if (Config.ENABLE_GZIP && response.length > 1024) {
            exchange.getResponseHeaders().set("Content-Encoding", "gzip");
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzos = new GZIPOutputStream(bos)) {
                gzos.write(response);
            }
            byte[] compressed = bos.toByteArray();
            exchange.sendResponseHeaders(code, compressed.length);
            exchange.getResponseBody().write(compressed);
        } else {
            exchange.sendResponseHeaders(code, response.length);
            exchange.getResponseBody().write(response);
        }
        exchange.close();
    }

    private static String calculateFolderMd5(Path folder) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            Files.walk(folder)
                .filter(Files::isRegularFile)
                .sorted()
                .forEach(p -> {
                    try {
                        long lastModified = Files.getLastModifiedTime(p).toMillis();
                        CacheEntry cache = MD5_CACHE.get(p);
                        if (cache != null && cache.lastModified == lastModified) {
                            digest.update(cache.md5.getBytes());
                        } else {
                            String md5 = calculateFileMd5(p);
                            MD5_CACHE.put(p, new CacheEntry(md5, lastModified));
                            digest.update(md5.getBytes());
                        }
                    } catch (IOException ignored) {}
                });
            byte[] hash = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(System.currentTimeMillis());
        }
    }

    private static String calculateFileMd5(Path file) {
        try (InputStream is = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) > 0) digest.update(buffer, 0, read);
            byte[] hash = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static void loadCache() {
        Path cacheFile = Platform.getConfigFolder().resolve("mmdsync-cache.json");
        if (!Files.exists(cacheFile)) return;
        try (Reader reader = Files.newBufferedReader(cacheFile)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            json.entrySet().forEach(entry -> {
                JsonObject obj = entry.getValue().getAsJsonObject();
                MD5_CACHE.put(Paths.get(entry.getKey()), new CacheEntry(
                    obj.get("md5").getAsString(),
                    obj.get("lastModified").getAsLong()
                ));
            });
        } catch (Exception ignored) {}
    }

    private static void saveCache() {
        Path cacheFile = Platform.getConfigFolder().resolve("mmdsync-cache.json");
        JsonObject json = new JsonObject();
        MD5_CACHE.forEach((path, entry) -> {
            JsonObject obj = new JsonObject();
            obj.addProperty("md5", entry.md5);
            obj.addProperty("lastModified", entry.lastModified);
            json.add(path.toString(), obj);
        });
        try (Writer writer = Files.newBufferedWriter(cacheFile)) {
            writer.write(json.toString());
        } catch (Exception ignored) {}
    }
}
