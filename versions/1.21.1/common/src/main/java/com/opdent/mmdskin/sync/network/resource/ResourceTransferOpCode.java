package com.opdent.mmdskin.sync.network.resource;

public final class ResourceTransferOpCode {
    private ResourceTransferOpCode() {
    }

    public static final int MANIFEST = 1;
    public static final int REQUEST_CHUNK = 2;
    public static final int CHUNK = 3;
    public static final int UPLOAD_BEGIN = 4;
    public static final int UPLOAD_CHUNK = 5;
    public static final int UPLOAD_FINISH = 6;
    public static final int ABORT = 7;
    public static final int ACK = 8;
}
