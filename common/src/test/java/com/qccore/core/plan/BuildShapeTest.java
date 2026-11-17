package com.qccore.core.plan;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildShapeTest {

    @Test
    void lineFollowsTheDominantAxisOnTheFirstLayer() {
        Set<Cell> cells = BuildShape.LINE.cells(List.of(new Cell(0, 1, 0), new Cell(3, 2, 1)));
        assertEquals(Set.of(new Cell(0, 1, 0), new Cell(1, 1, 0), new Cell(2, 1, 0), new Cell(3, 1, 0)), cells);
        Set<Cell> alongZ = BuildShape.LINE.cells(List.of(new Cell(0, 1, 0), new Cell(1, 1, -2)));
        assertEquals(Set.of(new Cell(0, 1, 0), new Cell(0, 1, -1), new Cell(0, 1, -2)), alongZ);
    }

    @Test
    void floorAndRingCoverTheRectangle() {
        List<Cell> corners = List.of(new Cell(2, 1, 2), new Cell(0, 1, 0));
        assertEquals(9, BuildShape.FLOOR.cells(corners).size());
        Set<Cell> ring = BuildShape.RING.cells(corners);
        assertEquals(8, ring.size());
        assertFalse(ring.contains(new Cell(1, 1, 1)));
    }

    @Test
    void boxTakesItsHeightFromTheThirdPoint() {
        List<Cell> points = List.of(new Cell(0, 2, 0), new Cell(1, 2, 1), new Cell(1, 4, 1));
        Set<Cell> box = BuildShape.BOX.cells(points);
        assertEquals(12, box.size());
        assertTrue(box.contains(new Cell(0, 4, 0)));
        Set<Cell> downwards = BuildShape.BOX.cells(List.of(new Cell(0, 3, 0), new Cell(0, 3, 0), new Cell(0, 1, 0)));
        assertEquals(Set.of(new Cell(0, 1, 0), new Cell(0, 2, 0), new Cell(0, 3, 0)), downwards);
        assertEquals(8 * 2, BuildShape.HOLLOW_BOX.cells(List.of(new Cell(0, 1, 0), new Cell(2, 1, 2), new Cell(2, 2, 2))).size());
    }

    @Test
    void anIncompleteShapeRepeatsItsLastPoint() {
        assertEquals(Set.of(new Cell(5, 1, 5)), BuildShape.BOX.cells(List.of(new Cell(5, 1, 5))));
        assertTrue(BuildShape.FLOOR.cells(List.of()).isEmpty());
    }

    @Test
    void modifiersArrayThenMirror() {
        Modifiers modifiers = new Modifiers();
        modifiers.arrayCount = 3;
        modifiers.arrayStepX = 2;
        Set<Cell> arrayed = modifiers.apply(Set.of(new Cell(0, 1, 0)));
        assertEquals(Set.of(new Cell(0, 1, 0), new Cell(2, 1, 0), new Cell(4, 1, 0)), arrayed);

        Modifiers mirror = new Modifiers();
        mirror.mirrorX = true;
        mirror.mirrorZ = true;
        mirror.mirrorCellX = 5;
        mirror.mirrorCellZ = 0;
        Set<Cell> mirrored = mirror.apply(Set.of(new Cell(3, 1, 2)));
        assertEquals(Set.of(new Cell(3, 1, 2), new Cell(7, 1, 2), new Cell(3, 1, -2), new Cell(7, 1, -2)), mirrored);
        assertEquals(Set.of(new Cell(5, 1, 0)), mirror.apply(Set.of(new Cell(5, 1, 0))));
    }
}
