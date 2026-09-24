package com.qccore.client;

import com.qccore.net.QcPackets.QcIndexS2C;
import com.qccore.net.QcPackets.QcResultS2C;
import com.qccore.world.QcUnitRecord;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class QCCoreClient implements ClientModInitializer {
	private static final Logger LOGGER = LoggerFactory.getLogger("qccore/client");

	@Override
	public void onInitializeClient() {
		QcKeys.register();
		ClientTickEvents.END_CLIENT_TICK.register(QcInteraction::onClientTick);
		WorldRenderEvents.AFTER_TRANSLUCENT.register(QcOverlayRenderer.INSTANCE);
		HudRenderCallback.EVENT.register(QcHud::render);
		ClientPlayNetworking.registerGlobalReceiver(QcIndexS2C.TYPE, (packet, player, sender) ->
				MinecraftClient.getInstance().execute(() ->
						receiveIndex(packet.dimension(), packet.units())));
		ClientPlayNetworking.registerGlobalReceiver(QcResultS2C.TYPE, (packet, player, sender) ->
				MinecraftClient.getInstance().execute(() ->
						receiveResult(packet.ok(), packet.message())));
		LOGGER.info("QCCore client initialized");
	}

	/** Stores the units the server reported for this dimension; other dimensions are dropped. */
	private static void receiveIndex(Identifier dimension, NbtCompound units) {
		MinecraftClient client = MinecraftClient.getInstance();
		Identifier current = client.world == null ? null : client.world.getRegistryKey().getValue();
		if (!dimension.equals(current)) {
			return;
		}
		Map<BlockPos, QcUnitRecord> records = new HashMap<>();
		NbtList list = units.getList("units", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < list.size(); i++) {
			QcUnitRecord record = QcUnitRecord.fromNbt(list.getCompound(i));
			records.put(record.origin(), record);
		}
		QcClientState.nearbyUnits.clear();
		QcClientState.nearbyUnits.putAll(records);
		QcClientState.dimension = dimension;
	}

	private static void receiveResult(boolean ok, String message) {
		QcClientState.say(message);
		MinecraftClient client = MinecraftClient.getInstance();
		if (ok && client.player != null) {
			client.player.sendMessage(Text.literal(message).formatted(Formatting.GREEN), false);
		}
	}
}
