package com.qccore.core.palette;

import com.qccore.core.GridOrigin;
import com.qccore.core.SpatialUtils;
import com.qccore.core.UnitExportContext;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;

import java.util.Map;

/**
 * The framework block: the 9x9x9 shell every QC Unit is built from.
 *
 * <p>It owns the corner cubes (three boundary coordinates), the +/-2 axial extensions growing out
 * of them, the outline transitions into diagonally empty neighbours, and the shell voxels that
 * carry two boundary coordinates.
 */
public class FrameworkBlock extends BlockComponent {

    public static final String DEFAULT_ID = "minecraft:polished_deepslate";

    @Override
    public void applyDefaultConfig() {
        setId(DEFAULT_ID);
    }

    @Override
    public void exportVoxel(UnitExportContext ctx, int x, int y, int z, int boundaryCount) {
        if (boundaryCount == 3) {
            exportCorner(ctx, x, y, z);
        } else if (boundaryCount == 2) {
            ctx.putIfAbsent(ctx.abs(x, y, z), state());
        }
    }

    /** A corner cube: the voxel itself, its axial extensions and the transitions into empty neighbours. */
    private void exportCorner(UnitExportContext ctx, int x, int y, int z) {
        BlockPos pos = ctx.abs(x, y, z);
        ctx.put(pos, state());

        for (int i = -2; i <= 2; ++i) {
            ctx.putIfAbsent(pos.add(i, 0, 0), state());
        }
        for (int j = -2; j <= 2; ++j) {
            ctx.putIfAbsent(pos.add(0, j, 0), state());
        }
        for (int k = -2; k <= 2; ++k) {
            ctx.putIfAbsent(pos.add(0, 0, k), state());
        }

        // The transition cubes sit one voxel outside the boundary, so they only appear when the
        // neighbouring unit in that direction is missing.
        int xx = x == 0 ? -1 : 9;
        int yy = y == 0 ? -1 : 9;
        int zz = z == 0 ? -1 : 9;

        if (ctx.isFree(x, yy, zz)) {
            ctx.putIfAbsent(new BlockPos(pos.getX(), ctx.absY(yy), ctx.absZ(zz)), state());
        }
        if (ctx.isFree(xx, y, zz)) {
            ctx.putIfAbsent(new BlockPos(ctx.absX(xx), pos.getY(), ctx.absZ(zz)), state());
        }
        if (ctx.isFree(xx, yy, z)) {
            ctx.putIfAbsent(new BlockPos(ctx.absX(xx), ctx.absY(yy), pos.getZ()), state());
        }
    }

    /**
     * Finds the origin voxel of the unit grid by looking for a 9x9x9 shell made of one single block
     * state, and adopts that state's block as this component's id.
     *
     * @throws IllegalStateException when the structure contains no framework shell at all
     */
    public GridOrigin detectGrid(Map<BlockPos, BlockState> blockMap) {
        int ox = -1, oy = -1, oz = -1;

        for (Map.Entry<BlockPos, BlockState> entry : blockMap.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState frameworkCandidate = entry.getValue();

            boolean flag = true;
            for (int i = 0; i < 9; i++)
                for (int j = 0; j < 9; j++)
                    for (int k = 0; k < 9; k++) {
                        if (SpatialUtils.boundaryCount(i, j, k) >= 2 && blockMap.get(pos.add(i, j, k)) != frameworkCandidate) {
                            flag = false;
                            break;
                        }
                    }

            if (flag) {
                ox = pos.getX() % 8;
                oy = pos.getY() % 8;
                oz = pos.getZ() % 8;
                setId(Registries.BLOCK.getId(frameworkCandidate.getBlock()).toString());
                break;
            }
        }

        if (ox == -1) {
            throw new IllegalStateException("No framework block found");
        }
        return new GridOrigin(ox, oy, oz);
    }
}
