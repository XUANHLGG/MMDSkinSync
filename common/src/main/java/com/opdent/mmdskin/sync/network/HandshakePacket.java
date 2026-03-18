package com.opdent.mmdskin.sync.network;

import com.opdent.mmdskin.sync.MMDSyncMod;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record HandshakePacket(int opCode, java.util.UUID playerUUID, String publicKey, String platform, String hwid) implements CustomPacketPayload {
    public static final Type<HandshakePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MMDSyncMod.MODID, "handshake"));

    public static final StreamCodec<FriendlyByteBuf, HandshakePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, HandshakePacket::opCode,
            UUIDUtil.STREAM_CODEC, HandshakePacket::playerUUID,
            ByteBufCodecs.STRING_UTF8, HandshakePacket::publicKey,
            ByteBufCodecs.STRING_UTF8, HandshakePacket::platform,
            ByteBufCodecs.STRING_UTF8, HandshakePacket::hwid,
            HandshakePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
