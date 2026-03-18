package com.tendoarisu.mmdskin.sync;

import dev.architectury.platform.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;


public class Config {
    private static final Logger LOGGER = LoggerFactory.getLogger(Config.class);
    private static final Path CONFIG_FILE = Platform.getConfigFolder().resolve("mmdsync-common.toml");

    // 配置项
    public static String SERVER_SECRET = "";
    public static long RESOURCE_PACKET_WAIT_TIMEOUT_SECONDS = 0L;

    public static void load() {
        if (!Files.exists(CONFIG_FILE)) {
            // 生成初始 Secret 并保存
            SERVER_SECRET = java.util.UUID.randomUUID().toString().replace("-", "");
            save();
            return;
        }

        try {
            List<String> lines = Files.readAllLines(CONFIG_FILE, StandardCharsets.UTF_8);
            boolean foundTimeoutConfig = false;
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("[")) continue;

                if (line.contains("=")) {
                    String[] parts = line.split("=", 2);
                    String key = parts[0].trim();
                    String value = parts[1].trim();

                    // 处理可能存在的引号（如果用户加了也兼容）
                    if (value.startsWith("\"") && value.endsWith("\"")) {
                        value = value.substring(1, value.length() - 1);
                    }

                    switch (key) {
                        case "serverSecret" -> SERVER_SECRET = value;
                        case "resourcePacketWaitTimeoutSeconds" -> {
                            try {
                                RESOURCE_PACKET_WAIT_TIMEOUT_SECONDS = Long.parseLong(value);
                                foundTimeoutConfig = true;
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }
                }
            }
            
            // 如果读取后 Secret 仍为空（旧配置文件升级），则补全
            if (SERVER_SECRET.isEmpty()) {
                SERVER_SECRET = java.util.UUID.randomUUID().toString().replace("-", "");
                save();
            } else if (!foundTimeoutConfig) {
                save();
            }
        } catch (IOException e) {
            LOGGER.error("加载配置文件失败", e);
        }
    }

    public static void save() {
        List<String> lines = new ArrayList<>();
        lines.add("# MMDSync 配置文件");
        lines.add("");
        lines.add("[general]");
        lines.add("# 服务器唯一私密盐 (用于加固加密流程，请勿公开)");
        lines.add("serverSecret = " + SERVER_SECRET);
        lines.add("");
        lines.add("# 资源分块等待超时(秒)");
        lines.add("# 0 或负数 = 无限等待直到文件传完");
        lines.add("# 正数 = 单个文件在该时间内未传完则视为失败");
        lines.add("resourcePacketWaitTimeoutSeconds = " + RESOURCE_PACKET_WAIT_TIMEOUT_SECONDS);

        try {
            Files.write(CONFIG_FILE, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("保存配置文件失败", e);
        }
    }
}
