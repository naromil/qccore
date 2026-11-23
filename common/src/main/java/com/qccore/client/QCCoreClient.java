package com.qccore.client;

import com.qccore.net.QcPackets.QcIndexS2C;
import com.qccore.net.QcPackets.QcResultS2C;
import com.qccore.world.QcUnitRecord;
import com.qccore.net.QcPackets.QcPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * The loader-neutral client entry: the loader glue ({@code QCCoreFabricClient}, {@code QCCoreForgeClient})
 * registers {@link QcKeys#create()}, {@link QcInteraction#onClientTick}, {@link QcOverlayRenderer#render},
 * {@link QcHud#render} and the server-to-client packets with its own API and calls {@link #handle} for
 * every decoded packet on the client thread.
 */
public final class QCCoreClient {
	private static final Logger LOGGER = LoggerFactory.getLogger("qccore/client");

	private QCCoreClient() {
	}

	/** Logged once the loader glue has registered everything. */
	public static void initialized() {
		LOGGER.info("QCCore client initialized");
	}

	/** Handles one server-to-client packet; called on the client thread. */
	public static void handle(QcPacket packet) {
		if (packet instanceof QcIndexS2C index) {
			receiveIndex(index.dimension(), index.units());
		} else if (packet instanceof QcResultS2C result) {
			receiveResult(result.ok(), result.message());
		}
	}

	/** Stores the units the server reported for this dimension; other dimensions are dropped. */
	private static void receiveIndex(ResourceLocation dimension, CompoundTag units) {
		Minecraft client = Minecraft.getInstance();
		ResourceLocation current = client.level == null ? null : client.level.dimension().location();
		if (!dimension.equals(current)) {
			return;
		}
		QcInteraction.syncDimension(client);
		Map<BlockPos, QcUnitRecord> records = new HashMap<>();
		for (QcUnitRecord record : QcUnitRecord.readAll(units)) {
			records.put(record.origin(), record);
		}
		QcClientState.nearbyUnits.clear();
		QcClientState.nearbyUnits.putAll(records);
		if (QcClientState.active && QcClientState.plan.isEmpty()) {
			// nothing planned yet: settle on the grid of what was just found (a /qc scan, a fresh apply)
			QcInteraction.adoptNearbyLattice();
		}
		// standing roads and doors change how the plan compiles: redraw the block preview
		QcClientState.changed();
	}

	private static void receiveResult(boolean ok, String message) {
		QcRequests.Pending request = QcClientState.requests.answered();
		if (ok && request != null && request.kind() == QcRequests.Kind.APPLY) {
			// what was applied now stands in the world: it leaves the plan, so K cannot build it twice
			message += QcInteraction.planApplied(request.revision());
		}
		QcClientState.say(message);
		Minecraft client = Minecraft.getInstance();
		if (ok && client.player != null) {
			client.player.displayClientMessage(Component.literal(message).withStyle(ChatFormatting.GREEN), false);
		}
	}
}
