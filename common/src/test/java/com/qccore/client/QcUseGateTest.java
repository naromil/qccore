package com.qccore.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** T13: one fresh right-click is one shape point, even when vanilla calls use twice in that tick. */
class QcUseGateTest {

    @Test
    void aFreshPressGivesAMultiPointShapeOnePoint() {
        QcUseGate gate = new QcUseGate();
        // tick 1: the queued click, then the held-key branch of the same handleKeybinds call
        assertTrue(gate.accept(true));
        assertFalse(gate.accept(true), "the same press took a second point (line finished as one cell)");
        gate.endTick(true);
        // tick 2: still held, the held-key branch repeats
        assertFalse(gate.accept(true));
        gate.endTick(false);
        // tick 3: released and pressed again between ticks: the second point
        assertTrue(gate.accept(true));
        assertFalse(gate.accept(true));
    }

    @Test
    void aQuickClickBetweenTicksStillCounts() {
        QcUseGate gate = new QcUseGate();
        gate.endTick(false);
        // pressed and released before the tick: only the queued click arrives, key no longer down
        assertTrue(gate.accept(true));
        gate.endTick(false);
        assertTrue(gate.accept(true));
    }

    @Test
    void aSingleCellShapePaintsWhileHeld() {
        QcUseGate gate = new QcUseGate();
        assertTrue(gate.accept(false));
        assertTrue(gate.accept(false));
        gate.endTick(true);
        assertTrue(gate.accept(false));
    }
}
