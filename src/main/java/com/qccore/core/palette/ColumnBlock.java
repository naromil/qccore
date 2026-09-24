package com.qccore.core.palette;

import com.qccore.core.UnitExportContext;
import com.qccore.core.UnitImportContext;
import net.minecraft.util.math.BlockPos;

/**
 * The column block: the vertical bar attached to the framework on the outside of a unit, plus the
 * corner columns that appear where a unit has no diagonal, side or straight neighbour.
 */
public class ColumnBlock extends BlockComponent {

    public static final String DEFAULT_ID = "minecraft:spruce_log";

    /** Offset of the probe block from the base corner of the south-east unit. */
    private static final int PROBE_DX = 0;
    private static final int PROBE_DY = -1;
    private static final int PROBE_DZ = 1;

    @Override
    public void applyDefaultConfig() {
        setId(DEFAULT_ID);
    }

    @Override
    public void exportVoxel(UnitExportContext ctx, int x, int y, int z, int boundaryCount) {
        if (boundaryCount != 2 || y == 0 || y == 8) {
            return;
        }

        // Between ceiling and floor the column reaches two voxels out of the unit.
        if (x == 0 || x == 8) {
            for (int bs = 2; bs > 0; bs--) {
                int nx = x + (x == 0 ? -bs : bs);
                if (ctx.isFree(nx, y, z)) {
                    ctx.put(new BlockPos(ctx.absX(nx), ctx.absY(y), ctx.absZ(z)), state());
                }
            }
        }
        if (z == 0 || z == 8) {
            for (int bs = 2; bs > 0; bs--) {
                int nz = z + (z == 0 ? -bs : bs);
                if (ctx.isFree(x, y, nz)) {
                    ctx.put(new BlockPos(ctx.absX(x), ctx.absY(y), ctx.absZ(nz)), state());
                }
            }
        }

        // A building corner column fills the diagonal voxel of a unit that has no diagonal,
        // side or straight neighbour.
        int xbs = (x == 0) ? -1 : 1;
        int zbs = (z == 0) ? -1 : 1;
        if (!ctx.unitExists(ctx.dx() + xbs, ctx.dy(), ctx.dz() + zbs)
                && !ctx.unitExists(ctx.dx(), ctx.dy(), ctx.dz() + zbs)
                && !ctx.unitExists(ctx.dx() + xbs, ctx.dy(), ctx.dz())) {
            ctx.put(new BlockPos(ctx.absX(x + xbs), ctx.absY(y), ctx.absZ(z + zbs)), state());
        }
    }

    @Override
    public void extract(UnitImportContext ctx) {
        BlockPos base = ctx.southeastBase();
        if (base == null) {
            return;
        }
        adoptProbe(ctx.stateOrDefault(base.add(PROBE_DX, PROBE_DY, PROBE_DZ), state()));
    }
}
