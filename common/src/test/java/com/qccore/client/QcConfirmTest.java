package com.qccore.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Deleting or overwriting a template or an export takes a second press of the same action. */
class QcConfirmTest {

    @Test
    void theSecondPressOfTheSameActionConfirmsOnce() {
        QcConfirm confirm = new QcConfirm();
        assertFalse(confirm.confirm("delete:house"), "the first press deleted at once");
        assertTrue(confirm.confirm("delete:house"));
        assertFalse(confirm.confirm("delete:house"), "a confirmed action stayed armed");
    }

    @Test
    void anotherActionOrTargetOrResetDisarms() {
        QcConfirm confirm = new QcConfirm();
        assertFalse(confirm.confirm("delete:house"));
        assertFalse(confirm.confirm("delete:tower"), "armed on one template, confirmed on another");
        assertFalse(confirm.confirm("save:tower"));
        assertFalse(confirm.confirm("delete:tower"));
        confirm.reset();
        assertFalse(confirm.confirm("delete:tower"), "a new selection kept the old arm");
    }
}
