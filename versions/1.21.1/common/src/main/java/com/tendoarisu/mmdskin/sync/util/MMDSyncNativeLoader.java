package com.tendoarisu.mmdskin.sync.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public final class MMDSyncNativeLoader {
    private static final Logger logger = LogManager.getLogger();
    private static volatile boolean loaded;

    private static final boolean isAndroid;
    private static final boolean isWindows = System.getProperty("os.name").toLowerCase().contains("windows");
    private static final boolean isMacOS = System.getProperty("os.name").toLowerCase().contains("mac");
    private static final boolean isLinux;
    private static final boolean isArm64;

    static {
        String arch = System.getProperty("os.arch").toLowerCase();
        isArm64 = arch.contains("aarch64") || arch.contains("arm64");

        boolean androidDetected = false;
        String[] launcherEnvKeys = { "FCL_NATIVEDIR", "POJAV_NATIVEDIR", "MOD_ANDROID_RUNTIME" };
        for (String key : launcherEnvKeys) {
            if (System.getenv(key) != null) {
                androidDetected = true;
                break;
            }
        }
        isAndroid = androidDetected;
        isLinux = System.getProperty("os.name").toLowerCase().contains("linux") && !isAndroid;
    }

    public static synchronized void load() {
        if (loaded) {
            return;
        }
        if (isAndroid) {
            loadAndroid();
        } else {
            loadDesktop();
        }
    }

    public static String getPlatformIdentifier() {
        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();
        
        String platform = "";
        if (os.contains("win")) {
            platform = "windows";
        } else if (os.contains("linux")) {
            platform = "linux";
        } else if (os.contains("mac")) {
            platform = "macos";
        }
        
        if (arch.contains("64")) {
            platform += "-x64";
        } else if (arch.contains("arm")) {
            platform += "-arm64";
        }
        
        return platform;
    }

    private static void loadDesktop() {
        String resourcePath;
        String fileName;

        if (isWindows) {
            String archDir = isArm64 ? "windows-arm64" : "windows-x64";
            resourcePath = "/natives/" + archDir + "/mmdsync_bridge.dll";
            fileName = "mmdsync_bridge.dll";
        } else if (isMacOS) {
            String archDir = isArm64 ? "macos-arm64" : "macos-x64";
            resourcePath = "/natives/" + archDir + "/libmmdsync_bridge.dylib";
            fileName = "libmmdsync_bridge.dylib";
        } else if (isLinux) {
            String archDir = isArm64 ? "linux-arm64" : "linux-x64";
            resourcePath = "/natives/" + archDir + "/libmmdsync_bridge.so";
            fileName = "libmmdsync_bridge.so";
        } else {
            throw nativeLoadError("不支持的桌面平台，未选择任何 native 资源", null, null);
        }

        try {
            Path extracted = extractLibrary(resourcePath, fileName);
            System.load(extracted.toAbsolutePath().toString());
            loaded = true;
            logger.info("MMDSync 辅助库加载成功: " + extracted.toAbsolutePath());
        } catch (UnsatisfiedLinkError e) {
            logger.error(e.getMessage(), e);
            throw e;
        } catch (Throwable e) {
            UnsatisfiedLinkError error = nativeLoadError("native 提取或加载失败", resourcePath, e);
            logger.error(error.getMessage(), e);
            throw error;
        }
    }

    private static UnsatisfiedLinkError nativeLoadError(String reason, String resourcePath, Throwable cause) {
        String message = "MMDSync native 不可用: " + reason
                + "; os=" + System.getProperty("os.name")
                + "; arch=" + System.getProperty("os.arch")
                + (resourcePath == null ? "" : "; resource=" + resourcePath);
        UnsatisfiedLinkError error = new UnsatisfiedLinkError(message);
        if (cause != null) {
            error.initCause(cause);
        }
        return error;
    }

    private static void loadAndroid() {
        String resourcePath = "/natives/android-arm64/libmmdsync_bridge.so";
        String fileName = "libmmdsync_bridge.so";
        
        try {
            Path extracted = extractLibrary(resourcePath, fileName);
            System.load(extracted.toAbsolutePath().toString());
            loaded = true;
            logger.info("MMDSync 辅助库 (Android) 加载成功: " + extracted.toAbsolutePath());
        } catch (Throwable e) {
            logger.warn("MMDSync 辅助库 (Android) 提取加载失败，尝试 System.loadLibrary: " + e.getMessage());
            try {
                System.loadLibrary("mmdsync_bridge");
                loaded = true;
            } catch (Throwable e2) {
                logger.error("MMDSync 辅助库 (Android) 彻底加载失败");
            }
        }
    }

    private static Path extractLibrary(String resourcePath, String fileName) throws IOException {
        Path nativeDirectory = resolveNativeDirectory();
        Files.createDirectories(nativeDirectory);
        Path targetFile = nativeDirectory.resolve(fileName);

        try (InputStream input = MMDSyncNativeLoader.class.getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IOException("JAR 中缺少 native 资源: " + resourcePath);
            }
            Files.copy(input, targetFile, StandardCopyOption.REPLACE_EXISTING);
        }
        return targetFile;
    }

    private static Path resolveNativeDirectory() {
        // 不硬引用 Minecraft/Architectury 客户端类：专服验证器仅装载本类时也可安全解析。
        String gameDirectory = System.getProperty("user.dir", ".");
        return Paths.get(gameDirectory).toAbsolutePath().normalize().resolve("mmdsync_natives");
    }
}
