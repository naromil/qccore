package com.qccore.core.plan;

import com.qccore.core.QcUnit;
import com.qccore.core.nbt.LayersCodec;
import com.qccore.core.qc.BlockChoice;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * New parts of a building join its standing parts. The user's rule (2026-11-21): "newly built parts
 * are allowed to change previously built parts to work with them, because we want them to look like as
 * if they are built at the same time."
 *
 * <p>An apply records, with every building cell, the {@link #signature} of the building's design
 * (template, options, style name and chosen blocks). When a plan building of the same signature touches
 * such a standing cell (a face neighbour, on the same layer or the layer above or below), {@link #join}
 * adds that cell, and every standing cell of the same signature connected to it, to the plan as a unit
 * of the new building. The plan then builds old and new as one building: shared planes, roofs and
 * facades come out as if both were built in one go. A standing building of another design, or one only
 * {@code /qc scan} knows (no signature), stays as it is and the new plan keeps out of it.
 *
 * <p>Registry-free.
 */
public final class Join {

    private static final int[][] FACES = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}, {0, -1, 0}};

    private Join() {
    }

    /**
     * A stable, non-zero number for one plan building's design, or 0 when the plan has no such
     * building. Equal designs give equal numbers across sessions and worlds.
     */
    public static int signature(QcPlan plan, String building) {
        BuildingSpec spec = building == null ? null : plan.buildings().get(building);
        if (spec == null) {
            return 0;
        }
        StringBuilder text = new StringBuilder();
        text.append(spec.template).append('|').append(spec.style).append('|').append(new TreeMap<>(spec.options));
        BlockChoice choice = spec.style == null ? null : plan.styles().get(spec.style);
        if (choice != null) {
            text.append('|').append(choice.kind()).append('|').append(new TreeMap<>(choice.blocks()));
        }
        int hash = text.toString().hashCode();
        return hash == 0 ? 1 : hash;
    }

    /**
     * The plan with every standing part of the same design that its buildings touch added as units of
     * those buildings; the plan itself when nothing joins. The standing part keeps its recorded
     * wall/gate flags. A cell the plan already uses (a unit, road or clear) is never taken.
     */
    public static QcPlan join(QcPlan plan, Surroundings around) {
        if (around.parts().isEmpty() || plan.unitBuildings().isEmpty()) {
            return plan;
        }
        QcPlan out = null;
        Set<Cell> taken = new HashSet<>();
        for (String building : plan.buildings().keySet()) {
            int signature = signature(plan, building);
            Deque<Cell> queue = new ArrayDeque<>();
            for (Map.Entry<Cell, String> entry : plan.unitBuildings().entrySet()) {
                if (building.equals(entry.getValue())) {
                    queue.add(entry.getKey());
                }
            }
            while (!queue.isEmpty()) {
                Cell cell = queue.poll();
                for (int[] f : FACES) {
                    Cell next = cell.offset(f[0], f[1], f[2]);
                    Surroundings.Part part = around.parts().get(next);
                    if (part == null || part.signature() != signature || taken.contains(next)
                            || plan.hasUnit(next) || plan.roads().containsKey(next) || plan.clears().contains(next)) {
                        continue;
                    }
                    if (out == null) {
                        out = plan.copy();
                    }
                    taken.add(next);
                    out.addUnit(next, building);
                    QcUnit unit = out.units().layer(next.layer()).get(next.pos());
                    LayersCodec.apply(unit, part.walls(), part.gates());
                    queue.add(next);
                }
            }
        }
        return out == null ? plan : out;
    }

    /**
     * The standing cells {@code cells} of {@code joined} as a plan of their own: the same buildings,
     * styles and flags, no roads, clears or decoration. Compiled, it gives what the standing part wrote
     * when it was built alone, so a rebuild can take back what no longer belongs (an eave over what is
     * now the new part, a roof end that is now a ridge).
     */
    public static QcPlan alone(QcPlan joined, Set<Cell> cells) {
        QcPlan out = new QcPlan();
        Set<String> used = new HashSet<>();
        for (Cell cell : cells) {
            String building = joined.building(cell);
            out.addUnit(cell, building);
            used.add(building);
            QcUnit from = joined.units().layer(cell.layer()).get(cell.pos());
            QcUnit to = out.units().layer(cell.layer()).get(cell.pos());
            LayersCodec.apply(to, LayersCodec.bits(from, false), LayersCodec.bits(from, true));
        }
        for (String building : used) {
            BuildingSpec spec = joined.buildings().get(building);
            out.buildings().put(building, spec.copy());
            BlockChoice style = spec.style == null ? null : joined.styles().get(spec.style);
            if (style != null) {
                out.styles().put(spec.style, style);
            }
        }
        out.decorDensity = 0;
        return out;
    }

    /** The standing cells {@link #join} added to {@code plan} to give {@code joined}. */
    public static Set<Cell> joined(QcPlan plan, QcPlan joined) {
        Set<Cell> out = new HashSet<>(joined.unitBuildings().keySet());
        out.removeAll(plan.unitBuildings().keySet());
        return out;
    }
}
