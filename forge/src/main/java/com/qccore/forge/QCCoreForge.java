package com.qccore.forge;

import com.qccore.QCCore;
import com.qccore.net.QcCommands;
import com.qccore.net.QcServerNetworking;
import com.qccore.platform.QcPlatform;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.nio.file.Path;

/**
 * The Forge entry point: installs {@link QcPlatform}, registers the packets on {@link QcForgeNetwork},
 * {@code /qc} on {@link RegisterCommandsEvent} and the {@link QcServerNetworking} hooks on the player
 * login/logout and server tick events; on a client it hands over to {@link QCCoreForgeClient}.
 */
@Mod(QCCore.MOD_ID)
public final class QCCoreForge {

	public QCCoreForge() {
		QcPlatform.install(new QcPlatform.Services() {
			@Override
			public Path gameDir() {
				return FMLPaths.GAMEDIR.get();
			}

			@Override
			public void sendToServer(com.qccore.net.QcPackets.QcPacket packet) {
				QcForgeNetwork.sendToServer(packet);
			}

			@Override
			public void sendToPlayer(ServerPlayer player, com.qccore.net.QcPackets.QcPacket packet) {
				QcForgeNetwork.sendToPlayer(player, packet);
			}
		});
		QcForgeNetwork.register();

		IEventBus forgeBus = MinecraftForge.EVENT_BUS;
		forgeBus.addListener(QCCoreForge::onRegisterCommands);
		forgeBus.addListener(QCCoreForge::onLogin);
		forgeBus.addListener(QCCoreForge::onLogout);
		forgeBus.addListener(QCCoreForge::onServerTick);

		if (FMLEnvironment.dist == Dist.CLIENT) {
			QCCoreForgeClient.init(FMLJavaModLoadingContext.get().getModEventBus());
		}
		QCCore.initialized();
	}

	private static void onRegisterCommands(RegisterCommandsEvent event) {
		QcCommands.register(event.getDispatcher());
	}

	private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			QcServerNetworking.onJoin(player);
		}
	}

	private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			QcServerNetworking.onLeave(player);
		}
	}

	private static void onServerTick(TickEvent.ServerTickEvent event) {
		if (event.phase == TickEvent.Phase.END && ServerLifecycleHooks.getCurrentServer() != null) {
			QcServerNetworking.onServerTick(ServerLifecycleHooks.getCurrentServer());
		}
	}
}
