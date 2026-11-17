package com.qccore.core.plan;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The Effortless-style modifiers on the unit lattice: an array (repeat the shape {@code count}
 * times, {@code step} cells apart) followed by a mirror across the X and/or Z axis of a mirror
 * cell. A mirror plane runs through the middle of its cell, so cell {@code c + k} maps to
 * {@code c - k}.
 */
public final class Modifiers {

    public boolean mirrorX;
    public boolean mirrorZ;
    /** The cell the mirror planes run through. */
    public int mirrorCellX;
    public int mirrorCellZ;

    /** Copies of the shape, the original included; 1 means off. */
    public int arrayCount = 1;
    public int arrayStepX = 1;
    public int arrayStepLayer;
    public int arrayStepZ;

    public boolean isActive() {
        return mirrorX || mirrorZ || arrayCount > 1;
    }

    public Modifiers copy() {
        Modifiers copy = new Modifiers();
        copy.mirrorX = mirrorX;
        copy.mirrorZ = mirrorZ;
        copy.mirrorCellX = mirrorCellX;
        copy.mirrorCellZ = mirrorCellZ;
        copy.arrayCount = arrayCount;
        copy.arrayStepX = arrayStepX;
        copy.arrayStepLayer = arrayStepLayer;
        copy.arrayStepZ = arrayStepZ;
        return copy;
    }

    /**
     * Every transform the modifiers produce, as {@link Placement}s: the array offset first, then the
     * mirror flags. The identity placement is always first.
     */
    public List<Placement> placements() {
        List<Placement> out = new ArrayList<>();
        int count = Math.max(1, arrayCount);
        for (int i = 0; i < count; i++) {
            int ox = i * arrayStepX;
            int oy = i * arrayStepLayer;
            int oz = i * arrayStepZ;
            out.add(new Placement(ox, oy, oz, false, false));
            if (mirrorX) {
                out.add(new Placement(ox, oy, oz, true, false));
            }
            if (mirrorZ) {
                out.add(new Placement(ox, oy, oz, false, true));
            }
            if (mirrorX && mirrorZ) {
                out.add(new Placement(ox, oy, oz, true, true));
            }
        }
        return out;
    }

    /** Applies every placement to the cells; duplicates collapse, the first occurrence wins. */
    public Set<Cell> apply(Set<Cell> cells) {
        Set<Cell> out = new LinkedHashSet<>();
        for (Placement placement : placements()) {
            for (Cell cell : cells) {
                out.add(map(placement, cell));
            }
        }
        return out;
    }

    /** Where one cell lands under one placement. */
    public Cell map(Placement placement, Cell cell) {
        Cell moved = cell.offset(placement.dx(), placement.dLayer(), placement.dz());
        int x = placement.flipX() ? 2 * mirrorCellX - moved.x() : moved.x();
        int z = placement.flipZ() ? 2 * mirrorCellZ - moved.z() : moved.z();
        return new Cell(x, moved.layer(), z);
    }

    /** One copy of the shape: an offset in cells and whether it is mirrored across X (east-west) / Z. */
    public record Placement(int dx, int dLayer, int dz, boolean flipX, boolean flipZ) {
    }
}
