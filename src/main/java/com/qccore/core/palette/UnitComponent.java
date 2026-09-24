package com.qccore.core.palette;

import com.qccore.core.UnitExportContext;
import com.qccore.core.UnitImportContext;

/**
 * One of the nine building components a QC Unit is assembled from.
 *
 * <p>Each component owns the blocks it contributes: the export hooks write them into the
 * {@link UnitExportContext} (the converters only drive the iteration order) and
 * {@link #extract(UnitImportContext)} reads them back out of an opened structure file.
 */
public interface UnitComponent {

    /** Installs this component's built-in default id or structure. */
    void applyDefaultConfig();

    /** Whether an uploaded structure of the given {@code size} triple is accepted for this component. */
    default boolean isValidSize(int x, int y, int z) {
        return false;
    }

    /**
     * Per-voxel export hook, called for every voxel of every unit.
     *
     * @param x,y,z unit-local coordinates in {@code 0..8}
     * @param boundaryCount how many of the three coordinates sit on a unit boundary ({@code 0}, {@code 1}, {@code 2} or {@code 3})
     */
    default void exportVoxel(UnitExportContext ctx, int x, int y, int z, int boundaryCount) {
    }

    /** Per-unit export hook, called once per unit after the voxel sweep. */
    default void exportUnit(UnitExportContext ctx) {
    }

    /** Import hook, called once per opened structure to pull this component's blocks out. */
    default void extract(UnitImportContext ctx) {
    }
}
