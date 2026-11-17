package com.qccore.core.plan;

import com.qccore.core.QcUnit;
import com.qccore.core.UnitLayers;
import com.qccore.core.UnitPos;
import com.qccore.core.UnitSide;

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
 *   <li>{@link #units()}: the QC units to build, with their wall/gate flags (the layer model);</li>
 *   <li>{@link #masks()}: per unit, which of the nine components it builds (absent = all), so a
 *       unit can be planned as "framework only" or "everything but the outer walls";</li>
 *   <li>{@link #clears()}: unit cells whose terrain is cleared to air (local y 1..8, plus
 *       {@link #clearMargin} blocks outside every exposed side and above), optionally levelled by
 *       filling the air of the floor plane with {@link #ground};</li>
 *   <li>{@link #roads()}: QC road cells (qckit's 7-wide road), by layer, each naming a road style
 *       from {@link #roadStyles()}.</li>
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
    private final Map<String, RoadStyle> roadStyles = new LinkedHashMap<>();
    /** Extra blocks cleared outside the exposed sides and above every cleared cell, 0..4. */
    public int clearMargin = 2;
    /** Block id filled into the air of a cleared cell's floor plane; blank = no levelling. */
    public String ground = "";

    public UnitLayers units() {
        return units;
    }

    public void setUnits(UnitLayers units) {
        this.units = units;
        masks.keySet().removeIf(cell -> !hasUnit(cell));
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

    public Map<String, RoadStyle> roadStyles() {
        return roadStyles;
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

    /** Adds a unit (keeping an existing one and its flags) with the given component mask. */
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
        if (layer.isEmpty()) {
            dropEmptyLayer(cell.layer());
        }
    }

    /**
     * Sets the partition on every shared side between two units of {@code cells}: {@code wall}
     * without a gate, {@code gate} (a wall with a passage), or {@code open} (no wall at all).
     * Returns how many shared sides changed.
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
                boolean wall = partition != Partition.OPEN;
                boolean gate = partition == Partition.GATE;
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

    /** A deep copy (units, flags, masks, clears, roads and styles). */
    public QcPlan copy() {
        QcPlan copy = new QcPlan();
        copy.units = units.copy();
        copy.masks.putAll(masks);
        copy.clears.addAll(clears);
        copy.roads.putAll(roads);
        for (Map.Entry<String, RoadStyle> entry : roadStyles.entrySet()) {
            copy.roadStyles.put(entry.getKey(), entry.getValue().copy());
        }
        copy.clearMargin = clearMargin;
        copy.ground = ground;
        return copy;
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
     * {@code mirrorZ}: z -> -z, north <-> south), then the offset. Wall and gate flags turn with
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
        for (Cell cell : clears) {
            out.clears.add(transform.apply(cell));
        }
        for (Map.Entry<Cell, String> entry : roads.entrySet()) {
            out.roads.put(transform.apply(entry.getKey()), entry.getValue());
        }
        for (Map.Entry<String, RoadStyle> entry : roadStyles.entrySet()) {
            out.roadStyles.put(entry.getKey(), entry.getValue().copy());
        }
        out.clearMargin = clearMargin;
        out.ground = ground;
        return out;
    }

    /**
     * Merges another plan into this one: its units overwrite units and roads in the same cell (with
     * their flags and masks), its roads overwrite units and roads in the same cell, its clears are
     * added, its road styles are added unless a style of that name exists. A cell is a unit or a road,
     * never both. Units placed next to existing ones keep the existing partitions.
     */
    public void merge(QcPlan other) {
        for (int layer : other.units.layerKeys()) {
            for (Map.Entry<UnitPos, QcUnit> entry : other.units.peekLayer(layer).entrySet()) {
                Cell cell = Cell.of(entry.getKey(), layer);
                units.layer(layer).put(entry.getKey(), new QcUnit(entry.getValue()));
                setMask(cell, other.mask(cell));
                roads.remove(cell);
            }
        }
        clears.addAll(other.clears);
        for (Map.Entry<Cell, String> entry : other.roads.entrySet()) {
            if (hasUnit(entry.getKey()) && !other.hasUnit(entry.getKey())) {
                removeUnit(entry.getKey());
            }
            roads.put(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, RoadStyle> entry : other.roadStyles.entrySet()) {
            roadStyles.putIfAbsent(entry.getKey(), entry.getValue().copy());
        }
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
        return units.unitCount() + " unit(s), " + clears.size() + " cleared cell(s), " + roads.size() + " road cell(s)";
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
        WALL, GATE, OPEN
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
