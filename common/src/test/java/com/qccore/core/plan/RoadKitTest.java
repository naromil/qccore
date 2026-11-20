package com.qccore.core.plan;

import com.qccore.core.UnitPos;
import com.qccore.core.qc.BlockChoice;
import com.qccore.core.qc.Roles;
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

    private static BlockChoice style() {
        BlockChoice style = new BlockChoice(Roles.ROAD);
        for (int lane = 1; lane <= 4; lane++) {
            style.set("floor" + lane, "lane" + lane);
        }
        style.set("joint1", "light");
        style.set("medallion", "medallion");
        style.set("centre", "centre");
        return style;
    }

    private static Map<UnitPos, BlockChoice> cells(int... xz) {
        Map<UnitPos, BlockChoice> cells = new LinkedHashMap<>();
        BlockChoice style = style();
        for (int i = 0; i < xz.length; i += 2) {
            cells.put(new UnitPos(xz[i], xz[i + 1]), style);
        }
        return cells;
    }

    @Test
    void aStraightStreetIsSevenWideWithLanesOneToFourAndBack() {
        Map<UnitPos, BlockChoice> street = cells(0, 0, 0, 1, 0, 2);
        Map<net.minecraft.core.BlockPos, String> blocks = RoadKit.build(street, Set.of(), Set.of());
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
        Map<UnitPos, BlockChoice> street = cells(0, 0);
        Map<net.minecraft.core.BlockPos, String> blocks = RoadKit.build(street, Set.of(), Set.of());
        assertEquals(49, blocks.size());
        for (net.minecraft.core.BlockPos pos : blocks.keySet()) {
            assertTrue(pos.getX() >= 1 && pos.getX() <= 7 && pos.getZ() >= 1 && pos.getZ() <= 7);
        }
    }

    @Test
    void aCrossingHasTheMedallionAndAPlazaPavesItsPost() {
        Map<UnitPos, BlockChoice> cross = cells(1, 1, 0, 1, 2, 1, 1, 0, 1, 2);
        Map<net.minecraft.core.BlockPos, String> blocks = RoadKit.build(cross, Set.of(), Set.of());
        assertEquals("cross", RoadKit.piece(RoadKit.arms(new UnitPos(1, 1), cross.keySet())));
        assertEquals("centre", blocks.get(new net.minecraft.core.BlockPos(12, 0, 12)));
        assertEquals("medallion", blocks.get(new net.minecraft.core.BlockPos(11, 0, 11)));
        assertNull(blocks.get(new net.minecraft.core.BlockPos(8, 0, 8)), "a corner post of a crossing is a building's");

        Map<UnitPos, BlockChoice> plaza = cells(0, 0, 1, 0, 0, 1, 1, 1);
        assertTrue(RoadKit.networkArea(plaza.keySet()).containsKey(RoadKit.key(8, 8)));
    }

    @Test
    void theRoadGivesWayToBuildingColumns() {
        Map<UnitPos, BlockChoice> street = cells(0, 0);
        Set<Long> columns = new HashSet<>();
        columns.add(RoadKit.key(1, 1));
        columns.add(RoadKit.key(4, 4));
        Map<net.minecraft.core.BlockPos, String> blocks = RoadKit.build(street, Set.of(), columns);
        assertEquals(47, blocks.size());
        assertNull(blocks.get(new net.minecraft.core.BlockPos(1, 0, 1)));
    }

    @Test
    void aRoadJoiningAStandingRoadPavesTheMouthAndWritesNothingInsideIt() {
        Map<UnitPos, BlockChoice> end = cells(0, 0);
        Set<UnitPos> standing = Set.of(new UnitPos(1, 0));
        Map<net.minecraft.core.BlockPos, String> alone = RoadKit.build(end, Set.of(), Set.of());
        Map<net.minecraft.core.BlockPos, String> joined = RoadKit.build(end, standing, Set.of(), Set.of());
        // alone the east plane is a closed side; joined, the planned cell paves the mouth (x 8)
        assertNull(alone.get(new net.minecraft.core.BlockPos(8, 0, 4)));
        assertEquals("lane4", joined.get(new net.minecraft.core.BlockPos(8, 0, 4)));
        // lanes run on into the standing street instead of turning back at an edge
        assertEquals("lane4", joined.get(new net.minecraft.core.BlockPos(7, 0, 4)));
        assertEquals("lane1", alone.get(new net.minecraft.core.BlockPos(7, 0, 4)));
        for (net.minecraft.core.BlockPos p : joined.keySet()) {
            assertTrue(p.getX() <= 8, "nothing written inside the standing cell: " + p);
        }
        assertEquals(Set.of(), RoadKit.networkArea(Set.of(), standing).keySet());
    }

    @Test
    void standingRoadCellsCountForPlazasAndMedallions() {
        Map<UnitPos, BlockChoice> one = cells(0, 0);
        Set<UnitPos> bend = Set.of(new UnitPos(1, 0), new UnitPos(0, 1));
        Map<net.minecraft.core.BlockPos, String> corner = RoadKit.build(one, bend, Set.of(), Set.of());
        assertEquals("medallion", corner.get(new net.minecraft.core.BlockPos(3, 0, 3)));
        assertEquals("centre", corner.get(new net.minecraft.core.BlockPos(4, 0, 4)));
        assertNull(corner.get(new net.minecraft.core.BlockPos(8, 0, 8)), "no plaza post without the fourth cell");

        Set<UnitPos> square = Set.of(new UnitPos(1, 0), new UnitPos(0, 1), new UnitPos(1, 1));
        assertTrue(RoadKit.networkArea(one.keySet(), square).containsKey(RoadKit.key(8, 8)),
                "the post shared with three standing cells is paved by the planned one");
    }
}
