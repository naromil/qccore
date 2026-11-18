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
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import java.io.DataInputStream;
import java.util.zip.GZIPInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import com.qccore.world.QcAlignService;
import com.qccore.world.QcBuildService;
import com.qccore.world.QcUnitIndex;
import com.qccore.world.QcUnitRecord;
import com.qccore.net.QcPackets.QcPacket;
import com.qccore.platform.QcPlatform;
import net.minecraft.server.MinecraftServer;
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
     * Heap the decoded plan request may claim, as counted by {@link NbtAccounter}. A plan at
     * every limit (4096 units, 4096 cleared and 4096 road cells, four component structures) stays
     * well below it, while a small gzip stream that inflates to gigabytes is cut off.
     */
    private static final long MAX_PLAN_NBT_BYTES = 32L * 1024 * 1024;

    /** Where and in which dimension each player was last pushed the units around them. */
    private static final Map<UUID, LastPush> LAST_PUSH = new HashMap<>();
    private static final Map<UUID, Transfer> TRANSFERS = new HashMap<>();
    private static int tick;

    private QcServerNetworking() {
    }

    /**
     * Handles one decoded client packet. The loader glue calls this on the server thread; a packet that
     * is not a request of this mod is ignored.
     */
    public static void handle(ServerPlayer player, QcPacket packet) {
        if (packet instanceof QcBuildC2S build) {
            receiveBuild(player, build);
        } else if (packet instanceof QcAlignC2S align) {
            receiveAlign(player, align);
        } else if (packet instanceof QcPlanC2S chunk) {
            receivePlanChunk(player, chunk);
        } else if (packet instanceof QcUndoC2S) {
            QcResult result = QcUndoService.undo(player);
            send(player, new QcResultS2C(result.ok(), result.message()));
            if (result.ok()) {
                push(player);
            }
        } else if (packet instanceof QcRedoC2S) {
            QcResult result = QcUndoService.redo(player);
            send(player, new QcResultS2C(result.ok(), result.message()));
            if (result.ok()) {
                push(player);
            }
        }
    }

    /** A player joined: push the units around them. */
    public static void onJoin(ServerPlayer player) {
        push(player);
    }

    /** A player left: forget their push position, pending transfer and undo history. */
    public static void onLeave(ServerPlayer player) {
        LAST_PUSH.remove(player.getUUID());
        TRANSFERS.remove(player.getUUID());
        QcUndoService.forget(player.getUUID());
    }

    /** Called at the end of every server tick; re-pushes players that moved far enough or changed dimension. */
    public static void onServerTick(MinecraftServer server) {
        tick++;
        if (tick % PUSH_INTERVAL_TICKS != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            LastPush last = LAST_PUSH.get(player.getUUID());
            if (last == null || !last.dimension().equals(player.serverLevel().dimension().location())
                    || !last.pos().closerThan(player.blockPosition(), PUSH_MOVE_DISTANCE)) {
                push(player);
            }
        }
    }

    private static void receiveBuild(ServerPlayer player, QcBuildC2S packet) {
        try {
            UnitLayers layers = LayersCodec.fromNbt(packet.layers());
            BlockPalette palette = PaletteCodec.fromNbt(packet.palette());
            QcResult result = QcBuildService.build(player, packet.anchor(), layers, palette);
            if (!result.ok()) {
                LOGGER.warn("qccore: build request from {} refused: {}", player.getName().getString(), result.message());
            }
            send(player, new QcResultS2C(result.ok(), result.message()));
            if (result.ok()) {
                push(player);
            }
        } catch (RuntimeException e) {
            LOGGER.warn("qccore: rejected build request from {}: {}", player.getName().getString(), e.toString());
            send(player, new QcResultS2C(false, "the build request was malformed"));
        }
    }

    private static void receiveAlign(ServerPlayer player, QcAlignC2S packet) {
        try {
            BlockPalette palette = PaletteCodec.fromNbt(packet.palette());
            QcResult result = QcAlignService.align(player, packet.unit(), packet.a(), packet.b(), packet.c(), palette);
            if (!result.ok()) {
                LOGGER.warn("qccore: align request from {} refused: {}", player.getName().getString(), result.message());
            }
            send(player, new QcResultS2C(result.ok(), result.message()));
            if (result.ok()) {
                push(player);
            }
        } catch (RuntimeException e) {
            LOGGER.warn("qccore: rejected align request from {}: {}", player.getName().getString(), e.toString());
            send(player, new QcResultS2C(false, "the align request was malformed"));
        }
    }

    private static void send(ServerPlayer player, QcPacket packet) {
        QcPlatform.sendToPlayer(player, packet);
    }

    /**
     * Collects the chunks of a plan request and runs it once the last chunk arrived. A chunk of
     * another transfer, one out of order or one announcing a different total restarts the collection
     * (and is refused unless it is a valid first chunk); the remaining chunks of a refused transfer
     * are dropped without another reply. A player out of creative is refused at the first chunk,
     * before anything is buffered.
     */
    private static void receivePlanChunk(ServerPlayer player, QcPlanC2S packet) {
        UUID id = player.getUUID();
        Transfer transfer = TRANSFERS.get(id);
        if (packet.index() == 0 || transfer == null || transfer.id != packet.transfer() || transfer.next != packet.index()
                || transfer.total != packet.total()) {
            if (packet.index() != 0 && transfer == null) {
                // the rest of a transfer that was already refused or abandoned: one reply was enough
                return;
            }
            if (packet.index() != 0 || packet.total() < 1 || packet.total() > MAX_CHUNKS) {
                TRANSFERS.remove(id);
                send(player, new QcResultS2C(false, "the plan request was malformed"));
                return;
            }
            if (!player.isCreative()) {
                // refused before any byte is buffered or decoded; QcPlanService.apply checks it again
                TRANSFERS.remove(id);
                send(player, new QcResultS2C(false, "creative mode is required to build into the world"));
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
            CompoundTag request = decode(transfer.bytes.toByteArray());
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
            send(player, new QcResultS2C(result.ok(), result.message()));
            if (result.ok()) {
                push(player);
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("qccore: rejected plan request from {}: {}", player.getName().getString(), e.toString());
            send(player, new QcResultS2C(false, "the plan request was malformed"));
        }
    }

    /**
     * Inflates and parses a reassembled plan request. Unlike {@link NbtIo#readCompressed} the read is
     * bounded by {@link #MAX_PLAN_NBT_BYTES}; going over it throws a {@link RuntimeException}, which
     * the caller reports as a malformed request.
     */
    static CompoundTag decode(byte[] gzip) throws IOException {
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(gzip)))) {
            return NbtIo.read(in, new NbtAccounter(MAX_PLAN_NBT_BYTES));
        }
    }

    private record LastPush(ResourceLocation dimension, BlockPos pos) {
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
    public static void push(ServerPlayer player) {
        ServerLevel world = player.serverLevel();
        ResourceLocation dimension = world.dimension().location();
        BlockPos center = player.blockPosition();

        QcUnitIndex index = QcUnitIndex.get(world);
        index.prune(world, center, PUSH_RADIUS);

        send(player, new QcIndexS2C(dimension, snapshot(index.units(dimension), center)));
        LAST_PUSH.put(player.getUUID(), new LastPush(dimension, center));
    }

    private static CompoundTag snapshot(Map<BlockPos, QcUnitRecord> units, BlockPos center) {
        List<QcUnitRecord> nearby = new ArrayList<>();
        for (Map.Entry<BlockPos, QcUnitRecord> entry : units.entrySet()) {
            if (entry.getKey().closerThan(center, PUSH_RADIUS)) {
                nearby.add(entry.getValue());
            }
        }
        nearby.sort(Comparator.comparingDouble(record -> record.origin().distSqr(center)));

        ListTag list = new ListTag();
        for (int i = 0; i < nearby.size() && i < PUSH_LIMIT; i++) {
            list.add(nearby.get(i).toNbt());
        }

        CompoundTag tag = new CompoundTag();
        tag.put("units", list);
        return tag;
    }
}
