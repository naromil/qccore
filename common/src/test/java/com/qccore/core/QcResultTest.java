package com.qccore.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class QcResultTest {

    @Test
    void firstSaysHowManyMoreFollow() {
        assertNull(QcResult.first(List.of()));
        assertEquals("style 'a' has no block for frame", QcResult.first(List.of("style 'a' has no block for frame")));
        assertEquals("style 'a' has no block for frame (+2 more)",
                QcResult.first(List.of("style 'a' has no block for frame", "style 'b' has no block for wall",
                        "road 'r' has no block for floor0")));
    }
}
