package com.qccore.core.qc;

import com.qccore.core.plan.QcPlan;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Building stairs (T35, the user's picks 2 and 3 of review #61): a dog-leg in a corner by default, a
 * 3-wide centre flight through a hall; flights 2+ wide, three blocks over every step, two free blocks
 * before the first step and after the last, and a walk from the ground floor up.
 */
class StairsTest {

    private static boolean solid(String s) {
        if (s == null) {
            return false;
        }
        String b = BlockStr.base(s);
        return !(b.contains("door") || b.contains("torch") || b.contains("lantern") || b.contains("carpet"));
    }

    /**
     * Feet positions reachable from {@code start}: a step up needs two free blocks over the target and a
     * third over the walker (the hitbox rises before it moves), a drop at most three.
     */
    static Set<BlockPos> walk(Map<BlockPos, String> b, BlockPos start, int maxY) {
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> todo = new ArrayDeque<>();
        seen.add(start);
        todo.add(start);
        while (!todo.isEmpty()) {
            BlockPos p = todo.poll();
            for (int[] dir : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                for (int dy = 1; dy >= -3; dy--) {
                    BlockPos q = p.offset(dir[0], dy, dir[1]);
                    if (q.getY() > maxY || !solid(b.get(q.below())) || solid(b.get(q)) || solid(b.get(q.above()))) {
                        continue;
                    }
                    if (dy == 1 && solid(b.get(p.above(2)))) {
                        continue;
                    }
                    if (dy < 0 && solid(b.get(q.above(2)))) {
                        continue;
                    }
                    if (seen.add(q)) {
                        todo.add(q);
                    }
                    break;
                }
            }
        }
        return seen;
    }

    private static long steps(Map<BlockPos, String> b) {
        return b.values().stream().filter(s -> s.startsWith("minecraft:flight_stairs") && s.contains("half=bottom")).count();
    }

    @Test
    void aHouseClimbsADogLegInACornerAndLeavesALaneThrough() {
        District.Result result = District.build(DistrictTest.house("house", 2, 2, 2));
        assertTrue(result.problems().isEmpty(), result.problems().toString());
        Map<BlockPos, String> b = result.blocks();
        assertEquals(12, steps(b), "two runs of three steps, each 2 wide");
        // the landing: a 2x4 floor at half height
        long landing = b.entrySet().stream().filter(e -> e.getKey().getY() == 12 && e.getValue().equals("minecraft:floor_block")).count();
        assertEquals(8, landing);
        Set<BlockPos> reach = walk(b, new BlockPos(4, 9, 4), 40);
        assertTrue(reach.stream().anyMatch(p -> p.getY() == 17), "the upper floor is reachable on foot");
        assertTrue(result.notes().stream().noneMatch(n -> n.contains("no stair up")), result.notes().toString());
        // the walk is honest: without stairs the upper floor is out of reach
        QcPlan none = DistrictTest.house("house", 2, 2, 2);
        none.buildings().get("A").options.put("stairs", "false");
        Set<BlockPos> stuck = walk(District.build(none).blocks(), new BlockPos(4, 9, 4), 40);
        assertFalse(stuck.stream().anyMatch(p -> p.getY() == 17));
    }

    @Test
    void aStackOfStoreysClimbsAllTheWayUp() {
        Map<BlockPos, String> b = District.build(DistrictTest.house("house", 2, 1, 4)).blocks();
        assertEquals(36, steps(b), "one dog-leg per storey change");
        Set<BlockPos> reach = walk(b, new BlockPos(4, 9, 4), 60);
        assertTrue(reach.stream().anyMatch(p -> p.getY() == 33), "the top storey is reachable on foot");
    }

    @Test
    void aHallClimbsThroughItsMiddleFromFrameworkToFramework() {
        QcPlan plan = DistrictTest.house("house", 3, 1, 2);
        plan.buildings().get("A").options.put("partition", "open");
        District.Result result = District.build(plan);
        assertTrue(result.problems().isEmpty(), result.problems().toString());
        Map<BlockPos, String> b = result.blocks();
        assertEquals(24, steps(b), "eight steps, 3 wide");
        // unit (1, 1, 0) climbs east: the first step sits in the shared plane x = 8 on the framework,
        // the eighth is set into the floor above at x = 15 and leans on the framework at x = 16
        for (int z = 3; z <= 5; z++) {
            assertTrue(b.get(new BlockPos(8, 9, z)).startsWith("minecraft:flight_stairs[facing=east,half=bottom"));
            assertTrue(b.get(new BlockPos(15, 16, z)).startsWith("minecraft:flight_stairs[facing=east,half=bottom"));
            assertTrue(solid(b.get(new BlockPos(16, 16, z))), "the top step leans on the framework");
            // two free blocks before and after
            for (int y = 9; y <= 11; y++) {
                assertFalse(solid(b.get(new BlockPos(6, y, z))));
                assertFalse(solid(b.get(new BlockPos(7, y, z))));
            }
            for (int x = 16; x <= 17; x++) {
                for (int y = 17; y <= 19; y++) {
                    assertFalse(solid(b.get(new BlockPos(x, y, z))));
                }
            }
        }
        // aisles: the 2-wide strips either side stay clear at walk height
        for (int x = 9; x <= 15; x++) {
            for (int z : new int[]{1, 2, 6, 7}) {
                assertFalse(solid(b.get(new BlockPos(x, 9, z))), "aisle at " + x + ", " + z);
            }
        }
        Set<BlockPos> reach = walk(b, new BlockPos(4, 9, 4), 40);
        assertTrue(reach.stream().anyMatch(p -> p.getY() == 17), "the upper floor is reachable on foot");
    }

    @Test
    void everyStepHasThreeFreeBlocksOverIt() {
        for (String partition : new String[]{"rooms", "open"}) {
            QcPlan plan = DistrictTest.house("house", 3, 2, 3);
            plan.buildings().get("A").options.put("partition", partition);
            Map<BlockPos, String> b = District.build(plan).blocks();
            for (Map.Entry<BlockPos, String> e : b.entrySet()) {
                if (e.getValue().startsWith("minecraft:flight_stairs") && e.getValue().contains("half=bottom")) {
                    for (int h = 1; h <= 3; h++) {
                        assertFalse(solid(b.get(e.getKey().above(h))), partition + ": over the step at " + e.getKey());
                    }
                }
            }
        }
    }
}
