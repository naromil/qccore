package com.qccore.core.plan;

import com.qccore.core.QcUnit;
import com.qccore.core.UnitLayers;
import com.qccore.core.UnitPos;
import com.qccore.core.UnitSide;
import com.qccore.core.qc.BlockChoice;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * A plan: the modifications a player queues up before deciding to apply them to the world or to
 * export them as a structure {@code .nbt}.
 *
 * <ul>
 *   <li>{@link #units()}: the QC units to build, with their partition flags (the layer model): on a
 *       shared side, wall = a party wall, wall + gate = a gate, gate alone = open; no flag leaves the
 *       side to the building's design;</li>
 *   <li>{@link #unitBuildings()} and {@link #buildings()}: which building every unit belongs to, and
 *       each building's template, style name and design overrides (qckit's {@code Plan.building});</li>
 *   <li>{@link #styles()}: the blocks chosen per style, role by role ({@link BlockChoice}), shared by
 *       buildings and roads; a template keeps the style names but strips the blocks;</li>
 *   <li>{@link #masks()}: legacy only - per unit of a {@code qcsimple} building, which of QCSimple's
 *       nine components it builds (absent = all);</li>
 *   <li>{@link #clears()}: unit cells whose terrain is cleared to air (local y 1..8, plus
 *       {@link #clearMargin} blocks outside every exposed side and above), optionally levelled by
 *       filling the air of the floor plane with {@link #ground};</li>
 *   <li>{@link #roads()}: QC road cells (qckit's 7-wide road), by layer, each naming a road style
 *       from {@link #styles()}.</li>
 * </ul>
 *
 * <p>Registry-free, so it is covered by plain JUnit; {@link PlanCompiler} turns it into blocks.
 */
public final class QcPlan {

    public static final int MAX_CLEAR_MARGIN = 4;

    private UnitLayers units = new UnitLayers();
    private final Map<Cell, Integer> masks = new LinkedHashMap<>();
    private final Set<Cell> clears = new LinkedHashSet<>();
    private final Map<Cell, String> roads = new LinkedHashMap<>();
    private final Map<Cell, String> unitBuildings = new LinkedHashMap<>();
    private final Map<String, BuildingSpec> buildings = new LinkedHashMap<>();
    private final Map<String, BlockChoice> styles = new LinkedHashMap<>();
    /** Extra blocks cleared outside the exposed sides and above every cleared cell, 0..4. */
    public int clearMargin = 2;
    /** The decor style (kind decor) the roads are dressed with; blank = no decor. */
    public String decorStyle = "";
    /** How much decor, 0..100 (qckit's density 0..1). */
    public int decorDensity = 60;
    /** The decor seed: the same plan and seed dress the same streets. */
    public int decorSeed;
    /** Block id filled into the air of a cleared cell's floor plane; blank = no levelling. */
    public String ground = "";

    public UnitLayers units() {
        return units;
    }

    public void setUnits(UnitLayers units) {
        this.units = units;
        masks.keySet().removeIf(cell -> !hasUnit(cell));
        unitBuildings.keySet().removeIf(cell -> !hasUnit(cell));
    }

    public Map<Cell, Integer> masks() {
        return masks;
    }

    public Set<Cell> clears() {
        return clears;
    }

    public Map<Cell, String> roads() {
        return roads;
    }

    /** Unit cell -> the key of the building it belongs to. */
    public Map<Cell, String> unitBuildings() {
        return unitBuildings;
    }

    /** Building key -> its template, style and design overrides. */
    public Map<String, BuildingSpec> buildings() {
        return buildings;
    }

    /** Style name -> the blocks chosen for it (buildings and roads). */
    public Map<String, BlockChoice> styles() {
        return styles;
    }

    /** The building a unit belongs to, or {@code null}. */
    public String building(Cell cell) {
        return unitBuildings.get(cell);
    }

    /** The first free building key: A..Z, then AA, AB, ... */
    public String nextBuildingKey() {
        for (int i = 0; ; i++) {
            String key = keyName(i);
            if (!buildings.containsKey(key)) {
                return key;
            }
        }
    }

    private static String keyName(int i) {
        StringBuilder name = new StringBuilder();
        int n = i;
        do {
            name.insert(0, (char) ('A' + n % 26));
            n = n / 26 - 1;
        } while (n >= 0);
        return name.toString();
    }

    /** Drops buildings no unit belongs to. */
    public void pruneBuildings() {
        buildings.keySet().removeIf(key -> !unitBuildings.containsValue(key));
    }

    public boolean isEmpty() {
        return units.unitCount() == 0 && clears.isEmpty() && roads.isEmpty();
    }

    public boolean hasUnit(Cell cell) {
        return units.contains(cell.x(), cell.layer(), cell.z());
    }

    public int mask(Cell cell) {
        Integer mask = masks.get(cell);
        return mask == null ? ComponentMask.ALL : mask;
    }

    public void setMask(Cell cell, int mask) {
        if ((mask & ComponentMask.ALL) == ComponentMask.ALL) {
            masks.remove(cell);
        } else {
            masks.put(cell, mask & ComponentMask.ALL);
        }
    }

    /** Adds a unit (keeping an existing one and its flags) to a building. */
    public void addUnit(Cell cell, String building) {
        units.layer(cell.layer()).putIfAbsent(cell.pos(), new QcUnit());
        unitBuildings.put(cell, building);
    }

    /** Adds a legacy QCSimple unit with the given component mask. */
    public void addUnit(Cell cell, int mask) {
        units.layer(cell.layer()).putIfAbsent(cell.pos(), new QcUnit());
        setMask(cell, mask);
    }

    /** Removes a unit and the flags its neighbours pointed at it, like the editor's erase. */
    public void removeUnit(Cell cell) {
        Map<UnitPos, QcUnit> layer = units.peekLayer(cell.layer());
        if (layer == null) {
            return;
        }
        for (UnitSide side : UnitSide.values()) {
            QcUnit neighbour = layer.get(Flags.neighbour(cell.pos(), side));
            if (neighbour != null) {
                Flags.setWall(neighbour, Flags.opposite(side), false);
                Flags.setGate(neighbour, Flags.opposite(side), false);
            }
        }
        layer.remove(cell.pos());
        masks.remove(cell);
        unitBuildings.remove(cell);
        if (layer.isEmpty()) {
            dropEmptyLayer(cell.layer());
        }
    }

    /**
     * Sets the partition on every shared side between two units of {@code cells}: {@code wall}
     * without a gate, {@code gate} (a wall with a passage), {@code open} (no wall at all, stored as
     * the gate flag alone) or {@code design} (no flag: the building's partition decides). Returns how
     * many shared sides changed.
     */
    public int partition(Set<Cell> cells, Partition partition) {
        int changed = 0;
        for (Cell cell : cells) {
            Map<UnitPos, QcUnit> layer = units.peekLayer(cell.layer());
            QcUnit unit = layer == null ? null : layer.get(cell.pos());
            if (unit == null) {
                continue;
            }
            for (UnitSide side : new UnitSide[]{UnitSide.EAST, UnitSide.SOUTH}) {
                UnitPos other = Flags.neighbour(cell.pos(), side);
                QcUnit neighbour = layer.get(other);
                if (neighbour == null || !cells.contains(Cell.of(other, cell.layer()))) {
                    continue;
                }
                boolean wall = partition == Partition.WALL || partition == Partition.GATE;
                boolean gate = partition == Partition.GATE || partition == Partition.OPEN;
                if (Flags.hasWall(unit, side) != wall || Flags.isGate(unit, side) != gate
                        || Flags.hasWall(neighbour, Flags.opposite(side)) != wall
                        || Flags.isGate(neighbour, Flags.opposite(side)) != gate) {
                    changed++;
                }
                Flags.setWall(unit, side, wall);
                Flags.setGate(unit, side, gate);
                Flags.setWall(neighbour, Flags.opposite(side), wall);
                Flags.setGate(neighbour, Flags.opposite(side), gate);
            }
        }
        return changed;
    }

    /**
     * The partition override of a shared side: {@code wall}, {@code gate}, {@code open}, or
     * {@code null} when the side follows the building's design.
     */
    public static String partyOverride(QcUnit unit, UnitSide side) {
        boolean wall = Flags.hasWall(unit, side);
        boolean gate = Flags.isGate(unit, side);
        return wall ? (gate ? "gate" : "wall") : gate ? "open" : null;
    }

    /** A deep copy (units, flags, buildings, masks, clears, roads and styles). */
    public QcPlan copy() {
        QcPlan copy = new QcPlan();
        copy.units = units.copy();
        copy.masks.putAll(masks);
        copy.unitBuildings.putAll(unitBuildings);
        buildings.forEach((k, v) -> copy.buildings.put(k, v.copy()));
        copy.clears.addAll(clears);
        copy.roads.putAll(roads);
        styles.forEach((k, v) -> copy.styles.put(k, v.copy()));
        copy.decorStyle = decorStyle;
        copy.decorDensity = decorDensity;
        copy.decorSeed = decorSeed;
        copy.clearMargin = clearMargin;
        copy.ground = ground;
        return copy;
    }

    /**
     * An empty plan with this plan's settings: styles, decor, clear margin and ground. What
     * "Clear plan" and a successful apply leave behind.
     */
    public QcPlan emptied() {
        QcPlan out = new QcPlan();
        styles.forEach((k, v) -> out.styles.put(k, v.copy()));
        out.decorStyle = decorStyle;
        out.decorDensity = decorDensity;
        out.decorSeed = decorSeed;
        out.clearMargin = clearMargin;
        out.ground = ground;
        return out;
    }

    /** Every cell the plan touches (units, clears and roads). */
    public Set<Cell> cells() {
        Set<Cell> cells = new LinkedHashSet<>();
        for (int layer : units.layerKeys()) {
            for (UnitPos pos : units.peekLayer(layer).keySet()) {
                cells.add(Cell.of(pos, layer));
            }
        }
        cells.addAll(clears);
        cells.addAll(roads.keySet());
        return cells;
    }

    /**
     * The plan moved so that its minimum cell is {@code (0, 1, 0)}: the normal form of a template.
     * An empty plan comes back as a plain copy.
     */
    public QcPlan normalized() {
        Set<Cell> cells = cells();
        if (cells.isEmpty()) {
            return copy();
        }
        int minX = Integer.MAX_VALUE;
        int minLayer = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        for (Cell cell : cells) {
            minX = Math.min(minX, cell.x());
            minLayer = Math.min(minLayer, cell.layer());
            minZ = Math.min(minZ, cell.z());
        }
        return transformed(0, false, false, -minX, 1 - minLayer, -minZ);
    }

    /**
     * A transformed copy: first {@code quarterTurns} clockwise turns about the cell origin (seen from
     * above, north -> east), then the mirrors ({@code mirrorX}: x -> -x, east <-> west;
     * {@code mirrorZ}: z -> -z, north <-> south), then the offset. Wall and gate flags (and a fixed {@code roof_axis}) turn with
     * their units, so a template keeps its partitions in any orientation.
     */
    public QcPlan transformed(int quarterTurns, boolean mirrorX, boolean mirrorZ, int dx, int dLayer, int dz) {
        CellTransform transform = new CellTransform(Math.floorMod(quarterTurns, 4), mirrorX, mirrorZ, dx, dLayer, dz);
        QcPlan out = new QcPlan();
        for (int layer : units.layerKeys()) {
            for (Map.Entry<UnitPos, QcUnit> entry : units.peekLayer(layer).entrySet()) {
                Cell moved = transform.apply(Cell.of(entry.getKey(), layer));
                QcUnit unit = new QcUnit();
                for (UnitSide side : UnitSide.values()) {
                    UnitSide target = transform.apply(side);
                    Flags.setWall(unit, target, Flags.hasWall(entry.getValue(), side));
                    Flags.setGate(unit, target, Flags.isGate(entry.getValue(), side));
                }
                out.units.layer(moved.layer()).put(moved.pos(), unit);
            }
        }
        for (Map.Entry<Cell, Integer> entry : masks.entrySet()) {
            out.masks.put(transform.apply(entry.getKey()), entry.getValue());
        }
        for (Map.Entry<Cell, String> entry : unitBuildings.entrySet()) {
            out.unitBuildings.put(transform.apply(entry.getKey()), entry.getValue());
        }
        buildings.forEach((k, v) -> {
            BuildingSpec spec = v.copy();
            // a fixed ridge axis turns with the building; mirrors keep it
            if (transform.turns() % 2 == 1) {
                spec.options.computeIfPresent("roof_axis", (key, axis) -> switch (axis.trim()) {
                    case "x" -> "z";
                    case "z" -> "x";
                    default -> axis;
                });
            }
            out.buildings.put(k, spec);
        });
        for (Cell cell : clears) {
            out.clears.add(transform.apply(cell));
        }
        for (Map.Entry<Cell, String> entry : roads.entrySet()) {
            out.roads.put(transform.apply(entry.getKey()), entry.getValue());
        }
        styles.forEach((k, v) -> out.styles.put(k, v.copy()));
        out.decorStyle = decorStyle;
        out.decorDensity = decorDensity;
        out.decorSeed = decorSeed;
        out.clearMargin = clearMargin;
        out.ground = ground;
        return out;
    }

    /**
     * Merges another plan into this one: its units overwrite units and roads in the same cell (with
     * their flags, masks and building tags), its roads overwrite units and roads in the same cell,
     * its clears are added; its buildings and styles are added unless one of that name exists (so
     * blocks already chosen win over a template's empty slots). A cell is a unit or a road, never
     * both. Units placed next to existing ones keep the existing partitions.
     */
    public void merge(QcPlan other) {
        for (int layer : other.units.layerKeys()) {
            for (Map.Entry<UnitPos, QcUnit> entry : other.units.peekLayer(layer).entrySet()) {
                Cell cell = Cell.of(entry.getKey(), layer);
                units.layer(layer).put(entry.getKey(), new QcUnit(entry.getValue()));
                setMask(cell, other.mask(cell));
                String building = other.unitBuildings.get(cell);
                if (building == null) {
                    unitBuildings.remove(cell);
                } else {
                    unitBuildings.put(cell, building);
                }
                roads.remove(cell);
            }
        }
        other.buildings.forEach((k, v) -> buildings.putIfAbsent(k, v.copy()));
        clears.addAll(other.clears);
        for (Map.Entry<Cell, String> entry : other.roads.entrySet()) {
            if (hasUnit(entry.getKey()) && !other.hasUnit(entry.getKey())) {
                removeUnit(entry.getKey());
            }
            roads.put(entry.getKey(), entry.getValue());
        }
        other.styles.forEach((k, v) -> styles.putIfAbsent(k, v.copy()));
        if ((decorStyle == null || decorStyle.isBlank()) && other.decorStyle != null && !other.decorStyle.isBlank()) {
            decorStyle = other.decorStyle;
            decorDensity = other.decorDensity;
            decorSeed = other.decorSeed;
        }
    }

    /**
     * A copy whose buildings are renamed to keys free in {@code target}, so stamping a template
     * twice makes two buildings, not one building in two places.
     */
    public QcPlan withFreshBuildings(QcPlan target) {
        QcPlan out = copy();
        out.buildings.clear();
        out.unitBuildings.clear();
        QcPlan taken = new QcPlan();
        taken.buildings.putAll(target.buildings);
        Map<String, String> renamed = new HashMap<>();
        for (Map.Entry<String, BuildingSpec> entry : buildings.entrySet()) {
            String key = taken.nextBuildingKey();
            taken.buildings.put(key, entry.getValue());
            renamed.put(entry.getKey(), key);
            out.buildings.put(key, entry.getValue().copy());
        }
        for (Map.Entry<Cell, String> entry : unitBuildings.entrySet()) {
            out.unitBuildings.put(entry.getKey(), renamed.getOrDefault(entry.getValue(), entry.getValue()));
        }
        return out;
    }

    /** A copy with every style's blocks removed: the form a template is saved in. */
    public QcPlan stripped() {
        QcPlan out = copy();
        out.styles.replaceAll((k, v) -> v.stripped());
        out.ground = "";
        return out;
    }

    /** Road cells grouped by layer, in insertion order. */
    public Map<Integer, Map<UnitPos, String>> roadLayers() {
        Map<Integer, Map<UnitPos, String>> out = new HashMap<>();
        for (Map.Entry<Cell, String> entry : roads.entrySet()) {
            out.computeIfAbsent(entry.getKey().layer(), k -> new LinkedHashMap<>())
                    .put(entry.getKey().pos(), entry.getValue());
        }
        return out;
    }

    /** One-line summary for the HUD and the tools screen. */
    public String summary() {
        return units.unitCount() + " unit(s) in " + buildings.size() + " building(s), " + roads.size() + " road cell(s), "
                + clears.size() + " cleared cell(s)";
    }

    private void dropEmptyLayer(int layer) {
        UnitLayers rebuilt = new UnitLayers();
        for (int key : units.layerKeys()) {
            Map<UnitPos, QcUnit> map = units.peekLayer(key);
            if (key != layer || !map.isEmpty()) {
                rebuilt.setLayer(key, map);
            }
        }
        units = rebuilt;
    }

    /** How {@link #partition} sets a shared side. */
    public enum Partition {
        WALL, GATE, OPEN, DESIGN
    }

    /** Rotation/mirror/offset of one cell, and of the side a flag points at. */
    record CellTransform(int turns, boolean mirrorX, boolean mirrorZ, int dx, int dLayer, int dz) {

        Cell apply(Cell cell) {
            int x = cell.x();
            int z = cell.z();
            for (int i = 0; i < turns; i++) {
                int nx = -z;
                z = x;
                x = nx;
            }
            if (mirrorX) {
                x = -x;
            }
            if (mirrorZ) {
                z = -z;
            }
            return new Cell(x + dx, cell.layer() + dLayer, z + dz);
        }

        UnitSide apply(UnitSide side) {
            UnitSide out = side;
            for (int i = 0; i < turns; i++) {
                out = switch (out) {
                    case NORTH -> UnitSide.EAST;
                    case EAST -> UnitSide.SOUTH;
                    case SOUTH -> UnitSide.WEST;
                    case WEST -> UnitSide.NORTH;
                };
            }
            if (mirrorX && (out == UnitSide.EAST || out == UnitSide.WEST)) {
                out = Flags.opposite(out);
            }
            if (mirrorZ && (out == UnitSide.NORTH || out == UnitSide.SOUTH)) {
                out = Flags.opposite(out);
            }
            return out;
        }
    }
}
