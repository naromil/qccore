package com.qccore.core.plan;

import com.qccore.core.QcUnit;
import com.qccore.core.UnitPos;
import com.qccore.core.UnitSide;

/** Side-addressed access to the eight wall/gate flags of a {@link QcUnit}. */
public final class Flags {

    private Flags() {
    }

    public static UnitPos neighbour(UnitPos cell, UnitSide side) {
        return switch (side) {
            case NORTH -> new UnitPos(cell.x(), cell.z() - 1);
            case SOUTH -> new UnitPos(cell.x(), cell.z() + 1);
            case EAST -> new UnitPos(cell.x() + 1, cell.z());
            case WEST -> new UnitPos(cell.x() - 1, cell.z());
        };
    }

    public static UnitSide opposite(UnitSide side) {
        return switch (side) {
            case NORTH -> UnitSide.SOUTH;
            case SOUTH -> UnitSide.NORTH;
            case EAST -> UnitSide.WEST;
            case WEST -> UnitSide.EAST;
        };
    }

    public static boolean hasWall(QcUnit unit, UnitSide side) {
        return switch (side) {
            case NORTH -> unit.hasWallN();
            case EAST -> unit.hasWallE();
            case SOUTH -> unit.hasWallS();
            case WEST -> unit.hasWallW();
        };
    }

    public static boolean isGate(QcUnit unit, UnitSide side) {
        return switch (side) {
            case NORTH -> unit.isGateN();
            case EAST -> unit.isGateE();
            case SOUTH -> unit.isGateS();
            case WEST -> unit.isGateW();
        };
    }

    public static void setWall(QcUnit unit, UnitSide side, boolean value) {
        switch (side) {
            case NORTH -> unit.setWallN(value);
            case EAST -> unit.setWallE(value);
            case SOUTH -> unit.setWallS(value);
            case WEST -> unit.setWallW(value);
        }
    }

    public static void setGate(QcUnit unit, UnitSide side, boolean value) {
        switch (side) {
            case NORTH -> unit.setGateN(value);
            case EAST -> unit.setGateE(value);
            case SOUTH -> unit.setGateS(value);
            case WEST -> unit.setGateW(value);
        }
    }
}
