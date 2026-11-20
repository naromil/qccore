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

        // 4. merge: outward decoration yields to neighbours and to the road below head height
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
                    if (b.blocks.containsKey(q) && b.outward.contains(q) && yields(q, b, ch, others, tops, roadFloor)) {
                        doomed.addAll(group);
                        break;
                    }
                }
            }
            for (Map.Entry<BlockPos, String> block : b.blocks.entrySet()) {
                BlockPos p = block.getKey();
                String s = block.getValue();
                long col = Building.key(p.getX(), p.getZ());
                if (doomed.contains(p)) {
                    continue;
                }
                if (b.outward.contains(p)) {
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
                    Set<Long> openings = new HashSet<>();
                    for (Building b : made.values()) {
                        if (b.groundLayer == layer) {
                            openings.addAll(b.openings);
                        }
                    }
                    Set<UnitPos> occupied = new HashSet<>();
                    if (plan.units().peekLayer(layer) != null) {
                        occupied.addAll(plan.units().peekLayer(layer).keySet());
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
