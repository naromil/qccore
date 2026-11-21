package com.qccore.core.qc;

import com.qccore.core.UnitPos;
import com.qccore.core.plan.Cell;
import com.qccore.core.plan.QcPlan;
import com.qccore.core.plan.RoadKit;
import com.qccore.core.plan.Surroundings;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a plan reads from the standing buildings and roads round it. */
class SurroundingsTest {

    @Test
    void doorFrontsAreTheCellsOnTheDoorSides() {
        // bit0 = NORTH, bit2 = SOUTH
        Surroundings around = new Surroundings(Set.of(), Map.of(new Cell(2, 1, 2), 0b0101));
        assertEquals(Set.of(new Cell(2, 1, 1), new Cell(2, 1, 3)), around.doorFronts());
    }

    @Test
    void doorOpeningsAreTheThreeByThreeApproachOutsideTheDoor() {
        Surroundings east = new Surroundings(Set.of(), Map.of(new Cell(0, 1, 0), 0b0010));
        Set<Long> open = east.doorOpenings(1);
        assertEquals(9, open.size());
        assertTrue(open.contains(RoadKit.key(9, 4)));
        assertTrue(open.contains(RoadKit.key(11, 3)));
        assertFalse(open.contains(RoadKit.key(8, 4)), "the door plane itself is the building's");
        assertTrue(east.doorOpenings(2).isEmpty());

        Surroundings north = new Surroundings(Set.of(), Map.of(new Cell(0, 1, 0), 0b0001));
        assertTrue(north.doorOpenings(1).contains(RoadKit.key(4, -1)));
        assertTrue(north.doorOpenings(1).contains(RoadKit.key(5, -3)));
    }

    @Test
    void aPlannedCellWinsOverAStandingRoad() {
        QcPlan plan = new QcPlan();
        plan.roads().put(new Cell(0, 1, 0), "street");
        Surroundings around = new Surroundings(Set.of(new Cell(0, 1, 0), new Cell(1, 1, 0), new Cell(5, 2, 0)), Map.of());
        assertEquals(Set.of(new UnitPos(1, 0)), around.joinedRoads(plan, 1));
        assertEquals(Set.of(new UnitPos(5, 0)), around.joinedRoads(plan, 2));
    }

    @Test
    void aPlannedRoadRunsIntoAStandingStreet() {
        QcPlan plan = new QcPlan();
        plan.styles().put("street", DistrictTest.road());
        plan.roads().put(new Cell(0, 1, 0), "street");
        Surroundings around = new Surroundings(Set.of(new Cell(1, 1, 0)), Map.of());

        Map<BlockPos, String> alone = District.build(plan).blocks();
        Map<BlockPos, String> joined = District.build(plan, around).blocks();
        // layer 1 sits at y 8; the east mouth (x 8) is paved only when the street is there
        assertNull(alone.get(new BlockPos(8, 8, 4)));
        assertNotNull(joined.get(new BlockPos(8, 8, 4)));
        for (BlockPos p : joined.keySet()) {
            assertTrue(p.getX() <= 8, "nothing is written over the standing street: " + p);
        }
    }

    @Test
    void aStandingBuildingKeepsItsCellAndSharedPlane() {
        QcPlan plan = DistrictTest.house("house", 2, 1, 1);
        Surroundings around = new Surroundings(Set.of(), Map.of(), Set.of(new Cell(2, 1, 0)));
        Map<BlockPos, String> alone = District.build(plan).blocks();
        Map<BlockPos, String> next = District.build(plan, around).blocks();
        assertTrue(alone.keySet().stream().anyMatch(p -> inCell(p, 2, 1, 0)), "without it the house writes its east plane");
        for (BlockPos p : next.keySet()) {
            assertFalse(inCell(p, 2, 1, 0), "a block written into the standing building: " + p);
        }
        assertNotNull(next.get(new BlockPos(0, 9, 0)), "the house itself still stands");
    }

    @Test
    void aStandingBuildingTheSamePlanRebuildsIsNotKept() {
        QcPlan plan = DistrictTest.house("house", 2, 1, 1);
        Surroundings around = new Surroundings(Set.of(), Map.of(), Set.of(new Cell(0, 1, 0), new Cell(1, 1, 0)));
        assertEquals(District.build(plan).blocks(), District.build(plan, around).blocks());
    }

    @Test
    void outwardDecorationYieldsToAStandingRoadBelowHeadHeight() {
        QcPlan plan = DistrictTest.house("house", 2, 1, 2);
        Surroundings around = new Surroundings(Set.of(new Cell(0, 1, -1), new Cell(1, 1, -1)), Map.of());
        for (BlockPos p : District.build(plan, around).blocks().keySet()) {
            boolean overRoad = p.getZ() < 0 && p.getZ() >= -8 && p.getX() >= 0 && p.getX() <= 16;
            assertFalse(overRoad && p.getY() < 12, "decoration below head height over the standing road: " + p);
        }
    }

    @Test
    void aStandingDoorKeepsItsWholeApproach() {
        // bit3 = WEST: the step is one block west of the cell's x 0 plane, 3 wide, 2 high
        Surroundings around = new Surroundings(Set.of(), Map.of(new Cell(3, 1, 0), 0b1000), Set.of(new Cell(3, 1, 0)));
        Map<BlockPos, String> zone = District.doorZones(Map.of(), around);
        assertEquals(6, zone.size());
        for (int z = 3; z <= 5; z++) {
            for (int y = 9; y <= 10; y++) {
                assertEquals("*", zone.get(new BlockPos(23, y, z)));
            }
        }
    }

    @Test
    void nothingOutwardLandsOnAPlannedDoorStep() {
        QcPlan plan = DistrictTest.house("house", 3, 1, 2);
        Map<BlockPos, String> b = District.build(plan).blocks();
        long doors = b.entrySet().stream().filter(e -> e.getValue().contains("_door") && e.getKey().getZ() == 8).count();
        assertTrue(doors > 0);
        b.forEach((p, s) -> {
            if (s.contains("_door") && p.getZ() == 8) {
                String step = b.get(new BlockPos(p.getX(), p.getY(), 9));
                assertNull(step, "outward block on the door step at " + p.getX() + "," + p.getY() + ",9");
            }
        });
    }

    private static boolean inCell(BlockPos p, int cx, int layer, int cz) {
        return p.getX() >= cx * 8 && p.getX() <= cx * 8 + 8 && p.getY() >= layer * 8 && p.getY() <= layer * 8 + 8
                && p.getZ() >= cz * 8 && p.getZ() <= cz * 8 + 8;
    }

    @Test
    void noSurroundingsCompilesAsBefore() {
        QcPlan plan = DistrictTest.house("house", 2, 1, 1);
        assertEquals(District.build(plan).blocks(), District.build(plan, Surroundings.NONE).blocks());
    }
}
