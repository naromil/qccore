package com.qccore.core.plan;

import com.qccore.core.qc.BlockChoice;
import com.qccore.core.qc.District;
import com.qccore.core.qc.Roles;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Road level changes (T35 picks 4 and 5): a street climbs to the street one layer up. */
class RoadStairsTest {

    static BlockChoice street(boolean flight) {
        BlockChoice road = new BlockChoice(Roles.ROAD);
        for (int lane = 1; lane <= 4; lane++) {
            road.set("floor" + lane, "minecraft:lane" + lane);
        }
        road.set("over1", "minecraft:hedge");
        if (flight) {
            road.set("flight", "minecraft:road_stairs");
        }
        return road;
    }

    @SuppressWarnings("unchecked")
    static Set<BlockPos> walk(Map<BlockPos, String> b, BlockPos start, int maxY) throws Exception {
        Class<?> test = Class.forName("com.qccore.core.qc.StairsTest");
        Method walk = test.getDeclaredMethod("walk", Map.class, BlockPos.class, int.class);
        walk.setAccessible(true);
        return (Set<BlockPos>) walk.invoke(null, b, start, maxY);
    }

    private static long steps(Map<BlockPos, String> b) {
        return b.values().stream().filter(s -> s.startsWith("minecraft:road_stairs") && s.contains("half=bottom")).count();
    }

    @Test
    void aStreetClimbsToTheStreetAheadOneLayerUpAndRunsOnUnderIt() throws Exception {
        QcPlan plan = new QcPlan();
        plan.styles().put("street", street(true));
        for (int x = 0; x <= 3; x++) {
            plan.roads().put(new Cell(x, 1, 0), "street");
        }
        for (int x = 3; x <= 5; x++) {
            plan.roads().put(new Cell(x, 2, 0), "street");
        }
        District.Result result = District.build(plan);
        assertTrue(result.problems().isEmpty(), result.problems().toString());
        Map<BlockPos, String> b = result.blocks();
        assertEquals(24, steps(b), "one stair cell: eight steps, 3 wide");
        // cell (2, 1, 0) climbs east in lanes 5-7 (z 5..7): the first step on the mouth behind (x 16), the
        // eighth at x 23 leaning on the upper street's mouth paving at x 24, y 16
        for (int z = 5; z <= 7; z++) {
            assertTrue(b.get(new BlockPos(16, 9, z)).startsWith("minecraft:road_stairs[facing=east,half=bottom"));
            assertTrue(b.get(new BlockPos(23, 16, z)).startsWith("minecraft:road_stairs[facing=east,half=bottom"));
            assertTrue(b.get(new BlockPos(24, 16, z)).startsWith("minecraft:lane"), "the upper street opens its mouth");
        }
        Set<BlockPos> reach = walk(b, new BlockPos(4, 9, 4), 40);
        assertTrue(reach.stream().anyMatch(p -> p.getY() == 17 && p.getX() >= 32), "the upper street is reachable");
        assertTrue(reach.contains(new BlockPos(30, 9, 2)), "lanes 1-4 run on level under the upper street");
        assertTrue(result.notes().stream().noneMatch(n -> n.contains("no stair")), result.notes().toString());
    }

    @Test
    void aStreetStackedOverAnotherIsReachedByASwitchbackBesideALevelLane() throws Exception {
        QcPlan plan = new QcPlan();
        plan.styles().put("street", street(true));
        for (int x = 0; x <= 2; x++) {
            plan.roads().put(new Cell(x, 1, 0), "street");
            plan.roads().put(new Cell(x, 2, 0), "street");
        }
        District.Result result = District.build(plan);
        assertTrue(result.problems().isEmpty(), result.problems().toString());
        Map<BlockPos, String> b = result.blocks();
        assertEquals(12, steps(b), "two runs of three steps, 2 wide: one switchback for the pair of streets");
        // the well in the upper paving over the second run
        for (int x = 3; x <= 5; x++) {
            for (int z = 4; z <= 5; z++) {
                assertFalse(b.containsKey(new BlockPos(x, 16, z)), "well at " + x + ", " + z);
            }
        }
        // the 3-wide level lane beside it (z 1..3) stays open at walk height through the cell
        for (int x = 1; x <= 7; x++) {
            for (int z = 1; z <= 3; z++) {
                assertFalse(b.containsKey(new BlockPos(x, 9, z)) && !b.get(new BlockPos(x, 9, z)).contains("hedge"),
                        "lane at " + x + ", " + z);
            }
        }
        Set<BlockPos> reach = walk(b, new BlockPos(12, 9, 4), 40);
        assertTrue(reach.stream().anyMatch(p -> p.getY() == 17), "the stacked street is reachable");
    }

    @Test
    void aLevelChangeWithoutAFlightBlockAsksForIt() {
        QcPlan plan = new QcPlan();
        plan.styles().put("street", street(false));
        for (int x = 0; x <= 2; x++) {
            plan.roads().put(new Cell(x, 1, 0), "street");
            plan.roads().put(new Cell(x, 2, 0), "street");
        }
        District.Result result = District.build(plan);
        assertTrue(result.missing().getOrDefault("street", Set.of()).contains("flight"), result.missing().toString());
        // a level street alone needs no flight
        QcPlan flat = new QcPlan();
        flat.styles().put("street", street(false));
        flat.roads().put(new Cell(0, 1, 0), "street");
        flat.roads().put(new Cell(1, 1, 0), "street");
        assertFalse(District.build(flat).missing().getOrDefault("street", Set.of()).contains("flight"));
    }
}
