package com.opdent.mmdskin.sync.network.resource;

/**
 * 资源传输协议操作码。
 *
 * <p>该协议用于后续统一 Fabric/NeoForge 与 Bukkit 插件之间的游戏内资源同步，
 * 替代当前 HTTP / Web UI 资源分发链路。</p>
 */
public final class ResourceTransferOpCode {
    private ResourceTransferOpCode() {
    }

    /** 服务端 -> 客户端：下发资源清单。 */
    public static final int MANIFEST = 1;
    /** 客户端 -> 服务端：请求某个文件分块。 */
    public static final int REQUEST_CHUNK = 2;
    /** 服务端 -> 客户端：发送某个文件分块（密文）。 */
    public static final int CHUNK = 3;
    /** 客户端 -> 服务端：上传任务开始。 */
    public static final int UPLOAD_BEGIN = 4;
    /** 客户端 -> 服务端：上传某个文件分块（密文）。 */
    public static final int UPLOAD_CHUNK = 5;
    /** 客户端 -> 服务端：上传任务结束。 */
    public static final int UPLOAD_FINISH = 6;
    /** 任意方向：中止任务。 */
    public static final int ABORT = 7;
    /** 任意方向：确认已收到分块或阶段完成。 */
    public static final int ACK = 8;
}
