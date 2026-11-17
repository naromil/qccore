package com.qccore.net;

import com.qccore.core.QcResult;
import com.qccore.core.UnitLayers;
import com.qccore.core.nbt.LayersCodec;
import com.qccore.core.nbt.PaletteCodec;
import com.qccore.core.palette.BlockPalette;
import com.qccore.net.QcPackets.QcAlignC2S;
import com.qccore.net.QcPackets.QcBuildC2S;
import com.qccore.net.QcPackets.QcIndexS2C;
import com.qccore.net.QcPackets.QcPlanC2S;
import com.qccore.net.QcPackets.QcResultS2C;
import com.qccore.net.QcPackets.QcRedoC2S;
import com.qccore.net.QcPackets.QcUndoC2S;
import com.qccore.core.nbt.PlanCodec;
import com.qccore.core.plan.QcPlan;
import com.qccore.world.QcPlanService;
import com.qccore.world.QcUndoService;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtTagSizeTracker;
import java.io.DataInputStream;
import java.util.zip.GZIPInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import com.qccore.world.QcAlignService;
import com.qccore.world.QcBuildService;
import com.qccore.world.QcUnitIndex;
import com.qccore.world.QcUnitRecord;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The server end of the editor: it receives build and align requests and keeps every client's view
 * of the placed QC Units up to date.
 */
public final class QcServerNetworking {

    private static final Logger LOGGER = LoggerFactory.getLogger("qccore");

    private static final int PUSH_RADIUS = 128;
    private static final int PUSH_LIMIT = 4096;
    private static final int PUSH_INTERVAL_TICKS = 40;
    private static final int PUSH_MOVE_DISTANCE = 16;

    /** 256 chunks of 30000 bytes: about 7.5 MB of compressed plan, far beyond any real request. */
    private static final int MAX_CHUNKS = 256;

    /**
     * Heap the decoded plan request may claim, as counted by {@link NbtTagSizeTracker}. A plan at
     * every limit (4096 units, 4096 cleared and 4096 road cells, four component structures) stays
     * well below it, while a small gzip stream that inflates to gigabytes is cut off.
     */
    private static final long MAX_PLAN_NBT_BYTES = 32L * 1024 * 1024;

    private static final Map<UUID, BlockPos> LAST_PUSH = new HashMap<>();
    private static final Map<UUID, Transfer> TRANSFERS = new HashMap<>();
    private static int tick;

    private QcServerNetworking() {
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(QcBuildC2S.TYPE, (packet, player, sender) ->
                player.server.execute(() -> {
                    try {
                        UnitLayers layers = LayersCodec.fromNbt(packet.layers());
                        BlockPalette palette = PaletteCodec.fromNbt(packet.palette());
                        QcResult result = QcBuildService.build(player, packet.anchor(), layers, palette);
                        if (!result.ok()) {
                            LOGGER.warn("qccore: build request from {} refused: {}", player.getName().getString(), result.message());
                        }
                        ServerPlayNetworking.send(player, new QcResultS2C(result.ok(), result.message()));
                        if (result.ok()) {
                            push(player);
                        }
                    } catch (RuntimeException e) {
                        LOGGER.warn("qccore: rejected build request from {}: {}", player.getName().getString(), e.toString());
                        ServerPlayNetworking.send(player, new QcResultS2C(false, "the build request was malformed"));
                    }
                }));

        ServerPlayNetworking.registerGlobalReceiver(QcAlignC2S.TYPE, (packet, player, sender) ->
                player.server.execute(() -> {
                    try {
                        BlockPalette palette = PaletteCodec.fromNbt(packet.palette());
                        QcResult result = QcAlignService.align(player, packet.unit(), packet.a(), packet.b(), packet.c(), palette);
                        if (!result.ok()) {
                            LOGGER.warn("qccore: align request from {} refused: {}", player.getName().getString(), result.message());
                        }
                        ServerPlayNetworking.send(player, new QcResultS2C(result.ok(), result.message()));
                        if (result.ok()) {
                            push(player);
                        }
                    } catch (RuntimeException e) {
                        LOGGER.warn("qccore: rejected align request from {}: {}", player.getName().getString(), e.toString());
                        ServerPlayNetworking.send(player, new QcResultS2C(false, "the align request was malformed"));
                    }
                }));

        ServerPlayNetworking.registerGlobalReceiver(QcPlanC2S.TYPE, (packet, player, sender) ->
                player.server.execute(() -> receivePlanChunk(player, packet)));

        ServerPlayNetworking.registerGlobalReceiver(QcUndoC2S.TYPE, (packet, player, sender) ->
                player.server.execute(() -> {
                    QcResult result = QcUndoService.undo(player);
                    ServerPlayNetworking.send(player, new QcResultS2C(result.ok(), result.message()));
                    if (result.ok()) {
                        push(player);
                    }
                }));

        ServerPlayNetworking.registerGlobalReceiver(QcRedoC2S.TYPE, (packet, player, sender) ->
                player.server.execute(() -> {
                    QcResult result = QcUndoService.redo(player);
                    ServerPlayNetworking.send(player, new QcResultS2C(result.ok(), result.message()));
                    if (result.ok()) {
                        push(player);
                    }
                }));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> push(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            LAST_PUSH.remove(handler.getPlayer().getUuid());
            TRANSFERS.remove(handler.getPlayer().getUuid());
            QcUndoService.forget(handler.getPlayer().getUuid());
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tick++;
            if (tick % PUSH_INTERVAL_TICKS != 0) {
                return;
            }
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                BlockPos last = LAST_PUSH.get(player.getUuid());
                if (last == null || !last.isWithinDistance(player.getBlockPos(), PUSH_MOVE_DISTANCE)) {
                    push(player);
                }
            }
        });
    }

    /**
     * Collects the chunks of a plan request and runs it once the last chunk arrived. A chunk of
     * another transfer, one out of order or one announcing a different total restarts the collection
     * (and is refused unless it is a valid first chunk); the remaining chunks of a refused transfer
     * are dropped without another reply. A player out of creative is refused at the first chunk,
     * before anything is buffered.
     */
    private static void receivePlanChunk(ServerPlayerEntity player, QcPlanC2S packet) {
        UUID id = player.getUuid();
        Transfer transfer = TRANSFERS.get(id);
        if (packet.index() == 0 || transfer == null || transfer.id != packet.transfer() || transfer.next != packet.index()
                || transfer.total != packet.total()) {
            if (packet.index() != 0 && transfer == null) {
                // the rest of a transfer that was already refused or abandoned: one reply was enough
                return;
            }
            if (packet.index() != 0 || packet.total() < 1 || packet.total() > MAX_CHUNKS) {
                TRANSFERS.remove(id);
                ServerPlayNetworking.send(player, new QcResultS2C(false, "the plan request was malformed"));
                return;
            }
            if (!player.isCreative()) {
                // refused before any byte is buffered or decoded; QcPlanService.apply checks it again
                TRANSFERS.remove(id);
                ServerPlayNetworking.send(player, new QcResultS2C(false, "creative mode is required to build into the world"));
                return;
            }
            transfer = new Transfer(packet.transfer(), packet.total());
            TRANSFERS.put(id, transfer);
        }
        transfer.bytes.writeBytes(packet.data());
        transfer.next++;
        if (transfer.next < transfer.total) {
            return;
        }
        TRANSFERS.remove(id);
        try {
            NbtCompound request = decode(transfer.bytes.toByteArray());
            String action = request.getString("action");
            QcPlan plan = PlanCodec.fromNbt(request.getCompound("plan"));
            BlockPalette palette = PaletteCodec.fromNbt(request.getCompound("palette"));
            int[] anchor = request.getIntArray("anchor");
            if (!"apply".equals(action) || anchor.length != 3) {
                throw new IllegalArgumentException("unknown plan action '" + action + "'");
            }
            QcResult result = QcPlanService.apply(player, new BlockPos(anchor[0], anchor[1], anchor[2]), plan, palette);
            if (!result.ok()) {
                LOGGER.warn("qccore: plan request from {} refused: {}", player.getName().getString(), result.message());
            }
            ServerPlayNetworking.send(player, new QcResultS2C(result.ok(), result.message()));
            if (result.ok()) {
                push(player);
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("qccore: rejected plan request from {}: {}", player.getName().getString(), e.toString());
            ServerPlayNetworking.send(player, new QcResultS2C(false, "the plan request was malformed"));
        }
    }

    /**
     * Inflates and parses a reassembled plan request. Unlike {@link NbtIo#readCompressed} the read is
     * bounded by {@link #MAX_PLAN_NBT_BYTES}; going over it throws a {@link RuntimeException}, which
     * the caller reports as a malformed request.
     */
    static NbtCompound decode(byte[] gzip) throws IOException {
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(gzip)))) {
            return NbtIo.read(in, new NbtTagSizeTracker(MAX_PLAN_NBT_BYTES));
        }
    }

    /** The chunks of one plan request received so far. */
    private static final class Transfer {
        private final int id;
        /** Fixed by the first chunk, so a later chunk cannot stretch the transfer past {@link #MAX_CHUNKS}. */
        private final int total;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private int next;

        private Transfer(int id, int total) {
            this.id = id;
            this.total = total;
        }
    }

    /**
     * Sends every unit within {@link #PUSH_RADIUS} blocks of the player, nearest first and capped at
     * {@link #PUSH_LIMIT}. Stale records are pruned before the snapshot is taken, so the client never
     * receives a record that was just dropped.
     */
    public static void push(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Identifier dimension = world.getRegistryKey().getValue();
        BlockPos center = player.getBlockPos();

        QcUnitIndex index = QcUnitIndex.get(world);
        index.prune(world, center, PUSH_RADIUS);

        ServerPlayNetworking.send(player, new QcIndexS2C(dimension, snapshot(index.units(dimension), center)));
        LAST_PUSH.put(player.getUuid(), center);
    }

    private static NbtCompound snapshot(Map<BlockPos, QcUnitRecord> units, BlockPos center) {
        List<QcUnitRecord> nearby = new ArrayList<>();
        for (Map.Entry<BlockPos, QcUnitRecord> entry : units.entrySet()) {
            if (entry.getKey().isWithinDistance(center, PUSH_RADIUS)) {
                nearby.add(entry.getValue());
            }
        }
        nearby.sort(Comparator.comparingDouble(record -> record.origin().getSquaredDistance(center)));

        NbtList list = new NbtList();
        for (int i = 0; i < nearby.size() && i < PUSH_LIMIT; i++) {
            list.add(nearby.get(i).toNbt());
        }

        NbtCompound tag = new NbtCompound();
        tag.put("units", list);
        return tag;
    }
}
