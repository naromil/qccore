package com.qccore.world;

import com.qccore.core.QcUnit;
import com.qccore.core.nbt.LayersCodec;
import com.qccore.core.qc.BlockChoice;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * One placed QC Unit as the world index remembers it: the origin corner of its 9x9x9 cell, the
 * wall/gate flags, its kind and its door sides.
 *
 * <p>Bit layout (identical to the layer wire format): <b>bit0 = NORTH, bit1 = EAST, bit2 = SOUTH,
 * bit3 = WEST</b>. {@code kind} is {@link #BUILDING} for every unit the mod applied and {@link #ROAD}
 * for road cells, recorded by a plan's apply or found by {@code /qc scan}; {@code doors} (same bits) is filled in by the scan, so a
 * later plan knows where an existing building opens onto the street. Both are left out of the NBT when
 * zero, so records written before them read back unchanged. {@code part} is the
 * {@link com.qccore.core.plan.Join#signature design signature} of the qckit building an apply built
 * the cell as (0 for scanned, legacy and road cells), so a later part of the same building joins it.
 * {@code road} is the road style (its blocks, role by role) an apply laid a road cell with, so a later
 * road that meets the cell rebuilds it in its own style and only changes how it connects; {@code null}
 * for building cells and for road cells scanned or applied before road styles were recorded.
 */
public record QcUnitRecord(BlockPos origin, int walls, int gates, int kind, int doors, int part, BlockChoice road) {

    public static final int BUILDING = 0;
    public static final int ROAD = 1;

    public QcUnitRecord(BlockPos origin, int walls, int gates) {
        this(origin, walls, gates, BUILDING, 0);
    }

    public QcUnitRecord(BlockPos origin, int walls, int gates, int kind, int doors) {
        this(origin, walls, gates, kind, doors, 0);
    }

    public QcUnitRecord(BlockPos origin, int walls, int gates, int kind, int doors, int part) {
        this(origin, walls, gates, kind, doors, part, null);
    }

    /** A road cell an apply laid with the road style {@code style} (copied; {@code null} = not known). */
    public static QcUnitRecord road(BlockPos origin, BlockChoice style) {
        return new QcUnitRecord(origin, 0, 0, ROAD, 0, 0, style == null ? null : style.copy());
    }

    public boolean isBuilding() {
        return kind == BUILDING;
    }

    public QcUnit toUnit() {
        QcUnit unit = new QcUnit();
        LayersCodec.apply(unit, walls, gates);
        return unit;
    }

    public static QcUnitRecord of(BlockPos origin, QcUnit unit) {
        return of(origin, unit, 0);
    }

    /** An applied building cell, built as part of the building whose design signature is {@code part}. */
    public static QcUnitRecord of(BlockPos origin, QcUnit unit, int part) {
        return new QcUnitRecord(origin, LayersCodec.bits(unit, false), LayersCodec.bits(unit, true), BUILDING, 0, part);
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putIntArray("pos", new int[]{origin.getX(), origin.getY(), origin.getZ()});
        tag.putByte("w", (byte) walls);
        tag.putByte("g", (byte) gates);
        if (kind != BUILDING) tag.putByte("k", (byte) kind);
        if (doors != 0) tag.putByte("d", (byte) doors);
        if (part != 0) tag.putInt("p", part);
        if (road != null) tag.put("r", road.toNbt());
        return tag;
    }

    /** Reads a record back; a malformed position falls back to the origin instead of failing. */
    public static QcUnitRecord fromNbt(CompoundTag tag) {
        int[] pos = tag.getIntArray("pos");
        BlockPos origin = pos.length == 3 ? new BlockPos(pos[0], pos[1], pos[2]) : BlockPos.ZERO;
        BlockChoice road = tag.contains("r", Tag.TAG_COMPOUND) ? BlockChoice.fromNbt(tag.getCompound("r")) : null;
        return new QcUnitRecord(origin, tag.getByte("w"), tag.getByte("g"), tag.getByte("k"), tag.getByte("d"), tag.getInt("p"), road);
    }

    /**
     * Many records in one tag for the client push. A street repeats one road style in every cell, so
     * each distinct style is written once under {@code styles} and a record names it by index
     * ({@code ri}), which keeps the packet small.
     */
    public static CompoundTag writeAll(List<QcUnitRecord> records) {
        Map<BlockChoice, Integer> table = new LinkedHashMap<>();
        ListTag units = new ListTag();
        for (QcUnitRecord record : records) {
            CompoundTag tag = record.toNbt();
            if (record.road != null) {
                tag.remove("r");
                tag.putInt("ri", table.computeIfAbsent(record.road, style -> table.size()));
            }
            units.add(tag);
        }
        ListTag styles = new ListTag();
        table.keySet().forEach(style -> styles.add(style.toNbt()));
        CompoundTag out = new CompoundTag();
        out.put("units", units);
        out.put("styles", styles);
        return out;
    }

    /** Reads what {@link #writeAll} wrote. */
    public static List<QcUnitRecord> readAll(CompoundTag tag) {
        ListTag styles = tag.getList("styles", Tag.TAG_COMPOUND);
        ListTag units = tag.getList("units", Tag.TAG_COMPOUND);
        List<QcUnitRecord> out = new ArrayList<>();
        for (int i = 0; i < units.size(); i++) {
            CompoundTag unit = units.getCompound(i);
            if (unit.contains("ri") && unit.getInt("ri") < styles.size()) {
                unit = unit.copy();
                unit.put("r", styles.getCompound(unit.getInt("ri")));
            }
            out.add(fromNbt(unit));
        }
        return out;
    }
}
