package com.tendoarisu.mmdskin.sync.util;

import com.opdent.mmdskin.sync.MMDSyncMod;
import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.api.MmdSkinApi;
import com.shiroha.mmdskin.bridge.runtime.NativeModelBridgePorts;
import com.shiroha.mmdskin.bridge.runtime.NativeModelPort;
import com.shiroha.mmdskin.bridge.runtime.NativeModelQueryPort;
import com.shiroha.mmdskin.bridge.runtime.NativeMorphBridgePorts;
import com.shiroha.mmdskin.bridge.runtime.NativeMorphPort;
import com.shiroha.mmdskin.compat.vr.VRBoneDriver;
import com.shiroha.mmdskin.expression.ExpressionApplicationService;
import com.shiroha.mmdskin.expression.ModelMorphCatalog;
import com.shiroha.mmdskin.player.runtime.FirstPersonManager;
import com.shiroha.mmdskin.renderer.runtime.bridge.ModelRuntimeBridge;
import com.shiroha.mmdskin.renderer.runtime.bridge.ModelRuntimeBridgeHolder;
import net.minecraft.world.entity.player.Player;

import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 将 MC-MMD-rust 1.0.5 的中心化运行时端口路由到 MMDSync bridge。
 * 普通非零句柄完整委托安装时捕获的上游实现；第 62 位带标签的句柄只访问 MMDSync bridge。
 */
public final class MMDSyncRuntimePorts {
    private static final AtomicBoolean INSTALL_LOGGED = new AtomicBoolean();
    private static final BridgeRuntime BRIDGE_RUNTIME = new BridgeRuntime();

    private static final NativeModelPort UPSTREAM_MODEL_PORT = NativeModelBridgePorts.modelPort();
    private static final NativeModelQueryPort UPSTREAM_QUERY_PORT = NativeModelBridgePorts.queryPort();
    private static final NativeMorphPort UPSTREAM_MORPH_PORT = NativeMorphBridgePorts.morphPort();
    private static final ModelRuntimeBridge UPSTREAM_RUNTIME_BRIDGE = ModelRuntimeBridgeHolder.get();

    private static final NativeModelPort ROUTED_MODEL_PORT = new RoutingModelPort(UPSTREAM_MODEL_PORT);
    private static final NativeModelQueryPort ROUTED_QUERY_PORT = new RoutingModelQueryPort(UPSTREAM_QUERY_PORT);
    private static final NativeMorphPort ROUTED_MORPH_PORT = new RoutingMorphPort(UPSTREAM_MORPH_PORT);
    private static final ModelRuntimeBridge ROUTED_RUNTIME_BRIDGE =
            new RoutingModelRuntimeBridge(UPSTREAM_RUNTIME_BRIDGE);

    private MMDSyncRuntimePorts() {
    }

    /**
     * 幂等地把同一组路由实例重新应用到所有已知 collaborator。
     * MC-MMD 客户端 bootstrap 会重新配置部分 collaborator，因此这里不能只安装一次。
     */
    public static synchronized void install() {
        ModelRuntimeBridgeHolder.set(ROUTED_RUNTIME_BRIDGE);
        MmdSkinApi.configureRuntimeCollaborators(ROUTED_MODEL_PORT, ROUTED_QUERY_PORT);
        ModelMorphCatalog.configureRuntimeCollaborators(ROUTED_QUERY_PORT);
        ExpressionApplicationService.configureRuntimeCollaborators(ROUTED_MORPH_PORT);
        VRBoneDriver.configureRuntimeCollaborators(ROUTED_MODEL_PORT);

        if (INSTALL_LOGGED.compareAndSet(false, true)) {
            MMDSyncMod.LOGGER.info("已安装 MC-MMD-rust 1.0.5 中心化句柄路由");
        }
    }

    public static NativeModelPort modelPort() {
        install();
        return ROUTED_MODEL_PORT;
    }

    public static NativeModelQueryPort queryPort() {
        install();
        return ROUTED_QUERY_PORT;
    }

    public static NativeMorphPort morphPort() {
        install();
        return ROUTED_MORPH_PORT;
    }

    /**
     * 与 bridge 模型访问共用会话读写锁，避免清理密钥/句柄时发生 Java 层 TOCTOU。
     */
    public static boolean clearSessionMaterial() {
        return BRIDGE_RUNTIME.clearSessionMaterial();
    }

    private static boolean bridge(long handle) {
        return BridgeRuntime.isBridgeHandle(handle);
    }

    private static final class RoutingModelPort implements NativeModelPort {
        private final NativeModelPort upstream;

        private RoutingModelPort(NativeModelPort upstream) {
            this.upstream = upstream;
        }

        @Override
        public boolean setLayerBoneMask(long modelHandle, int layer, String rootBoneName) {
            if (modelHandle == 0L) {
                return false;
            }
            if (!bridge(modelHandle)) {
                return upstream.setLayerBoneMask(modelHandle, layer, rootBoneName);
            }
            if (layer < 0 || rootBoneName == null || rootBoneName.isBlank()) {
                return false;
            }
            return BRIDGE_RUNTIME.query(modelHandle, "setLayerBoneMask", false,
                    () -> MMDSyncNativeBridge.setLayerBoneMask(modelHandle, layer, rootBoneName));
        }

        @Override
        public boolean setLayerBoneExclude(long modelHandle, int layer, String rootBoneName) {
            if (modelHandle == 0L) {
                return false;
            }
            if (!bridge(modelHandle)) {
                return upstream.setLayerBoneExclude(modelHandle, layer, rootBoneName);
            }
            if (layer < 0 || rootBoneName == null || rootBoneName.isBlank()) {
                return false;
            }
            return BRIDGE_RUNTIME.query(modelHandle, "setLayerBoneExclude", false,
                    () -> MMDSyncNativeBridge.setLayerBoneExclude(modelHandle, layer, rootBoneName));
        }

        @Override
        public long getModelMemoryUsage(long modelHandle) {
            if (modelHandle == 0L) {
                return 0L;
            }
            if (!bridge(modelHandle)) {
                return upstream.getModelMemoryUsage(modelHandle);
            }
            return nonNegative(BRIDGE_RUNTIME.query(modelHandle, "getModelMemoryUsage", 0L,
                    () -> MMDSyncNativeBridge.getModelMemoryUsage(modelHandle)));
        }

        @Override
        public void setFirstPersonMode(long modelHandle, boolean enabled) {
            if (modelHandle == 0L) {
                return;
            }
            if (!bridge(modelHandle)) {
                upstream.setFirstPersonMode(modelHandle, enabled);
                return;
            }
            BRIDGE_RUNTIME.mutate(modelHandle, "setFirstPersonMode",
                    () -> MMDSyncNativeBridge.setFirstPersonMode(modelHandle, enabled));
        }

        @Override
        public void getEyeBonePosition(long modelHandle, float[] output) {
            if (modelHandle == 0L) {
                clearVector3(output);
                return;
            }
            if (!bridge(modelHandle)) {
                upstream.getEyeBonePosition(modelHandle, output);
                return;
            }
            if (output == null || output.length < 3) {
                return;
            }
            clearVector3(output);
            boolean completed = BRIDGE_RUNTIME.query(modelHandle, "getEyeBonePosition", false, () -> {
                MMDSyncNativeBridge.getEyeBonePosition(modelHandle, output);
                return true;
            });
            if (!completed || !Float.isFinite(output[0]) || !Float.isFinite(output[1]) || !Float.isFinite(output[2])) {
                clearVector3(output);
            }
        }

        @Override
        public void applyVrTrackingInput(long modelHandle, float[] trackingData) {
            if (modelHandle == 0L) {
                return;
            }
            if (!bridge(modelHandle)) {
                upstream.applyVrTrackingInput(modelHandle, trackingData);
                return;
            }
            if (!validFiniteArray(trackingData, BridgeRuntime.VR_TRACKING_FLOAT_COUNT)) {
                return;
            }
            BRIDGE_RUNTIME.mutate(modelHandle, "applyVrTrackingInput",
                    () -> MMDSyncNativeBridge.setVRTrackingData(modelHandle, trackingData));
        }

        @Override
        public void setVrEnabled(long modelHandle, boolean enabled) {
            if (modelHandle == 0L) {
                return;
            }
            if (!bridge(modelHandle)) {
                upstream.setVrEnabled(modelHandle, enabled);
                return;
            }
            BRIDGE_RUNTIME.mutate(modelHandle, "setVrEnabled",
                    () -> MMDSyncNativeBridge.setVREnabled(modelHandle, enabled));
        }

        @Override
        public void setVrIkParams(long modelHandle, float armIkStrength) {
            if (modelHandle == 0L) {
                return;
            }
            if (!bridge(modelHandle)) {
                upstream.setVrIkParams(modelHandle, armIkStrength);
                return;
            }
            if (!Float.isFinite(armIkStrength)) {
                return;
            }
            BRIDGE_RUNTIME.mutate(modelHandle, "setVrIkParams",
                    () -> MMDSyncNativeBridge.setVRIKParams(modelHandle, armIkStrength));
        }

        @Override
        public int getMaterialCount(long modelHandle) {
            if (modelHandle == 0L) {
                return 0;
            }
            if (!bridge(modelHandle)) {
                return upstream.getMaterialCount(modelHandle);
            }
            return BRIDGE_RUNTIME.materialCount(modelHandle);
        }

        @Override
        public void setMaterialVisible(long modelHandle, int materialIndex, boolean visible) {
            if (modelHandle == 0L) {
                return;
            }
            if (!bridge(modelHandle)) {
                upstream.setMaterialVisible(modelHandle, materialIndex, visible);
                return;
            }
            if (materialIndex < 0) {
                return;
            }
            BRIDGE_RUNTIME.mutate(modelHandle, "setMaterialVisible", () -> {
                long count = MMDSyncNativeBridge.getMaterialCount(modelHandle);
                if (count > 0L && materialIndex < count) {
                    MMDSyncNativeBridge.setMaterialVisible(modelHandle, materialIndex, visible);
                }
            });
        }

        @Override
        public void setAllMaterialsVisible(long modelHandle, boolean visible) {
            if (modelHandle == 0L) {
                return;
            }
            if (!bridge(modelHandle)) {
                upstream.setAllMaterialsVisible(modelHandle, visible);
                return;
            }
            BRIDGE_RUNTIME.mutate(modelHandle, "setAllMaterialsVisible",
                    () -> MMDSyncNativeBridge.setAllMaterialsVisible(modelHandle, visible));
        }

        @Override
        public void deleteModel(long modelHandle) {
            if (modelHandle == 0L) {
                return;
            }
            if (!bridge(modelHandle)) {
                upstream.deleteModel(modelHandle);
                return;
            }
            BRIDGE_RUNTIME.deleteModel(modelHandle);
        }
    }

    private static final class RoutingModelQueryPort implements NativeModelQueryPort {
        private static final int FLOAT_BYTES = Float.BYTES;
        private static final int BONE_POSITION_COMPONENTS = 3;
        private static final int UV_COMPONENTS = 2;

        private final NativeModelQueryPort upstream;

        private RoutingModelQueryPort(NativeModelQueryPort upstream) {
            this.upstream = upstream;
        }

        @Override
        public int getMaterialCount(long modelHandle) {
            if (modelHandle == 0L) {
                return 0;
            }
            if (!bridge(modelHandle)) {
                return upstream.getMaterialCount(modelHandle);
            }
            return BRIDGE_RUNTIME.materialCount(modelHandle);
        }

        @Override
        public int getBoneCount(long modelHandle) {
            if (modelHandle == 0L) {
                return 0;
            }
            if (!bridge(modelHandle)) {
                return upstream.getBoneCount(modelHandle);
            }
            return Math.max(0, BRIDGE_RUNTIME.query(modelHandle, "getBoneCount", 0,
                    () -> MMDSyncNativeBridge.getBoneCount(modelHandle)));
        }

        @Override
        public long getVertexCount(long modelHandle) {
            if (modelHandle == 0L) {
                return 0L;
            }
            if (!bridge(modelHandle)) {
                return upstream.getVertexCount(modelHandle);
            }
            return nonNegative(BRIDGE_RUNTIME.query(modelHandle, "getVertexCount", 0L,
                    () -> MMDSyncNativeBridge.getVertexCount(modelHandle)));
        }

        @Override
        public long getIndexCount(long modelHandle) {
            if (modelHandle == 0L) {
                return 0L;
            }
            if (!bridge(modelHandle)) {
                return upstream.getIndexCount(modelHandle);
            }
            return nonNegative(BRIDGE_RUNTIME.query(modelHandle, "getIndexCount", 0L,
                    () -> MMDSyncNativeBridge.getIndexCount(modelHandle)));
        }

        @Override
        public String getBoneNames(long modelHandle) {
            if (modelHandle == 0L) {
                return "[]";
            }
            if (!bridge(modelHandle)) {
                return upstream.getBoneNames(modelHandle);
            }
            String names = BRIDGE_RUNTIME.query(modelHandle, "getBoneNames", null,
                    () -> MMDSyncNativeBridge.getBoneNames(modelHandle));
            return names != null ? names : "[]";
        }

        @Override
        public int copyBonePositionsToBuffer(long modelHandle, ByteBuffer targetBuffer) {
            if (modelHandle == 0L) {
                return 0;
            }
            if (!bridge(modelHandle)) {
                return upstream.copyBonePositionsToBuffer(modelHandle, targetBuffer);
            }
            if (!writableDirectBuffer(targetBuffer)) {
                return 0;
            }
            return BRIDGE_RUNTIME.query(modelHandle, "copyBonePositionsToBuffer", 0, () -> {
                int count = Math.max(0, MMDSyncNativeBridge.getBoneCount(modelHandle));
                if (!hasBufferSpace(targetBuffer, count, BONE_POSITION_COMPONENTS)) {
                    return 0;
                }
                return clampCopiedCount(MMDSyncNativeBridge.copyBonePositionsToBuffer(modelHandle, targetBuffer), count);
            });
        }

        @Override
        public int copyRealtimeUvsToBuffer(long modelHandle, ByteBuffer targetBuffer) {
            if (modelHandle == 0L) {
                return 0;
            }
            if (!bridge(modelHandle)) {
                return upstream.copyRealtimeUvsToBuffer(modelHandle, targetBuffer);
            }
            if (!writableDirectBuffer(targetBuffer)) {
                return 0;
            }
            return BRIDGE_RUNTIME.query(modelHandle, "copyRealtimeUvsToBuffer", 0, () -> {
                long vertexCount = MMDSyncNativeBridge.getVertexCount(modelHandle);
                if (vertexCount <= 0L || vertexCount > Integer.MAX_VALUE) {
                    return 0;
                }
                int count = (int) vertexCount;
                if (!hasBufferSpace(targetBuffer, count, UV_COMPONENTS)) {
                    return 0;
                }
                return clampCopiedCount(MMDSyncNativeBridge.copyRealtimeUVsToBuffer(modelHandle, targetBuffer), count);
            });
        }

        @Override
        public int getVertexMorphCount(long modelHandle) {
            if (modelHandle == 0L) {
                return 0;
            }
            if (!bridge(modelHandle)) {
                return upstream.getVertexMorphCount(modelHandle);
            }
            // 90-JNI bridge 暴露完整 morph 目录与权重操作，但未暴露 GPU offset 分类计数。
            return 0;
        }

        @Override
        public int getUvMorphCount(long modelHandle) {
            if (modelHandle == 0L) {
                return 0;
            }
            if (!bridge(modelHandle)) {
                return upstream.getUvMorphCount(modelHandle);
            }
            return 0;
        }

        @Override
        public long getGpuMorphOffsetsSize(long modelHandle) {
            if (modelHandle == 0L) {
                return 0L;
            }
            if (!bridge(modelHandle)) {
                return upstream.getGpuMorphOffsetsSize(modelHandle);
            }
            return 0L;
        }

        @Override
        public long getGpuUvMorphOffsetsSize(long modelHandle) {
            if (modelHandle == 0L) {
                return 0L;
            }
            if (!bridge(modelHandle)) {
                return upstream.getGpuUvMorphOffsetsSize(modelHandle);
            }
            return 0L;
        }

        @Override
        public int getMorphCount(long modelHandle) {
            if (modelHandle == 0L) {
                return 0;
            }
            if (!bridge(modelHandle)) {
                return upstream.getMorphCount(modelHandle);
            }
            return Math.max(0, BRIDGE_RUNTIME.query(modelHandle, "getMorphCount", 0,
                    () -> MMDSyncNativeBridge.getMorphCount(modelHandle)));
        }

        @Override
        public String getMorphName(long modelHandle, int morphIndex) {
            if (modelHandle == 0L) {
                return "";
            }
            if (!bridge(modelHandle)) {
                return upstream.getMorphName(modelHandle, morphIndex);
            }
            if (morphIndex < 0) {
                return "";
            }
            String name = BRIDGE_RUNTIME.query(modelHandle, "getMorphName", null, () -> {
                int count = MMDSyncNativeBridge.getMorphCount(modelHandle);
                return morphIndex < count ? MMDSyncNativeBridge.getMorphName(modelHandle, morphIndex) : null;
            });
            return name != null ? name : "";
        }

        @Override
        public String getMaterialName(long modelHandle, int materialIndex) {
            if (modelHandle == 0L) {
                return "";
            }
            if (!bridge(modelHandle)) {
                return upstream.getMaterialName(modelHandle, materialIndex);
            }
            if (materialIndex < 0) {
                return "";
            }
            String name = BRIDGE_RUNTIME.query(modelHandle, "getMaterialName", null, () -> {
                long count = MMDSyncNativeBridge.getMaterialCount(modelHandle);
                return materialIndex < count ? MMDSyncNativeBridge.getMaterialName(modelHandle, materialIndex) : null;
            });
            return name != null ? name : "";
        }

        @Override
        public boolean isMaterialVisible(long modelHandle, int materialIndex) {
            if (modelHandle == 0L) {
                return false;
            }
            if (!bridge(modelHandle)) {
                return upstream.isMaterialVisible(modelHandle, materialIndex);
            }
            if (materialIndex < 0) {
                return false;
            }
            return BRIDGE_RUNTIME.query(modelHandle, "isMaterialVisible", false, () -> {
                long count = MMDSyncNativeBridge.getMaterialCount(modelHandle);
                return materialIndex < count && MMDSyncNativeBridge.isMaterialVisible(modelHandle, materialIndex);
            });
        }

        private static boolean writableDirectBuffer(ByteBuffer buffer) {
            return buffer != null && buffer.isDirect() && !buffer.isReadOnly() && buffer.position() == 0;
        }

        private static boolean hasBufferSpace(ByteBuffer buffer, int count, int componentCount) {
            if (count <= 0) {
                return false;
            }
            long required = (long) count * componentCount * FLOAT_BYTES;
            return required <= Integer.MAX_VALUE && buffer.remaining() >= required;
        }

        private static int clampCopiedCount(int copied, int expectedMaximum) {
            return copied > 0 && copied <= expectedMaximum ? copied : 0;
        }
    }

    private static final class RoutingMorphPort implements NativeMorphPort {
        private final NativeMorphPort upstream;

        private RoutingMorphPort(NativeMorphPort upstream) {
            this.upstream = upstream;
        }

        @Override
        public void resetAllMorphs(long modelHandle) {
            if (modelHandle == 0L) {
                return;
            }
            if (!bridge(modelHandle)) {
                upstream.resetAllMorphs(modelHandle);
                return;
            }
            BRIDGE_RUNTIME.mutate(modelHandle, "resetAllMorphs",
                    () -> MMDSyncNativeBridge.resetAllMorphs(modelHandle));
        }

        @Override
        public void setMorphWeight(long modelHandle, int morphIndex, float weight) {
            if (modelHandle == 0L) {
                return;
            }
            if (!bridge(modelHandle)) {
                upstream.setMorphWeight(modelHandle, morphIndex, weight);
                return;
            }
            if (morphIndex < 0 || !Float.isFinite(weight)) {
                return;
            }
            BRIDGE_RUNTIME.mutate(modelHandle, "setMorphWeight", () -> {
                int count = MMDSyncNativeBridge.getMorphCount(modelHandle);
                if (morphIndex < count) {
                    MMDSyncNativeBridge.setMorphWeight(modelHandle, morphIndex, weight);
                }
            });
        }

        @Override
        public void syncGpuMorphWeights(long modelHandle) {
            if (modelHandle == 0L) {
                return;
            }
            if (!bridge(modelHandle)) {
                upstream.syncGpuMorphWeights(modelHandle);
                return;
            }
            BRIDGE_RUNTIME.mutate(modelHandle, "syncGpuMorphWeights",
                    () -> MMDSyncNativeBridge.syncGpuMorphWeights(modelHandle));
        }

        @Override
        public int applyVpdMorph(long modelHandle, String filePath) {
            if (modelHandle == 0L) {
                return -2;
            }
            if (!bridge(modelHandle)) {
                return upstream.applyVpdMorph(modelHandle, filePath);
            }
            if (filePath == null || filePath.isBlank()) {
                return -2;
            }
            return BRIDGE_RUNTIME.query(modelHandle, "applyVpdMorph", -2,
                    () -> MMDSyncNativeBridge.applyVpdMorph(modelHandle, filePath));
        }
    }

    private static final class RoutingModelRuntimeBridge implements ModelRuntimeBridge {
        private final ModelRuntimeBridge upstream;

        private RoutingModelRuntimeBridge(ModelRuntimeBridge upstream) {
            this.upstream = upstream;
        }

        @Override
        public boolean setLayerBoneMask(long modelHandle, int layer, String rootBoneName) {
            if (modelHandle == 0L) {
                return false;
            }
            if (!bridge(modelHandle)) {
                return upstream.setLayerBoneMask(modelHandle, layer, rootBoneName);
            }
            return ROUTED_MODEL_PORT.setLayerBoneMask(modelHandle, layer, rootBoneName);
        }

        @Override
        public boolean setLayerBoneExclude(long modelHandle, int layer, String rootBoneName) {
            if (modelHandle == 0L) {
                return false;
            }
            if (!bridge(modelHandle)) {
                return upstream.setLayerBoneExclude(modelHandle, layer, rootBoneName);
            }
            return ROUTED_MODEL_PORT.setLayerBoneExclude(modelHandle, layer, rootBoneName);
        }

        @Override
        public long getModelMemoryUsage(long modelHandle) {
            if (modelHandle == 0L) {
                return 0L;
            }
            if (!bridge(modelHandle)) {
                return upstream.getModelMemoryUsage(modelHandle);
            }
            return ROUTED_MODEL_PORT.getModelMemoryUsage(modelHandle);
        }

        @Override
        public void populateHandMatrix(long modelHandle, long handMatrixHandle, boolean mainHand) {
            if (modelHandle == 0L) {
                return;
            }
            if (!bridge(modelHandle)) {
                upstream.populateHandMatrix(modelHandle, handMatrixHandle, mainHand);
            }
            // 90-JNI bridge 不创建上游矩阵句柄，bridge/stale 模型不能回落 mmd_engine。
        }

        @Override
        public boolean copyMatrixToBuffer(long matrixHandle, ByteBuffer targetBuffer) {
            if (matrixHandle == 0L || bridge(matrixHandle)) {
                return false;
            }
            return upstream.copyMatrixToBuffer(matrixHandle, targetBuffer);
        }

        @Override
        public void preRenderFirstPerson(long modelHandle, float combinedScale, boolean isLocalPlayer) {
            if (modelHandle == 0L) {
                return;
            }
            if (!bridge(modelHandle)) {
                upstream.preRenderFirstPerson(modelHandle, combinedScale, isLocalPlayer);
                return;
            }
            if (!Float.isFinite(combinedScale)) {
                return;
            }
            BRIDGE_RUNTIME.mutate(modelHandle, "preRenderFirstPerson", () -> FirstPersonManager.preRender(
                    NativeFunc.GetInst(), modelHandle, combinedScale, isLocalPlayer));
        }

        @Override
        public void postRenderFirstPerson(long modelHandle, Player player, float tickDelta) {
            if (modelHandle == 0L) {
                return;
            }
            if (!bridge(modelHandle)) {
                upstream.postRenderFirstPerson(modelHandle, player, tickDelta);
                return;
            }
            if (!Float.isFinite(tickDelta)) {
                return;
            }
            BRIDGE_RUNTIME.mutate(modelHandle, "postRenderFirstPerson",
                    () -> FirstPersonManager.postRender(NativeFunc.GetInst(), modelHandle, player, tickDelta));
        }

        @Override
        public boolean isAndroid() {
            return upstream.isAndroid();
        }

        @Override
        public int getMaterialCount(long modelHandle) {
            if (modelHandle == 0L) {
                return 0;
            }
            if (!bridge(modelHandle)) {
                return upstream.getMaterialCount(modelHandle);
            }
            return ROUTED_MODEL_PORT.getMaterialCount(modelHandle);
        }

        @Override
        public void setMaterialVisible(long modelHandle, int materialIndex, boolean visible) {
            if (modelHandle == 0L) {
                return;
            }
            if (!bridge(modelHandle)) {
                upstream.setMaterialVisible(modelHandle, materialIndex, visible);
                return;
            }
            ROUTED_MODEL_PORT.setMaterialVisible(modelHandle, materialIndex, visible);
        }

        @Override
        public void deleteModel(long modelHandle) {
            if (modelHandle == 0L) {
                return;
            }
            if (!bridge(modelHandle)) {
                upstream.deleteModel(modelHandle);
                return;
            }
            BRIDGE_RUNTIME.deleteModel(modelHandle);
        }
    }

    private static final class BridgeRuntime {
        private static final long BRIDGE_HANDLE_MASK = 1L << 62;
        private static final int LOCK_STRIPE_COUNT = 64;
        private static final int VR_TRACKING_FLOAT_COUNT = 21;

        private final Object[] modelLocks = createLockStripes();
        private final ReentrantReadWriteLock sessionLock = new ReentrantReadWriteLock();
        private final Set<Long> knownModelHandles = ConcurrentHashMap.newKeySet();

        private static boolean isBridgeHandle(long handle) {
            return handle != 0L && (handle & BRIDGE_HANDLE_MASK) != 0L;
        }

        private static Object[] createLockStripes() {
            Object[] locks = new Object[LOCK_STRIPE_COUNT];
            for (int index = 0; index < locks.length; index++) {
                locks[index] = new Object();
            }
            return locks;
        }

        private Object modelLock(long handle) {
            return modelLocks[Long.hashCode(handle) & (LOCK_STRIPE_COUNT - 1)];
        }

        private int materialCount(long handle) {
            long count = query(handle, "getMaterialCount", 0L,
                    () -> MMDSyncNativeBridge.getMaterialCount(handle));
            return safeInt(count);
        }

        private <T> T query(long handle, String operation, T fallback, BridgeQuery<T> query) {
            sessionLock.readLock().lock();
            try {
                synchronized (modelLock(handle)) {
                    knownModelHandles.add(handle);
                    if (!validModelLocked(handle, operation)) {
                        return fallback;
                    }
                    try {
                        return query.get();
                    } catch (LinkageError | RuntimeException exception) {
                        logFailure(operation, handle, exception);
                        return fallback;
                    }
                }
            } finally {
                sessionLock.readLock().unlock();
            }
        }

        private void mutate(long handle, String operation, BridgeMutation mutation) {
            sessionLock.readLock().lock();
            try {
                synchronized (modelLock(handle)) {
                    knownModelHandles.add(handle);
                    if (!validModelLocked(handle, operation)) {
                        return;
                    }
                    try {
                        mutation.run();
                    } catch (LinkageError | RuntimeException exception) {
                        logFailure(operation, handle, exception);
                    }
                }
            } finally {
                sessionLock.readLock().unlock();
            }
        }

        private void deleteModel(long handle) {
            sessionLock.readLock().lock();
            try {
                synchronized (modelLock(handle)) {
                    knownModelHandles.add(handle);
                    boolean releaseCompleted = false;
                    try {
                        if (!MMDSyncNativeBridge.isModelHandleValid(handle)) {
                            releaseCompleted = true;
                            return;
                        }
                        MMDSyncNativeBridge.deleteModel(handle);
                        releaseCompleted = true;
                    } catch (LinkageError | RuntimeException exception) {
                        logFailure("deleteModel", handle, exception);
                    } finally {
                        invalidateMorphCatalog(handle);
                        if (releaseCompleted) {
                            knownModelHandles.remove(handle);
                        }
                    }
                }
            } finally {
                sessionLock.readLock().unlock();
            }
        }

        private boolean clearSessionMaterial() {
            sessionLock.writeLock().lock();
            boolean callCompleted = false;
            try {
                boolean cleared = MMDSyncNativeBridge.clearSessionMaterial();
                callCompleted = true;
                return cleared;
            } catch (LinkageError | RuntimeException exception) {
                logFailure("clearSessionMaterial", 0L, exception);
                return false;
            } finally {
                for (Long handle : knownModelHandles) {
                    invalidateMorphCatalog(handle);
                }
                if (callCompleted) {
                    knownModelHandles.clear();
                }
                sessionLock.writeLock().unlock();
            }
        }

        private boolean validModelLocked(long handle, String operation) {
            try {
                return MMDSyncNativeBridge.isModelHandleValid(handle);
            } catch (LinkageError | RuntimeException exception) {
                logFailure(operation + "/validate", handle, exception);
                return false;
            }
        }

        private void invalidateMorphCatalog(long handle) {
            try {
                ModelMorphCatalog.invalidate(handle);
            } catch (RuntimeException exception) {
                logFailure("invalidateMorphCatalog", handle, exception);
            }
        }

        private void logFailure(String operation, long handle, Throwable throwable) {
            MMDSyncMod.LOGGER.debug(
                    "MMDSync bridge 操作失败: operation={}, model={}", operation, handle, throwable);
        }
    }

    private static boolean validFiniteArray(float[] values, int requiredLength) {
        if (values == null || values.length < requiredLength) {
            return false;
        }
        for (int index = 0; index < requiredLength; index++) {
            if (!Float.isFinite(values[index])) {
                return false;
            }
        }
        return true;
    }

    private static void clearVector3(float[] output) {
        if (output != null && output.length >= 3) {
            output[0] = 0.0F;
            output[1] = 0.0F;
            output[2] = 0.0F;
        }
    }

    private static int safeInt(long value) {
        return value > 0L && value <= Integer.MAX_VALUE ? (int) value : 0;
    }

    private static long nonNegative(long value) {
        return Math.max(0L, value);
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
