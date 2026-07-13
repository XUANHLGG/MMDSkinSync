package com.opdent.mmdskin.sync;

import com.tendoarisu.mmdskin.sync.runtime.HybridNativeRuntime;
import dev.architectury.event.events.client.ClientLifecycleEvent;

import java.util.concurrent.atomic.AtomicBoolean;

public final class MMDSyncModClient {
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();

    private MMDSyncModClient() {
    }

    public static void init() {
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }

        // Install immediately for collaborators used during initialization. MC-MMD's own client
        // bootstrap subsequently resets MmdSkinApi, so reapply the same ports after all client
        // initializers have completed. HybridNativeRuntime.install() is deliberately idempotent.
        HybridNativeRuntime.install();
        ClientLifecycleEvent.CLIENT_STARTED.register(client -> HybridNativeRuntime.install());
    }
}
