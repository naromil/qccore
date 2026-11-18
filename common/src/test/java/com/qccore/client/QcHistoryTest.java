package com.qccore.client;

import com.qccore.core.plan.Cell;
import com.qccore.core.plan.ComponentMask;
import com.qccore.core.plan.QcPlan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QcHistoryTest {

    private static final Cell A = new Cell(0, 1, 0);
    private static final Cell B = new Cell(1, 1, 0);

    /** Mirrors {@code QcInteraction.edit}: snapshot, change, push the snapshot. */
    private static QcPlan add(QcHistory history, QcPlan plan, Cell cell) {
        QcPlan before = plan.copy();
        plan.addUnit(cell, ComponentMask.ALL);
        history.push(before);
        return plan;
    }

    @Test
    void undoAndRedoWalkTheSnapshots() {
        QcHistory history = new QcHistory();
        QcPlan plan = add(history, new QcPlan(), A);
        plan = add(history, plan, B);
        assertEquals(2, history.undoDepth());

        plan = history.undo(plan);
        assertTrue(plan.hasUnit(A));
        assertFalse(plan.hasUnit(B));
        plan = history.undo(plan);
        assertTrue(plan.isEmpty());
        assertNull(history.undo(plan));
        assertEquals(2, history.redoDepth());

        plan = history.redo(plan);
        assertTrue(plan.hasUnit(A) && !plan.hasUnit(B));
        plan = history.redo(plan);
        assertTrue(plan.hasUnit(A) && plan.hasUnit(B));
        assertNull(history.redo(plan));
    }

    @Test
    void aNewEditDropsTheRedoBranch() {
        QcHistory history = new QcHistory();
        QcPlan plan = add(history, new QcPlan(), A);
        plan = history.undo(plan);
        assertEquals(1, history.redoDepth());
        add(history, plan, B);
        assertEquals(0, history.redoDepth());
    }

    @Test
    void snapshotsAreCopiesNotTheLivePlan() {
        QcHistory history = new QcHistory();
        QcPlan plan = new QcPlan();
        history.push(plan);
        plan.addUnit(A, ComponentMask.ALL);
        assertTrue(history.undo(plan).isEmpty());
    }

    @Test
    void depthIsCappedAtOneHundred() {
        QcHistory history = new QcHistory();
        QcPlan plan = new QcPlan();
        for (int x = 0; x < 150; x++) {
            plan = add(history, plan, new Cell(x, 1, 0));
        }
        assertEquals(100, history.undoDepth());
        for (int i = 0; i < 100; i++) {
            plan = history.undo(plan);
        }
        // the oldest 50 steps fell off: the earliest reachable plan still holds units 0..49
        assertEquals(50, plan.cells().size());
        assertNull(history.undo(plan));
    }
}
