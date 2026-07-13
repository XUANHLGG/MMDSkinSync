package com.opdent.mmdskin.sync.network.resource;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.opdent.mmdskin.sync.MMDSyncMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public record ResourceTransferPacket(
        int opCode,
        String transferId,
        String serverId,
        String zone,
        String folderName,
        String relativePath,
        int chunkIndex,
        int chunkCount,
        long totalSize,
        String digest,
        byte[] payload,
        String manifestJson,
        String message
) implements CustomPacketPayload {
    public static final int INVALID_OPCODE = Integer.MIN_VALUE;
    public static final int MAX_PACKET_BYTES = 32_766;
    private static final int MAX_TRANSFER_ID_BYTES = 128;
    private static final int MAX_SERVER_ID_BYTES = 256;
    private static final int MAX_ZONE_BYTES = 16;
    private static final int MAX_FOLDER_NAME_BYTES = 255;
    private static final int MAX_RELATIVE_PATH_BYTES = 2_048;
    private static final int MAX_DIGEST_BYTES = 128;
    private static final int MAX_PAYLOAD_BYTES = 24 * 1024;
    private static final int MAX_MANIFEST_JSON_BYTES = 30 * 1024;
    private static final int MAX_MESSAGE_BYTES = 512;

    public static final Type<ResourceTransferPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MMDSyncMod.MODID, "resource_transfer"));

    public static final StreamCodec<FriendlyByteBuf, ResourceTransferPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ResourceTransferPacket decode(FriendlyByteBuf buf) {
            String transferId = "";
            try {
                if (buf.readableBytes() > MAX_PACKET_BYTES) {
                    throw new IllegalArgumentException("resource packet exceeds safe body limit");
                }
                int opCode = buf.readInt();
                transferId = readBoundedString(buf, MAX_TRANSFER_ID_BYTES, "transferId");
                String serverId = readBoundedString(buf, MAX_SERVER_ID_BYTES, "serverId");
                String zone = readBoundedString(buf, MAX_ZONE_BYTES, "zone");
                String folderName = readBoundedString(buf, MAX_FOLDER_NAME_BYTES, "folderName");
                String relativePath = readBoundedString(buf, MAX_RELATIVE_PATH_BYTES, "relativePath");
                int chunkIndex = buf.readInt();
                int chunkCount = buf.readInt();
                long totalSize = buf.readVarLong();
                String digest = readBoundedString(buf, MAX_DIGEST_BYTES, "digest");
                byte[] payload = readBoundedBytes(buf, MAX_PAYLOAD_BYTES, "payload");
                String manifestJson = readBoundedString(buf, MAX_MANIFEST_JSON_BYTES, "manifestJson");
                String message = readBoundedString(buf, MAX_MESSAGE_BYTES, "message");
                if (chunkIndex < 0 || chunkCount < 0 || totalSize < 0L) {
                    throw new IllegalArgumentException("negative resource packet metadata");
                }
                if (buf.isReadable()) {
                    throw new IllegalArgumentException("trailing bytes in resource packet");
                }
                return new ResourceTransferPacket(
                        opCode, transferId, serverId, zone, folderName, relativePath,
                        chunkIndex, chunkCount, totalSize, digest, payload, manifestJson, message);
            } catch (RuntimeException e) {
                if (buf.isReadable()) {
                    buf.skipBytes(buf.readableBytes());
                }
                MMDSyncMod.LOGGER.warn(
                        "资源传输包解码被安全拒绝(transferId={}): {}", transferId, e.toString());
                return invalidPacket(transferId, e.getClass().getSimpleName());
            }
        }

        @Override
        public void encode(FriendlyByteBuf buf, ResourceTransferPacket packet) {
            ByteBufCodecs.INT.encode(buf, packet.opCode());
            ByteBufCodecs.STRING_UTF8.encode(buf, safe(packet.transferId()));
            ByteBufCodecs.STRING_UTF8.encode(buf, safe(packet.serverId()));
            ByteBufCodecs.STRING_UTF8.encode(buf, safe(packet.zone()));
            ByteBufCodecs.STRING_UTF8.encode(buf, safe(packet.folderName()));
            ByteBufCodecs.STRING_UTF8.encode(buf, safe(packet.relativePath()));
            ByteBufCodecs.INT.encode(buf, packet.chunkIndex());
            ByteBufCodecs.INT.encode(buf, packet.chunkCount());
            ByteBufCodecs.VAR_LONG.encode(buf, packet.totalSize());
            ByteBufCodecs.STRING_UTF8.encode(buf, safe(packet.digest()));
            ByteBufCodecs.BYTE_ARRAY.encode(buf, packet.payload() == null ? new byte[0] : packet.payload());
            ByteBufCodecs.STRING_UTF8.encode(buf, safe(packet.manifestJson()));
            ByteBufCodecs.STRING_UTF8.encode(buf, safe(packet.message()));
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public ResourcePacketCodec.ResourcePacket toResourcePacket() {
        return new ResourcePacketCodec.ResourcePacket(
                opCode, transferId, serverId, zone, folderName, relativePath,
                chunkIndex, chunkCount, totalSize, digest, payload,
                decodeManifestEntries(manifestJson), message);
    }

    public static ResourceTransferPacket fromResourcePacket(ResourcePacketCodec.ResourcePacket packet) {
        return new ResourceTransferPacket(
                packet.opCode(), safe(packet.transferId()), safe(packet.serverId()), safe(packet.zone()),
                safe(packet.folderName()), safe(packet.relativePath()), packet.chunkIndex(), packet.chunkCount(),
                packet.totalSize(), safe(packet.digest()),
                packet.payload() == null ? new byte[0] : packet.payload(),
                encodeManifestEntries(packet.manifestEntries()), safe(packet.message()));
    }

    private static String readBoundedString(FriendlyByteBuf buf, int maxBytes, String field) {
        int length = buf.readVarInt();
        if (length < 0 || length > maxBytes || length > buf.readableBytes()) {
            throw new IllegalArgumentException(field + " length is outside safe bounds");
        }
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException(field + " contains invalid UTF-8", e);
        }
    }

    private static byte[] readBoundedBytes(FriendlyByteBuf buf, int maxBytes, String field) {
        int length = buf.readVarInt();
        if (length < 0 || length > maxBytes || length > buf.readableBytes()) {
            throw new IllegalArgumentException(field + " length is outside safe bounds");
        }
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        return bytes;
    }

    private static ResourceTransferPacket invalidPacket(String transferId, String reason) {
        return new ResourceTransferPacket(
                INVALID_OPCODE, safe(transferId), "", "", "", "",
                0, 0, 0L, "", new byte[0], "[]", "invalid_packet:" + safe(reason));
    }

    private static String encodeManifestEntries(List<ResourcePacketCodec.ManifestEntry> entries) {
        JsonArray array = new JsonArray();
        if (entries != null) {
            for (ResourcePacketCodec.ManifestEntry entry : entries) {
                JsonObject obj = new JsonObject();
                obj.addProperty("zone", safe(entry.zone()));
                obj.addProperty("folderName", safe(entry.folderName()));
                obj.addProperty("relativePath", safe(entry.relativePath()));
                obj.addProperty("size", entry.size());
                obj.addProperty("sha256", safe(entry.sha256()));
                array.add(obj);
            }
        }
        return array.toString();
    }

    private static List<ResourcePacketCodec.ManifestEntry> decodeManifestEntries(String json) {
        List<ResourcePacketCodec.ManifestEntry> entries = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return entries;
        }

        try {
            JsonArray array = JsonParser.parseString(json).getAsJsonArray();
            array.forEach(element -> {
                JsonObject obj = element.getAsJsonObject();
                entries.add(new ResourcePacketCodec.ManifestEntry(
                        obj.has("zone") ? obj.get("zone").getAsString() : "",
                        obj.has("folderName") ? obj.get("folderName").getAsString() : "",
                        obj.has("relativePath") ? obj.get("relativePath").getAsString() : "",
                        obj.has("size") ? obj.get("size").getAsLong() : 0L,
                        obj.has("sha256") ? obj.get("sha256").getAsString() : ""));
            });
        } catch (Exception ignored) {
        }
        return entries;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
