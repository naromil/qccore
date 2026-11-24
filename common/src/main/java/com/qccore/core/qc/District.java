package com.qccore.core.qc;

import com.qccore.core.QcUnit;
import com.qccore.core.UnitPos;
import com.qccore.core.plan.BuildingSpec;
import com.qccore.core.plan.Cell;
import com.qccore.core.plan.QcPlan;
import com.qccore.core.plan.RoadKit;
import com.qccore.core.plan.Surroundings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;

/**
 * A plan's qckit buildings and roads -> blocks, ported from qckit's {@code Plan.build}: every
 * building is generated from its design and style, the roads are laid round them (keeping door
 * approaches free and giving way to thick columns), and the parts are merged - outward decoration
 * yields to a neighbour up to that neighbour's height and to a road below head height, a facade
 * piece's relief goes whole or not at all, a shared plane is kept by the first building, and the
 * connections of panes, fences and walls are spelled out at the end.
 *
 * <p>Positions are plan-local: unit {@code (x, layer, z)} has its origin at {@code (8x, 8 layer, 8z)}.
 * Units of {@link Templates#LEGACY} buildings are not generated here; they only count as neighbours.
 * Registry-free: the result is block-id strings.
 */
public final class District {

    /**
     * The generated blocks, plan-local, in write order; the problems that stop an apply (unchosen
     * roles, unknown design values); notes worth showing (doors onto nothing, unreachable units,
     * collisions); and per style, the roles its buildings and roads reached.
     */
    public record Result(Map<BlockPos, String> blocks, List<String> problems, List<String> notes,
                         Map<String, Set<String>> reached, Map<String, Set<String>> missing) {
    }

    private District() {
    }

    /** Whether the unit at {@code cell} is built by this generator (a qckit building's unit). */
    public static boolean generates(QcPlan plan, Cell cell) {
        String key = plan.building(cell);
        BuildingSpec spec = key == null ? null : plan.buildings().get(key);
        return spec != null && !Templates.LEGACY.equals(spec.template);
    }

    public static Result build(QcPlan plan) {
        return build(plan, Surroundings.NONE);
    }

    /**
     * As {@link #build(QcPlan)}, with the roads joining the standing road cells of {@code around} and
     * keeping the approach of its standing doors free of road furniture.
     */
    public static Result build(QcPlan plan, Surroundings around) {
        List<String> problems = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        Map<String, Set<String>> reached = new LinkedHashMap<>();
        Map<String, Set<String>> missing = new LinkedHashMap<>();

        // 1. units by building; legacy units are neighbours only
        Map<String, Set<Building.Unit>> byBuilding = new TreeMap<>();
        Map<Cell, String> owner = new HashMap<>();
        Set<Cell> legacy = new HashSet<>();
        for (int layer : plan.units().layerKeys()) {
            for (UnitPos pos : plan.units().peekLayer(layer).keySet()) {
                Cell cell = Cell.of(pos, layer);
                if (generates(plan, cell)) {
                    String key = plan.building(cell);
                    byBuilding.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(new Building.Unit(pos.x(), layer, pos.z()));
                    owner.put(cell, key);
                } else {
                    legacy.add(cell);
                }
            }
        }
        Set<Cell> roadCells = plan.roads().keySet();

        // 2. generate every building
        Map<String, Building> made = new LinkedHashMap<>();
        for (Map.Entry<String, Set<Building.Unit>> entry : byBuilding.entrySet()) {
            String key = entry.getKey();
            BuildingSpec spec = plan.buildings().get(key);
            Design design = spec.design();
            if (!Templates.exists(spec.template)) {
                problems.add("building " + key + ": template '" + spec.template + "' does not exist");
                continue;
            }
            for (String p : design.problems()) {
                problems.add("building " + key + ": " + p);
            }
            BlockChoice choice = plan.styles().get(spec.style);
            if (choice == null) {
                problems.add("building " + key + ": style '" + spec.style + "' does not exist");
                continue;
            }
            StyleReader reader = new StyleReader(spec.style, choice);
            Building.Context ctx = new Building.Context() {
                @Override
                public boolean road(int cx, int layer, int cz) {
                    return roadCells.contains(new Cell(cx, layer, cz));
                }

                @Override
                public boolean occupied(int cx, int layer, int cz) {
                    Cell cell = new Cell(cx, layer, cz);
                    return legacy.contains(cell) || owner.containsKey(cell) && !key.equals(owner.get(cell));
                }

                @Override
                public int top(int cx, int cz) {
                    int top = Integer.MIN_VALUE;
                    for (int layer : plan.units().layerKeys()) {
                        Cell cell = new Cell(cx, layer, cz);
                        if (legacy.contains(cell) || owner.containsKey(cell) && !key.equals(owner.get(cell))) {
                            top = Math.max(top, layer * 8 + 8);
                        }
                    }
                    return top;
                }
            };
            Building.Party party = (u, side) -> {
                QcUnit unit = plan.units().peekLayer(u.s()) == null ? null
                        : plan.units().peekLayer(u.s()).get(new UnitPos(u.x(), u.z()));
                return unit == null ? null : QcPlan.partyOverride(unit, side.unitSide());
            };
            Building b = new Building(key, entry.getValue(), design, reader, ctx, party).build();
            made.put(key, b);
            reached.computeIfAbsent(spec.style, k -> new LinkedHashSet<>()).addAll(reader.reached());
            if (!reader.missing().isEmpty()) {
                missing.computeIfAbsent(spec.style, k -> new LinkedHashSet<>()).addAll(reader.missing());
            }
            for (String note : b.notes) {
                notes.add("building " + key + ": " + note);
            }
        }

        // 3. the roads, layer by layer, round the buildings
        Map<Integer, Map<UnitPos, BlockChoice>> roadLayers = new TreeMap<>();
        for (Map.Entry<Cell, String> entry : plan.roads().entrySet()) {
            BlockChoice style = plan.styles().get(entry.getValue());
            if (style == null) {
                problems.add("road style '" + entry.getValue() + "' does not exist");
                continue;
            }
            roadLayers.computeIfAbsent(entry.getKey().layer(), k -> new LinkedHashMap<>()).put(entry.getKey().pos(), style);
            Set<String> roles = reached.computeIfAbsent(entry.getValue(), k -> new LinkedHashSet<>());
            for (Role role : Roles.of(Roles.ROAD)) {
                roles.add(role.name());
            }
            Set<String> gone = new LinkedHashSet<>();
            for (int lane = 1; lane <= 4; lane++) {
                if (style.get("floor" + lane) == null) {
                    gone.add("floor" + lane);
                }
            }
            if (!gone.isEmpty()) {
                missing.computeIfAbsent(entry.getValue(), k -> new LinkedHashSet<>()).addAll(gone);
            }
        }
        Map<BlockPos, String> roadBlocks = new LinkedHashMap<>();
        Map<Long, Set<Integer>> roadFloor = new HashMap<>();
        for (Map.Entry<Integer, Map<UnitPos, BlockChoice>> entry : roadLayers.entrySet()) {
            int layer = entry.getKey();
            Set<Long> openings = new HashSet<>(around.doorOpenings(layer));
            Set<Long> columns = new HashSet<>();
            for (Building b : made.values()) {
                if (b.groundLayer == layer) {
                    openings.addAll(b.openings);
                    for (long c : b.groundThick) {
                        boolean elsewhere = false;
                        for (Building o : made.values()) {
                            elsewhere |= o != b && o.cols.contains(c);
                        }
                        if (!elsewhere) {
                            columns.add(c);
                        }
                    }
                }
            }
            Set<UnitPos> joined = around.joinedRoads(plan, layer);
            for (Map.Entry<BlockPos, String> road : RoadKit.build(entry.getValue(), joined, openings, columns).entrySet()) {
                roadBlocks.put(road.getKey().offset(0, layer * 8, 0), road.getValue());
            }
            for (long column : RoadKit.networkArea(entry.getValue().keySet(), joined).keySet()) {
                roadFloor.computeIfAbsent(column, k -> new HashSet<>()).add(layer * 8);
            }
        }

        // standing roads count as road for the yield rules, and standing buildings and doors are kept
        for (Cell cell : around.standingRoads(plan)) {
            for (int x = 0; x <= 8; x++) {
                for (int z = 0; z <= 8; z++) {
                    roadFloor.computeIfAbsent(RoadKit.key(cell.x() * 8 + x, cell.z() * 8 + z), k -> new HashSet<>()).add(cell.layer() * 8);
                }
            }
        }
        Set<Cell> standing = around.standingBuildings(plan);
        Map<BlockPos, String> doorZone = doorZones(made, around);

        // 4. merge: outward decoration yields to neighbours, to the road below head height, to what
        // already stands and to every door step
        Map<String, Map<Long, Integer>> tops = new HashMap<>();
        for (Map.Entry<String, Building> entry : made.entrySet()) {
            Map<Long, Integer> t = new HashMap<>();
            Building b = entry.getValue();
            for (BlockPos p : b.blocks.keySet()) {
                long col = Building.key(p.getX(), p.getZ());
                if (b.cols.contains(col)) {
                    t.merge(col, p.getY(), Math::max);
                }
            }
            tops.put(entry.getKey(), t);
        }
        Map<BlockPos, String> out = new LinkedHashMap<>();
        Map<BlockPos, String> by = new HashMap<>();
        for (Map.Entry<String, Building> entry : made.entrySet()) {
            String ch = entry.getKey();
            Building b = entry.getValue();
            Set<Long> others = new HashSet<>();
            for (Map.Entry<String, Building> o : made.entrySet()) {
                if (!o.getKey().equals(ch)) {
                    others.addAll(o.getValue().cols);
                }
            }
            Set<BlockPos> doomed = new HashSet<>();
            for (Set<BlockPos> group : b.relief) {
                for (BlockPos q : group) {
                    if (b.blocks.containsKey(q) && b.outward.contains(q)
                            && (yields(q, b, ch, others, tops, roadFloor) || inside(standing, q) || blocksDoor(doorZone, q, ch))) {
                        doomed.addAll(group);
                        break;
                    }
                }
            }
            for (Map.Entry<BlockPos, String> block : b.blocks.entrySet()) {
                BlockPos p = block.getKey();
                String s = block.getValue();
                long col = Building.key(p.getX(), p.getZ());
                if (doomed.contains(p) || inside(standing, p)) {
                    continue;
                }
                if (b.outward.contains(p)) {
                    if (blocksDoor(doorZone, p, ch)) {
                        continue;
                    }
                    if (others.contains(col) && p.getY() <= otherTop(tops, ch, col)) {
                        continue;
                    }
                    if (b.thick.contains(col) && b.pier.containsKey(p)) {
                        // a thick column: the road gives way
                    } else if (underRoad(roadFloor, col, p.getY())) {
                        continue;
                    } else if (roadFloor.containsKey(col) && b.pier.containsKey(p)) {
                        s = b.pier.get(p);
                        if (s == null) {
                            continue;
                        }
                    }
                }
                if (out.containsKey(p) && !ch.equals(by.get(p))) {
                    continue;
                }
                out.put(p, s);
                by.put(p, ch);
            }
        }
        for (Map.Entry<BlockPos, String> road : roadBlocks.entrySet()) {
            BlockPos p = road.getKey();
            if (out.containsKey(p)) {
                notes.add("road block at " + p.toShortString() + " gives way to building " + by.get(p));
                continue;
            }
            if (inside(standing, p)) {
                continue;
            }
            out.put(p, road.getValue());
            by.put(p, "road");
        }

        // 5. street decor round the buildings (decor.py), before the connection pass
        BlockChoice decor = plan.decorStyle == null || plan.decorStyle.isBlank() ? null : plan.styles().get(plan.decorStyle);
        if (plan.decorStyle != null && !plan.decorStyle.isBlank()) {
            if (decor == null || !decor.kind().equals(Roles.DECOR)) {
                problems.add("decor style '" + plan.decorStyle + "' does not exist or is not a decor style");
            } else {
                Set<String> roles = reached.computeIfAbsent(plan.decorStyle, k -> new LinkedHashSet<>());
                for (Role role : Roles.of(Roles.DECOR)) {
                    roles.add(role.name());
                }
                Map<String, String> templates = new HashMap<>();
                made.keySet().forEach(k -> templates.put(k, plan.buildings().get(k).template));
                for (Map.Entry<Integer, Map<UnitPos, BlockChoice>> entry : roadLayers.entrySet()) {
                    int layer = entry.getKey();
                    // standing doors keep their approach and standing buildings are no open edge, as for the roads
                    Set<Long> openings = new HashSet<>(around.doorOpenings(layer));
                    for (Building b : made.values()) {
                        if (b.groundLayer == layer) {
                            openings.addAll(b.openings);
                        }
                    }
                    Set<UnitPos> occupied = new HashSet<>();
                    if (plan.units().peekLayer(layer) != null) {
                        occupied.addAll(plan.units().peekLayer(layer).keySet());
                    }
                    for (Cell cell : standing) {
                        if (cell.layer() == layer) {
                            occupied.add(cell.pos());
                        }
                    }
                    Decor.dress(out, by, decor, Math.max(0, Math.min(100, plan.decorDensity)) / 100.0, plan.decorSeed,
                            layer, entry.getValue().keySet(), openings, made, templates, occupied);
                }
            }
        }

        // 6. connections, completed states, checks
        BlockStr.connect(out);
        out.replaceAll((p, s) -> BlockStr.complete(s));
        for (Building b : made.values()) {
            for (Building.Door d : b.doors) {
                BlockPos step = b.doorStep(d);
                for (BlockPos q : new BlockPos[]{step, step.above()}) {
                    String s = out.get(q);
                    if (s != null && !BlockStr.base(s).contains("rail")) {
                        notes.add("building " + b.name() + ": door blocked at " + q.toShortString() + " by " + s);
                    }
                }
            }
            List<Building.Unit> lost = b.unreachable();
            if (!lost.isEmpty()) {
                notes.add("building " + b.name() + ": " + lost.size() + " unit(s) not reachable from a door");
            }
        }
        for (Map.Entry<String, Set<String>> entry : missing.entrySet()) {
            BlockChoice style = plan.styles().get(entry.getKey());
            List<String> ordered = new ArrayList<>();
            for (Role role : Roles.of(style == null ? Roles.BUILDING : style.kind())) {
                if (entry.getValue().contains(role.name())) {
                    ordered.add(role.name());
                }
            }
            entry.setValue(new LinkedHashSet<>(ordered));
            problems.add("style '" + entry.getKey() + "' has no block for " + String.join(", ", ordered)
                    + " (choose them in the block chooser)");
        }
        return new Result(out, problems, notes, reached, missing);
    }

    /**
     * The door steps no outward piece may land on, mapped to who may still put one there: {@code "*"}
     * for nobody (the walking column of every door, and the whole 3-wide approach of a standing door),
     * else the name of the door's own building (the sides of its 3-wide approach, so its own door
     * surround stays while a neighbour's sill or balcony gives way).
     */
    static Map<BlockPos, String> doorZones(Map<String, Building> made, Surroundings around) {
        Map<BlockPos, String> zone = new HashMap<>();
        for (Map.Entry<String, Building> entry : made.entrySet()) {
            Building b = entry.getValue();
            for (Building.Door d : b.doors) {
                for (int a = 3; a <= 5; a++) {
                    for (int y = 1; y <= 2; y++) {
                        BlockPos p = b.out(d.u(), d.side(), a, y, 1);
                        if (a == 4) {
                            zone.put(p, "*");
                        } else {
                            zone.merge(p, entry.getKey(), (was, now) -> was.equals(now) ? was : "*");
                        }
                    }
                }
            }
        }
        int[][] sides = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}}; // N E S W, the door bit order
        for (Map.Entry<Cell, Integer> entry : around.doors().entrySet()) {
            Cell cell = entry.getKey();
            for (int s = 0; s < 4; s++) {
                if ((entry.getValue() & (1 << s)) == 0) {
                    continue;
                }
                int dx = sides[s][0], dz = sides[s][1];
                for (int a = 3; a <= 5; a++) {
                    int x = cell.x() * 8 + (dx == 0 ? a : dx > 0 ? 9 : -1);
                    int z = cell.z() * 8 + (dz == 0 ? a : dz > 0 ? 9 : -1);
                    for (int y = 1; y <= 2; y++) {
                        zone.put(new BlockPos(x, cell.layer() * 8 + y, z), "*");
                    }
                }
            }
        }
        return zone;
    }

    private static boolean blocksDoor(Map<BlockPos, String> zone, BlockPos p, String ch) {
        String owner = zone.get(p);
        return owner != null && (owner.equals("*") || !owner.equals(ch));
    }

    /** Whether {@code p} lies in the 9x9x9 box (shared planes included) of any of {@code cells}. */
    static boolean inside(Set<Cell> cells, BlockPos p) {
        if (cells.isEmpty()) {
            return false;
        }
        int x = p.getX(), y = p.getY(), z = p.getZ();
        for (int cx = Math.floorDiv(x - 1, 8); cx <= Math.floorDiv(x, 8); cx++) {
            for (int cy = Math.floorDiv(y - 1, 8); cy <= Math.floorDiv(y, 8); cy++) {
                for (int cz = Math.floorDiv(z - 1, 8); cz <= Math.floorDiv(z, 8); cz++) {
                    if (cells.contains(new Cell(cx, cy, cz))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static int otherTop(Map<String, Map<Long, Integer>> tops, String ch, long col) {
        int top = Integer.MIN_VALUE;
        for (Map.Entry<String, Map<Long, Integer>> entry : tops.entrySet()) {
            if (!entry.getKey().equals(ch)) {
                top = Math.max(top, entry.getValue().getOrDefault(col, Integer.MIN_VALUE));
            }
        }
        return top;
    }

    /** Below head height over a road column: from a storey under the road plane up to 3 above it. */
    private static boolean underRoad(Map<Long, Set<Integer>> roadFloor, long col, int y) {
        Set<Integer> floors = roadFloor.get(col);
        if (floors == null) {
            return false;
        }
        for (int ry : floors) {
            if (y - ry < 4 && y - ry >= -8) {
                return true;
            }
        }
        return false;
    }

    private static boolean yields(BlockPos p, Building b, String ch, Set<Long> others,
                                  Map<String, Map<Long, Integer>> tops, Map<Long, Set<Integer>> roadFloor) {
        long col = Building.key(p.getX(), p.getZ());
        if (others.contains(col) && p.getY() <= otherTop(tops, ch, col)) {
            return true;
        }
        return underRoad(roadFloor, col, p.getY()) && !(b.thick.contains(col) && b.pier.containsKey(p));
    }
}
