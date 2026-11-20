package com.qccore.core.qc;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Batch presets: real roles, vanilla ids, they build, and loading one hands out an editable copy. */
class PresetsTest {

    @Test
    void thereAreBuildingAndRoadPresets() {
        assertTrue(Presets.names(Roles.BUILDING).size() >= 4, Presets.names(Roles.BUILDING).toString());
        assertTrue(Presets.names(Roles.ROAD).size() >= 4, Presets.names(Roles.ROAD).toString());
        assertTrue(Presets.names(Roles.DECOR).isEmpty());
    }

    @Test
    void everyPresetUsesKnownRolesAndVanillaIds() {
        for (Presets.Preset preset : Presets.all()) {
            assertFalse(preset.blocks().isEmpty(), preset.name());
            for (Map.Entry<String, String> entry : preset.blocks().entrySet()) {
                assertNotNull(Roles.role(preset.kind(), entry.getKey()), preset.name() + ": unknown role " + entry.getKey());
                for (String id : entry.getValue().split(",")) {
                    String block = id.contains("=") ? id.substring(0, id.lastIndexOf('=')) : id;
                    assertTrue(block.startsWith("minecraft:"), preset.name() + "." + entry.getKey() + ": " + id);
                }
            }
        }
    }

    @Test
    void everyPresetBuildsAndOnlyItsOwnUnsetRolesAreMissing() {
        for (Presets.Preset preset : Presets.all()) {
            District.Result result = District.build(Preview.sample(preset.choice(), "house", Map.of()));
            assertFalse(result.blocks().isEmpty(), preset.name());
            String style = Roles.ROAD.equals(preset.kind()) ? Preview.ROAD_STYLE : Preview.STYLE;
            Set<String> missing = result.missing().getOrDefault(style, Set.of());
            for (String role : missing) {
                assertNull(preset.choice().resolve(role), preset.name() + " reports " + role + " missing although it sets it");
            }
        }
    }

    @Test
    void loadingAPresetGivesAnEditableCopy() {
        String name = Presets.names(Roles.BUILDING).get(0);
        BlockChoice mine = Presets.get(name).choice();
        String before = Presets.get(name).blocks().get("wall");
        mine.set("wall", "minecraft:andesite");
        assertEquals("minecraft:andesite", mine.get("wall"));
        assertEquals(before, Presets.get(name).choice().get("wall"), "the preset itself is unchanged");
    }
}
