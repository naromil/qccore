package com.qccore.core;

import net.minecraft.util.math.BlockPos;

/**
 * Lattice arithmetic: a QC Unit building can be aligned to a residue per axis, so every unit origin
 * satisfies {@code x % 8 == a}, {@code y % 8 == b}, {@code z % 8 == c}.
 */
public final class Alignment {

    private Alignment() {
    }

    /** The signed distance from {@code value} to the nearest {@code value'} with {@code value' % 8 == residue}. */
    public static int nearestDelta(int value, int residue) {
        int d = Math.floorMod(residue - value, 8);
        return d > 4 ? d - 8 : d;
    }

    /** The nearest value on the lattice {@code value' % 8 == residue}. */
    public static int snap(int value, int residue) {
        return value + nearestDelta(value, residue);
    }

    /** The residue triple of a position. */
    public static BlockPos residues(BlockPos p) {
        return new BlockPos(Math.floorMod(p.getX(), 8), Math.floorMod(p.getY(), 8), Math.floorMod(p.getZ(), 8));
    }
}
