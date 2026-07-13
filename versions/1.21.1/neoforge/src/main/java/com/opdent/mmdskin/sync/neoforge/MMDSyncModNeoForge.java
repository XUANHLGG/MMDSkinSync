package com.opdent.mmdskin.sync.neoforge;

import com.opdent.mmdskin.sync.MMDSyncMod;
import com.opdent.mmdskin.sync.network.HandshakePacket;
import com.opdent.mmdskin.sync.network.SyncUrlPacket;
import com.opdent.mmdskin.sync.network.resource.ResourceTransferPacket;
import com.opdent.mmdskin.sync.network.resource.ResourceTransferServerManager;
import com.tendoarisu.mmdskin.sync.MMDSync;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@Mod(MMDSyncMod.MODID)
public final class MMDSyncModNeoForge {
    public MMDSyncModNeoForge(IEventBus modEventBus) {
        MMDSync.init();
        modEventBus.addListener(MMDSyncModNeoForge::registerPayloads);
        NeoForge.EVENT_BUS.addListener(MMDSyncModNeoForge::onPlayerLoggedOut);
    }

    private static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        ResourceTransferServerManager.abortUploadsFor(event.getEntity().getUUID());
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(MMDSyncMod.MODID)
                .versioned("1")
                .optional()
                .executesOn(HandlerThread.NETWORK);
        registrar.playToServer(
                HandshakePacket.TYPE,
                HandshakePacket.STREAM_CODEC,
                MMDSyncNeoForgePayloadHandlers::handleHandshakePacket
        );
        registrar.playToClient(
                SyncUrlPacket.TYPE,
                SyncUrlPacket.STREAM_CODEC,
                MMDSyncNeoForgePayloadHandlers::handleSyncUrlPacket
        );
        registrar.playBidirectional(
                ResourceTransferPacket.TYPE,
                ResourceTransferPacket.STREAM_CODEC,
                MMDSyncNeoForgePayloadHandlers::handleResourceTransferPacket
        );
    }
}
