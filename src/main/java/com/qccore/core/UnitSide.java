package com.qccore.core;

import net.minecraft.util.math.Direction;

/**
 * The four horizontal faces of a QC Unit. North points towards negative Z, east towards
 * positive X, mirroring the placement anchors of the block-based components.
 */
public enum UnitSide {
    NORTH,
    EAST,
    SOUTH,
    WEST;

    /** The side matching a horizontal {@link Direction}, or {@code null} for UP/DOWN. */
    public static UnitSide of(Direction direction) {
        return switch (direction) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            default -> null;
        };
    }

    public Direction toDirection() {
        return switch (this) {
            case NORTH -> Direction.NORTH;
            case EAST -> Direction.EAST;
            case SOUTH -> Direction.SOUTH;
            case WEST -> Direction.WEST;
        };
    }
}
