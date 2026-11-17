package com.qccore.core.plan;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The build modes, in the spirit of Effortless Building but on the QC lattice: every point is a
 * unit cell, and the shape is the set of cells between the points.
 *
 * <ul>
 *   <li>{@link #SINGLE}: one cell, one click (hold the button to paint);</li>
 *   <li>{@link #LINE}: a straight run along the dominant horizontal axis, two clicks;</li>
 *   <li>{@link #FLOOR}: the filled rectangle between two corners on the first point's layer;</li>
 *   <li>{@link #RING}: the outline of that rectangle (a perimeter of units);</li>
 *   <li>{@link #BOX}: a rectangle extruded over layers, three clicks (the third picks the height);</li>
 *   <li>{@link #HOLLOW_BOX}: the ring of the rectangle on every layer of the box.</li>
 * </ul>
 */
public enum BuildShape {
    SINGLE("single", 1),
    LINE("line", 2),
    FLOOR("floor", 2),
    RING("ring", 2),
    BOX("box", 3),
    HOLLOW_BOX("hollow box", 3);

    private final String label;
    private final int points;

    BuildShape(String label, int points) {
        this.label = label;
        this.points = points;
    }

    public String label() {
        return label;
    }

    /** How many clicks complete the shape. */
    public int points() {
        return points;
    }

    public BuildShape next() {
        BuildShape[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    /**
     * The cells of the shape for the given points. Fewer points than {@link #points()} are allowed
     * (the preview while the shape is in progress): the missing points repeat the last one.
     * Iteration order is layer, then z, then x, ascending.
     */
    public Set<Cell> cells(List<Cell> points) {
        Set<Cell> out = new LinkedHashSet<>();
        if (points.isEmpty()) {
            return out;
        }
        Cell a = points.get(0);
        Cell b = points.size() > 1 ? points.get(1) : a;
        int top = points.size() > 2 ? points.get(2).layer() : a.layer();
        switch (this) {
            case SINGLE -> out.add(a);
            case LINE -> {
                int dx = Math.abs(b.x() - a.x());
                int dz = Math.abs(b.z() - a.z());
                if (dx >= dz) {
                    for (int x = Math.min(a.x(), b.x()); x <= Math.max(a.x(), b.x()); x++) {
                        out.add(new Cell(x, a.layer(), a.z()));
                    }
                } else {
                    for (int z = Math.min(a.z(), b.z()); z <= Math.max(a.z(), b.z()); z++) {
                        out.add(new Cell(a.x(), a.layer(), z));
                    }
                }
            }
            case FLOOR -> rect(out, a, b, a.layer(), a.layer(), false);
            case RING -> rect(out, a, b, a.layer(), a.layer(), true);
            case BOX -> rect(out, a, b, Math.min(a.layer(), top), Math.max(a.layer(), top), false);
            case HOLLOW_BOX -> rect(out, a, b, Math.min(a.layer(), top), Math.max(a.layer(), top), true);
        }
        return out;
    }

    private static void rect(Set<Cell> out, Cell a, Cell b, int low, int high, boolean ring) {
        int x0 = Math.min(a.x(), b.x());
        int x1 = Math.max(a.x(), b.x());
        int z0 = Math.min(a.z(), b.z());
        int z1 = Math.max(a.z(), b.z());
        for (int layer = low; layer <= high; layer++) {
            for (int z = z0; z <= z1; z++) {
                for (int x = x0; x <= x1; x++) {
                    if (ring && x != x0 && x != x1 && z != z0 && z != z1) {
                        continue;
                    }
                    out.add(new Cell(x, layer, z));
                }
            }
        }
    }
}
