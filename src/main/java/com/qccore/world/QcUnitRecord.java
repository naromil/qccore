package com.qccore.world;

import com.qccore.core.QcUnit;
import com.qccore.core.nbt.LayersCodec;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

/**
 * One placed QC Unit as the world index remembers it: the origin corner of its 9x9x9 cell and the
 * wall/gate flags.
 *
 * <p>Bit layout (identical to the layer wire format): <b>bit0 = NORTH, bit1 = EAST, bit2 = SOUTH,
 * bit3 = WEST</b>.
 */
public record QcUnitRecord(BlockPos origin, int walls, int gates) {

    public QcUnit toUnit() {
        QcUnit unit = new QcUnit();
        LayersCodec.apply(unit, walls, gates);
        return unit;
    }

    public static QcUnitRecord of(BlockPos origin, QcUnit unit) {
        return new QcUnitRecord(origin, LayersCodec.bits(unit, false), LayersCodec.bits(unit, true));
    }

    public NbtCompound toNbt() {
        NbtCompound tag = new NbtCompound();
        tag.putIntArray("pos", new int[]{origin.getX(), origin.getY(), origin.getZ()});
        tag.putByte("w", (byte) walls);
        tag.putByte("g", (byte) gates);
        return tag;
    }

    /** Reads a record back; a malformed position falls back to the origin instead of failing. */
    public static QcUnitRecord fromNbt(NbtCompound tag) {
        int[] pos = tag.getIntArray("pos");
        BlockPos origin = pos.length == 3 ? new BlockPos(pos[0], pos[1], pos[2]) : BlockPos.ORIGIN;
        return new QcUnitRecord(origin, tag.getByte("w"), tag.getByte("g"));
    }
}
