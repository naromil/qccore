package com.qccore.core.plan;

import com.qccore.core.qc.BlockChoice;
import com.qccore.core.qc.Roles;
import com.qccore.core.QcUnit;
import com.qccore.core.UnitSide;
import com.qccore.core.nbt.PlanCodec;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QcPlanTest {

    private static QcUnit unit(QcPlan plan, Cell cell) {
        return PlanCompiler.unit(plan, cell);
    }

    private static Set<Cell> row(int count) {
        Set<Cell> cells = new LinkedHashSet<>();
        for (int x = 0; x < count; x++) {
            cells.add(new Cell(x, 1, 0));
        }
        return cells;
    }

    @Test
    void partitionSetsBothSidesOfEverySharedFace() {
        QcPlan plan = new QcPlan();
        for (Cell cell : row(3)) {
            plan.addUnit(cell, ComponentMask.ALL);
        }
        assertEquals(2, plan.partition(row(3), QcPlan.Partition.GATE));
        QcUnit middle = unit(plan, new Cell(1, 1, 0));
        assertTrue(middle.hasWallE() && middle.isGateE() && middle.hasWallW() && middle.isGateW());
        assertFalse(middle.hasWallN() || middle.hasWallS());
        assertTrue(unit(plan, new Cell(0, 1, 0)).isGateE());
        assertEquals(2, plan.partition(row(3), QcPlan.Partition.WALL));
        assertFalse(unit(plan, new Cell(2, 1, 0)).isGateW());
        assertTrue(unit(plan, new Cell(2, 1, 0)).hasWallW());
        assertEquals(0, plan.partition(row(3), QcPlan.Partition.WALL));
        plan.partition(row(3), QcPlan.Partition.OPEN);
        QcUnit open = unit(plan, new Cell(1, 1, 0));
        assertTrue(!open.hasWallE() && open.isGateE(), "open is stored as the gate flag alone");
        assertEquals("open", QcPlan.partyOverride(open, UnitSide.EAST));
        plan.partition(row(3), QcPlan.Partition.DESIGN);
        assertFalse(unit(plan, new Cell(1, 1, 0)).hasAnyE());
        assertNull(QcPlan.partyOverride(unit(plan, new Cell(1, 1, 0)), UnitSide.EAST));
    }

    @Test
    void removingAUnitDropsItsNeighboursFlagsAndItsMask() {
        QcPlan plan = new QcPlan();
        plan.addUnit(new Cell(0, 1, 0), ComponentMask.FRAMEWORK);
        plan.addUnit(new Cell(1, 1, 0), ComponentMask.ALL);
        plan.partition(row(2), QcPlan.Partition.WALL);
        plan.removeUnit(new Cell(0, 1, 0));
        assertFalse(unit(plan, new Cell(1, 1, 0)).hasWallW());
        assertTrue(plan.masks().isEmpty());
        plan.removeUnit(new Cell(1, 1, 0));
        assertTrue(plan.units().layerKeys().isEmpty());
    }

    @Test
    void aQuarterTurnMovesCellsAndFlagsClockwise() {
        QcPlan plan = new QcPlan();
        plan.addUnit(new Cell(0, 1, 0), ComponentMask.ALL);
        plan.addUnit(new Cell(1, 1, 0), ComponentMask.ROW);
        plan.partition(row(2), QcPlan.Partition.GATE);
        QcPlan turned = plan.transformed(1, false, false, 0, 0, 0);
        // (1, 0) -> (0, 1): the east neighbour becomes the south neighbour
        QcUnit first = unit(turned, new Cell(0, 1, 0));
        QcUnit second = unit(turned, new Cell(0, 1, 1));
        assertNotNull(first);
        assertNotNull(second);
        assertTrue(first.hasWallS() && first.isGateS() && !first.hasWallE());
        assertTrue(second.hasWallN() && second.isGateN());
        assertEquals(ComponentMask.ROW, turned.mask(new Cell(0, 1, 1)));

        QcPlan mirrored = plan.transformed(0, true, false, 0, 0, 0);
        assertTrue(unit(mirrored, new Cell(0, 1, 0)).hasWallW());
        assertTrue(unit(mirrored, new Cell(-1, 1, 0)).hasWallE());

        QcPlan full = plan.transformed(4, false, false, 0, 0, 0);
        assertEquals(PlanCodec.toNbt(plan), PlanCodec.toNbt(full));
    }

    @Test
    void normalizedPutsTheMinimumCellAtTheOriginOfLayerOne() {
        QcPlan plan = new QcPlan();
        plan.addUnit(new Cell(-3, 4, 7), ComponentMask.ALL);
        plan.clears().add(new Cell(-5, 5, 9));
        plan.roads().put(new Cell(-2, 4, 6), "street");
        QcPlan normal = plan.normalized();
        assertTrue(normal.hasUnit(new Cell(2, 1, 1)));
        assertTrue(normal.clears().contains(new Cell(0, 2, 3)));
        assertEquals("street", normal.roads().get(new Cell(3, 1, 0)));
    }

    @Test
    void mergeOverwritesUnitsAndAddsTheRest() {
        QcPlan plan = new QcPlan();
        plan.addUnit(new Cell(0, 1, 0), ComponentMask.FLOOR);
        QcPlan other = new QcPlan();
        other.addUnit(new Cell(0, 1, 0), ComponentMask.ALL);
        other.clears().add(new Cell(4, 1, 4));
        BlockChoice style = new BlockChoice(Roles.ROAD);
        style.set("floor1", "a");
        other.styles().put("s", style);
        plan.merge(other);
        assertEquals(ComponentMask.ALL, plan.mask(new Cell(0, 1, 0)));
        assertTrue(plan.clears().contains(new Cell(4, 1, 4)));
        assertEquals("a", plan.styles().get("s").get("floor1"));

        QcPlan road = new QcPlan();
        road.roads().put(new Cell(0, 1, 0), "s");
        plan.merge(road);
        assertFalse(plan.hasUnit(new Cell(0, 1, 0)), "a merged road replaces the unit in its cell");
        plan.merge(other);
        assertTrue(plan.hasUnit(new Cell(0, 1, 0)));
        assertFalse(plan.roads().containsKey(new Cell(0, 1, 0)), "a merged unit replaces the road in its cell");
    }

    @Test
    void planCodecRoundTrip() {
        QcPlan plan = new QcPlan();
        plan.addUnit(new Cell(0, 1, 0), ComponentMask.FRAMEWORK | ComponentMask.FLOOR);
        plan.addUnit(new Cell(1, 1, 0), ComponentMask.ALL);
        plan.partition(row(2), QcPlan.Partition.GATE);
        plan.clears().add(new Cell(3, 2, 1));
        plan.clearMargin = 3;
        plan.ground = "minecraft:dirt";
        plan.roads().put(new Cell(5, 1, 5), "street");
        BlockChoice style = new BlockChoice(Roles.ROAD);
        style.set("floor1", "minecraft:stone");
        style.set("post1", "minecraft:stone_brick_wall,minecraft:lantern");
        style.set("medallion", "minecraft:chiseled_stone_bricks");
        plan.styles().put("street", style);
        plan.addUnit(new Cell(7, 1, 7), "A");
        plan.buildings().put("A", new BuildingSpec("house", "brick"));
        plan.buildings().get("A").options.put("roof", "gable");

        QcPlan back = PlanCodec.fromNbt(PlanCodec.toNbt(plan));
        assertEquals(PlanCodec.toNbt(plan), PlanCodec.toNbt(back));
        assertEquals(ComponentMask.FRAMEWORK | ComponentMask.FLOOR, back.mask(new Cell(0, 1, 0)));
        assertTrue(unit(back, new Cell(1, 1, 0)).isGateW());
        assertEquals(3, back.clearMargin);
        assertEquals(List.of("minecraft:stone_brick_wall", "minecraft:lantern"), RoadKit.posts(back.styles().get("street").get("post1")));
        assertEquals("A", back.building(new Cell(7, 1, 7)));
        assertEquals("gable", back.buildings().get("A").options.get("roof"));
        assertNull(back.masks().get(new Cell(1, 1, 0)));
        assertEquals(UnitSide.WEST, Flags.opposite(UnitSide.EAST));
    }
}
