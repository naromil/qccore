package com.qccore.core.palette;

import com.qccore.core.QcUnit;
import com.qccore.core.UnitExportContext;
import com.qccore.core.UnitImportContext;
import com.qccore.core.UnitPos;
import com.qccore.core.UnitSide;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;

import java.util.Map;

/**
 * Shared behaviour of the two 7x7x1..3 structures placed on a unit border: the inner wall and the
 * gate. Both are anchored on the north and east side of a unit, probe the same region and are
 * extracted with the same rotation.
 *
 * <p>The subclasses only differ in the wall flags they accept ({@link #acceptsGateSides()}) and in
 * whether their blocks may overwrite existing ones ({@link #preserveOnPlace()}).
 */
public abstract class InnerStructure extends QcComponent {

    protected InnerStructure() {
        super();
    }

    protected InnerStructure(String path, com.qccore.core.QcStructure structure) {
        super(path, structure);
    }

    /** Which {@code QcUnit} flag pair makes this structure placeable on a side. */
    protected abstract boolean acceptsGateSides();

    /** Whether blocks placed by this structure may overwrite blocks that are already there. */
    protected boolean preserveOnPlace() {
        return false;
    }

    /** The extra depth layer of a 3 deep structure goes outward, a 1 deep one stays on the border. */
    protected final int outShift() {
        return getSizeZ() == 3 ? 1 : 0;
    }

    @Override
    public final void exportUnit(UnitExportContext ctx) {
        if (isEmpty()) {
            return;
        }
        exportSide(ctx, UnitSide.SOUTH);
        exportSide(ctx, UnitSide.EAST);
    }

    public final void exportSide(UnitExportContext ctx, UnitSide side) {
        if (!canBePlaced(ctx.layerMap(), ctx.dx(), ctx.dz(), side)) {
            return;
        }
        ctx.putStructure(relativePos(ctx, side), getStructure(), rotationFor(side), preserveOnPlace());
    }

    protected final BlockRotation rotationFor(UnitSide side) {
        return switch (side) {
            case SOUTH -> BlockRotation.NONE;
            case EAST -> BlockRotation.CLOCKWISE_90;
            default -> throw new IllegalArgumentException("Inner structures only sit on the south or east side, got " + side);
        };
    }

    /** The anchor of the structure for this unit and side. */
    protected final BlockPos relativePos(UnitExportContext ctx, UnitSide side) {
        int absX = ctx.absX(0);
        int absY = ctx.absY(0);
        int absZ = ctx.absZ(0);
        return switch (side) {
            case SOUTH -> new BlockPos(absX + 1, absY + 1, absZ + 8 - outShift());
            case EAST -> new BlockPos(absX + 8 + outShift(), absY + 1, absZ + 1);
            default -> throw new IllegalArgumentException("Inner structures only sit on the south or east side, got " + side);
        };
    }

    /** Whether the two units sharing this border carry the matching wall (and gate) flags. */
    protected final boolean canBePlaced(Map<UnitPos, QcUnit> layerMap, int dx, int dz, UnitSide side) {
        QcUnit unit = layerMap.get(new UnitPos(dx, dz));
        QcUnit neighbour = layerMap.get(switch (side) {
            case SOUTH -> new UnitPos(dx, dz + 1);
            case EAST -> new UnitPos(dx + 1, dz);
            default -> throw new IllegalArgumentException("Inner structures only sit on the south or east side, got " + side);
        });

        if (unit == null || neighbour == null) {
            return false;
        }

        boolean walls = switch (side) {
            case SOUTH -> neighbour.hasWallN() && unit.hasWallS();
            case EAST -> neighbour.hasWallW() && unit.hasWallE();
            default -> false;
        };
        if (!walls) {
            return false;
        }

        boolean gates = switch (side) {
            case SOUTH -> neighbour.isGateN() && unit.isGateS();
            case EAST -> neighbour.isGateW() && unit.isGateE();
            default -> false;
        };
        return gates == acceptsGateSides();
    }

    /**
     * Scans every unit and hands each detected border to the handler, setting the shared wall and
     * gate flags on both units of that border on the way.
     */
    public static void scanSides(UnitImportContext ctx, SideHandler handler) {
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
                if (eastUnit != null && ctx.hasAnyBlock(absX + 7, absY + 1, absZ + 2, absX + 9, absY + 7, absZ + 6)) {
                    boolean isGate = !ctx.hasAnyBlock(absX + 7, absY + 1, absZ + 3, absX + 9, absY + 3, absZ + 5);

                    unit.setWallE(true);
                    eastUnit.setWallW(true);
                    if (isGate) {
                        unit.setGateE(true);
                        eastUnit.setGateW(true);
                    }

                    handler.onSide(ctx, absX, absY, absZ, UnitSide.EAST, isGate);
                }

                QcUnit southUnit = currentLayer.get(new UnitPos(dx, dz + 1));
                if (southUnit != null && ctx.hasAnyBlock(absX + 2, absY + 1, absZ + 7, absX + 6, absY + 7, absZ + 9)) {
                    boolean isGate = !ctx.hasAnyBlock(absX + 3, absY + 1, absZ + 7, absX + 5, absY + 3, absZ + 9);

                    unit.setWallS(true);
                    southUnit.setWallN(true);
                    if (isGate) {
                        unit.setGateS(true);
                        southUnit.setGateN(true);
                    }

                    handler.onSide(ctx, absX, absY, absZ, UnitSide.SOUTH, isGate);
                }
            }
        }
    }

    /** Reads this structure's blocks back out of the given border and stores them as the component. */
    protected final void extractSide(UnitImportContext ctx, int absX, int absY, int absZ, UnitSide side) {
        setExtracted(switch (side) {
            case SOUTH -> ctx.extractStructure(absX + 1, absY + 1, absZ + 7,
                    absX + 7, absY + 7, absZ + 9,
                    BlockRotation.NONE);
            case EAST -> ctx.extractStructure(absX + 7, absY + 1, absZ + 1,
                    absX + 9, absY + 7, absZ + 7,
                    BlockRotation.CLOCKWISE_90);
            default -> throw new IllegalArgumentException("Inner structures only sit on the south or east side, got " + side);
        });
    }

    /** Called once per detected border, for the structure this border belongs to. */
    public abstract void onDetectedSide(UnitImportContext ctx, int absX, int absY, int absZ, UnitSide side);

    @FunctionalInterface
    public interface SideHandler {
        void onSide(UnitImportContext ctx, int absX, int absY, int absZ, UnitSide side, boolean isGate);
    }
}
