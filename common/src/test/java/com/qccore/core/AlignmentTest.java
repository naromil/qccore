package com.qccore.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;

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
    void nearestLayerPicksTheClosestFloorPlaneAndTiesGoLower() {
        int anchorY = 64;
        // layer 0 spans y 64..71: local y 0..4 stays on layer 0, 5..7 goes to layer 1
        assertEquals(0, Alignment.nearestLayer(64, anchorY));     // d = 0
        assertEquals(0, Alignment.nearestLayer(68, anchorY));     // d = 4, tie -> lower
        assertEquals(1, Alignment.nearestLayer(69, anchorY));     // d = 5
        assertEquals(1, Alignment.nearestLayer(71, anchorY));     // d = 7
        assertEquals(1, Alignment.nearestLayer(72, anchorY));     // d = 0 of layer 1
        // below the anchor (negative layers) and an anchor off the 8-grid
        assertEquals(-1, Alignment.nearestLayer(56, anchorY));    // d = 0 of layer -1
        assertEquals(0, Alignment.nearestLayer(61, anchorY));     // d = 5 of layer -1
        assertEquals(2, Alignment.nearestLayer(23, 3));           // 23 - 3 = 20: layer 2, d = 4
        assertEquals(3, Alignment.nearestLayer(24, 3));           // d = 5
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
