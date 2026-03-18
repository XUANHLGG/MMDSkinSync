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
    public static final Type<ResourceTransferPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MMDSyncMod.MODID, "resource_transfer"));

    public static final StreamCodec<FriendlyByteBuf, ResourceTransferPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ResourceTransferPacket decode(FriendlyByteBuf buf) {
            int opCode = ByteBufCodecs.INT.decode(buf);
            String transferId = ByteBufCodecs.STRING_UTF8.decode(buf);
            String serverId = ByteBufCodecs.STRING_UTF8.decode(buf);
            String zone = ByteBufCodecs.STRING_UTF8.decode(buf);
            String folderName = ByteBufCodecs.STRING_UTF8.decode(buf);
            String relativePath = ByteBufCodecs.STRING_UTF8.decode(buf);
            int chunkIndex = ByteBufCodecs.INT.decode(buf);
            int chunkCount = ByteBufCodecs.INT.decode(buf);
            long totalSize = ByteBufCodecs.VAR_LONG.decode(buf);
            String digest = ByteBufCodecs.STRING_UTF8.decode(buf);
            byte[] payload = ByteBufCodecs.BYTE_ARRAY.decode(buf);
            String manifestJson = ByteBufCodecs.STRING_UTF8.decode(buf);
            String message = ByteBufCodecs.STRING_UTF8.decode(buf);
            return new ResourceTransferPacket(
                    opCode,
                    transferId,
                    serverId,
                    zone,
                    folderName,
                    relativePath,
                    chunkIndex,
                    chunkCount,
                    totalSize,
                    digest,
                    payload,
                    manifestJson,
                    message
            );
        }

        @Override
        public void encode(FriendlyByteBuf buf, ResourceTransferPacket packet) {
            ByteBufCodecs.INT.encode(buf, packet.opCode());
            ByteBufCodecs.STRING_UTF8.encode(buf, packet.transferId());
            ByteBufCodecs.STRING_UTF8.encode(buf, packet.serverId());
            ByteBufCodecs.STRING_UTF8.encode(buf, packet.zone());
            ByteBufCodecs.STRING_UTF8.encode(buf, packet.folderName());
            ByteBufCodecs.STRING_UTF8.encode(buf, packet.relativePath());
            ByteBufCodecs.INT.encode(buf, packet.chunkIndex());
            ByteBufCodecs.INT.encode(buf, packet.chunkCount());
            ByteBufCodecs.VAR_LONG.encode(buf, packet.totalSize());
            ByteBufCodecs.STRING_UTF8.encode(buf, packet.digest());
            ByteBufCodecs.BYTE_ARRAY.encode(buf, packet.payload());
            ByteBufCodecs.STRING_UTF8.encode(buf, packet.manifestJson());
            ByteBufCodecs.STRING_UTF8.encode(buf, packet.message());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public ResourcePacketCodec.ResourcePacket toResourcePacket() {
        return new ResourcePacketCodec.ResourcePacket(
                opCode,
                transferId,
                serverId,
                zone,
                folderName,
                relativePath,
                chunkIndex,
                chunkCount,
                totalSize,
                digest,
                payload,
                decodeManifestEntries(manifestJson),
                message
        );
    }

    public static ResourceTransferPacket fromResourcePacket(ResourcePacketCodec.ResourcePacket packet) {
        return new ResourceTransferPacket(
                packet.opCode(),
                safe(packet.transferId()),
                safe(packet.serverId()),
                safe(packet.zone()),
                safe(packet.folderName()),
                safe(packet.relativePath()),
                packet.chunkIndex(),
                packet.chunkCount(),
                packet.totalSize(),
                safe(packet.digest()),
                packet.payload() == null ? new byte[0] : packet.payload(),
                encodeManifestEntries(packet.manifestEntries()),
                safe(packet.message())
        );
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
                        obj.has("sha256") ? obj.get("sha256").getAsString() : ""
                ));
            });
        } catch (Exception ignored) {
        }
        return entries;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
