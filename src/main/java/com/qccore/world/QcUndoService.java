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
import java.util.Map;
import java.util.UUID;

/**
 * World undo for applied plans, per player and in memory only (it does not survive a restart).
 *
 * <p>Each entry holds the block states an apply overwrote, first state per position, and the index
 * records it replaced. At most {@link #MAX_ENTRIES} entries and {@link #MAX_BLOCKS} recorded
 * positions are kept per player; the oldest entries are dropped first. Block entity contents (chest
 * inventories, signs) are not recorded, so an undo restores the block but not what was in it.
 */
public final class QcUndoService {

    private static final Logger LOGGER = LoggerFactory.getLogger("qccore");

    static final int MAX_ENTRIES = 10;
    static final int MAX_BLOCKS = 1_500_000;

    private static final Map<UUID, Deque<Entry>> HISTORY = new HashMap<>();

    private QcUndoService() {
    }

    /** One applied plan: the dimension, the overwritten states and the replaced index records. */
    record Entry(Identifier dimension, Map<BlockPos, BlockState> blocks, Map<BlockPos, QcUnitRecord> records) {
    }

    static void push(ServerPlayerEntity player, Entry entry) {
        Deque<Entry> history = HISTORY.computeIfAbsent(player.getUuid(), k -> new ArrayDeque<>());
        history.push(entry);
        int total = 0;
        int kept = 0;
        Iterator<Entry> it = history.iterator();
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

    public static void forget(UUID player) {
        HISTORY.remove(player);
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
        QcWorldOps.restore(world, entry.blocks());
        QcUnitIndex index = QcUnitIndex.get(world);
        for (Map.Entry<BlockPos, QcUnitRecord> record : entry.records().entrySet()) {
            if (record.getValue() == null) {
                index.remove(dimension, record.getKey());
            } else {
                index.put(dimension, record.getKey(), record.getValue());
            }
        }
        LOGGER.info("qccore: {} undid an apply of {} block(s) in {}", player.getName().getString(), entry.blocks().size(), dimension);
        return new QcResult(true, "undid the last apply (" + entry.blocks().size() + " block(s) restored, "
                + history.size() + " more to undo)");
    }
}
