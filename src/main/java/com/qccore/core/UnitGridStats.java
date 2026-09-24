package com.qccore.core;

/**
 * What the unit grid scan found in an imported structure: the south-east corner unit the block ids
 * are probed from (-1 in all three fields when no grid was found) and the average unit position the
 * imported layout gets centred around.
 */
public record UnitGridStats(int maxUnitX, int maxUnitY, int maxUnitZ, int avgDx, int avgDz, int unitCount) {

    public static final UnitGridStats EMPTY = new UnitGridStats(-1, -1, -1, 0, 0, 0);

    public boolean hasSoutheastCorner() {
        return maxUnitX != -1 && maxUnitY != -1 && maxUnitZ != -1;
    }
}
