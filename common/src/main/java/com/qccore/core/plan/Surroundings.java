package com.qccore.core.plan;

import com.qccore.core.UnitPos;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * What already stands round a plan, in the plan's own cells: the road cells, the building cells and
 * the doors of the buildings {@code /qc scan} (or an earlier apply) recorded on the same lattice. A
 * plan reads it so a new road joins a standing street instead of ending at its edge, so no new unit is
 * planned in front of a standing door, and so a new plan's outward decoration yields to standing
 * buildings and roads and nothing new is written into a standing building's cell. The exception is a
 * standing part of the same building ({@link #parts()}, {@link Join}): the new part may change it, so
 * both look built at the same time.
 *
 * <p>Registry-free. {@code doors} uses the layer wire bits: <b>bit0 = NORTH, bit1 = EAST, bit2 = SOUTH,
 * bit3 = WEST</b>.
 */
public record Surroundings(Set<Cell> roads, Map<Cell, Integer> doors, Set<Cell> buildings, Map<Cell, Part> parts) {

    public static final Surroundings NONE = new Surroundings(Set.of(), Map.of(), Set.of());

    /**
     * A standing building cell an earlier apply recorded: the {@link Join#signature design signature}
     * of the building it was built as, and its wall/gate flags (layer wire bits). A new building of the
     * same design that touches it rebuilds it together with itself (see {@link Join}).
     */
    public record Part(int signature, int walls, int gates) {
    }

    /** (dx, dz) of the four sides in bit order N, E, S, W. */
    private static final int[][] SIDES = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    public Surroundings {
        roads = Collections.unmodifiableSet(new LinkedHashSet<>(roads));
        doors = Collections.unmodifiableMap(new LinkedHashMap<>(doors));
        buildings = Collections.unmodifiableSet(new LinkedHashSet<>(buildings));
        parts = Collections.unmodifiableMap(new LinkedHashMap<>(parts));
    }

    /** Standing roads, doors and building cells, with no recorded designs. */
    public Surroundings(Set<Cell> roads, Map<Cell, Integer> doors, Set<Cell> buildings) {
        this(roads, doors, buildings, Map.of());
    }

    /** Standing roads and doors only, no standing building cells. */
    public Surroundings(Set<Cell> roads, Map<Cell, Integer> doors) {
        this(roads, doors, Set.of());
    }

    public boolean isEmpty() {
        return roads.isEmpty() && doors.isEmpty() && buildings.isEmpty();
    }

    /**
     * The standing building cells the plan does not itself rebuild: a new plan's blocks keep out of
     * them (their shared planes included). A standing part of the same design as a building it touches
     * is not kept out: {@link Join#join} puts it into the plan, so it is rebuilt with the new part.
     */
    public Set<Cell> standingBuildings(QcPlan plan) {
        Set<Cell> out = new LinkedHashSet<>();
        for (Cell cell : buildings) {
            if (!plan.hasUnit(cell) && !plan.roads().containsKey(cell)) {
                out.add(cell);
            }
        }
        return out;
    }

    /** The standing road cells, on any layer, the plan does not itself use. */
    public Set<Cell> standingRoads(QcPlan plan) {
        Set<Cell> out = new LinkedHashSet<>();
        for (Cell cell : roads) {
            if (!plan.roads().containsKey(cell) && !plan.hasUnit(cell)) {
                out.add(cell);
            }
        }
        return out;
    }

    /** The standing road cells of one layer the plan does not itself use (a planned cell always wins). */
    public Set<UnitPos> joinedRoads(QcPlan plan, int layer) {
        Set<UnitPos> out = new LinkedHashSet<>();
        for (Cell cell : roads) {
            if (cell.layer() == layer && !plan.roads().containsKey(cell) && !plan.hasUnit(cell)) {
                out.add(cell.pos());
            }
        }
        return out;
    }

    /** The cells right in front of a standing door, on the door's layer: a unit there would wall it up. */
    public Set<Cell> doorFronts() {
        Set<Cell> out = new LinkedHashSet<>();
        for (Map.Entry<Cell, Integer> entry : doors.entrySet()) {
            for (int s = 0; s < 4; s++) {
                if ((entry.getValue() & (1 << s)) != 0) {
                    out.add(entry.getKey().offset(SIDES[s][0], 0, SIDES[s][1]));
                }
            }
        }
        return out;
    }

    /**
     * The layer-local (x, z) columns of the 3x3 approach outside every standing door of a layer, the
     * same columns a planned building keeps free of road furniture in front of its own doors.
     */
    public Set<Long> doorOpenings(int layer) {
        Set<Long> out = new HashSet<>();
        for (Map.Entry<Cell, Integer> entry : doors.entrySet()) {
            Cell cell = entry.getKey();
            if (cell.layer() != layer) {
                continue;
            }
            int ox = cell.x() * 8;
            int oz = cell.z() * 8;
            for (int s = 0; s < 4; s++) {
                if ((entry.getValue() & (1 << s)) == 0) {
                    continue;
                }
                for (int a = 3; a <= 5; a++) {
                    for (int d = 1; d <= 3; d++) {
                        int x = switch (s) {
                            case 0, 2 -> ox + a;
                            case 1 -> ox + 8 + d;
                            default -> ox - d;
                        };
                        int z = switch (s) {
                            case 1, 3 -> oz + a;
                            case 2 -> oz + 8 + d;
                            default -> oz - d;
                        };
                        out.add(RoadKit.key(x, z));
                    }
                }
            }
        }
        return out;
    }
}
