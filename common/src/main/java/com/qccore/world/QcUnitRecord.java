package com.qccore.world;

import com.qccore.core.QcUnit;
import com.qccore.core.nbt.LayersCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

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
 */
public record QcUnitRecord(BlockPos origin, int walls, int gates, int kind, int doors, int part) {

    public static final int BUILDING = 0;
    public static final int ROAD = 1;

    public QcUnitRecord(BlockPos origin, int walls, int gates) {
        this(origin, walls, gates, BUILDING, 0);
    }

    public QcUnitRecord(BlockPos origin, int walls, int gates, int kind, int doors) {
        this(origin, walls, gates, kind, doors, 0);
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
        return tag;
    }

    /** Reads a record back; a malformed position falls back to the origin instead of failing. */
    public static QcUnitRecord fromNbt(CompoundTag tag) {
        int[] pos = tag.getIntArray("pos");
        BlockPos origin = pos.length == 3 ? new BlockPos(pos[0], pos[1], pos[2]) : BlockPos.ZERO;
        return new QcUnitRecord(origin, tag.getByte("w"), tag.getByte("g"), tag.getByte("k"), tag.getByte("d"), tag.getInt("p"));
    }
}
