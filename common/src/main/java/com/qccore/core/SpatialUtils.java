package com.qccore.core;

/**
 * Queries about the shape of the unit grid: how many coordinates of a unit-local voxel sit on a
 * unit boundary, and whether a voxel reaches into empty space.
 */
public final class SpatialUtils {

    private static final int[][][] BOUNDARY_COUNT = new int[9][9][9];

    static {
        for (int x = 0; x < 9; x++) {
            for (int y = 0; y < 9; y++) {
                for (int z = 0; z < 9; z++) {
                    int count = 0;
                    if (x == 0 || x == 8) count++;
                    if (y == 0 || y == 8) count++;
                    if (z == 0 || z == 8) count++;

                    BOUNDARY_COUNT[x][y][z] = count;
                }
            }
        }
    }

    private SpatialUtils() {
    }

    /**
     * How many of the three unit-local coordinates sit on a unit boundary: {@code 0} inside the
     * shell, {@code 2} on a face, {@code 3} on a corner.
     */
    public static int boundaryCount(int x, int y, int z) {
        return BOUNDARY_COUNT[x][y][z];
    }

    /** Check if a relative position (x, y, z) in unit (dx, dy, dz) has another unit on it. */
    static boolean isValidPlacement(UnitLayers layers, int x, int y, int z, int dx, int dy, int dz) {
        // 1. Shift the block offset coordinates if the internal coordinate spills past the 0-8 boundaries
        if (x < 0) dx--;
        else if (x > 8) dx++;

        if (y < 0) dy--;
        else if (y > 8) dy++;

        if (z < 0) dz--;
        else if (z > 8) dz++;

        // 2. Base check: Does the current root chunk exist?
        boolean res = layers.contains(dx, dy, dz);

        // 3. Boundary Neighbor Verification:
        // If an internal coordinate sits exactly on an outer shell (0 or 8),
        // ensure the adjacent chunk in that specific direction also exists.

        // X-Boundaries
        if (x == 0) res |= layers.contains(dx - 1, dy, dz);
        if (x == 8) res |= layers.contains(dx + 1, dy, dz);

        // Y-Boundaries
        if (y == 0) res |= layers.contains(dx, dy - 1, dz);
        if (y == 8) res |= layers.contains(dx, dy + 1, dz);

        // Z-Boundaries
        if (z == 0) res |= layers.contains(dx, dy, dz - 1);
        if (z == 8) res |= layers.contains(dx, dy, dz + 1);

        return !res;
    }
}
