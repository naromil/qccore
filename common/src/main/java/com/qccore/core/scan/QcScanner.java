package com.qccore.core.scan;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds QC units that already stand in the world, so the index can remember them like units the mod
 * built itself. Registry-free: it reads a box of block ids through {@link Voxels}.
 *
 * <p>What it recognises comes from qckit's building.py and road.py:
 * <ul>
 *   <li><b>building unit</b>: a 9x9x9 cell on the 8-grid whose twelve edges are framework (frame posts on the
 *       four vertical edges, beams on the eight horizontal ones), with a mostly hollow interior. The grid is the
 *       one of the 512 offsets {@code (a, b, c)} that explains the most such cells.</li>
 *   <li><b>shared side</b> of two building units on one layer: {@code wall} (the 7x7 plane is filled),
 *       {@code gate} (filled except a 3x3 passage at floor level, qckit's "rooms" partition) or {@code open}.</li>
 *   <li><b>door</b>: an exterior side of a ground unit with a door block at its centre (a = 4, y = 1) or qckit's
 *       3x3 archway opening there.</li>
 *   <li><b>road cell</b>: a non-building cell on a ground layer whose 7x7 floor is filled with a laid
 *       (non-terrain) block and whose 7x7x7 space above is mostly air, reached from a building door or front by
 *       walking from road cell to road cell. Plain terrain never counts, so a meadow is not a road.</li>
 * </ul>
 *
 * <p>Side bits follow the index wire format: bit0 north (-z), bit1 east (+x), bit2 south (+z), bit3 west (-x).
 * Party sides map onto the plan's partition flags: wall = wall bit, gate = wall + gate bits, open = gate bit;
 * exterior sides carry no flag (the design decides).
 */
public final class QcScanner {

    /** Read access to a box of the world; coordinates are absolute. */
    public interface Voxels {
        /** The block id at a position, {@code "minecraft:air"} for air and anything outside the box. */
        String id(int x, int y, int z);
    }

    public enum Kind { BUILDING, ROAD }

    /** One unit found: its origin corner (absolute), kind, partition flags and door sides. */
    public record Found(int x, int y, int z, Kind kind, int walls, int gates, int doors) {
    }

    /** The grid found ({@code anchor} = origin mod 8 per axis) and the units on it, buildings first. */
    public record Result(int ax, int ay, int az, List<Found> units) {
        public long count(Kind kind) {
            return units.stream().filter(u -> u.kind() == kind).count();
        }
    }

    static final int[][] SIDES = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}}; // N E S W as (dx, dz)

    /** Natural blocks that never make a road floor, so open terrain is not read as a street. */
    private static final Set<String> TERRAIN = Set.of(
            "grass_block", "dirt", "coarse_dirt", "rooted_dirt", "podzol", "mycelium", "dirt_path", "farmland",
            "mud", "sand", "red_sand", "gravel", "clay", "stone", "granite", "diorite", "andesite", "deepslate",
            "tuff", "calcite", "dripstone_block", "sandstone", "red_sandstone", "terracotta", "snow_block",
            "powder_snow", "ice", "packed_ice", "blue_ice", "netherrack", "soul_sand", "soul_soil", "end_stone",
            "basalt", "blackstone", "moss_block", "bedrock", "water", "lava");

    private final int minX, minY, minZ, maxX, maxY, maxZ;
    private final int sizeX, sizeY, sizeZ;
    /** The box read once: a palette index per position, the palette's bare names and solidity. */
    private final int[] cells;
    private final List<String> names = new ArrayList<>();
    private final List<Boolean> solids = new ArrayList<>();
    private boolean[] solidByIndex;

    /** Scans the box {@code [min, max]} (inclusive); only cells lying wholly inside it are considered. */
    public QcScanner(Voxels voxels, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        this.sizeX = maxX - minX + 1;
        this.sizeY = maxY - minY + 1;
        this.sizeZ = maxZ - minZ + 1;
        this.cells = new int[sizeX * sizeY * sizeZ];
        Map<String, Integer> palette = new HashMap<>();
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    String id = voxels.id(x, y, z);
                    Integer i = palette.get(id);
                    if (i == null) {
                        i = names.size();
                        palette.put(id, i);
                        String bare = bare(id);
                        names.add(bare);
                        solids.add(!(bare.equals("air") || bare.equals("cave_air") || bare.equals("void_air")
                                || bare.equals("water") || bare.equals("lava")));
                    }
                    cells[index(x, y, z)] = i;
                }
            }
        }
        solidByIndex = new boolean[solids.size()];
        for (int i = 0; i < solidByIndex.length; i++) {
            solidByIndex[i] = solids.get(i);
        }
    }

    private int index(int x, int y, int z) {
        return ((y - minY) * sizeX + (x - minX)) * sizeZ + (z - minZ);
    }

    private boolean inBox(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    /** Finds the grid and every unit on it, or {@code null} when no building unit stands in the box. */
    public Result scan() {
        int bestCount = 0;
        int[] best = null;
        Map<Long, int[]> bestCells = null;
        for (int a = 0; a < 8; a++) {
            for (int b = 0; b < 8; b++) {
                for (int c = 0; c < 8; c++) {
                    Map<Long, int[]> cells = buildingCells(a, b, c);
                    if (cells.size() > bestCount) {
                        bestCount = cells.size();
                        best = new int[]{a, b, c};
                        bestCells = cells;
                    }
                }
            }
        }
        if (best == null) {
            return null;
        }

        List<Found> units = new ArrayList<>();
        for (int[] o : bestCells.values()) {
            units.add(describe(o, bestCells));
        }
        units.addAll(roads(bestCells, units));
        return new Result(best[0], best[1], best[2], units);
    }

    // -- buildings --------------------------------------------------------------------------

    private Map<Long, int[]> buildingCells(int a, int b, int c) {
        Map<Long, int[]> cells = new LinkedHashMap<>();
        for (int y = first(minY, b); y + 8 <= maxY; y += 8) {
            for (int x = first(minX, a); x + 8 <= maxX; x += 8) {
                for (int z = first(minZ, c); z + 8 <= maxZ; z += 8) {
                    if (isBuildingCell(x, y, z)) {
                        cells.put(key(x, y, z), new int[]{x, y, z});
                    }
                }
            }
        }
        return cells;
    }

    /**
     * All twelve edges built (two gaps allowed for wear) and at least a quarter of the interior air. A
     * natural block on an edge counts as a gap, so a cave in solid rock is never read as a room.
     */
    boolean isBuildingCell(int x, int y, int z) {
        int gaps = 0;
        for (int i = 0; i <= 8 && gaps <= 2; i++) {
            for (int p = 0; p <= 8; p += 8) {
                for (int q = 0; q <= 8; q += 8) {
                    gaps += edge(x + p, y + i, z + q);                  // vertical posts
                    if (i > 0 && i < 8) {
                        gaps += edge(x + i, y + p, z + q);              // beams along x
                        gaps += edge(x + p, y + q, z + i);              // beams along z
                    }
                }
            }
        }
        if (gaps > 2) {
            return false;
        }
        int air = 0;
        for (int i = 1; i <= 7; i++) {
            for (int j = 1; j <= 7; j++) {
                for (int k = 1; k <= 7; k++) {
                    if (!solid(x + i, y + j, z + k)) air++;
                }
            }
        }
        return air * 4 >= 343;
    }

    private int edge(int x, int y, int z) {
        return solid(x, y, z) && !TERRAIN.contains(name(x, y, z)) ? 0 : 1;
    }

    private Found describe(int[] o, Map<Long, int[]> cells) {
        int walls = 0, gates = 0, doors = 0;
        boolean ground = !cells.containsKey(key(o[0], o[1] - 8, o[2]));
        for (int s = 0; s < 4; s++) {
            int bit = 1 << s;
            boolean party = cells.containsKey(key(o[0] + 8 * SIDES[s][0], o[1], o[2] + 8 * SIDES[s][1]));
            if (party) {
                switch (partyKind(o, s)) {
                    case "wall" -> walls |= bit;
                    case "gate" -> { walls |= bit; gates |= bit; }
                    default -> gates |= bit;
                }
            } else if (ground && isDoor(o, s)) {
                doors |= bit;
            }
        }
        return new Found(o[0], o[1], o[2], Kind.BUILDING, walls, gates, doors);
    }

    /** wall | gate | open for a side shared with another building unit. */
    String partyKind(int[] o, int side) {
        int solid = 0, passage = 0;
        for (int a = 1; a <= 7; a++) {
            for (int y = 1; y <= 7; y++) {
                boolean filled = solid(face(o, side, a, y));
                if (filled) solid++;
                if (a >= 3 && a <= 5 && y <= 3 && !filled) passage++;
            }
        }
        if (solid >= 45) {
            return "wall";
        }
        if (passage == 9 && solid >= 30) {
            return "gate";
        }
        return "open";
    }

    /** A door block at the side's centre, or qckit's 3x3 archway in an otherwise filled face. */
    boolean isDoor(int[] o, int side) {
        if (name(face(o, side, 4, 1)).endsWith("door") && name(face(o, side, 4, 2)).endsWith("door")) {
            return true;
        }
        int filled = 0;
        for (int a = 1; a <= 7; a++) {
            for (int y = 1; y <= 7; y++) {
                boolean opening = a >= 3 && a <= 5 && y <= 3;
                boolean s = solid(face(o, side, a, y));
                if (opening && s) return false;
                if (!opening && s) filled++;
            }
        }
        // Not a door when the whole face is open: a glazed or framed face keeps most of its plane.
        return filled >= 20;
    }

    // -- roads ------------------------------------------------------------------------------

    private List<Found> roads(Map<Long, int[]> buildings, List<Found> described) {
        Map<Long, int[]> roads = new LinkedHashMap<>();
        Deque<int[]> queue = new ArrayDeque<>();
        Map<Long, Boolean> seen = new HashMap<>();
        for (Found f : described) {
            if (buildings.containsKey(key(f.x(), f.y() - 8, f.z()))) {
                continue;                                   // only ground units front a street
            }
            for (int s = 0; s < 4; s++) {
                queue.add(new int[]{f.x() + 8 * SIDES[s][0], f.y(), f.z() + 8 * SIDES[s][1]});
            }
        }
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            long k = key(c[0], c[1], c[2]);
            if (seen.put(k, Boolean.TRUE) != null || buildings.containsKey(k) || !inside(c)) {
                continue;
            }
            if (!isRoadCell(c)) {
                continue;
            }
            roads.put(k, c);
            for (int s = 0; s < 4; s++) {
                queue.add(new int[]{c[0] + 8 * SIDES[s][0], c[1], c[2] + 8 * SIDES[s][1]});
            }
        }
        List<Found> out = new ArrayList<>();
        for (int[] c : roads.values()) {
            out.add(new Found(c[0], c[1], c[2], Kind.ROAD, 0, 0, 0));
        }
        return out;
    }

    /** A laid 7x7 floor at y 0 (at most 4 gaps, no terrain block dominating) under a mostly empty 7x7x7. */
    boolean isRoadCell(int[] c) {
        int gaps = 0, terrain = 0;
        for (int i = 1; i <= 7; i++) {
            for (int k = 1; k <= 7; k++) {
                int[] p = {c[0] + i, c[1], c[2] + k};
                if (!solid(p)) {
                    gaps++;
                } else if (TERRAIN.contains(name(p))) {
                    terrain++;
                }
            }
        }
        if (gaps > 4 || terrain * 2 > 49) {
            return false;
        }
        int filled = 0;
        for (int i = 1; i <= 7; i++) {
            for (int j = 1; j <= 7; j++) {
                for (int k = 1; k <= 7; k++) {
                    if (solid(c[0] + i, c[1] + j, c[2] + k)) filled++;
                }
            }
        }
        return filled * 6 <= 343;                           // street furniture allowed, a room is not
    }

    // -- helpers ----------------------------------------------------------------------------

    private static int[] face(int[] o, int side, int a, int y) {
        return switch (side) {
            case 0 -> new int[]{o[0] + a, o[1] + y, o[2]};
            case 1 -> new int[]{o[0] + 8, o[1] + y, o[2] + a};
            case 2 -> new int[]{o[0] + a, o[1] + y, o[2] + 8};
            default -> new int[]{o[0], o[1] + y, o[2] + a};
        };
    }

    private boolean inside(int[] c) {
        return c[0] >= minX && c[1] >= minY && c[2] >= minZ && c[0] + 8 <= maxX && c[1] + 8 <= maxY
                && c[2] + 8 <= maxZ;
    }

    private boolean solid(int[] p) {
        return solid(p[0], p[1], p[2]);
    }

    private boolean solid(int x, int y, int z) {
        return inBox(x, y, z) && solidByIndex[cells[index(x, y, z)]];
    }

    private String name(int[] p) {
        return name(p[0], p[1], p[2]);
    }

    private String name(int x, int y, int z) {
        return inBox(x, y, z) ? names.get(cells[index(x, y, z)]) : "air";
    }

    /** The id without namespace or block state, so {@code minecraft:oak_door[half=upper]} reads {@code oak_door}. */
    static String bare(String id) {
        int bracket = id.indexOf('[');
        if (bracket >= 0) id = id.substring(0, bracket);
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }

    /** The first coordinate {@code >= min} that is congruent to {@code offset} mod 8. */
    static int first(int min, int offset) {
        return min + Math.floorMod(offset - min, 8);
    }

    static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (y & 0xFFF) << 26) | (z & 0x3FFFFFF);
    }
}
