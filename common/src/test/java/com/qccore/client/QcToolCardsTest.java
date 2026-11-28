package com.qccore.client;

import com.qccore.client.QcToolCards.Card;
import com.qccore.core.qc.BlockChoice;
import com.qccore.core.qc.Roles;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The tools screen's settings card (T33): one card per tool and its plain-word status lines. */
class QcToolCardsTest {

    @Test
    void everyToolHasTheCardOfItsSettings() {
        assertEquals(Card.BUILDING, Card.of(QcTool.UNITS));
        assertEquals(Card.BUILDING, Card.of(QcTool.ASSIGN));
        assertEquals(Card.BUILDING, Card.of(QcTool.ERASE));
        assertEquals(Card.ROAD, Card.of(QcTool.ROAD));
        assertEquals(Card.CLEAR, Card.of(QcTool.CLEAR));
        assertEquals(Card.TWEAK, Card.of(QcTool.TWEAK));
        assertEquals(Card.TEMPLATE, Card.of(QcTool.TEMPLATE));
        assertEquals("Clear terrain", Card.CLEAR.title);
    }

    @Test
    void aRoadStyleNamesTheLanesWithoutABlock() {
        assertEquals("blocks complete", QcToolCards.roadStatus(Set.of()));
        assertEquals("lanes 1, 2, 3, 4: no block → Blocks…",
                QcToolCards.roadStatus(new BlockChoice(Roles.ROAD).missing()));
        BlockChoice style = new BlockChoice(Roles.ROAD);
        style.set("floor1", "minecraft:stone");
        style.set("floor4", "minecraft:stone");
        assertEquals("lanes 2, 3: no block → Blocks…", QcToolCards.roadStatus(style.missing()));
        assertEquals("lane 3, medallion: no block → Blocks…",
                QcToolCards.roadStatus(new LinkedHashSet<>(List.of("floor3", "medallion"))));
    }

    @Test
    void aNewRoadStyleTakesTheNextFreeNumber() {
        assertEquals("street 2", QcToolCards.nextRoadName("street", Set.of("street")));
        assertEquals("street 4", QcToolCards.nextRoadName("street 2", Set.of("street", "street 2", "street 3")));
        assertEquals("street 2", QcToolCards.nextRoadName("", Set.of()));
    }
}
