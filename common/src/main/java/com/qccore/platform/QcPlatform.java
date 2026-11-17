package com.qccore.platform;

import com.qccore.net.QcPackets.QcPacket;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;

/**
 * The seam between the loader-neutral code in {@code common} and the mod loader it runs on.
 *
 * <p>Everything under {@code com.qccore} outside the {@code fabric} and {@code forge} packages is
 * compiled once against Mojang's names and must not import a loader API. The few services it needs
 * from the loader - the game directory and sending a packet - go through the {@link Services}
 * implementation each loader installs from its entry point, before anything else of the mod runs.
 * Incoming packets and events travel the other way: the loader glue calls the plain hook methods
 * ({@code QcServerNetworking.handle/onJoin/onLeave/onServerTick}, {@code QcCommands.register},
 * {@code QCCoreClient.handle}, {@code QcKeys.create}, {@code QcOverlayRenderer.render},
 * {@code QcHud.render}, {@code QcInteraction.onClientTick/click}).
 */
public final class QcPlatform {

    /** What a loader provides to the common code. */
    public interface Services {

        /** The game directory ({@code run/} in development). */
        Path gameDir();

        /** Sends a client-to-server packet; only called on the client. */
        void sendToServer(QcPacket packet);

        /** Sends a server-to-client packet to one player. */
        void sendToPlayer(ServerPlayer player, QcPacket packet);
    }

    private static Services services;

    private QcPlatform() {
    }

    /** Called once by the loader entry point. */
    public static void install(Services implementation) {
        services = implementation;
    }

    private static Services services() {
        if (services == null) {
            throw new IllegalStateException("qccore: no platform services installed");
        }
        return services;
    }

    public static Path gameDir() {
        return services().gameDir();
    }

    public static void sendToServer(QcPacket packet) {
        services().sendToServer(packet);
    }

    public static void sendToPlayer(ServerPlayer player, QcPacket packet) {
        services().sendToPlayer(player, packet);
    }
}
