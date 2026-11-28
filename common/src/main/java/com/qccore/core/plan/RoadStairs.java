package com.qccore.core.plan;

import com.qccore.core.UnitPos;
import com.qccore.core.UnitSide;
import com.qccore.core.qc.BlockChoice;
import com.qccore.core.qc.BlockStr;
import com.qccore.core.qc.Side;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;

/**
 * Road level changes (T35, the user's picks 4 and 5 of review #61): where a planned street meets a
 * street one layer up, a road cell of the lower street climbs to it. Which stair is decided by where
 * the upper street is:
 *
 * <ul>
 *   <li><b>stair cell</b> (pick 4): the upper street is one layer up and one cell ahead. A 3-wide flight in
 *       one side of the 7 (the side with no side arm and no door, lanes 5-7 first) climbs eight steps to it;
 *       the other four lanes stay level and run on under the upper street. The first step sits on the
 *       framework in the mouth behind, the eighth leans on the upper street's mouth paving ahead, with two
 *       free blocks before and after. The upper street opens its mouth toward the stair (a joined cell).</li>
 *   <li><b>switchback</b> (pick 5): the upper street is stacked right over the cell. Two 2-wide runs with a
 *       half landing take four of the 7 and leave a 3-wide level lane beside them; the upper street gets a
 *       well over the second run.</li>
 * </ul>
 *
 * <p>Every step gets three free blocks over it and furniture (posts, hedges) gives way in the flight, its
 * approach and its exit. The steps are the road style's {@code flight} role; the wedge under a straight
 * flight and the switchback's first run and landing are the lane floors. Registry-free; positions are
 * plan-local like {@link RoadKit}'s, with the layer already added ({@code y = 8 layer + local}).
 */
public final class RoadStairs {

    private RoadStairs() {
    }

    /** What the stairs add to a road network. */
    public record Result(Map<BlockPos, String> blocks, Set<BlockPos> cleared, Map<Integer, Set<UnitPos>> joined,
                         Map<Integer, Set<Long>> columns, Set<String> missing, List<String> notes, Set<Cell> stairCells) {

        /** Cells of {@code layer} the street there opens a mouth toward (the cell over a stair cell). */
        public Set<UnitPos> joined(int layer) {
            return joined.getOrDefault(layer, Set.of());
        }

        /** Columns of {@code layer} a stair, its approach or its exit takes: decor keeps out. */
        public Set<Long> columns(int layer) {
            return columns.getOrDefault(layer, Set.of());
        }
    }

    /**
     * @param roads    every planned road cell with its style
     * @param free     whether a cell holds nothing (no unit, no road, nothing standing): the room over a stair cell
     * @param openings door-approach columns per layer, which a stair never takes
     */
    public static Result plan(Map<Cell, BlockChoice> roads, Predicate<Cell> free, Function<Integer, Set<Long>> openings) {
        Map<BlockPos, String> blocks = new LinkedHashMap<>();
        Set<BlockPos> cleared = new HashSet<>();
        Map<Integer, Set<UnitPos>> joined = new HashMap<>();
        Map<Integer, Set<Long>> columns = new HashMap<>();
        Set<String> missing = new HashSet<>();
        List<String> notes = new ArrayList<>();
        Map<Cell, Integer> group = groups(roads.keySet());
        Set<Long> linked = new HashSet<>();
        Map<Long, Cell> wanted = new LinkedHashMap<>();
        Set<Cell> stairCells = new HashSet<>();
        List<Cell> order = new ArrayList<>(roads.keySet());
        order.sort((a, b) -> a.layer() != b.layer() ? Integer.compare(a.layer(), b.layer())
                : a.x() != b.x() ? Integer.compare(a.x(), b.x()) : Integer.compare(a.z(), b.z()));
        for (Cell r : order) {
            Cell above = new Cell(r.x(), r.layer() + 1, r.z());
            Plan made = null;
            Cell upper = null;
            if (roads.containsKey(above)) {
                // a street stacked right over this one: a switchback
                upper = above;
                if (linked.contains(pair(group, r, upper))) {
                    continue;
                }
                wanted.putIfAbsent(pair(group, r, upper), r);
                made = switchback(r, roads, openings);
            } else if (free.test(above)) {
                // a street one layer up and one cell ahead: a stair cell climbing to it
                for (UnitSide d : UnitSide.values()) {
                    Cell ahead = step(above, d);
                    if (!roads.containsKey(ahead) || linked.contains(pair(group, r, ahead))) {
                        continue;
                    }
                    upper = ahead;
                    wanted.putIfAbsent(pair(group, r, upper), r);
                    made = straight(r, d, roads, openings);
                    if (made != null) {
                        joined.computeIfAbsent(above.layer(), k -> new HashSet<>()).add(above.pos());
                        break;
                    }
                }
            }
            if (made == null) {
                continue;
            }
            // one stair links two streets: the other cells of the same pair stay level
            linked.add(pair(group, r, upper));
            stairCells.add(r);
            BlockChoice style = roads.get(r);

            String flight = style.get("flight");
            if (flight == null) {
                missing.add("flight");
                continue;
            }
            for (Map.Entry<BlockPos, String> e : made.blocks.entrySet()) {
                String s = e.getValue();
                if (s.startsWith("@")) {
                    // "@facing,half": the flight role in that orientation
                    String[] parts = s.substring(1).split(",");
                    s = BlockStr.stair(flight, Side.valueOf(parts[0]), parts[1], "straight");
                } else if (s.startsWith("#")) {
                    String lane = style.get("floor" + s.substring(1));
                    if (lane == null) {
                        continue;
                    }
                    s = BlockStr.oriented(lane, "y");
                }
                blocks.put(e.getKey(), s);
            }
            cleared.addAll(made.cleared);
            made.columns.forEach((layer, set) -> columns.computeIfAbsent(layer, k -> new HashSet<>()).addAll(set));
        }
        wanted.forEach((key, r) -> {
            if (!linked.contains(key)) {
                notes.add("road " + r.x() + ", " + r.layer() + ", " + r.z() + ": the street one layer up gets no stair here"
                        + " (a stair needs the street to come in from behind, a side with no side street or door, and the cell over it empty)");
            }
        });
        return new Result(blocks, cleared, joined, columns, missing, notes, stairCells);
    }

    /** A stair's blocks ("@E,bottom" = the flight role facing east, "#2" = lane 2's floor), air and columns. */
    private static final class Plan {
        final Map<BlockPos, String> blocks = new LinkedHashMap<>();
        final Set<BlockPos> cleared = new HashSet<>();
        final Map<Integer, Set<Long>> columns = new HashMap<>();

        void column(int layer, BlockPos p) {
            columns.computeIfAbsent(layer, k -> new HashSet<>()).add(RoadKit.key(p.getX(), p.getZ()));
        }
    }

    /**
     * The stair cell: a 3-wide straight flight toward {@code d}, in the side of the 7 with no side arm and no
     * door approach (lanes 5-7 first). Needs the street to come in from behind on this layer (the first step's
     * mouth) and the cell over this one empty.
     */
    private static Plan straight(Cell r, UnitSide d, Map<Cell, BlockChoice> roads, Function<Integer, Set<Long>> openings) {
        if (!roads.containsKey(step(r, opp(d)))) {
            return null;
        }
        int layer = r.layer();
        for (int[] cross : new int[][]{{5, 6, 7}, {1, 2, 3}}) {
            UnitSide sideArm = cross[0] == 5 ? right(d) : opp(right(d));
            if (roads.containsKey(step(r, sideArm))) {
                continue;
            }
            Plan plan = new Plan();
            boolean ok = true;
            for (int c : cross) {
                for (int along = -2; along <= 9 && ok; along++) {
                    int lay = along >= 8 ? layer + 1 : layer;
                    BlockPos col = at(r, d, along, 0, c);
                    ok = !openings.apply(lay).contains(RoadKit.key(col.getX(), col.getZ()));
                }
            }
            if (!ok) {
                continue;
            }
            for (int c : cross) {
                int lane = Math.min(Math.min(c, 8 - c), 4);
                for (int i = 1; i <= 8; i++) {
                    plan.blocks.put(at(r, d, i - 1, i, c), "@" + side(d) + ",bottom");
                    for (int y = 1; y < i; y++) {
                        plan.blocks.put(at(r, d, i - 1, y, c), "#" + lane);
                    }
                    for (int h = 1; h <= 3; h++) {
                        plan.cleared.add(at(r, d, i - 1, i + h, c));
                    }
                }
                for (int along = -2; along <= 9; along++) {
                    int base = along >= 8 ? 8 : 0;
                    for (int y = base + 1; y <= base + 7; y++) {
                        plan.cleared.add(at(r, d, along, y, c));
                    }
                    plan.column(along >= 8 ? layer + 1 : layer, at(r, d, along, 0, c));
                }
            }
            plan.cleared.removeAll(plan.blocks.keySet());
            return plan;
        }
        return null;
    }

    /**
     * The switchback under a stacked street: run one (2 wide, the outer columns) climbs three steps, a 4-wide
     * half landing turns, run two (the next 2 columns) climbs three more back onto the street above, through a
     * well in its paving. Four columns of the 7; the other three stay a level lane.
     */
    private static Plan switchback(Cell r, Map<Cell, BlockChoice> roads, Function<Integer, Set<Long>> openings) {
        int layer = r.layer();
        for (UnitSide d : new UnitSide[]{UnitSide.EAST, UnitSide.SOUTH, UnitSide.WEST, UnitSide.NORTH}) {
            for (int[] cross : new int[][]{{7, 6, 5, 4}, {1, 2, 3, 4}}) {
                UnitSide sideArm = cross[0] == 7 ? right(d) : opp(right(d));
                if (roads.containsKey(step(r, sideArm))) {
                    continue;
                }
                Plan plan = new Plan();
                boolean ok = true;
                for (int c : cross) {
                    for (int along = 1; along <= 7 && ok; along++) {
                        BlockPos col = at(r, d, along, 0, c);
                        long key = RoadKit.key(col.getX(), col.getZ());
                        ok = !openings.apply(layer).contains(key) && !openings.apply(layer + 1).contains(key);
                    }
                }
                if (!ok) {
                    continue;
                }
                int[] outer = {cross[0], cross[1]};
                int[] inner = {cross[2], cross[3]};
                for (int c : outer) {
                    int lane = Math.min(Math.min(c, 8 - c), 4);
                    for (int i = 1; i <= 3; i++) {
                        plan.blocks.put(at(r, d, 2 + i, i, c), "@" + side(d) + ",bottom");
                        for (int y = 1; y < i; y++) {
                            plan.blocks.put(at(r, d, 2 + i, y, c), "#" + lane);
                        }
                    }
                }
                for (int c : cross) {
                    int lane = Math.min(Math.min(c, 8 - c), 4);
                    for (int along = 6; along <= 7; along++) {
                        for (int y = 1; y <= 4; y++) {
                            plan.blocks.put(at(r, d, along, y, c), "#" + lane);
                        }
                    }
                }
                for (int c : inner) {
                    for (int i = 5; i <= 7; i++) {
                        plan.blocks.put(at(r, d, 10 - i, i, c), "@" + side(opp(d)) + ",bottom");
                        plan.blocks.put(at(r, d, 10 - i, i - 1, c), "@" + side(d) + ",top");
                    }
                }
                for (int c : cross) {
                    for (int along = 1; along <= 7; along++) {
                        for (int y = 1; y <= 15; y++) {
                            if (y == 8 && !(along >= 3 && along <= 5 && contains(inner, c))) {
                                continue;
                            }
                            plan.cleared.add(at(r, d, along, y, c));
                        }
                        plan.column(layer, at(r, d, along, 0, c));
                        plan.column(layer + 1, at(r, d, along, 0, c));
                    }
                }
                plan.cleared.removeAll(plan.blocks.keySet());
                return plan;
            }
        }
        return null;
    }

    /** Connected road cells of one layer get one group number. */
    static Map<Cell, Integer> groups(Set<Cell> cells) {
        Map<Cell, Integer> gid = new HashMap<>();
        int n = 0;
        for (Cell c : cells) {
            if (gid.containsKey(c)) {
                continue;
            }
            java.util.ArrayDeque<Cell> todo = new java.util.ArrayDeque<>();
            gid.put(c, n);
            todo.add(c);
            while (!todo.isEmpty()) {
                Cell q = todo.poll();
                for (UnitSide d : UnitSide.values()) {
                    Cell v = step(q, d);
                    if (cells.contains(v) && !gid.containsKey(v)) {
                        gid.put(v, n);
                        todo.add(v);
                    }
                }
            }
            n++;
        }
        return gid;
    }

    private static long pair(Map<Cell, Integer> group, Cell lower, Cell upper) {
        return ((long) group.get(lower) << 32) | (group.get(upper) & 0xffffffffL);
    }

    private static boolean contains(int[] values, int v) {
        for (int x : values) {
            if (x == v) {
                return true;
            }
        }
        return false;
    }

    /**
     * Plan-local position in road cell {@code r}: {@code along} from the plane behind (0) toward {@code d}
     * (8 = the plane ahead), {@code c} across, {@code y} over the cell's floor plane.
     */
    static BlockPos at(Cell r, UnitSide d, int along, int y, int c) {
        int ox = r.x() * RoadKit.PITCH;
        int oy = r.layer() * RoadKit.PITCH;
        int oz = r.z() * RoadKit.PITCH;
        return switch (d) {
            case EAST -> new BlockPos(ox + along, oy + y, oz + c);
            case WEST -> new BlockPos(ox + 8 - along, oy + y, oz + 8 - c);
            case SOUTH -> new BlockPos(ox + 8 - c, oy + y, oz + along);
            case NORTH -> new BlockPos(ox + c, oy + y, oz + 8 - along);
        };
    }

    /** The side to the right of travel toward {@code d}, which is where cross 8 lies in {@link #at}. */
    static UnitSide right(UnitSide d) {
        return switch (d) {
            case EAST -> UnitSide.SOUTH;
            case SOUTH -> UnitSide.WEST;
            case WEST -> UnitSide.NORTH;
            case NORTH -> UnitSide.EAST;
        };
    }

    static UnitSide opp(UnitSide d) {
        return switch (d) {
            case EAST -> UnitSide.WEST;
            case WEST -> UnitSide.EAST;
            case NORTH -> UnitSide.SOUTH;
            case SOUTH -> UnitSide.NORTH;
        };
    }

    private static String side(UnitSide d) {
        return switch (d) {
            case NORTH -> "N";
            case EAST -> "E";
            case SOUTH -> "S";
            case WEST -> "W";
        };
    }

    static Cell step(Cell c, UnitSide d) {
        return switch (d) {
            case NORTH -> new Cell(c.x(), c.layer(), c.z() - 1);
            case SOUTH -> new Cell(c.x(), c.layer(), c.z() + 1);
            case EAST -> new Cell(c.x() + 1, c.layer(), c.z());
            case WEST -> new Cell(c.x() - 1, c.layer(), c.z());
        };
    }
}
