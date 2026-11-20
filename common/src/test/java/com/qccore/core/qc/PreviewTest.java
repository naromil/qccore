package com.qccore.core.qc;

import com.qccore.core.plan.BuildingSpec;
import com.qccore.core.plan.Cell;
import com.qccore.core.plan.QcPlan;
import java.util.Map;
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
}
