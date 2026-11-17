package com.qccore.forge;

import com.qccore.QCCore;
import com.qccore.client.QCCoreClient;
import com.qccore.net.QcPackets;
import com.qccore.net.QcPackets.QcPacket;
import com.qccore.net.QcServerNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

/**
 * The Forge transport of {@link QcPackets}: one {@link SimpleChannel} {@code qccore:main}, one
 * discriminator per packet in the order of {@link QcPackets#C2S} then {@link QcPackets#S2C}. Handlers run
 * on the game thread ({@code consumerMainThread}); a client-to-server packet without a sender is dropped.
 * A client and a server must both run this mod at the same {@link #PROTOCOL}.
 */
public final class QcForgeNetwork {

	private static final String PROTOCOL = "1";

	private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(QCCore.id("main"),
			() -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

	private QcForgeNetwork() {
	}

	static void register() {
		int discriminator = 0;
		for (QcPackets.Type<?> type : QcPackets.C2S) {
			register(type, discriminator++, NetworkDirection.PLAY_TO_SERVER);
		}
		for (QcPackets.Type<?> type : QcPackets.S2C) {
			register(type, discriminator++, NetworkDirection.PLAY_TO_CLIENT);
		}
	}

	private static <T extends QcPacket> void register(QcPackets.Type<T> type, int discriminator, NetworkDirection direction) {
		CHANNEL.messageBuilder(type.packetClass(), discriminator, direction)
				.encoder(QcPacket::write)
				.decoder(type::read)
				.consumerMainThread((T packet, Supplier<NetworkEvent.Context> context) -> {
					if (direction == NetworkDirection.PLAY_TO_SERVER) {
						ServerPlayer sender = context.get().getSender();
						if (sender != null) {
							QcServerNetworking.handle(sender, packet);
						}
					} else {
						DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> QCCoreClient.handle(packet));
					}
				})
				.add();
	}

	static void sendToServer(QcPacket packet) {
		CHANNEL.sendToServer(packet);
	}

	static void sendToPlayer(ServerPlayer player, QcPacket packet) {
		CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
	}
}
