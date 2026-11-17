package com.qccore.core.plan;

import java.util.ArrayList;
import java.util.List;

/**
 * Bit mask over the nine QC components, used to build only part of a unit ("just the framework",
 * "everything but the outer walls").
 *
 * <p>The bits follow the export order of {@link com.qccore.core.UnitExporter}: the five per-voxel
 * components first, then the four per-unit structures. {@link #ALL} reproduces the plain export
 * exactly, so masks never change the golden output.
 */
public final class ComponentMask {

    public static final int FRAMEWORK = 1;
    public static final int ROW = 1 << 1;
    public static final int COLUMN = 1 << 2;
    public static final int WALL = 1 << 3;
    public static final int FLOOR = 1 << 4;
    public static final int INNER_COLUMN = 1 << 5;
    public static final int INNER_WALL = 1 << 6;
    public static final int GATE = 1 << 7;
    public static final int OUTER_WALL = 1 << 8;

    public static final int ALL = (1 << 9) - 1;

    /** The bits in export order, paired with {@link #NAMES}. */
    public static final int[] BITS = {FRAMEWORK, ROW, COLUMN, WALL, FLOOR, INNER_COLUMN, INNER_WALL, GATE, OUTER_WALL};
    public static final String[] NAMES = {"framework", "row", "column", "wall", "floor",
            "inner column", "inner wall", "gate", "outer wall"};

    private ComponentMask() {
    }

    public static boolean has(int mask, int bit) {
        return (mask & bit) != 0;
    }

    /** A short label: {@code all}, {@code none} or the enabled component names joined by {@code +}. */
    public static String describe(int mask) {
        if ((mask & ALL) == ALL) {
            return "all";
        }
        if ((mask & ALL) == 0) {
            return "none";
        }
        List<String> names = new ArrayList<>();
        for (int i = 0; i < BITS.length; i++) {
            if (has(mask, BITS[i])) {
                names.add(NAMES[i]);
            }
        }
        return String.join("+", names);
    }
}
