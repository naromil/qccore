package com.qccore.client;

import com.qccore.core.plan.Cell;
import com.qccore.core.plan.ComponentMask;
import com.qccore.core.plan.QcPlan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** T14: a successful apply's result is recognised, so the applied plan can leave the editor. */
class QcRequestsTest {

    @Test
    void resultsAnswerRequestsInTheOrderTheyWereSent() {
        QcRequests requests = new QcRequests();
        requests.expect(QcRequests.Kind.ALIGN, 3);
        requests.expect(QcRequests.Kind.APPLY, 4);
        // an align sent just before K: its result comes first and must not count as the apply's
        assertEquals(QcRequests.Kind.ALIGN, requests.answered().kind());
        QcRequests.Pending apply = requests.answered();
        assertEquals(QcRequests.Kind.APPLY, apply.kind());
        assertEquals(4, apply.revision());
        // a result nobody waits for (none should come) answers nothing
        assertNull(requests.answered());
    }

    @Test
    void anApplyOfTheSameRevisionStillOnItsWayIsWaiting() {
        QcRequests requests = new QcRequests();
        requests.expect(QcRequests.Kind.UNDO, 7);
        assertFalse(requests.waiting(QcRequests.Kind.APPLY, 7), "an undo is not an apply");
        requests.expect(QcRequests.Kind.APPLY, 7);
        assertTrue(requests.waiting(QcRequests.Kind.APPLY, 7), "K again before the result would send the plan twice");
        assertFalse(requests.waiting(QcRequests.Kind.APPLY, 8), "an edited plan may be applied");
        requests.answered();
        requests.answered();
        assertFalse(requests.waiting(QcRequests.Kind.APPLY, 7), "once answered, the plan may be applied again");
    }

    @Test
    void pendingRequestsAreBounded() {
        QcRequests requests = new QcRequests();
        for (int i = 0; i < QcRequests.LIMIT + 5; i++) {
            requests.expect(QcRequests.Kind.UNDO, i);
        }
        assertEquals(QcRequests.LIMIT, requests.size());
        assertEquals(5, requests.answered().revision());
        requests.clear();
        assertNull(requests.answered());
    }

    @Test
    void historyRevisionMovesOnEveryPlanEdit() {
        QcHistory history = new QcHistory();
        QcPlan plan = new QcPlan();
        int sent = history.revision();
        history.push(plan);
        assertNotEquals(sent, history.revision(), "an edit after sending means the plan is no longer the applied one");
        int afterPush = history.revision();
        history.undo(plan);
        assertNotEquals(afterPush, history.revision());
        int afterUndo = history.revision();
        history.redo(plan);
        assertNotEquals(afterUndo, history.revision());
        int afterRedo = history.revision();
        history.clear();
        assertNotEquals(afterRedo, history.revision());
    }

    @Test
    void emptiedPlanKeepsSettingsButNoUnitsRoadsOrClears() {
        QcPlan plan = new QcPlan();
        plan.addUnit(new Cell(0, 1, 0), ComponentMask.ALL);
        plan.clears().add(new Cell(2, 1, 0));
        plan.clearMargin = 1;
        plan.ground = "minecraft:dirt";
        plan.decorSeed = 77;
        QcPlan empty = plan.emptied();
        assertTrue(empty.isEmpty());
        assertTrue(empty.buildings().isEmpty());
        assertEquals(1, empty.clearMargin);
        assertEquals("minecraft:dirt", empty.ground);
        assertEquals(77, empty.decorSeed);
        assertEquals(plan.styles().keySet(), empty.styles().keySet());
        // the applied plan itself is untouched, so it can go into the undo history
        assertTrue(plan.hasUnit(new Cell(0, 1, 0)));
    }
}
