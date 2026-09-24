package com.qccore.world;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The persistent record of every QC Unit that was built into the world, per dimension.
 *
 * <p>The state is stored by the dimension it was read from, and the per-dimension map inside keeps
 * the data correct no matter which dimension's data directory holds it.
 */
public final class QcUnitIndex extends PersistentState {

    private static final Logger LOGGER = LoggerFactory.getLogger("qccore");
    private static final String STATE_ID = "qccore_units";

    private final Map<Identifier, LinkedHashMap<BlockPos, QcUnitRecord>> byDimension = new LinkedHashMap<>();

    public static QcUnitIndex get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(QcUnitIndex::read, QcUnitIndex::new, STATE_ID);
    }

    /** The records of one dimension; an unknown dimension has no units. */
    public Map<BlockPos, QcUnitRecord> units(Identifier dimension) {
        LinkedHashMap<BlockPos, QcUnitRecord> units = byDimension.get(dimension);
        return units == null ? Collections.emptyMap() : units;
    }

    public void put(Identifier dimension, BlockPos origin, QcUnitRecord record) {
        byDimension.computeIfAbsent(dimension, k -> new LinkedHashMap<>()).put(origin, record);
        markDirty();
    }

    public void remove(Identifier dimension, BlockPos origin) {
        Map<BlockPos, QcUnitRecord> units = byDimension.get(dimension);
        if (units == null || units.remove(origin) == null) {
            return;
        }
        markDirty();
    }

    public int totalUnits() {
        int total = 0;
        for (Map<BlockPos, QcUnitRecord> units : byDimension.values()) {
            total += units.size();
        }
        return total;
    }

    /**
     * Drops the records within {@code radius} blocks of {@code center} whose origin block is air: a
     * built unit always carries a framework block at its origin corner, so an air origin means the
     * building was mined by hand and must no longer be matched against.
     *
     * @return the number of dropped records
     */
    public int prune(ServerWorld world, BlockPos center, int radius) {
        Map<BlockPos, QcUnitRecord> units = byDimension.get(world.getRegistryKey().getValue());
        if (units == null) {
            return 0;
        }

        int dropped = 0;
        Iterator<Map.Entry<BlockPos, QcUnitRecord>> iterator = units.entrySet().iterator();
        while (iterator.hasNext()) {
            BlockPos origin = iterator.next().getKey();
            if (!origin.isWithinDistance(center, radius)) {
                continue;
            }
            if (world.getBlockState(origin).isAir()) {
                iterator.remove();
                dropped++;
            }
        }

        if (dropped > 0) {
            markDirty();
            LOGGER.info("qccore: pruned {} stale unit record(s)", dropped);
        }
        return dropped;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound dimensions = new NbtCompound();
        for (Map.Entry<Identifier, LinkedHashMap<BlockPos, QcUnitRecord>> entry : byDimension.entrySet()) {
            NbtList units = new NbtList();
            for (QcUnitRecord record : entry.getValue().values()) {
                units.add(record.toNbt());
            }
            NbtCompound dimensionTag = new NbtCompound();
            dimensionTag.put("units", units);
            dimensions.put(entry.getKey().toString(), dimensionTag);
        }
        nbt.put("dimensions", dimensions);
        return nbt;
    }

    /** Reads the state back, tolerating missing keys and skipping malformed records. */
    public static QcUnitIndex read(NbtCompound nbt) {
        QcUnitIndex index = new QcUnitIndex();
        if (nbt == null || !nbt.contains("dimensions", NbtElement.COMPOUND_TYPE)) {
            return index;
        }

        NbtCompound dimensions = nbt.getCompound("dimensions");
        for (String key : dimensions.getKeys()) {
            Identifier dimension = Identifier.tryParse(key);
            if (dimension == null) {
                continue;
            }
            NbtCompound dimensionTag = dimensions.getCompound(key);
            NbtList units = dimensionTag.getList("units", NbtElement.COMPOUND_TYPE);
            LinkedHashMap<BlockPos, QcUnitRecord> records = new LinkedHashMap<>();
            for (int i = 0; i < units.size(); i++) {
                NbtCompound unitTag = units.getCompound(i);
                int[] pos = unitTag.getIntArray("pos");
                if (pos.length != 3) {
                    continue;
                }
                QcUnitRecord record = new QcUnitRecord(new BlockPos(pos[0], pos[1], pos[2]),
                        unitTag.getByte("w"), unitTag.getByte("g"));
                records.put(record.origin(), record);
            }
            index.byDimension.put(dimension, records);
        }

        return index;
    }
}
