package com.tendoarisu.mmdskin.sync;

public final class StagePackRefreshCoordinator {
    private static volatile boolean refreshPending = false;

    private StagePackRefreshCoordinator() {
    }

    public static void requestRefreshWhenSessionReady() {
        refreshPending = true;
    }

    public static boolean shouldRefreshWhenSessionReady() {
        return refreshPending;
    }

    public static void markRefreshed() {
        refreshPending = false;
    }

    public static void reset() {
        refreshPending = false;
    }
}
