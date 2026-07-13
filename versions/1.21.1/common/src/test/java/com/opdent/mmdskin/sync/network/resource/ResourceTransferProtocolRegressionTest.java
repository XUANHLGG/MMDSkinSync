package com.opdent.mmdskin.sync.network.resource;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ResourceTransferProtocolRegressionTest {
    @Test
    void uploadAckAndAbortStatesAreObservableAndClearable() {
        String transferId = "upload-test";
        ResourceTransferClientManager.prepareUpload(transferId);
        ResourceTransferClientManager.acceptPacket(packet(
                ResourceTransferOpCode.ACK, transferId, "upload_begin_ok;ack-v1"));
        assertTrue(ResourceTransferClientManager.hasUploadResponse(transferId));
        assertTrue(ResourceTransferClientManager.hasUploadAck(
                transferId, "upload_begin_ok;ack-v1"));
        assertFalse(ResourceTransferClientManager.isUploadAborted(transferId));

        ResourceTransferClientManager.acceptPacket(packet(
                ResourceTransferOpCode.ABORT, transferId, "invalid_upload_chunk"));
        assertTrue(ResourceTransferClientManager.isUploadAborted(transferId));
        assertEquals("invalid_upload_chunk",
                ResourceTransferClientManager.uploadAbortMessage(transferId));

        ResourceTransferClientManager.clearUploadState(transferId);
        assertFalse(ResourceTransferClientManager.hasUploadResponse(transferId));
    }

    @Test
    void typedPayloadRoundTripsAndRejectsOversizedStringWithoutThrowing() {
        ResourceTransferPacket expected = ResourceTransferPacket.fromResourcePacket(
                new ResourcePacketCodec.ResourcePacket(
                        ResourceTransferOpCode.UPLOAD_CHUNK,
                        "upload-id", "server", "pmx", "模型", "nested/file.pmx",
                        0, 1, 3L, "a".repeat(64), new byte[]{1, 2, 3},
                        List.of(), "upload_chunk"));
        FriendlyByteBuf encoded = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ResourceTransferPacket.STREAM_CODEC.encode(encoded, expected);
            assertTrue(encoded.readableBytes() <= ResourceTransferPacket.MAX_PACKET_BYTES);
            ResourceTransferPacket actual = ResourceTransferPacket.STREAM_CODEC.decode(encoded);
            assertEquals(expected.opCode(), actual.opCode());
            assertEquals(expected.transferId(), actual.transferId());
            assertEquals(expected.relativePath(), actual.relativePath());
            assertArrayEquals(expected.payload(), actual.payload());
        } finally {
            encoded.release();
        }

        FriendlyByteBuf malformed = new FriendlyByteBuf(Unpooled.buffer());
        try {
            malformed.writeInt(ResourceTransferOpCode.UPLOAD_BEGIN);
            malformed.writeVarInt(129);
            malformed.writeZero(129);
            ResourceTransferPacket rejected = assertDoesNotThrow(
                    () -> ResourceTransferPacket.STREAM_CODEC.decode(malformed));
            assertEquals(ResourceTransferPacket.INVALID_OPCODE, rejected.opCode());
            assertTrue(rejected.message().startsWith("invalid_packet:"));
        } finally {
            malformed.release();
        }
    }

    @Test
    void f3ResetAndUploadSafetyContractsRemainPresentInSource() throws Exception {
        String firstPersonMixin = Files.readString(Path.of(
                "src/main/java/com/tendoarisu/mmdskin/sync/mixin/MixinFirstPersonManager.java"),
                StandardCharsets.UTF_8);
        assertAll(
                () -> assertTrue(firstPersonMixin.contains("method = \"reset()V\"")),
                () -> assertTrue(firstPersonMixin.contains("MMDSyncNativeBridge.setFirstPersonMode")),
                () -> assertTrue(firstPersonMixin.contains("trackedModelHandle, false")),
                () -> assertTrue(firstPersonMixin.contains("activeDesktopFirstPerson = false")));

        String syncManager = Files.readString(Path.of(
                "src/main/java/com/opdent/mmdskin/sync/SyncManager.java"),
                StandardCharsets.UTF_8);
        assertAll(
                () -> assertTrue(syncManager.contains("encodedBytes > 32_766")),
                () -> assertTrue(syncManager.contains("超过 32,766 字节安全上限")),
                () -> assertTrue(syncManager.contains("upload_begin_ok;ack-v1")),
                () -> assertTrue(syncManager.contains("upload_chunk_ok:")),
                () -> assertTrue(syncManager.contains("legacy-paced")));
    }

    private static ResourcePacketCodec.ResourcePacket packet(
            int opCode, String transferId, String message) {
        return new ResourcePacketCodec.ResourcePacket(
                opCode, transferId, "server", "", "", "",
                0, 0, 0L, "", new byte[0], List.of(), message);
    }
}
