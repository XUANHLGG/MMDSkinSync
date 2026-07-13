package com.opdent.mmdskin.sync.ui;

public final class SyncProgressTracker {
    private static volatile String activeTransferId = "";
    private static volatile boolean active;
    private static volatile boolean failed;
    private static volatile int totalFiles;
    private static volatile int completedFiles;
    private static volatile String statusText = "";
    private static volatile long visibleUntilMs;

    private SyncProgressTracker() {
    }

    public static void begin(String transferId) {
        activeTransferId = transferId == null ? "" : transferId;
        active = true;
        failed = false;
        totalFiles = 0;
        completedFiles = 0;
        statusText = "正在请求 MMD 资源清单...";
        visibleUntilMs = Long.MAX_VALUE;
    }

    public static void onManifest(String transferId, int total) {
        if (!matches(transferId)) {
            return;
        }
        totalFiles = Math.max(total, 0);
        completedFiles = Math.min(completedFiles, totalFiles);
        statusText = totalFiles > 0 ? "正在同步 MMD 模型资源..." : "MMD 资源文件已是最新";
    }

    public static void onFileCompleted(String transferId) {
        if (!matches(transferId)) {
            return;
        }
        completedFiles++;
        if (totalFiles > 0 && completedFiles > totalFiles) {
            completedFiles = totalFiles;
        }
    }

    public static void finish(String transferId, String text) {
        if (!matches(transferId)) {
            return;
        }
        active = false;
        failed = false;
        if (totalFiles > 0) {
            completedFiles = Math.max(completedFiles, totalFiles);
        }
        statusText = text == null || text.isBlank() ? "MMD 资源同步完成" : text;
        visibleUntilMs = System.currentTimeMillis() + 3000L;
    }

    public static void fail(String transferId, String text) {
        if (!matches(transferId)) {
            return;
        }
        active = false;
        failed = true;
        statusText = text == null || text.isBlank() ? "MMD 资源同步失败" : text;
        visibleUntilMs = System.currentTimeMillis() + 5000L;
    }

    public static Snapshot snapshot() {
        boolean visible = active || System.currentTimeMillis() < visibleUntilMs;
        if (!visible) {
            return Snapshot.hidden();
        }
        float progress;
        if (totalFiles <= 0) {
            progress = active ? 0.05F : 1.0F;
        } else {
            progress = Math.min(1.0F, Math.max(0.0F, (float) completedFiles / (float) totalFiles));
        }
        return new Snapshot(true, failed, progress, completedFiles, totalFiles, statusText);
    }

    private static boolean matches(String transferId) {
        return transferId != null && transferId.equals(activeTransferId);
    }

    public record Snapshot(boolean visible, boolean failed, float progress, int completedFiles, int totalFiles, String statusText) {
        private static Snapshot hidden() {
            return new Snapshot(false, false, 0.0F, 0, 0, "");
        }
    }
}
