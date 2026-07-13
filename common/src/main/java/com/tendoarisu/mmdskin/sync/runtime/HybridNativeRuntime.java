package com.tendoarisu.mmdskin.sync.runtime;

import com.opdent.mmdskin.sync.MMDSyncMod;
import com.shiroha.mmdskin.api.MmdSkinApi;
import com.shiroha.mmdskin.bridge.runtime.NativeModelBridgePorts;
import com.shiroha.mmdskin.bridge.runtime.NativeModelPort;
import com.shiroha.mmdskin.bridge.runtime.NativeModelQueryPort;
import com.shiroha.mmdskin.bridge.runtime.NativeMorphBridgePorts;
import com.shiroha.mmdskin.bridge.runtime.NativeMorphPort;
import com.shiroha.mmdskin.compat.vr.VRBoneDriver;
import com.shiroha.mmdskin.expression.ExpressionApplicationService;
import com.shiroha.mmdskin.expression.ModelMorphCatalog;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Routes the bridge-tagged model handles produced and tracked by MMDSync's native bridge to that
 * bridge, while preserving MC-MMD 1.0.5's default ports for every ordinary model handle.
 *
 * <p>The bridge does not expose layer bone masks/excludes, model memory usage, morph catalog/GPU
 * offset queries, indexed morph weights, or explicit GPU morph synchronization. Those operations
 * therefore use the documented safe fallback for bridge handles: empty/zero/false for reads and a
 * no-op for writes. Ordinary handles are delegated without argument rewriting.</p>
 */
public final class HybridNativeRuntime {
    private static final AtomicBoolean INSTALL_LOGGED = new AtomicBoolean();
    private static final HybridPorts PORTS = new HybridPorts(
            NativeModelBridgePorts.modelPort(),
            NativeModelBridgePorts.queryPort(),
            NativeMorphBridgePorts.morphPort()
    );

    private HybridNativeRuntime() {
    }

    /**
     * Idempotently reapplies one shared hybrid port instance to all 1.0.5 runtime collaborators.
     * Reapplication is intentional because MC-MMD's own client bootstrap configures MmdSkinApi.
     */
    public static void install() {
        MmdSkinApi.configureRuntimeCollaborators(PORTS, PORTS);
        ExpressionApplicationService.configureRuntimeCollaborators(PORTS);
        ModelMorphCatalog.configureRuntimeCollaborators(PORTS);
        VRBoneDriver.configureRuntimeCollaborators(PORTS);
        if (INSTALL_LOGGED.compareAndSet(false, true)) {
            MMDSyncMod.LOGGER.info("已安装 MC-MMD 1.0.5 混合 Native runtime ports");
        }
    }

    private static final class HybridPorts implements NativeModelPort, NativeModelQueryPort, NativeMorphPort {
        private static final int LOCK_STRIPE_COUNT = 64;

        private final NativeModelPort defaultModel;
        private final NativeModelQueryPort defaultQuery;
        private final NativeMorphPort defaultMorph;
        private final Object[] bridgeModelLocks = createLockStripes();

        private HybridPorts(NativeModelPort defaultModel,
                            NativeModelQueryPort defaultQuery,
                            NativeMorphPort defaultMorph) {
            this.defaultModel = defaultModel;
            this.defaultQuery = defaultQuery;
            this.defaultMorph = defaultMorph;
        }

        private static Object[] createLockStripes() {
            Object[] locks = new Object[LOCK_STRIPE_COUNT];
            for (int i = 0; i < locks.length; i++) {
                locks[i] = new Object();
            }
            return locks;
        }

        private Object bridgeModelLock(long handle) {
            return bridgeModelLocks[Long.hashCode(handle) & (LOCK_STRIPE_COUNT - 1)];
        }

        /**
         * The tag is assigned by the bridge when it registers model handles. Classification must
         * not probe MC-MMD's native engine because a tagged value is not one of its handles.
         */
        private boolean isBridgeHandle(long handle) {
            return handle != 0L && MMDSyncNativeBridge.isBridgeHandle(handle);
        }

        private boolean isValidBridgeHandleLocked(long handle, String operation) {
            try {
                return MMDSyncNativeBridge.isModelHandleValid(handle);
            } catch (LinkageError | RuntimeException exception) {
                logBridgeFailure(operation + "/validate", handle, exception);
                return false;
            }
        }

        private <T> T queryBridge(long handle, String operation, T fallback, BridgeQuery<T> query) {
            synchronized (bridgeModelLock(handle)) {
                if (!isValidBridgeHandleLocked(handle, operation)) {
                    return fallback;
                }
                try {
                    return query.get();
                } catch (LinkageError | RuntimeException exception) {
                    logBridgeFailure(operation, handle, exception);
                    return fallback;
                }
            }
        }

        private void mutateBridge(long handle, String operation, BridgeMutation mutation) {
            synchronized (bridgeModelLock(handle)) {
                if (!isValidBridgeHandleLocked(handle, operation)) {
                    return;
                }
                try {
                    mutation.run();
                } catch (LinkageError | RuntimeException exception) {
                    logBridgeFailure(operation, handle, exception);
                }
            }
        }

        private void logBridgeFailure(String operation, long handle, Throwable throwable) {
            MMDSyncMod.LOGGER.debug(
                    "MMDSync bridge 操作失败: operation={}, model={}", operation, handle, throwable);
        }

        @Override
        public boolean setLayerBoneMask(long modelHandle, int layer, String rootBoneName) {
            if (modelHandle == 0L) {
                return false;
            }
            if (!isBridgeHandle(modelHandle)) {
                return defaultModel.setLayerBoneMask(modelHandle, layer, rootBoneName);
            }
            // MMDSyncNativeBridge has no layer bone-mask declaration.
            return false;
        }

        @Override
        public boolean setLayerBoneExclude(long modelHandle, int layer, String rootBoneName) {
            if (modelHandle == 0L) {
                return false;
            }
            if (!isBridgeHandle(modelHandle)) {
                return defaultModel.setLayerBoneExclude(modelHandle, layer, rootBoneName);
            }
            // MMDSyncNativeBridge has no layer bone-exclude declaration.
            return false;
        }

        @Override
        public long getModelMemoryUsage(long modelHandle) {
            if (modelHandle == 0L) {
                return 0L;
            }
            if (!isBridgeHandle(modelHandle)) {
                return defaultModel.getModelMemoryUsage(modelHandle);
            }
            // MMDSyncNativeBridge has no model-memory query declaration.
            return 0L;
        }

        @Override
        public void setFirstPersonMode(long modelHandle, boolean enabled) {
            if (modelHandle == 0L) {
                return;
            }
            if (!isBridgeHandle(modelHandle)) {
                defaultModel.setFirstPersonMode(modelHandle, enabled);
                return;
            }
            mutateBridge(modelHandle, "setFirstPersonMode",
                    () -> MMDSyncNativeBridge.setFirstPersonMode(modelHandle, enabled));
        }

        @Override
        public void getEyeBonePosition(long modelHandle, float[] output) {
            if (modelHandle == 0L) {
                return;
            }
            if (!isBridgeHandle(modelHandle)) {
                defaultModel.getEyeBonePosition(modelHandle, output);
                return;
            }
            if (output == null || output.length < 3) {
                return;
            }
            mutateBridge(modelHandle, "getEyeBonePosition",
                    () -> MMDSyncNativeBridge.getEyeBonePosition(modelHandle, output));
        }

        @Override
        public void applyVrTrackingInput(long modelHandle, float[] trackingData) {
            if (modelHandle == 0L) {
                return;
            }
            if (!isBridgeHandle(modelHandle)) {
                defaultModel.applyVrTrackingInput(modelHandle, trackingData);
                return;
            }
            if (trackingData == null) {
                return;
            }
            mutateBridge(modelHandle, "applyVrTrackingInput",
                    () -> MMDSyncNativeBridge.setVRTrackingData(modelHandle, trackingData));
        }

        @Override
        public void setVrEnabled(long modelHandle, boolean enabled) {
            if (modelHandle == 0L) {
                return;
            }
            if (!isBridgeHandle(modelHandle)) {
                defaultModel.setVrEnabled(modelHandle, enabled);
                return;
            }
            mutateBridge(modelHandle, "setVrEnabled",
                    () -> MMDSyncNativeBridge.setVREnabled(modelHandle, enabled));
        }

        @Override
        public void setVrIkParams(long modelHandle, float armIkStrength) {
            if (modelHandle == 0L) {
                return;
            }
            if (!isBridgeHandle(modelHandle)) {
                defaultModel.setVrIkParams(modelHandle, armIkStrength);
                return;
            }
            if (!Float.isFinite(armIkStrength)) {
                return;
            }
            mutateBridge(modelHandle, "setVrIkParams",
                    () -> MMDSyncNativeBridge.setVRIKParams(modelHandle, armIkStrength));
        }

        @Override
        public int getMaterialCount(long modelHandle) {
            if (modelHandle == 0L) {
                return 0;
            }
            if (!isBridgeHandle(modelHandle)) {
                return defaultQuery.getMaterialCount(modelHandle);
            }
            long count = queryBridge(modelHandle, "getMaterialCount", 0L,
                    () -> MMDSyncNativeBridge.getMaterialCount(modelHandle));
            return count > 0L && count <= Integer.MAX_VALUE ? (int) count : 0;
        }

        @Override
        public void setMaterialVisible(long modelHandle, int materialIndex, boolean visible) {
            if (modelHandle == 0L) {
                return;
            }
            if (!isBridgeHandle(modelHandle)) {
                defaultModel.setMaterialVisible(modelHandle, materialIndex, visible);
                return;
            }
            if (materialIndex < 0) {
                return;
            }
            mutateBridge(modelHandle, "setMaterialVisible", () -> {
                long count = MMDSyncNativeBridge.getMaterialCount(modelHandle);
                if (materialIndex < count) {
                    MMDSyncNativeBridge.setMaterialVisible(modelHandle, materialIndex, visible);
                }
            });
        }

        @Override
        public void setAllMaterialsVisible(long modelHandle, boolean visible) {
            if (modelHandle == 0L) {
                return;
            }
            if (!isBridgeHandle(modelHandle)) {
                defaultModel.setAllMaterialsVisible(modelHandle, visible);
                return;
            }
            mutateBridge(modelHandle, "setAllMaterialsVisible",
                    () -> MMDSyncNativeBridge.setAllMaterialsVisible(modelHandle, visible));
        }

        @Override
        public void deleteModel(long modelHandle) {
            if (modelHandle == 0L) {
                return;
            }
            if (!isBridgeHandle(modelHandle)) {
                defaultModel.deleteModel(modelHandle);
                return;
            }

            synchronized (bridgeModelLock(modelHandle)) {
                try {
                    // Native validity tracking makes repeated release and stale-session handles no-op.
                    if (MMDSyncNativeBridge.isModelHandleValid(modelHandle)) {
                        MMDSyncNativeBridge.deleteModel(modelHandle);
                    }
                } catch (LinkageError | RuntimeException exception) {
                    logBridgeFailure("deleteModel", modelHandle, exception);
                } finally {
                    ModelMorphCatalog.invalidate(modelHandle);
                }
            }
        }

        @Override
        public int getBoneCount(long modelHandle) {
            if (modelHandle == 0L) {
                return 0;
            }
            if (!isBridgeHandle(modelHandle)) {
                return defaultQuery.getBoneCount(modelHandle);
            }
            return Math.max(0, queryBridge(modelHandle, "getBoneCount", 0,
                    () -> MMDSyncNativeBridge.getBoneCount(modelHandle)));
        }

        @Override
        public long getVertexCount(long modelHandle) {
            if (modelHandle == 0L) {
                return 0L;
            }
            if (!isBridgeHandle(modelHandle)) {
                return defaultQuery.getVertexCount(modelHandle);
            }
            return Math.max(0L, queryBridge(modelHandle, "getVertexCount", 0L,
                    () -> MMDSyncNativeBridge.getVertexCount(modelHandle)));
        }

        @Override
        public long getIndexCount(long modelHandle) {
            if (modelHandle == 0L) {
                return 0L;
            }
            if (!isBridgeHandle(modelHandle)) {
                return defaultQuery.getIndexCount(modelHandle);
            }
            return Math.max(0L, queryBridge(modelHandle, "getIndexCount", 0L,
                    () -> MMDSyncNativeBridge.getIndexCount(modelHandle)));
        }

        @Override
        public String getBoneNames(long modelHandle) {
            if (modelHandle == 0L) {
                return "[]";
            }
            if (!isBridgeHandle(modelHandle)) {
                return defaultQuery.getBoneNames(modelHandle);
            }
            String names = queryBridge(modelHandle, "getBoneNames", null,
                    () -> MMDSyncNativeBridge.getBoneNames(modelHandle));
            return names != null ? names : "[]";
        }

        @Override
        public int copyBonePositionsToBuffer(long modelHandle, ByteBuffer targetBuffer) {
            if (modelHandle == 0L) {
                return 0;
            }
            if (!isBridgeHandle(modelHandle)) {
                return defaultQuery.copyBonePositionsToBuffer(modelHandle, targetBuffer);
            }
            if (targetBuffer == null || !targetBuffer.isDirect()) {
                return 0;
            }
            return Math.max(0, queryBridge(modelHandle, "copyBonePositionsToBuffer", 0,
                    () -> MMDSyncNativeBridge.copyBonePositionsToBuffer(modelHandle, targetBuffer)));
        }

        @Override
        public int copyRealtimeUvsToBuffer(long modelHandle, ByteBuffer targetBuffer) {
            if (modelHandle == 0L) {
                return 0;
            }
            if (!isBridgeHandle(modelHandle)) {
                return defaultQuery.copyRealtimeUvsToBuffer(modelHandle, targetBuffer);
            }
            if (targetBuffer == null || !targetBuffer.isDirect()) {
                return 0;
            }
            return Math.max(0, queryBridge(modelHandle, "copyRealtimeUvsToBuffer", 0,
                    () -> MMDSyncNativeBridge.copyRealtimeUVsToBuffer(modelHandle, targetBuffer)));
        }

        @Override
        public int getVertexMorphCount(long modelHandle) {
            if (modelHandle == 0L) {
                return 0;
            }
            if (!isBridgeHandle(modelHandle)) {
                return defaultQuery.getVertexMorphCount(modelHandle);
            }
            // MMDSyncNativeBridge has no vertex-morph count declaration.
            return 0;
        }

        @Override
        public int getUvMorphCount(long modelHandle) {
            if (modelHandle == 0L) {
                return 0;
            }
            if (!isBridgeHandle(modelHandle)) {
                return defaultQuery.getUvMorphCount(modelHandle);
            }
            // MMDSyncNativeBridge has no UV-morph count declaration.
            return 0;
        }

        @Override
        public long getGpuMorphOffsetsSize(long modelHandle) {
            if (modelHandle == 0L) {
                return 0L;
            }
            if (!isBridgeHandle(modelHandle)) {
                return defaultQuery.getGpuMorphOffsetsSize(modelHandle);
            }
            // MMDSyncNativeBridge has no GPU morph-offset size declaration.
            return 0L;
        }

        @Override
        public long getGpuUvMorphOffsetsSize(long modelHandle) {
            if (modelHandle == 0L) {
                return 0L;
            }
            if (!isBridgeHandle(modelHandle)) {
                return defaultQuery.getGpuUvMorphOffsetsSize(modelHandle);
            }
            // MMDSyncNativeBridge has no GPU UV-morph-offset size declaration.
            return 0L;
        }

        @Override
        public int getMorphCount(long modelHandle) {
            if (modelHandle == 0L) {
                return 0;
            }
            if (!isBridgeHandle(modelHandle)) {
                return defaultQuery.getMorphCount(modelHandle);
            }
            // MMDSyncNativeBridge has no morph-count declaration.
            return 0;
        }

        @Override
        public String getMorphName(long modelHandle, int morphIndex) {
            if (modelHandle == 0L) {
                return "";
            }
            if (!isBridgeHandle(modelHandle)) {
                return defaultQuery.getMorphName(modelHandle, morphIndex);
            }
            // MMDSyncNativeBridge has no morph-name declaration.
            return "";
        }

        @Override
        public String getMaterialName(long modelHandle, int materialIndex) {
            if (modelHandle == 0L) {
                return "";
            }
            if (!isBridgeHandle(modelHandle)) {
                return defaultQuery.getMaterialName(modelHandle, materialIndex);
            }
            if (materialIndex < 0) {
                return "";
            }
            String name = queryBridge(modelHandle, "getMaterialName", null, () -> {
                long count = MMDSyncNativeBridge.getMaterialCount(modelHandle);
                return materialIndex < count
                        ? MMDSyncNativeBridge.getMaterialName(modelHandle, materialIndex)
                        : null;
            });
            return name != null ? name : "";
        }

        @Override
        public boolean isMaterialVisible(long modelHandle, int materialIndex) {
            if (modelHandle == 0L) {
                return false;
            }
            if (!isBridgeHandle(modelHandle)) {
                return defaultQuery.isMaterialVisible(modelHandle, materialIndex);
            }
            if (materialIndex < 0) {
                return false;
            }
            return queryBridge(modelHandle, "isMaterialVisible", false, () -> {
                long count = MMDSyncNativeBridge.getMaterialCount(modelHandle);
                return materialIndex < count
                        && MMDSyncNativeBridge.isMaterialVisible(modelHandle, materialIndex);
            });
        }

        @Override
        public void resetAllMorphs(long modelHandle) {
            if (modelHandle == 0L) {
                return;
            }
            if (!isBridgeHandle(modelHandle)) {
                defaultMorph.resetAllMorphs(modelHandle);
                return;
            }
            mutateBridge(modelHandle, "resetAllMorphs",
                    () -> MMDSyncNativeBridge.resetAllMorphs(modelHandle));
        }

        @Override
        public void setMorphWeight(long modelHandle, int morphIndex, float weight) {
            if (modelHandle == 0L) {
                return;
            }
            if (!isBridgeHandle(modelHandle)) {
                defaultMorph.setMorphWeight(modelHandle, morphIndex, weight);
                return;
            }
            // The verified bridge has no indexed morph setter: safe no-op.
        }

        @Override
        public void syncGpuMorphWeights(long modelHandle) {
            if (modelHandle == 0L) {
                return;
            }
            if (!isBridgeHandle(modelHandle)) {
                defaultMorph.syncGpuMorphWeights(modelHandle);
                return;
            }
            // Bridge VPD/reset paths synchronize internally; no explicit primitive exists.
        }

        @Override
        public int applyVpdMorph(long modelHandle, String filePath) {
            if (modelHandle == 0L) {
                return -1;
            }
            if (!isBridgeHandle(modelHandle)) {
                return defaultMorph.applyVpdMorph(modelHandle, filePath);
            }
            if (filePath == null || filePath.isBlank()) {
                return -1;
            }
            return queryBridge(modelHandle, "applyVpdMorph", -1,
                    () -> MMDSyncNativeBridge.applyVpdMorph(modelHandle, filePath));
        }

        @FunctionalInterface
        private interface BridgeQuery<T> {
            T get();
        }

        @FunctionalInterface
        private interface BridgeMutation {
            void run();
        }
    }
}
