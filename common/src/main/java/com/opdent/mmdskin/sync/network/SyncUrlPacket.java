package com.opdent.mmdskin.sync.network;

import com.opdent.mmdskin.sync.MMDSyncMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncUrlPacket(String url, String encryptedKey, String serverSecret, String serverId) implements CustomPacketPayload {
    public SyncUrlPacket(String url, String encryptedKey, String serverSecret, String serverId) {
        this.url = url;
        this.encryptedKey = encryptedKey;
        this.serverSecret = serverSecret;
        this.serverId = serverId;
    }

    public static final StreamCodec<FriendlyByteBuf, SyncUrlPacket> STREAM_CODEC = new StreamCodec<FriendlyByteBuf, SyncUrlPacket>() {
        @Override
        public SyncUrlPacket decode(FriendlyByteBuf buf) {
            String url = ByteBufCodecs.STRING_UTF8.decode(buf);
            String encryptedKey = ByteBufCodecs.STRING_UTF8.decode(buf);
            String serverSecret = ByteBufCodecs.STRING_UTF8.decode(buf);
            String serverId = ByteBufCodecs.STRING_UTF8.decode(buf);
            return new SyncUrlPacket(url, encryptedKey, serverSecret, serverId);
        }

        @Override
        public void encode(FriendlyByteBuf buf, SyncUrlPacket packet) {
            ByteBufCodecs.STRING_UTF8.encode(buf, packet.url());
            ByteBufCodecs.STRING_UTF8.encode(buf, packet.encryptedKey());
            ByteBufCodecs.STRING_UTF8.encode(buf, packet.serverSecret());
            ByteBufCodecs.STRING_UTF8.encode(buf, packet.serverId());
        }
    };

    public static final Type<SyncUrlPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MMDSyncMod.MODID, "sync_url"));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
