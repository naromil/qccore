package com.qccore.core.qc;

import com.qccore.core.plan.BuildingSpec;
import com.qccore.core.plan.Cell;
import com.qccore.core.plan.QcPlan;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Previews: default blocks only fill gaps in a copy, samples show exactly the chosen blocks. */
class PreviewTest {

    @Test
    void theDefaultsBuildEveryTemplateWithoutMissingRoles() {
        for (String template : Templates.names()) {
            if (Templates.LEGACY.equals(template)) {
                continue;
            }
            QcPlan sample = Preview.sample(Preview.defaults(Roles.BUILDING), template, Map.of());
            District.Result result = District.build(sample);
            assertTrue(result.problems().isEmpty(), template + ": " + result.problems());
            assertFalse(result.blocks().isEmpty(), template);
        }
    }

    @Test
    void withDefaultsKeepsThePlayersBlocksAndLeavesThePlanAlone() {
        QcPlan plan = new QcPlan();
        plan.buildings().put("A", new BuildingSpec("house", "mine"));
        plan.addUnit(new Cell(0, 1, 0), "A");
        BlockChoice mine = new BlockChoice(Roles.BUILDING);
        mine.set("wall", "minecraft:andesite");
        plan.styles().put("mine", mine);
        plan.roads().put(new Cell(0, 1, 1), "street");

        QcPlan filled = Preview.withDefaults(plan);

        assertEquals("minecraft:andesite", filled.styles().get("mine").get("wall"));
        assertEquals("minecraft:stripped_spruce_log", filled.styles().get("mine").get("frame"));
        assertEquals("minecraft:polished_granite", filled.styles().get("street").get("floor1"));
        assertTrue(District.build(filled).problems().isEmpty(), District.build(filled).problems().toString());
        // the plan itself is untouched: still only the player's one block, and no road style
        assertEquals(Map.of("wall", "minecraft:andesite"), plan.styles().get("mine").blocks());
        assertNull(plan.styles().get("street"));
    }

    @Test
    void aSampleShowsOnlyTheChosenBlocks() {
        BlockChoice style = new BlockChoice(Roles.BUILDING);
        style.set("frame", "minecraft:oak_log");
        District.Result result = District.build(Preview.sample(style, "house", Map.of()));
        assertTrue(result.blocks().containsValue("minecraft:oak_log[axis=y]")
                || result.blocks().values().stream().anyMatch(id -> id.startsWith("minecraft:oak_log")));
        assertFalse(result.blocks().values().stream().anyMatch(id -> id.startsWith("minecraft:bricks") || id.startsWith("minecraft:brick_")),
                "no default stands in for the unset wall");
        assertTrue(result.missing().get(Preview.STYLE).contains("wall"));
    }

    @Test
    void aRoadSampleIsACrossingAndDecorHasNone() {
        BlockChoice road = Preview.defaults(Roles.ROAD);
        QcPlan sample = Preview.sample(road, null, null);
        assertEquals(5, sample.roads().size());
        assertFalse(District.build(sample).blocks().isEmpty());
        assertNull(Preview.sample(new BlockChoice(Roles.DECOR), "house", Map.of()));
    }

    private static Set<BlockPos> hovered(QcPlan sample, District.Result normal, String role) {
        return Preview.roleBlocks(sample, normal, Preview.STYLE, role);
    }

    @Test
    void aHoveredRoleFindsExactlyItsOwnBlocks() {
        QcPlan sample = Preview.sample(Preview.defaults(Roles.BUILDING), "house", Map.of());
        District.Result normal = District.build(sample);
        Set<BlockPos> wall = hovered(sample, normal, "wall");
        assertFalse(wall.isEmpty());
        long bricks = normal.blocks().values().stream().filter(b -> BlockStr.base(b).equals("minecraft:bricks")).count();
        assertEquals(bricks, wall.size(), "every brick and only bricks: no other role is bricks here");
        for (BlockPos pos : wall) {
            assertEquals("minecraft:bricks", BlockStr.base(normal.blocks().get(pos)));
        }
        assertEquals(normal.blocks(), District.build(sample).blocks(), "the plan is left alone");
    }

    @Test
    void rolesSharingABlockAreToldApart() {
        // row and coping are both brick_stairs in the defaults
        QcPlan sample = Preview.sample(Preview.defaults(Roles.BUILDING), "house", Map.of());
        District.Result normal = District.build(sample);
        for (String[] pair : new String[][] {{"row", "coping"}}) {
            Set<BlockPos> a = hovered(sample, normal, pair[0]);
            Set<BlockPos> b = hovered(sample, normal, pair[1]);
            assertFalse(a.isEmpty(), pair[0]);
            assertFalse(b.isEmpty(), pair[1]);
            for (BlockPos pos : a) {
                assertFalse(b.contains(pos), pair[0] + " and " + pair[1] + " overlap at " + pos);
            }
        }
    }

    @Test
    void anUnsetRoleShowsTheBlocksOfTheRoleItRepeats() {
        BlockChoice style = Preview.defaults(Roles.BUILDING);
        QcPlan sample = Preview.sample(style, "house", Map.of());
        District.Result normal = District.build(sample);
        assertNull(style.get("wall_upper"));
        assertTrue(hovered(sample, normal, "wall").containsAll(hovered(sample, normal, "wall_upper")));
        style.set("hang", null);
        style.set("parapet", null);
        QcPlan bare = Preview.sample(style, "house", Map.of());
        assertTrue(hovered(bare, District.build(bare), "parapet").isEmpty(), "unset with nothing to repeat: nothing lit");
    }

    @Test
    void aBlockTheGeneratorTreatsByIdStillLightsUp() {
        // a vanilla lantern as light hangs inside instead of sitting in the wall
        BlockChoice style = Preview.defaults(Roles.BUILDING);
        style.set("light", "minecraft:lantern");
        style.set("hang", "minecraft:chain");
        QcPlan sample = Preview.sample(style, "house", Map.of());
        District.Result normal = District.build(sample);
        Set<BlockPos> light = hovered(sample, normal, "light");
        assertFalse(light.isEmpty());
        for (BlockPos pos : light) {
            assertEquals("minecraft:lantern", BlockStr.base(normal.blocks().get(pos)));
        }
    }

    @Test
    void aRoadRoleLightsItsLane() {
        QcPlan sample = Preview.sample(Preview.defaults(Roles.ROAD), null, Map.of());
        District.Result normal = District.build(sample);
        Set<BlockPos> lane = Preview.roleBlocks(sample, normal, Preview.sampleStyle(Roles.ROAD), "floor4");
        assertFalse(lane.isEmpty());
        for (BlockPos pos : lane) {
            assertEquals("minecraft:mud_bricks", BlockStr.base(normal.blocks().get(pos)));
        }
    }
}
