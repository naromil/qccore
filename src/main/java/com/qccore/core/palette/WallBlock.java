package com.qccore.core.palette;

import com.qccore.core.UnitExportContext;
import com.qccore.core.UnitImportContext;
import net.minecraft.util.math.BlockPos;

/**
 * The wall block: the default wall used above the ceiling and below the floor of a unit.
 */
public class WallBlock extends BlockComponent {

    public static final String DEFAULT_ID = "minecraft:deepslate_tiles";

    /** Offset of the probe block from the base corner of the south-east unit. */
    private static final int PROBE_DX = -1;
    private static final int PROBE_DY = 1;
    private static final int PROBE_DZ = 0;

    @Override
    public void applyDefaultConfig() {
        setId(DEFAULT_ID);
    }

    @Override
    public void exportVoxel(UnitExportContext ctx, int x, int y, int z, int boundaryCount) {
        if (boundaryCount != 2 || (y != 0 && y != 8)) {
            return;
        }

        int ny = y + (y == 0 ? -1 : 1);
        if (ctx.isFree(x, ny, z)) {
            ctx.put(new BlockPos(ctx.absX(x), ctx.absY(ny), ctx.absZ(z)), state());
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
