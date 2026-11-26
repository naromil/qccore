package com.qccore.fabric;

import com.qccore.QCCore;
import com.qccore.net.QcCommands;
import com.qccore.net.QcPackets;
import com.qccore.net.QcPackets.QcPacket;
import com.qccore.net.QcServerNetworking;
import com.qccore.platform.QcPlatform;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * The Fabric entry point: installs {@link QcPlatform}, registers one raw play channel per
 * client-to-server packet id ({@code qccore:build|plan|undo|redo}) whose payload is exactly the
 * record's {@code write}, {@code /qc}, and the join/leave/tick hooks of {@link QcServerNetworking}.
 */
public class QCCoreFabric implements ModInitializer {

	/** Set by {@link QCCoreFabricClient}; a dedicated server never sends to a server. */
	static Consumer<QcPacket> clientSender = packet -> {
		throw new IllegalStateException("qccore: client packet sent on a dedicated server");
	};

	@Override
	public void onInitialize() {
		QcPlatform.install(new QcPlatform.Services() {
			@Override
			public Path gameDir() {
				return FabricLoader.getInstance().getGameDir();
			}

			@Override
			public void sendToServer(QcPacket packet) {
				clientSender.accept(packet);
			}

			@Override
			public void sendToPlayer(ServerPlayer player, QcPacket packet) {
				ServerPlayNetworking.send(player, packet.type().id(), encode(packet));
			}
		});

		for (QcPackets.Type<?> type : QcPackets.C2S) {
			ServerPlayNetworking.registerGlobalReceiver(type.id(), (server, player, handler, buf, sender) -> {
				QcPacket packet = type.read(buf);
				server.execute(() -> QcServerNetworking.handle(player, packet));
			});
		}
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> QcServerNetworking.onJoin(handler.getPlayer()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> QcServerNetworking.onLeave(handler.getPlayer()));
		ServerTickEvents.END_SERVER_TICK.register(QcServerNetworking::onServerTick);
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> QcCommands.register(dispatcher));
		QCCore.initialized();
	}

	static FriendlyByteBuf encode(QcPacket packet) {
		FriendlyByteBuf buf = PacketByteBufs.create();
		packet.write(buf);
		return buf;
	}
}
