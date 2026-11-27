package com.qccore.core.plan;

import com.qccore.core.UnitSide;

/**
 * Where a placing tool (building, template) lands when it is aimed near other units and roads (T29,
 * the user's picks of review #52: rules 1, 3, 4; rule 5, Shift for exactly the aimed cell, is the
 * caller's: it skips this class). Registry-free, so JUnit covers it.
 *
 * <ol>
 *   <li><b>Beside a unit</b> ({@link #beside}): the cell across the aimed face, unless the crosshair is
 *       near one end of that face and the cell round that corner fits better: it touches more other
 *       units, or fronts a road where the straight cell doesn't. Ties keep the straight cell, so the
 *       cursor only moves when the surroundings give a reason.</li>
 *   <li><b>On the ground</b> ({@link #groundLayer}): aiming down at terrain, the layer of the nearest
 *       unit within {@link #GROUND_REACH} cells, if it is within one layer of the floor-plane rule's
 *       ({@code Alignment.nearestLayer}), so a new unit lines up with its neighbours on uneven
 *       ground.</li>
 *   <li><b>Doors</b>: a cell in front of a door is never picked over one that isn't.</li>
 * </ol>
 */
public final class SmartCursor {
    /** Blocks from either end of a face (0..8) within which the cell round that corner is considered. */
    public static final double CORNER = 2.5;
    /** Cells (Chebyshev, same or neighbouring layer) searched for units when aiming at the ground. */
    public static final int GROUND_REACH = 2;

    /** What stands or is planned round the cursor. */
    public interface Around {
        /** A planned or standing building unit fills the cell. */
        boolean unit(Cell cell);

        /** A planned or standing road fills the cell. */
        boolean road(Cell cell);

        /** A unit in the cell would stand in front of a door (that the tool would refuse). */
        boolean doorFront(Cell cell);
    }

    private SmartCursor() {
    }

    /**
     * The unit cell a block face pointing {@code side} belongs to, for a block at {@code bx, bz} blocks from
     * the anchor on {@code layer}. Usually the block's own cell; but a unit's east and south walls stand on
     * the plane it shares with the next cell (its local 8 = the neighbour's local 0), so an east or south
     * face of a block on that plane belongs to the cell west or north of the block's.
     */
    public static Cell aimedUnit(int bx, int bz, int layer, UnitSide side) {
        int x = Math.floorDiv(bx, 8);
        int z = Math.floorDiv(bz, 8);
        if (side == UnitSide.EAST && Math.floorMod(bx, 8) == 0) {
            x--;
        } else if (side == UnitSide.SOUTH && Math.floorMod(bz, 8) == 0) {
            z--;
        }
        return new Cell(x, layer, z);
    }

    /**
     * The cell a placing tool takes when aimed at {@code side} of the unit in {@code aimed}.
     * {@code along} is where the crosshair meets that face, 0..8 blocks along it from its west end
     * (north and south faces) or its north end (east and west faces).
     */
    public static Cell beside(Cell aimed, UnitSide side, double along, Around around) {
        Cell straight = step(aimed, side);
        UnitSide corner = null;
        if (along < CORNER) {
            corner = side == UnitSide.NORTH || side == UnitSide.SOUTH ? UnitSide.WEST : UnitSide.NORTH;
        } else if (along > 8 - CORNER) {
            corner = side == UnitSide.NORTH || side == UnitSide.SOUTH ? UnitSide.EAST : UnitSide.SOUTH;
        }
        if (corner == null) {
            return straight;
        }
        Cell round = step(aimed, corner);
        // never move onto a unit or a road (the building tool would pave over it), or in front of a door
        if (around.unit(round) || around.road(round) || around.doorFront(round)) {
            return straight;
        }
        if (around.unit(straight) || around.doorFront(straight)) {
            return round;
        }
        return score(round, aimed, around) > score(straight, aimed, around) ? round : straight;
    }

    /**
     * The layer a placing tool takes when aimed down at the ground in {@code cell} (the layer the
     * floor-plane rule gave): the layer of the nearest unit round it, when that is within one layer.
     */
    public static int groundLayer(Cell cell, Around around) {
        // nearest ring first; within a ring the floor-plane rule's own layer wins, then the one below
        for (int reach = 1; reach <= GROUND_REACH; reach++) {
            for (int dLayer : new int[]{0, -1, 1}) {
                for (int dx = -reach; dx <= reach; dx++) {
                    for (int dz = -reach; dz <= reach; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) == reach
                                && around.unit(new Cell(cell.x() + dx, cell.layer() + dLayer, cell.z() + dz))) {
                            return cell.layer() + dLayer;
                        }
                    }
                }
            }
        }
        return cell.layer();
    }

    /** Other units the cell would touch (the aimed one left out), plus one if it fronts a road. */
    static int score(Cell cell, Cell aimed, Around around) {
        int units = 0;
        boolean road = false;
        for (UnitSide side : UnitSide.values()) {
            Cell next = step(cell, side);
            if (!next.equals(aimed) && around.unit(next)) {
                units++;
            }
            road |= around.road(next);
        }
        return 2 * units + (road ? 1 : 0);
    }

    static Cell step(Cell cell, UnitSide side) {
        return switch (side) {
            case NORTH -> new Cell(cell.x(), cell.layer(), cell.z() - 1);
            case EAST -> new Cell(cell.x() + 1, cell.layer(), cell.z());
            case SOUTH -> new Cell(cell.x(), cell.layer(), cell.z() + 1);
            case WEST -> new Cell(cell.x() - 1, cell.layer(), cell.z());
        };
    }
}
