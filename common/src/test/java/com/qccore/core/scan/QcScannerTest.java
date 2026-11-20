package com.qccore.core.scan;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The scanner on a hand-made street on the grid (8x+3, 8y+2, 8z+5): three ground units in a row, west
 * to east C | A | B, with a gate between C and A, a party wall between A and B, a door on A's south face
 * opening onto a stone-brick road cell, and grass in front of B that must not read as a road.
 */
class QcScannerTest {

    private static final int N = 1, E = 2, S = 4, W = 8;

    private final Map<String, String> world = new HashMap<>();

    private void set(int x, int y, int z, String id) {
        world.put(x + "," + y + "," + z, id);
    }

    private String get(int x, int y, int z) {
        return world.getOrDefault(x + "," + y + "," + z, "minecraft:air");
    }

    private void frame(int ox, int oy, int oz) {
        frame(ox, oy, oz, "minecraft:spruce_log");
    }

    private void frame(int ox, int oy, int oz, String block) {
        for (int i = 0; i <= 8; i++) {
            for (int p = 0; p <= 8; p += 8) {
                for (int q = 0; q <= 8; q += 8) {
                    set(ox + p, oy + i, oz + q, block);
                    set(ox + i, oy + p, oz + q, block);
                    set(ox + p, oy + q, oz + i, block);
                }
            }
        }
        for (int i = 1; i <= 7; i++) {
            for (int k = 1; k <= 7; k++) {
                set(ox + i, oy, oz + k, "minecraft:spruce_planks");
            }
        }
    }

    /** Fills the 7x7 plane x = px (a along z) except where {@code hole} says so. */
    private void planeX(int px, int oy, int oz, java.util.function.BiPredicate<Integer, Integer> hole) {
        for (int a = 1; a <= 7; a++) {
            for (int y = 1; y <= 7; y++) {
                if (!hole.test(a, y)) set(px, oy + y, oz + a, "minecraft:bricks");
            }
        }
    }

    private QcScanner.Result scanStreet() {
        for (int x = -16; x <= 32; x++) {
            for (int z = -8; z <= 32; z++) {
                set(x, 65, z, "minecraft:grass_block");
            }
        }
        frame(3, 66, 5);    // A
        frame(11, 66, 5);   // B
        frame(-5, 66, 5);   // C
        planeX(11, 66, 5, (a, y) -> false);                                 // A|B party wall
        planeX(3, 66, 5, (a, y) -> a >= 3 && a <= 5 && y <= 3);              // C|A gate
        for (int a = 1; a <= 7; a++) {                                      // A's south face with a door
            for (int y = 1; y <= 7; y++) {
                String id = y >= 4 && a % 2 == 0 ? "minecraft:glass" : "minecraft:bricks";
                set(3 + a, 66 + y, 13, id);
            }
        }
        set(7, 67, 13, "minecraft:oak_door[half=lower,facing=north]");
        set(7, 68, 13, "minecraft:oak_door[half=upper,facing=north]");
        for (int i = 1; i <= 7; i++) {
            for (int k = 1; k <= 7; k++) {
                set(3 + i, 66, 13 + k, "minecraft:stone_bricks");            // road south of A
                set(11 + i, 66, 13 + k, "minecraft:grass_block");            // meadow south of B
            }
        }
        set(6, 67, 17, "minecraft:lantern");                                // street furniture
        return new QcScanner(this::get, -12, 60, -4, 30, 90, 30).scan();
    }

    private static QcScanner.Found at(QcScanner.Result result, int x, int y, int z) {
        return result.units().stream().filter(u -> u.x() == x && u.y() == y && u.z() == z).findFirst().orElse(null);
    }

    @Test
    void findsTheGridAndTheThreeUnits() {
        QcScanner.Result result = scanStreet();
        assertNotNull(result);
        assertEquals(List.of(3, 2, 5), List.of(result.ax(), result.ay(), result.az()));
        assertEquals(3, result.count(QcScanner.Kind.BUILDING));
    }

    @Test
    void readsPartyWallsGatesAndDoors() {
        QcScanner.Result result = scanStreet();
        QcScanner.Found a = at(result, 3, 66, 5);
        QcScanner.Found b = at(result, 11, 66, 5);
        QcScanner.Found c = at(result, -5, 66, 5);
        assertEquals(E | W, a.walls());     // wall to B, gate (wall + gate) to C
        assertEquals(W, a.gates());
        assertEquals(S, a.doors());
        assertEquals(W, b.walls());
        assertEquals(0, b.gates());
        assertEquals(0, b.doors());
        assertEquals(E, c.walls());
        assertEquals(E, c.gates());
        assertEquals(0, c.doors() & N);
    }

    @Test
    void findsTheRoadButNotTheMeadow() {
        QcScanner.Result result = scanStreet();
        assertEquals(1, result.count(QcScanner.Kind.ROAD));
        QcScanner.Found road = at(result, 3, 66, 13);
        assertNotNull(road);
        assertEquals(QcScanner.Kind.ROAD, road.kind());
        assertNull(at(result, 11, 66, 13));
    }

    @Test
    void readsATuffFrameworkAndACalciteStreet() {
        for (int x = -4; x <= 30; x++) {
            for (int z = -4; z <= 30; z++) {
                set(x, 65, z, "minecraft:grass_block");
            }
        }
        frame(3, 66, 5, "minecraft:tuff");                                  // white citadel's framework
        for (int i = 1; i <= 7; i++) {
            for (int k = 1; k <= 7; k++) {
                set(3 + i, 66, 13 + k, i == 1 || i == 7 ? "minecraft:polished_diorite" : "minecraft:calcite");
            }
        }
        QcScanner.Result result = new QcScanner(this::get, -4, 60, -4, 30, 90, 30).scan();
        assertNotNull(result);
        assertEquals(1, result.count(QcScanner.Kind.BUILDING));
        assertNotNull(at(result, 3, 66, 13));
        assertEquals(QcScanner.Kind.ROAD, at(result, 3, 66, 13).kind());
    }

    @Test
    void solidRockAndACaveAreNoBuilding() {
        for (int x = 0; x <= 24; x++) {
            for (int y = 0; y <= 24; y++) {
                for (int z = 0; z <= 24; z++) {
                    boolean cave = x > 2 && x < 20 && y > 2 && y < 20 && z > 2 && z < 20;
                    set(x, y, z, cave ? "minecraft:cave_air" : "minecraft:stone");
                }
            }
        }
        assertNull(new QcScanner(this::get, 0, 0, 0, 24, 24, 24).scan());
    }

    @Test
    void openSidesAreNeitherWallNorDoor() {
        frame(3, 66, 5);
        frame(11, 66, 5);
        QcScanner.Result result = new QcScanner(this::get, -4, 60, -4, 30, 90, 30).scan();
        QcScanner.Found a = at(result, 3, 66, 5);
        assertEquals(0, a.walls());
        assertEquals(E, a.gates());          // gate bit alone = open partition
        assertEquals(0, a.doors());
        assertFalse(result.units().stream().anyMatch(u -> u.kind() == QcScanner.Kind.ROAD));
        assertTrue(QcScanner.first(-12, 3) == -13 + 8 && QcScanner.first(5, 5) == 5);
    }
}
