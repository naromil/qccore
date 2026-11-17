package com.qccore.core.plan;

import com.qccore.core.UnitPos;

/** A unit cell of the session lattice: grid {@code x}/{@code z} and the editor {@code layer}. */
public record Cell(int x, int layer, int z) {

    public static Cell of(UnitPos pos, int layer) {
        return new Cell(pos.x(), layer, pos.z());
    }

    public UnitPos pos() {
        return new UnitPos(x, z);
    }

    public Cell offset(int dx, int dLayer, int dz) {
        return new Cell(x + dx, layer + dLayer, z + dz);
    }
}
