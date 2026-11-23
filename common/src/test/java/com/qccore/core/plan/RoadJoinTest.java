package com.qccore.core.plan;

import com.qccore.core.UnitPos;
import com.qccore.core.qc.BlockChoice;
import com.qccore.core.qc.District;
import com.qccore.core.qc.Roles;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** A new road rebuilds the standing road cells it meets, so two applies equal one. */
class RoadJoinTest {

    private static BlockChoice hedged(String tag) {
        BlockChoice road = new BlockChoice(Roles.ROAD);
        for (int lane = 1; lane <= 4; lane++) {
            road.set("floor" + lane, "minecraft:" + tag + "lane" + lane);
        }
        road.set("over1", "minecraft:oak_leaves");
        road.set("medallion", "minecraft:" + tag + "medallion");
        return road;
    }

    private static QcPlan roads(String style, Cell... cells) {
        QcPlan plan = new QcPlan();
        plan.styles().put("street", hedged(""));
        plan.styles().put("lane", hedged("x"));
        for (Cell cell : cells) {
            plan.roads().put(cell, style);
        }
        return plan;
    }

    /**
     * The world after applying {@code plan} over {@code world}: the road headroom of every planned
     * column is cleared, then the plan's blocks are written (what {@code QcPlanService.apply} does).
     */
    private static Map<BlockPos, String> apply(Map<BlockPos, String> world, QcPlan plan, Surroundings around) {
        QcPlan joined = RoadJoin.join(plan, around);
        Map<BlockPos, String> out = new HashMap<>(world);
        for (Map.Entry<Integer, Map<UnitPos, String>> layer : joined.roadLayers().entrySet()) {
            for (long column : RoadKit.networkArea(layer.getValue().keySet(), around.joinedRoads(joined, layer.getKey())).keySet()) {
                for (int y = 1; y <= 7; y++) {
                    out.remove(new BlockPos(RoadKit.x(column), layer.getKey() * 8 + y, RoadKit.z(column)));
                }
            }
        }
        out.putAll(District.build(joined, around).blocks());
        return out;
    }

    @Test
    void aRoadBuiltInTwoGoesEqualsOneBuiltAtOnce() {
        Cell a = new Cell(0, 1, 0);
        Cell b = new Cell(1, 1, 0);
        Cell c = new Cell(1, 1, 1);
        Map<BlockPos, String> once = District.build(roads("street", a, b, c)).blocks();

        Map<BlockPos, String> first = apply(Map.of(), roads("street", a), Surroundings.NONE);
        Map<BlockPos, String> then = apply(first, roads("street", b, c), new Surroundings(Set.of(a), Map.of()));
        assertEquals(once, then);

        // and the other way round: a bend laid first, the end cell added later
        Map<BlockPos, String> bend = apply(Map.of(), roads("street", b, c), Surroundings.NONE);
        assertEquals(once, apply(bend, roads("street", a), new Surroundings(Set.of(b, c), Map.of())));
    }

    @Test
    void onlyTouchingStandingCellsAreRebuiltWithTheStyleTheyMeet() {
        Surroundings around = new Surroundings(Set.of(new Cell(1, 1, 0), new Cell(2, 1, 0), new Cell(0, 2, 0)), Map.of());
        QcPlan plan = roads("lane", new Cell(0, 1, 0));
        assertEquals(Map.of(new Cell(1, 1, 0), "lane"), RoadJoin.touching(plan, around));
        QcPlan none = roads("lane", new Cell(5, 1, 5));
        assertSame(none, RoadJoin.join(none, around));
    }

    @Test
    void aStandingRoadKeepsItsOwnStyleWhenANewRoadOfAnotherStyleMeetsIt() {
        Cell a = new Cell(0, 1, 0);
        Cell b = new Cell(1, 1, 0);
        Cell c = new Cell(1, 1, 1);
        // laid at once: a in "lane" style, b and c in "street"
        QcPlan mixed = roads("street", b, c);
        mixed.roads().put(a, "lane");
        Map<BlockPos, String> once = District.build(mixed).blocks();

        Map<BlockPos, String> first = apply(Map.of(), roads("lane", a), Surroundings.NONE);
        Surroundings standing = new Surroundings(Set.of(a), Map.of(), Set.of(), Map.of(), Map.of(a, hedged("x")));
        assertEquals(once, apply(first, roads("street", b, c), standing));
    }

    @Test
    void aStandingStyleThePlanLacksIsAddedUnderItsOwnName() {
        Cell standingCell = new Cell(1, 1, 0);
        BlockChoice gravel = hedged("gravel_");
        Surroundings around = new Surroundings(Set.of(standingCell), Map.of(), Set.of(), Map.of(), Map.of(standingCell, gravel));
        QcPlan joined = RoadJoin.join(roads("street", new Cell(0, 1, 0)), around);
        String name = joined.roads().get(standingCell);
        assertEquals(RoadJoin.STANDING + 1, name);
        assertEquals(gravel, joined.styles().get(name));
    }
}
