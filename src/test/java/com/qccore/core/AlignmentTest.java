package com.qccore.core;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lattice arithmetic: the delta that moves a value onto a residue, and the residue triple of a
 * position. The values are the ones the in-world re-align depends on.
 */
class AlignmentTest {

    @Test
    void nearestDeltaPicksTheNearestLatticeStep() {
        assertEquals(-2, Alignment.nearestDelta(2, 0));
        assertEquals(-3, Alignment.nearestDelta(0, 5));
        assertEquals(0, Alignment.nearestDelta(3, 3));
    }

    @Test
    void nearestDeltaStaysWithinHalfTheSpacing() {
        for (int value = -20; value <= 20; value++) {
            for (int residue = 0; residue < 8; residue++) {
                int delta = Alignment.nearestDelta(value, residue);
                assertTrue(Math.abs(delta) <= 4, "delta " + delta + " out of range for " + value + "/" + residue);
                assertEquals(residue, Math.floorMod(value + delta, 8), "value " + value + " does not land on " + residue);
            }
        }
    }

    @Test
    void snapMovesToTheNearestLatticeValue() {
        assertEquals(16, Alignment.snap(13, 0));
        assertEquals(13, Alignment.snap(13, 5));
        assertEquals(16, Alignment.snap(Alignment.snap(13, 0), 0));
    }

    @Test
    void residuesAreUnsigned() {
        assertPos(new BlockPos(5, 1, 7), Alignment.residues(new BlockPos(-3, 9, -1)));
        assertPos(new BlockPos(0, 0, 0), Alignment.residues(new BlockPos(16, -64, 32)));
    }

    private static void assertPos(BlockPos expected, BlockPos actual) {
        assertEquals(expected.getX(), actual.getX());
        assertEquals(expected.getY(), actual.getY());
        assertEquals(expected.getZ(), actual.getZ());
    }
}
