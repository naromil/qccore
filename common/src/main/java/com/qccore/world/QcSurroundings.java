package com.qccore.world;

import com.qccore.core.plan.Cell;
import com.qccore.core.plan.Surroundings;
import com.qccore.core.qc.BlockChoice;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;

/**
 * Turns unit-index records into the {@link Surroundings} of a plan anchored at {@code anchor}: only
 * records on the plan's own lattice count (origin - anchor a multiple of 8 on every axis), a road record
 * becomes a standing road cell (with its recorded road style, if any), a building record's door sides become standing doors, and an applied
 * building record's design signature becomes a standing part a new part of the same design joins. Used by the
 * server's apply (the whole index of the dimension) and the client's block preview (the pushed records),
 * so both compile the same plan the same way.
 */
public final class QcSurroundings {

    private QcSurroundings() {
    }

    public static Surroundings of(Iterable<QcUnitRecord> records, BlockPos anchor) {
        if (anchor == null) {
            return Surroundings.NONE;
        }
        Set<Cell> roads = new LinkedHashSet<>();
        Map<Cell, Integer> doors = new LinkedHashMap<>();
        Set<Cell> buildings = new LinkedHashSet<>();
        Map<Cell, Surroundings.Part> parts = new LinkedHashMap<>();
        Map<Cell, BlockChoice> roadStyles = new LinkedHashMap<>();
        for (QcUnitRecord record : records) {
            Cell cell = cell(record.origin(), anchor);
            if (cell == null) {
                continue;
            }
            if (record.kind() == QcUnitRecord.ROAD) {
                roads.add(cell);
                if (record.road() != null) {
                    roadStyles.put(cell, record.road());
                }
            } else {
                buildings.add(cell);
                if (record.part() != 0) {
                    parts.put(cell, new Surroundings.Part(record.part(), record.walls(), record.gates()));
                }
                if (record.doors() != 0) {
                    doors.put(cell, record.doors() & 0xF);
                }
            }
        }
        return roads.isEmpty() && buildings.isEmpty() ? Surroundings.NONE : new Surroundings(roads, doors, buildings, parts, roadStyles);
    }

    /** The plan cell whose origin is {@code origin}, or {@code null} when it is off the plan's lattice. */
    static Cell cell(BlockPos origin, BlockPos anchor) {
        int dx = origin.getX() - anchor.getX();
        int dy = origin.getY() - anchor.getY();
        int dz = origin.getZ() - anchor.getZ();
        if (Math.floorMod(dx, 8) != 0 || Math.floorMod(dy, 8) != 0 || Math.floorMod(dz, 8) != 0) {
            return null;
        }
        return new Cell(Math.floorDiv(dx, 8), Math.floorDiv(dy, 8), Math.floorDiv(dz, 8));
    }
}
