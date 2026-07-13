package com.tendoarisu.mmdskin.sync;

import com.opdent.mmdskin.sync.MMDSyncMod;
import com.opdent.mmdskin.sync.MMDSyncModClient;

public class MMDSync {
    public static void init() {
        MMDSyncMod.init();
    }

    public static void initClient() {
        MMDSyncModClient.init();
    }
}
