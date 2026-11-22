package com.qccore.core.plan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A new road changes the standing road cells it meets so the network looks built at once (user rule,
 * 2026-11-21: new parts may change what stands so both look built together). A standing road cell
 * next to a planned road cell gets a new arm, so its own layout changes too: the closed edge it had
 * on that side (lane-1 paving and furniture, e.g. a hedge row) now runs across the junction, its
 * lanes are measured again and a bend or tee gains its medallion. Only those touching cells change;
 * a cell further away keeps its arms, and its lanes are at most 3 blocks deep, so its paving is the
 * same either way.
 *
 * <p>{@link #join} adds every touching standing road cell to the plan as a road cell, so the
 * compiler rebuilds it with the new one (paving, furniture, medallion, and the road headroom clear
 * that takes the old furniture away). A standing record does not say which road style it was laid
 * with, so a rebuilt cell takes the style of the planned road cell it touches (the first in x, z
 * order when several touch it). Registry-free.
 */
public final class RoadJoin {

    private RoadJoin() {
    }

    /** The plan with the standing road cells next to its road cells added; {@code plan} itself when none. */
    public static QcPlan join(QcPlan plan, Surroundings around) {
        Map<Cell, String> touching = touching(plan, around);
        if (touching.isEmpty()) {
            return plan;
        }
        QcPlan out = plan.copy();
        out.roads().putAll(touching);
        return out;
    }

    /** Standing road cell -> the road style it is rebuilt with. */
    public static Map<Cell, String> touching(QcPlan plan, Surroundings around) {
        Map<Cell, String> out = new LinkedHashMap<>();
        if (plan.roads().isEmpty() || around.roads().isEmpty()) {
            return out;
        }
        List<Cell> planned = new ArrayList<>(plan.roads().keySet());
        planned.sort(Comparator.comparingInt(Cell::layer).thenComparingInt(Cell::x).thenComparingInt(Cell::z));
        Set<Cell> standing = new LinkedHashSet<>(around.standingRoads(plan));
        for (Cell cell : planned) {
            for (int[] d : new int[][]{{0, -1}, {1, 0}, {0, 1}, {-1, 0}}) {
                Cell next = cell.offset(d[0], 0, d[1]);
                if (standing.contains(next)) {
                    out.putIfAbsent(next, plan.roads().get(cell));
                }
            }
        }
        return out;
    }
}
