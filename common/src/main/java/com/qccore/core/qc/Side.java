package com.qccore.core.qc;

import com.qccore.core.UnitSide;

/**
 * The four horizontal sides as qckit names them ({@code N E S W}), with the offsets and the
 * vocabulary of {@code blocks.py}: north is {@code -z}, east {@code +x}.
 */
public enum Side {
    N(0, -1, "north"),
    E(1, 0, "east"),
    S(0, 1, "south"),
    W(-1, 0, "west");

    /** qckit's {@code SIDES} order, the order every loop over sides uses. */
    public static final Side[] ALL = {N, E, S, W};

    public final int dx;
    public final int dz;
    public final String facing;

    Side(int dx, int dz, String facing) {
        this.dx = dx;
        this.dz = dz;
        this.facing = facing;
    }

    public Side opp() {
        return switch (this) {
            case N -> S;
            case S -> N;
            case E -> W;
            case W -> E;
        };
    }

    /** qckit's {@code CCW}: a stair's left side when it faces this way. */
    public Side ccw() {
        return switch (this) {
            case N -> W;
            case W -> S;
            case S -> E;
            case E -> N;
        };
    }

    /** {@code facades.ALONG}: the compass side of {@code +a} on a face of this side. */
    public Side along() {
        return this == N || this == S ? E : S;
    }

    public UnitSide unitSide() {
        return switch (this) {
            case N -> UnitSide.NORTH;
            case E -> UnitSide.EAST;
            case S -> UnitSide.SOUTH;
            case W -> UnitSide.WEST;
        };
    }

    public static Side of(UnitSide side) {
        return switch (side) {
            case NORTH -> N;
            case EAST -> E;
            case SOUTH -> S;
            case WEST -> W;
        };
    }

    /** The side whose offset is {@code (dx, dz)}, or {@code null}. */
    public static Side of(int dx, int dz) {
        for (Side side : ALL) {
            if (side.dx == dx && side.dz == dz) {
                return side;
            }
        }
        return null;
    }
}
