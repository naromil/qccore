package com.qccore.world;

import com.qccore.core.QcResult;
import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * World undo and redo for applied plans, per player and in memory only (it does not survive a
 * restart).
 *
 * <p>Each entry holds the block states a change overwrote, first state per position, and the index
 * records it replaced. Undoing an entry swaps it with the world: the states and records standing
 * there right before the undo become a redo entry, and redoing swaps that one back onto the undo
 * stack. A new apply drops the redo stack. At most {@link #MAX_ENTRIES} entries and
 * {@link #MAX_BLOCKS} recorded positions are kept per player and per stack; the oldest entries are
 * dropped first. Block entity contents (chest inventories, signs) are not recorded, so an undo
 * restores the block but not what was in it.
 */
public final class QcUndoService {

    private static final Logger LOGGER = LoggerFactory.getLogger("qccore");

    static final int MAX_ENTRIES = 10;
    static final int MAX_BLOCKS = 1_500_000;

    private static final Map<UUID, Deque<Entry>> HISTORY = new HashMap<>();
    private static final Map<UUID, Deque<Entry>> REDO = new HashMap<>();

    private QcUndoService() {
    }

    /** One applied plan: the dimension, the overwritten states and the replaced index records. */
    record Entry(Identifier dimension, Map<BlockPos, BlockState> blocks, Map<BlockPos, QcUnitRecord> records) {
    }

    /** Records a fresh apply; it becomes the newest undo step and drops everything redoable. */
    static void push(ServerPlayerEntity player, Entry entry) {
        REDO.remove(player.getUuid());
        pushTrimmed(HISTORY, player.getUuid(), entry);
    }

    private static void pushTrimmed(Map<UUID, Deque<Entry>> stacks, UUID player, Entry entry) {
        Deque<Entry> stack = stacks.computeIfAbsent(player, k -> new ArrayDeque<>());
        stack.push(entry);
        long total = 0;
        int kept = 0;
        Iterator<Entry> it = stack.iterator();
        while (it.hasNext()) {
            Entry e = it.next();
            total += e.blocks().size();
            kept++;
            if (kept > MAX_ENTRIES || total > MAX_BLOCKS && kept > 1) {
                it.remove();
            }
        }
    }

    /** How many applies the player can still undo. */
    public static int depth(ServerPlayerEntity player) {
        Deque<Entry> history = HISTORY.get(player.getUuid());
        return history == null ? 0 : history.size();
    }

    /** How many undone applies the player can redo. */
    public static int redoDepth(ServerPlayerEntity player) {
        Deque<Entry> redo = REDO.get(player.getUuid());
        return redo == null ? 0 : redo.size();
    }

    public static void forget(UUID player) {
        HISTORY.remove(player);
        REDO.remove(player);
    }

    /** Reverts the player's most recent apply in the dimension the player is in. */
    public static QcResult undo(ServerPlayerEntity player) {
        if (!player.isCreative()) {
            return new QcResult(false, "creative mode is required to undo a build");
        }
        Deque<Entry> history = HISTORY.get(player.getUuid());
        if (history == null || history.isEmpty()) {
            return new QcResult(false, "nothing to undo");
        }
        ServerWorld world = player.getServerWorld();
        Identifier dimension = world.getRegistryKey().getValue();
        Entry entry = history.peek();
        if (!entry.dimension().equals(dimension)) {
            return new QcResult(false, "the last apply was in " + entry.dimension());
        }
        history.pop();
        pushTrimmed(REDO, player.getUuid(), swap(world, entry));
        LOGGER.info("qccore: {} undid an apply of {} block(s) in {}", player.getName().getString(), entry.blocks().size(), dimension);
        return new QcResult(true, "undid the last apply (" + entry.blocks().size() + " block(s) restored, "
                + history.size() + " more to undo; /qc redo puts it back)");
    }

    /** Re-applies the player's most recently undone apply in the dimension the player is in. */
    public static QcResult redo(ServerPlayerEntity player) {
        if (!player.isCreative()) {
            return new QcResult(false, "creative mode is required to redo a build");
        }
        Deque<Entry> redo = REDO.get(player.getUuid());
        if (redo == null || redo.isEmpty()) {
            return new QcResult(false, "nothing to redo");
        }
        ServerWorld world = player.getServerWorld();
        Identifier dimension = world.getRegistryKey().getValue();
        Entry entry = redo.peek();
        if (!entry.dimension().equals(dimension)) {
            return new QcResult(false, "the last undo was in " + entry.dimension());
        }
        redo.pop();
        pushTrimmed(HISTORY, player.getUuid(), swap(world, entry));
        LOGGER.info("qccore: {} redid an apply of {} block(s) in {}", player.getName().getString(), entry.blocks().size(), dimension);
        return new QcResult(true, "redid the last undone apply (" + entry.blocks().size() + " block(s) written, "
                + redo.size() + " more to redo)");
    }

    /**
     * Writes {@code entry} back into the world and the index, and returns its inverse: the states and
     * records that stood at the same positions right before.
     */
    private static Entry swap(ServerWorld world, Entry entry) {
        Identifier dimension = entry.dimension();
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        for (BlockPos pos : entry.blocks().keySet()) {
            blocks.put(pos, world.getBlockState(pos));
        }
        QcUnitIndex index = QcUnitIndex.get(world);
        Map<BlockPos, QcUnitRecord> records = new LinkedHashMap<>();
        for (BlockPos origin : entry.records().keySet()) {
            records.put(origin, index.units(dimension).get(origin));
        }

        QcWorldOps.restore(world, entry.blocks());
        for (Map.Entry<BlockPos, QcUnitRecord> record : entry.records().entrySet()) {
            if (record.getValue() == null) {
                index.remove(dimension, record.getKey());
            } else {
                index.put(dimension, record.getKey(), record.getValue());
            }
        }
        return new Entry(dimension, blocks, records);
    }
}
