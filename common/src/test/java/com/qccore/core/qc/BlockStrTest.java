package com.qccore.core.qc;

import com.qccore.core.plan.RoadKit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Comma lists of ids (post and decor roles), split without cutting a block state apart. */
class BlockStrTest {

    @Test
    void idsSplitsPlainLists() {
        assertEquals(List.of("oak_log", "birch_log", "spruce_log"), BlockStr.ids("oak_log, birch_log,spruce_log"));
    }

    @Test
    void idsDropsBlankEntries() {
        assertEquals(List.of("oak_log"), BlockStr.ids("oak_log,, ,"));
        assertEquals(List.of(), BlockStr.ids(""));
        assertEquals(List.of(), BlockStr.ids(null));
    }

    @Test
    void idsKeepsCommasInsideAState() {
        assertEquals(List.of("minecraft:barrel[facing=up,open=false]", "hay_block"),
                BlockStr.ids("minecraft:barrel[facing=up,open=false],hay_block"));
        assertEquals(List.of("lantern[hanging=false,waterlogged=false]"),
                BlockStr.ids("lantern[hanging=false,waterlogged=false]"));
    }

    @Test
    void roadPostsKeepStatesWhole() {
        assertEquals(List.of("oak_fence", "lantern[hanging=false,waterlogged=false]"),
                RoadKit.posts("oak_fence,lantern[hanging=false,waterlogged=false]"));
    }
}
