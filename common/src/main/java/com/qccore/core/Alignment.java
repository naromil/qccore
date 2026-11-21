package com.qccore.core;

import net.minecraft.core.BlockPos;

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

    /**
     * The layer whose floor plane (local y 0) lies nearest a block at {@code blockY}: the block's own
     * layer when it sits at local y 0..4, the layer above at 5..7 (a tie at 4 goes to the lower
     * layer). The lattice itself never moves; this only picks which of its layers to use.
     */
    public static int nearestLayer(int blockY, int anchorY) {
        int base = Math.floorDiv(blockY - anchorY, 8);
        return Math.floorMod(blockY - anchorY, 8) <= 4 ? base : base + 1;
    }

    /** The residue triple of a position. */
    public static BlockPos residues(BlockPos p) {
        return new BlockPos(Math.floorMod(p.getX(), 8), Math.floorMod(p.getY(), 8), Math.floorMod(p.getZ(), 8));
    }
}
