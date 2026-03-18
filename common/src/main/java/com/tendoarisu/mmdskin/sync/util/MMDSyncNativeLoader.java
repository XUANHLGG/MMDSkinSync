package com.tendoarisu.mmdskin.sync.util;

import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
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
            return;
        }

        try {
            File extracted = extractLibrary(resourcePath, fileName);
            if (extracted != null) {
                System.load(extracted.getAbsolutePath());
                loaded = true;
                logger.info("MMDSync 辅助库加载成功: " + extracted.getAbsolutePath());
            }
        } catch (Throwable e) {
            logger.error("MMDSync 辅助库加载失败: " + e.getMessage());
        }
    }

    private static void loadAndroid() {
        String resourcePath = "/natives/android-arm64/libmmdsync_bridge.so";
        String fileName = "libmmdsync_bridge.so";
        
        try {
            File extracted = extractLibrary(resourcePath, fileName);
            if (extracted != null) {
                System.load(extracted.getAbsolutePath());
                loaded = true;
                logger.info("MMDSync 辅助库 (Android) 加载成功: " + extracted.getAbsolutePath());
            }
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

    private static File extractLibrary(String resourcePath, String fileName) {
        try {
            File tempDir = new File(Minecraft.getInstance().gameDirectory, "mmdsync_natives");
            if (!tempDir.exists()) tempDir.mkdirs();
            
            File targetFile = new File(tempDir, fileName);
            
            try (InputStream is = MMDSyncNativeLoader.class.getResourceAsStream(resourcePath)) {
                if (is != null) {
                    Files.copy(is, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    return targetFile;
                }
            }
        } catch (Exception e) {
            logger.error("提取 MMDSync 辅助库失败: " + e.getMessage());
        }
        return null;
    }
}
