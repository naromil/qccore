package com.qccore.core.palette;

import com.qccore.core.QcStructure;
import com.qccore.core.UnitExportContext;
import com.qccore.core.UnitImportContext;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;

/**
 * The outer wall: a 7x7x1..3 structure closing every face of a unit that has no neighbour. A
 * structure deeper than one voxel grows outward, away from the unit.
 */
public class OuterWall extends QcComponent {

    public OuterWall() {
        super();
    }

    @Override
    public void applyDefaultConfig() {
        setStructure(Defaults.simple(Defaults.state("minecraft:deepslate_bricks"), new byte[][][]{
                {{-1, 0, 0}, {-1, 0, 0}, {-1, 0, 0}, {-1, 0, -1}, {-1, 0, -1}, {-1, 0, -1}, {-1, 0, -1}},
                {{0, 0, -1}, {0, 0, -1}, {0, 0, -1}, {0, 0, -1}, {0, 0, -1}, {-1, 0, -1}, {-1, 0, -1}},
                {{-1, -1, -1}, {-1, -1, -1}, {-1, -1, -1}, {-1, -1, -1}, {0, 0, -1}, {-1, 0, 0}, {-1, 0, 0}},
                {{-1, -1, -1}, {-1, -1, -1}, {-1, -1, -1}, {-1, -1, -1}, {0, 0, -1}, {-1, 0, 0}, {-1, 0, 0}},
                {{-1, -1, -1}, {-1, -1, -1}, {-1, -1, -1}, {-1, -1, -1}, {0, 0, -1}, {-1, 0, 0}, {-1, 0, 0}},
                {{0, 0, -1}, {0, 0, -1}, {0, 0, -1}, {0, 0, -1}, {0, 0, -1}, {-1, 0, -1}, {-1, 0, -1}},
                {{-1, 0, 0}, {-1, 0, 0}, {-1, 0, 0}, {-1, 0, -1}, {-1, 0, -1}, {-1, 0, -1}, {-1, 0, -1}},
        }), "[Generated Default: Deepslate Bricks]");
    }

    @Override
    public boolean isValidSize(int x, int y, int z) {
        return x == 7 && y == 7 && z <= 3;
    }

    @Override
    public void exportUnit(UnitExportContext ctx) {
        if (isEmpty()) {
            return;
        }

        // If the wall is 2 or more blocks deep the extra layers go outward; a 1 deep wall stays
        // exactly on the boundary.
        int outShift = (getSizeZ() >= 2) ? 1 : 0;

        int absX = ctx.absX(0);
        int absY = ctx.absY(0);
        int absZ = ctx.absZ(0);
        QcStructure structure = getStructure();

        if (!ctx.unitExists(ctx.dx(), ctx.dy(), ctx.dz() - 1)) {
            ctx.putStructure(new BlockPos(absX + 1, absY + 1, absZ - outShift), structure, BlockRotation.NONE, false);
        }
        if (!ctx.unitExists(ctx.dx(), ctx.dy(), ctx.dz() + 1)) {
            ctx.putStructure(new BlockPos(absX + 7, absY + 1, absZ + 8 + outShift), structure, BlockRotation.CLOCKWISE_180, false);
        }
        if (!ctx.unitExists(ctx.dx() + 1, ctx.dy(), ctx.dz())) {
            ctx.putStructure(new BlockPos(absX + 8 + outShift, absY + 1, absZ + 1), structure, BlockRotation.CLOCKWISE_90, false);
        }
        if (!ctx.unitExists(ctx.dx() - 1, ctx.dy(), ctx.dz())) {
            ctx.putStructure(new BlockPos(absX - outShift, absY + 1, absZ + 7), structure, BlockRotation.COUNTERCLOCKWISE_90, false);
        }
    }

    @Override
    public void extract(UnitImportContext ctx) {
        BlockPos base = ctx.southeastBase();
        if (base == null) {
            return;
        }
        setExtracted(ctx.extractStructure(
                base.getX() - 7, base.getY() - 7, base.getZ() - 1,
                base.getX() - 1, base.getY() - 1, base.getZ() + 1,
                BlockRotation.CLOCKWISE_180));
    }
}
