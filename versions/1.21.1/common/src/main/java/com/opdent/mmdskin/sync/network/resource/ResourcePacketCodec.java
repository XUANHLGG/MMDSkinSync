package com.opdent.mmdskin.sync.network.resource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class ResourcePacketCodec {
    private ResourcePacketCodec() {
    }

    public record ManifestEntry(String zone, String folderName, String relativePath, long size, String sha256) {
    }

    public record ResourcePacket(
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
            List<ManifestEntry> manifestEntries,
            String message
    ) {
    }

    public static byte[] encode(ResourcePacket packet) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        out.writeInt(packet.opCode());
        writeString(out, packet.transferId());
        writeString(out, packet.serverId());
        writeString(out, packet.zone());
        writeString(out, packet.folderName());
        writeString(out, packet.relativePath());
        out.writeInt(packet.chunkIndex());
        out.writeInt(packet.chunkCount());
        out.writeLong(packet.totalSize());
        writeString(out, packet.digest());
        writeBytes(out, packet.payload());

        List<ManifestEntry> entries = packet.manifestEntries();
        out.writeInt(entries == null ? 0 : entries.size());
        if (entries != null) {
            for (ManifestEntry entry : entries) {
                writeString(out, entry.zone());
                writeString(out, entry.folderName());
                writeString(out, entry.relativePath());
                out.writeLong(entry.size());
                writeString(out, entry.sha256());
            }
        }

        writeString(out, packet.message());
        out.flush();
        return baos.toByteArray();
    }

    public static ResourcePacket decode(byte[] data) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        int opCode = in.readInt();
        String transferId = readString(in);
        String serverId = readString(in);
        String zone = readString(in);
        String folderName = readString(in);
        String relativePath = readString(in);
        int chunkIndex = in.readInt();
        int chunkCount = in.readInt();
        long totalSize = in.readLong();
        String digest = readString(in);
        byte[] payload = readBytes(in);

        int manifestSize = in.readInt();
        List<ManifestEntry> entries = new ArrayList<>(Math.max(manifestSize, 0));
        for (int i = 0; i < manifestSize; i++) {
            entries.add(new ManifestEntry(
                    readString(in),
                    readString(in),
                    readString(in),
                    in.readLong(),
                    readString(in)
            ));
        }

        String message = readString(in);
        return new ResourcePacket(
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
                entries,
                message
        );
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length <= 0) {
            return "";
        }
        byte[] bytes = in.readNBytes(length);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeBytes(DataOutputStream out, byte[] payload) throws IOException {
        if (payload == null || payload.length == 0) {
            out.writeInt(0);
            return;
        }
        out.writeInt(payload.length);
        out.write(payload);
    }

    private static byte[] readBytes(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length <= 0) {
            return new byte[0];
        }
        return in.readNBytes(length);
    }
}
