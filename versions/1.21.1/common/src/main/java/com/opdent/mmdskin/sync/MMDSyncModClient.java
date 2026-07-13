package com.opdent.mmdskin.sync;

import com.tendoarisu.mmdskin.sync.util.MMDSyncRuntimePorts;
import dev.architectury.event.events.client.ClientLifecycleEvent;

import java.util.concurrent.atomic.AtomicBoolean;

public final class MMDSyncModClient {
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();

    private MMDSyncModClient() {
    }

    public static void init() {
        // 入口处立即安装，覆盖初始化期间可能使用到的所有 runtime collaborator。
        MMDSyncRuntimePorts.install();

        // 事件只注册一次；MC-MMD 客户端 bootstrap 会重置 MmdSkinApi，客户端启动后重装同一组实例。
        if (INITIALIZED.compareAndSet(false, true)) {
            ClientLifecycleEvent.CLIENT_STARTED.register(client -> MMDSyncRuntimePorts.install());
        }
    }
}
