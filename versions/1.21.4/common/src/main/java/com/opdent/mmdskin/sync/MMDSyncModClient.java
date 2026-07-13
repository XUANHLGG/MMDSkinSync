package com.opdent.mmdskin.sync;

import com.tendoarisu.mmdskin.sync.runtime.HybridNativeRuntime;
import dev.architectury.event.events.client.ClientLifecycleEvent;

import java.util.concurrent.atomic.AtomicBoolean;

public final class MMDSyncModClient {
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();

    private MMDSyncModClient() {
    }

    public static void init() {
        // Always reapply immediately: MC-MMD bootstrap may have reset one or more collaborators.
        HybridNativeRuntime.install();

        if (INITIALIZED.compareAndSet(false, true)) {
            ClientLifecycleEvent.CLIENT_STARTED.register(client -> HybridNativeRuntime.install());
        }
    }
}
