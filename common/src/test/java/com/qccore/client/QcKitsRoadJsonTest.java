package com.qccore.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.qccore.core.plan.RoadStyle;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QcKitsRoadJsonTest {

    @Test
    void roadStyleRoundTripsThroughKitJson() {
        RoadStyle style = new RoadStyle();
        for (int i = 0; i < RoadStyle.LANES; i++) {
            style.floor[i] = "minecraft:stone_bricks";
            style.joint[i] = "minecraft:polished_andesite";
        }
        style.over[1] = "minecraft:lantern[hanging=true]";
        style.post[0].addAll(List.of("minecraft:spruce_log", "minecraft:lantern"));
        style.medallion = "minecraft:chiseled_stone_bricks";
        style.centre = "minecraft:gold_block";

        RoadStyle back = QcKits.road(QcKits.roadJson(style));

        assertArrayEquals(style.floor, back.floor);
        assertArrayEquals(style.joint, back.joint);
        assertArrayEquals(style.over, back.over);
        assertEquals(style.post[0], back.post[0]);
        assertTrue(back.post[3].isEmpty());
        assertEquals(style.medallion, back.medallion);
        assertEquals(style.centre, back.centre);
        assertTrue(back.isComplete());
    }

    @Test
    void handEditedKitWithNullsAndNestedValuesDoesNotThrow() {
        JsonObject json = JsonParser.parseString("""
                {"floor": ["minecraft:stone", null, {"id": "x"}, ["y"]],
                 "joint": null,
                 "over": [1, true],
                 "post": [[null, "minecraft:spruce_log", {}], "not a column", null],
                 "medallion": {"id": "x"},
                 "centre": null}
                """).getAsJsonObject();

        RoadStyle style = QcKits.road(json);

        assertArrayEquals(new String[]{"minecraft:stone", "", "", ""}, style.floor);
        assertArrayEquals(new String[]{"", "", "", ""}, style.joint);
        assertArrayEquals(new String[]{"1", "true", "", ""}, style.over);
        assertEquals(List.of("minecraft:spruce_log"), style.post[0]);
        assertTrue(style.post[1].isEmpty());
        assertEquals("", style.medallion);
        assertEquals("", style.centre);
    }
}
