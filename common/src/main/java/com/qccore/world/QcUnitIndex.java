package com.qccore.world;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The persistent record of every QC Unit that was built into the world, per dimension.
 *
 * <p>The state is stored by the dimension it was read from, and the per-dimension map inside keeps
 * the data correct no matter which dimension's data directory holds it.
 */
public final class QcUnitIndex extends SavedData {

    private static final Logger LOGGER = LoggerFactory.getLogger("qccore");
    private static final String STATE_ID = "qccore_units";

    private final Map<ResourceLocation, LinkedHashMap<BlockPos, QcUnitRecord>> byDimension = new LinkedHashMap<>();

    public static QcUnitIndex get(ServerLevel world) {
        return world.getDataStorage().computeIfAbsent(QcUnitIndex::read, QcUnitIndex::new, STATE_ID);
    }

    /** The records of one dimension; an unknown dimension has no units. */
    public Map<BlockPos, QcUnitRecord> units(ResourceLocation dimension) {
        LinkedHashMap<BlockPos, QcUnitRecord> units = byDimension.get(dimension);
        return units == null ? Collections.emptyMap() : units;
    }

    public void put(ResourceLocation dimension, BlockPos origin, QcUnitRecord record) {
        byDimension.computeIfAbsent(dimension, k -> new LinkedHashMap<>()).put(origin, record);
        setDirty();
    }

    public void remove(ResourceLocation dimension, BlockPos origin) {
        Map<BlockPos, QcUnitRecord> units = byDimension.get(dimension);
        if (units == null || units.remove(origin) == null) {
            return;
        }
        setDirty();
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
     * building was mined by hand and must no longer be matched against. Road records (from
     * {@code /qc scan}) have no framework at their origin and are never pruned here.
     *
     * @return the number of dropped records
     */
    public int prune(ServerLevel world, BlockPos center, int radius) {
        Map<BlockPos, QcUnitRecord> units = byDimension.get(world.dimension().location());
        if (units == null) {
            return 0;
        }

        int dropped = 0;
        Iterator<Map.Entry<BlockPos, QcUnitRecord>> iterator = units.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, QcUnitRecord> entry = iterator.next();
            BlockPos origin = entry.getKey();
            if (!entry.getValue().isBuilding() || !origin.closerThan(center, radius)) {
                continue;
            }
            if (world.getBlockState(origin).isAir()) {
                iterator.remove();
                dropped++;
            }
        }

        if (dropped > 0) {
            setDirty();
            LOGGER.info("qccore: pruned {} stale unit record(s)", dropped);
        }
        return dropped;
    }

    @Override
    public CompoundTag save(CompoundTag nbt) {
        CompoundTag dimensions = new CompoundTag();
        for (Map.Entry<ResourceLocation, LinkedHashMap<BlockPos, QcUnitRecord>> entry : byDimension.entrySet()) {
            ListTag units = new ListTag();
            for (QcUnitRecord record : entry.getValue().values()) {
                units.add(record.toNbt());
            }
            CompoundTag dimensionTag = new CompoundTag();
            dimensionTag.put("units", units);
            dimensions.put(entry.getKey().toString(), dimensionTag);
        }
        nbt.put("dimensions", dimensions);
        return nbt;
    }

    /** Reads the state back, tolerating missing keys and skipping malformed records. */
    public static QcUnitIndex read(CompoundTag nbt) {
        QcUnitIndex index = new QcUnitIndex();
        if (nbt == null || !nbt.contains("dimensions", Tag.TAG_COMPOUND)) {
            return index;
        }

        CompoundTag dimensions = nbt.getCompound("dimensions");
        for (String key : dimensions.getAllKeys()) {
            ResourceLocation dimension = ResourceLocation.tryParse(key);
            if (dimension == null) {
                continue;
            }
            CompoundTag dimensionTag = dimensions.getCompound(key);
            ListTag units = dimensionTag.getList("units", Tag.TAG_COMPOUND);
            LinkedHashMap<BlockPos, QcUnitRecord> records = new LinkedHashMap<>();
            for (int i = 0; i < units.size(); i++) {
                CompoundTag unitTag = units.getCompound(i);
                int[] pos = unitTag.getIntArray("pos");
                if (pos.length != 3) {
                    continue;
                }
                QcUnitRecord record = QcUnitRecord.fromNbt(unitTag);
                records.put(record.origin(), record);
            }
            index.byDimension.put(dimension, records);
        }

        return index;
    }
}
