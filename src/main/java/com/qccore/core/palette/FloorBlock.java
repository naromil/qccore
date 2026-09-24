package com.qccore.core.palette;

import com.qccore.core.UnitExportContext;
import com.qccore.core.UnitImportContext;
import net.minecraft.util.math.BlockPos;

/**
 * The floor block: the interior surface of every ceiling and floor level.
 */
public class FloorBlock extends BlockComponent {

    public static final String DEFAULT_ID = "minecraft:spruce_planks";

    /** Offset of the probe block from the base corner of the south-east unit. */
    private static final int PROBE_DX = -1;
    private static final int PROBE_DY = -8;
    private static final int PROBE_DZ = -1;

    @Override
    public void applyDefaultConfig() {
        setId(DEFAULT_ID);
    }

    @Override
    public void exportVoxel(UnitExportContext ctx, int x, int y, int z, int boundaryCount) {
        if ((y == 0 || y == 8) && x > 0 && x < 8 && z > 0 && z < 8) {
            ctx.put(ctx.abs(x, y, z), state());
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
