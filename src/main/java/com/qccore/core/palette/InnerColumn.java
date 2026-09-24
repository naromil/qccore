package com.qccore.core.palette;

import com.qccore.core.QcStructure;
import com.qccore.core.QcUnit;
import com.qccore.core.UnitExportContext;
import com.qccore.core.UnitImportContext;
import com.qccore.core.UnitPos;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;

import java.util.Map;

/**
 * The inner column: a 3x7x3 structure placed on the crossing point of a 2x2 group of units that
 * share no inner walls and no gate.
 */
public class InnerColumn extends QcComponent {

    public static final String DEFAULT_ID = "minecraft:stripped_spruce_log";

    public InnerColumn() {
        super();
    }

    @Override
    public void applyDefaultConfig() {
        setStructure(Defaults.simple(Defaults.state(DEFAULT_ID), new byte[][][]{
                {{-1, 0, -1}, {-1, -1, -1}, {-1, -1, -1}, {-1, -1, -1}, {-1, -1, -1}, {-1, -1, -1}, {-1, 0, -1}},
                {{0, 0, 0}, {-1, 0, -1}, {-1, 0, -1}, {-1, 0, -1}, {-1, 0, -1}, {-1, 0, -1}, {0, 0, 0}},
                {{-1, 0, -1}, {-1, -1, -1}, {-1, -1, -1}, {-1, -1, -1}, {-1, -1, -1}, {-1, -1, -1}, {-1, 0, -1}},
        }), "[Generated Default: Stripped Spruce Log]");
    }

    @Override
    public boolean isValidSize(int x, int y, int z) {
        return x == 3 && y == 7 && z == 3;
    }

    @Override
    public void exportUnit(UnitExportContext ctx) {
        if (isEmpty()) {
            return;
        }

        BlockPalette palette = ctx.palette();
        boolean isWallEmpty = palette.innerWall().isEmpty() && palette.gate().isEmpty();
        if (!canBePlaced(ctx.layerMap(), ctx.dx(), ctx.dz(), isWallEmpty)) {
            return;
        }
        ctx.putStructure(relativePos(ctx), getStructure(), BlockRotation.NONE, false);
    }

    /**
     * Whether the crossing point of the four units around (dx, dz) can carry a column: the four
     * units have to exist and must not expose conflicting walls, unless no inner wall and no gate
     * is configured at all.
     */
    public boolean canBePlaced(Map<UnitPos, QcUnit> layerMap, int dx, int dz, boolean isWallEmpty) {
        // We treat the current unit (dx, dz) as the North-West corner.
        QcUnit uNW = layerMap.get(new UnitPos(dx, dz));
        QcUnit uNE = layerMap.get(new UnitPos(dx + 1, dz));
        QcUnit uSW = layerMap.get(new UnitPos(dx, dz + 1));
        QcUnit uSE = layerMap.get(new UnitPos(dx + 1, dz + 1));

        // Verify all 4 units exist to form a complete square for the inner column
        if (uNW == null || uNE == null || uSW == null || uSE == null) {
            return false;
        }

        // 1. Check the internal cross for any conflicting walls
        boolean hasConflictingWalls =
                uNW.hasAnyE() || uNW.hasAnyS() ||
                        uNE.hasAnyW() || uNE.hasAnyS() ||
                        uSW.hasAnyE() || uSW.hasAnyN() ||
                        uSE.hasAnyW() || uSE.hasAnyN();

        // 2. Place the column exactly at the intersection: only when nothing conflicts, or when
        // neither an inner wall nor a gate is configured.
        return !hasConflictingWalls || isWallEmpty;
    }

    /** The intersection point converges at x=8, z=8 of the north-west unit. */
    public BlockPos relativePos(UnitExportContext ctx) {
        return new BlockPos(ctx.absX(7), ctx.absY(1), ctx.absZ(7));
    }

    @Override
    public void extract(UnitImportContext ctx) {
        for (int layerKey : ctx.layers().layerKeys()) {
            int dy = layerKey - 1;
            Map<UnitPos, QcUnit> currentLayer = ctx.layers().peekLayer(layerKey);

            for (Map.Entry<UnitPos, QcUnit> unitEntry : currentLayer.entrySet()) {
                int dx = unitEntry.getKey().x();
                int dz = unitEntry.getKey().z();
                QcUnit unit = unitEntry.getValue();

                int absX = ctx.absX(dx);
                int absY = ctx.absY(dy);
                int absZ = ctx.absZ(dz);

                QcUnit eastUnit = currentLayer.get(new UnitPos(dx + 1, dz));
                QcUnit southUnit = currentLayer.get(new UnitPos(dx, dz + 1));
                QcUnit southEastUnit = currentLayer.get(new UnitPos(dx + 1, dz + 1));
                if (ctx.innerColumnExtracted() || eastUnit == null || southUnit == null || southEastUnit == null) {
                    continue;
                }

                boolean hasConflictingWalls =
                        unit.hasAnyE() || unit.hasAnyS() ||
                                eastUnit.hasAnyW() || eastUnit.hasAnyS() ||
                                southUnit.hasAnyE() || southUnit.hasAnyN() ||
                                southEastUnit.hasAnyW() || southEastUnit.hasAnyN();

                if (!hasConflictingWalls || (!ctx.innerWallExtracted() && !ctx.gateExtracted())) {
                    boolean hasInnerColumn = ctx.hasAnyBlock(
                            absX + 7, absY + 1, absZ + 7,
                            absX + 9, absY + 7, absZ + 9,
                            ctx.frameworkState());

                    if (hasInnerColumn) {
                        QcStructure extracted = ctx.extractStructure(
                                absX + 7, absY + 1, absZ + 7,
                                absX + 9, absY + 7, absZ + 9,
                                BlockRotation.NONE);
                        setExtracted(extracted);
                        if (!isEmpty()) {
                            ctx.markInnerColumnExtracted();
                        }
                    }
                }
            }
        }
    }
}
