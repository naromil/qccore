package com.qccore.core.plan;

import com.qccore.core.UnitPos;
import com.qccore.core.UnitSide;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The QC road template, ported from qckit's {@code road.py}: the 7-wide negative space between
 * buildings, laid on one layer of the lattice.
 *
 * <p>A road cell owns its 7-wide interior plus the <em>mouth</em> of each arm (the shared plane the
 * floor crosses to reach the next road cell) and nothing else: a closed side's plane is a building's
 * wall line and the four corner posts are the buildings' corners. A lattice post with road on all
 * four sides is nobody's corner and is paved (a plaza). Every road block lies inside that area, a
 * piece is one block tall (plus optional furniture), and any piece tiles against any other.
 *
 * <p>Coordinates are layer-local: cell {@code (cx, cz)} spans {@code x 8cx..8cx+8}, {@code z
 * 8cz..8cz+8}, and y 0 is the floor plane of the layer. The result maps those positions to the
 * block id strings of the {@link RoadStyle}; no registry is touched here.
 */
public final class RoadKit {

    public static final int PITCH = 8;

    private RoadKit() {
    }

    /** The open sides of a road cell: the sides with another road cell next to them. */
    public static Set<UnitSide> arms(UnitPos cell, Set<UnitPos> cells) {
        Set<UnitSide> arms = EnumSet.noneOf(UnitSide.class);
        for (UnitSide side : UnitSide.values()) {
            if (cells.contains(neighbour(cell, side))) {
                arms.add(side);
            }
        }
        return arms;
    }

    /** A bend, a tee or a crossing carries the medallion; a straight run and an end do not. */
    public static boolean isNode(Set<UnitSide> arms) {
        boolean straight = arms.size() == 2
                && (arms.contains(UnitSide.NORTH) && arms.contains(UnitSide.SOUTH)
                || arms.contains(UnitSide.EAST) && arms.contains(UnitSide.WEST));
        return arms.size() >= 2 && !straight;
    }

    /** {@code island}, {@code end}, {@code street}, {@code corner}, {@code tee} or {@code cross}. */
    public static String piece(Set<UnitSide> arms) {
        return switch (arms.size()) {
            case 0 -> "island";
            case 1 -> "end";
            case 3 -> "tee";
            case 4 -> "cross";
            default -> isNode(arms) ? "corner" : "street";
        };
    }

    /** The local (x, z) a cell owns: interior + arm mouths, never a closed plane or a corner post. */
    public static Set<Long> cellArea(Set<UnitSide> arms) {
        Set<Long> keep = new HashSet<>();
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 8; z++) {
                if ((x == 0 || x == 8) && (z == 0 || z == 8)) {
                    continue;
                }
                if (z == 0 && !arms.contains(UnitSide.NORTH) || z == 8 && !arms.contains(UnitSide.SOUTH)
                        || x == 0 && !arms.contains(UnitSide.WEST) || x == 8 && !arms.contains(UnitSide.EAST)) {
                    continue;
                }
                keep.add(key(x, z));
            }
        }
        return keep;
    }

    /**
     * Every road column of the network with the cell that owns it. A mouth column is shared by two
     * cells that write it identically; the first cell in (x, z) order keeps it.
     */
    public static Map<Long, UnitPos> networkArea(Set<UnitPos> cells) {
        List<UnitPos> sorted = new ArrayList<>(cells);
        sorted.sort(Comparator.comparingInt(UnitPos::x).thenComparingInt(UnitPos::z));
        Map<Long, UnitPos> out = new LinkedHashMap<>();
        for (UnitPos cell : sorted) {
            Set<Long> area = cellArea(arms(cell, cells));
            for (int x = 0; x <= 8; x++) {
                for (int z = 0; z <= 8; z++) {
                    if (area.contains(key(x, z))) {
                        out.putIfAbsent(key(cell.x() * PITCH + x, cell.z() * PITCH + z), cell);
                    }
                }
            }
        }
        for (UnitPos cell : sorted) {
            if (cells.contains(new UnitPos(cell.x() + 1, cell.z())) && cells.contains(new UnitPos(cell.x(), cell.z() + 1))
                    && cells.contains(new UnitPos(cell.x() + 1, cell.z() + 1))) {
                out.putIfAbsent(key((cell.x() + 1) * PITCH, (cell.z() + 1) * PITCH), cell);
            }
        }
        return out;
    }

    /** (x, z) -> lane 1..4: the Chebyshev distance to the nearest non-road column, capped at 4. */
    public static Map<Long, Integer> lanes(Set<Long> area) {
        Map<Long, Integer> out = new LinkedHashMap<>();
        for (long column : area) {
            int x = x(column);
            int z = z(column);
            int lane = 4;
            search:
            for (int r = 1; r <= 3; r++) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) == r && !area.contains(key(x + dx, z + dz))) {
                            lane = r;
                            break search;
                        }
                    }
                }
            }
            out.put(column, lane);
        }
        return out;
    }

    /** On a transverse cell plane: the light/joint line every 8 blocks. */
    public static boolean isJoint(int x, int z) {
        return Math.floorMod(x, PITCH) == 0 || Math.floorMod(z, PITCH) == 0;
    }

    /**
     * Lays the road network.
     *
     * @param cells   road cell -> its style (cells of one layer)
     * @param columns layer-local (x, z) columns a building already stands on; the road writes nothing
     *                there (paving, joint lights and furniture all give way)
     * @return layer-local position -> block id, floor first, then furniture, then medallions
     */
    public static Map<BlockPos, String> build(Map<UnitPos, RoadStyle> cells, Set<Long> columns) {
        Map<Long, UnitPos> area = networkArea(cells.keySet());
        Map<Long, Integer> lane = lanes(area.keySet());
        Map<BlockPos, String> blocks = new LinkedHashMap<>();
        for (Map.Entry<Long, UnitPos> entry : area.entrySet()) {
            long column = entry.getKey();
            if (columns.contains(column)) {
                continue;
            }
            int x = x(column);
            int z = z(column);
            RoadStyle style = cells.get(entry.getValue());
            int ln = lane.get(column) - 1;
            boolean joint = isJoint(x, z);
            String surface = joint && !RoadStyle.blank(style.joint[ln]) ? style.joint[ln] : style.floor[ln];
            if (!RoadStyle.blank(surface)) {
                blocks.put(new BlockPos(x, 0, z), surface);
            }
            List<String> post = joint ? style.post[ln] : List.of();
            if (!post.isEmpty()) {
                for (int i = 0; i < post.size(); i++) {
                    if (!RoadStyle.blank(post.get(i))) {
                        blocks.put(new BlockPos(x, i + 1, z), post.get(i));
                    }
                }
                continue;
            }
            if (!RoadStyle.blank(style.over[ln]) && !(joint && !style.post[ln].isEmpty())) {
                blocks.put(new BlockPos(x, 1, z), style.over[ln]);
            }
        }
        for (Map.Entry<UnitPos, RoadStyle> entry : cells.entrySet()) {
            UnitPos cell = entry.getKey();
            RoadStyle style = entry.getValue();
            if (!isNode(arms(cell, cells.keySet())) || RoadStyle.blank(style.medallion)) {
                continue;
            }
            int ox = cell.x() * PITCH;
            int oz = cell.z() * PITCH;
            for (int x = 3; x <= 5; x++) {
                for (int z = 3; z <= 5; z++) {
                    if (!columns.contains(key(ox + x, oz + z))) {
                        blocks.put(new BlockPos(ox + x, 0, oz + z), style.medallion);
                    }
                }
            }
            if (!RoadStyle.blank(style.centre) && !columns.contains(key(ox + 4, oz + 4))) {
                blocks.put(new BlockPos(ox + 4, 0, oz + 4), style.centre);
            }
        }
        return blocks;
    }

    public static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    public static int x(long key) {
        return (int) (key >> 32);
    }

    public static int z(long key) {
        return (int) key;
    }

    private static UnitPos neighbour(UnitPos cell, UnitSide side) {
        return switch (side) {
            case NORTH -> new UnitPos(cell.x(), cell.z() - 1);
            case SOUTH -> new UnitPos(cell.x(), cell.z() + 1);
            case EAST -> new UnitPos(cell.x() + 1, cell.z());
            case WEST -> new UnitPos(cell.x() - 1, cell.z());
        };
    }
}
