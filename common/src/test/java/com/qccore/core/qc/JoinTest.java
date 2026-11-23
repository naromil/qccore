package com.qccore.core.qc;

import com.qccore.core.plan.Cell;
import com.qccore.core.plan.Join;
import com.qccore.core.plan.QcPlan;
import com.qccore.core.plan.Surroundings;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A new part of a building rebuilds the standing parts of the same design it touches (T17). */
class JoinTest {

    /** The 3x1 house of {@link DistrictTest#house} with its east column of cells left out. */
    private static QcPlan westPart() {
        QcPlan plan = DistrictTest.house("house", 3, 1, 2);
        plan.removeUnit(new Cell(2, 1, 0));
        plan.removeUnit(new Cell(2, 2, 0));
        plan.pruneBuildings();
        return plan;
    }

    private static Surroundings standing(int signature, Cell... cells) {
        Map<Cell, Surroundings.Part> parts = new java.util.LinkedHashMap<>();
        for (Cell cell : cells) {
            parts.put(cell, new Surroundings.Part(signature, 0, 0));
        }
        return new Surroundings(Set.of(), Map.of(), Set.of(cells), parts);
    }

    @Test
    void theSignatureFollowsTheDesignAndItsBlocks() {
        QcPlan a = DistrictTest.house("house", 2, 1, 1);
        QcPlan b = DistrictTest.house("house", 3, 1, 2);
        int sig = Join.signature(a, "A");
        assertNotEquals(0, sig);
        assertEquals(sig, Join.signature(b, "A"), "the cells don't count, only the design");
        b.styles().get("s").set("wall", "minecraft:stone");
        assertNotEquals(sig, Join.signature(b, "A"), "another wall block is another design");
        assertEquals(0, Join.signature(a, "Z"));
    }

    @Test
    void aNewPartBuildsAsIfBothWereBuiltTogether() {
        QcPlan west = westPart();
        QcPlan whole = DistrictTest.house("house", 3, 1, 2);
        Surroundings around = standing(Join.signature(west, "A"), new Cell(2, 1, 0), new Cell(2, 2, 0));

        QcPlan joined = Join.join(west, around);
        assertEquals(Set.of(new Cell(2, 1, 0), new Cell(2, 2, 0)), Join.joined(west, joined));
        assertEquals("A", joined.building(new Cell(2, 2, 0)));
        assertEquals(District.build(whole).blocks(), District.build(joined, around).blocks());
        assertTrue(west.unitBuildings().size() == 4, "the player's plan itself is left as it was");
    }

    @Test
    void anotherDesignStillStands() {
        QcPlan west = westPart();
        Surroundings around = standing(Join.signature(west, "A") + 1, new Cell(2, 1, 0), new Cell(2, 2, 0));
        assertSame(west, Join.join(west, around));
    }

    @Test
    void onlyTouchingPartsJoinButAWholeConnectedPartDoes() {
        QcPlan west = westPart();
        int sig = Join.signature(west, "A");
        // (5,1,0) is cells away: not touching. (2,1,0) touches and (3,1,0) hangs off it.
        Surroundings around = standing(sig, new Cell(5, 1, 0), new Cell(2, 1, 0), new Cell(3, 1, 0));
        Set<Cell> joined = Join.joined(west, Join.join(west, around));
        assertEquals(Set.of(new Cell(2, 1, 0), new Cell(3, 1, 0)), joined);
    }

    @Test
    void aPartAboveJoinsAndAPlannedRoadCellIsNeverTaken() {
        QcPlan low = DistrictTest.house("house", 2, 1, 1);
        int sig = Join.signature(low, "A");
        // the house plan has its street at z = 1
        Surroundings around = standing(sig, new Cell(0, 2, 0), new Cell(0, 1, 1));
        assertEquals(Set.of(new Cell(0, 2, 0)), Join.joined(low, Join.join(low, around)));
    }

    @Test
    void scannedBuildingsWithoutADesignNeverJoin() {
        QcPlan west = westPart();
        Surroundings around = new Surroundings(Set.of(), Map.of(), Set.of(new Cell(2, 1, 0)));
        assertSame(west, Join.join(west, around));
    }
}
