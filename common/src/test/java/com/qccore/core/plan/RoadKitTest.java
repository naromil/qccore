package com.qccore.core.plan;

import com.qccore.core.UnitPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The road contract of qckit's road.py / QCSYSTEM.md, checked block by block. */
class RoadKitTest {

    private static RoadStyle style() {
        RoadStyle style = new RoadStyle();
        for (int i = 0; i < RoadStyle.LANES; i++) {
            style.floor[i] = "lane" + (i + 1);
        }
        style.joint[0] = "light";
        style.medallion = "medallion";
        style.centre = "centre";
        return style;
    }

    private static Map<UnitPos, RoadStyle> cells(int... xz) {
        Map<UnitPos, RoadStyle> cells = new LinkedHashMap<>();
        RoadStyle style = style();
        for (int i = 0; i < xz.length; i += 2) {
            cells.put(new UnitPos(xz[i], xz[i + 1]), style);
        }
        return cells;
    }

    @Test
    void aStraightStreetIsSevenWideWithLanesOneToFourAndBack() {
        Map<UnitPos, RoadStyle> street = cells(0, 0, 0, 1, 0, 2);
        Map<net.minecraft.core.BlockPos, String> blocks = RoadKit.build(street, Set.of());
        Map<Long, UnitPos> area = RoadKit.networkArea(street.keySet());
        UnitPos middle = new UnitPos(0, 1);
        for (int t = 0; t <= 8; t++) {
            int width = 0;
            for (int i = 0; i <= 8; i++) {
                if (area.containsKey(RoadKit.key(i, 8 + t))) {
                    width++;
                }
            }
            assertEquals(7, width, "row " + t + " of the middle cell");
        }
        assertEquals(RoadKit.piece(RoadKit.arms(middle, street.keySet())), "street");
        // across the middle of the middle cell: 1 2 3 4 3 2 1, planes empty
        String[] expected = {null, "lane1", "lane2", "lane3", "lane4", "lane3", "lane2", "lane1", null};
        for (int x = 0; x <= 8; x++) {
            assertEquals(expected[x], blocks.get(new net.minecraft.core.BlockPos(x, 0, 12)), "x " + x);
        }
        // the joint line every 8 blocks carries the light in lane 1, and the mouth is paved
        assertEquals("light", blocks.get(new net.minecraft.core.BlockPos(1, 0, 8)));
        assertEquals("lane4", blocks.get(new net.minecraft.core.BlockPos(4, 0, 8)));
        // a straight run carries no medallion
        assertFalse(blocks.containsValue("medallion"));
        // one block tall
        for (net.minecraft.core.BlockPos pos : blocks.keySet()) {
            assertEquals(0, pos.getY());
        }
    }

    @Test
    void cornerPostsAndClosedPlanesStayTheBuildings() {
        Map<UnitPos, RoadStyle> street = cells(0, 0);
        Map<net.minecraft.core.BlockPos, String> blocks = RoadKit.build(street, Set.of());
        assertEquals(49, blocks.size());
        for (net.minecraft.core.BlockPos pos : blocks.keySet()) {
            assertTrue(pos.getX() >= 1 && pos.getX() <= 7 && pos.getZ() >= 1 && pos.getZ() <= 7);
        }
    }

    @Test
    void aCrossingHasTheMedallionAndAPlazaPavesItsPost() {
        Map<UnitPos, RoadStyle> cross = cells(1, 1, 0, 1, 2, 1, 1, 0, 1, 2);
        Map<net.minecraft.core.BlockPos, String> blocks = RoadKit.build(cross, Set.of());
        assertEquals("cross", RoadKit.piece(RoadKit.arms(new UnitPos(1, 1), cross.keySet())));
        assertEquals("centre", blocks.get(new net.minecraft.core.BlockPos(12, 0, 12)));
        assertEquals("medallion", blocks.get(new net.minecraft.core.BlockPos(11, 0, 11)));
        assertNull(blocks.get(new net.minecraft.core.BlockPos(8, 0, 8)), "a corner post of a crossing is a building's");

        Map<UnitPos, RoadStyle> plaza = cells(0, 0, 1, 0, 0, 1, 1, 1);
        assertTrue(RoadKit.networkArea(plaza.keySet()).containsKey(RoadKit.key(8, 8)));
    }

    @Test
    void theRoadGivesWayToBuildingColumns() {
        Map<UnitPos, RoadStyle> street = cells(0, 0);
        Set<Long> columns = new HashSet<>();
        columns.add(RoadKit.key(1, 1));
        columns.add(RoadKit.key(4, 4));
        Map<net.minecraft.core.BlockPos, String> blocks = RoadKit.build(street, columns);
        assertEquals(47, blocks.size());
        assertNull(blocks.get(new net.minecraft.core.BlockPos(1, 0, 1)));
    }
}
