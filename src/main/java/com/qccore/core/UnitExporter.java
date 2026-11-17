package com.qccore.core;

import com.qccore.core.palette.BlockPalette;
import com.qccore.core.plan.ComponentMask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Walks the layers and lets the components write their blocks, in the order the components were
 * originally applied: the framework first (corners, extensions, outline transitions and shell
 * voxels), then rows, columns, walls and floors voxel by voxel, then the inner column, the inner
 * wall, the gate and the outer wall once per unit.
 */
public final class UnitExporter {

    private static final Logger LOGGER = LoggerFactory.getLogger("qccore");

    private UnitExporter() {
    }

    public static Map<BlockPos, BlockState> export(UnitLayers layers, BlockPalette palette, BlockPos origin) {
        return export(layers, palette, origin, (dx, dy, dz) -> ComponentMask.ALL);
    }

    /**
     * The export with a component mask per unit. Every unit still counts as a neighbour (exposed
     * faces, shared planes and inner columns are decided by the whole layout), but a unit only writes
     * the components its mask enables, and a unit whose mask is {@code 0} writes nothing. With
     * {@link ComponentMask#ALL} everywhere this is exactly the plain export.
     */
    public static Map<BlockPos, BlockState> export(UnitLayers layers, BlockPalette palette, BlockPos origin, UnitMask masks) {
        Map<BlockPos, BlockState> blockMap = new LinkedHashMap<>();

        // Guard clause: Prevent crashes if exporting an empty canvas
        if (layers.isEmpty()) {
            LOGGER.info("qccore: export skipped, empty layers");
            return blockMap;
        }

        UnitExportContext ctx = new UnitExportContext(layers, blockMap, palette, origin);

        for (int dy : layers.layerKeys()) {
            Map<UnitPos, QcUnit> currentLayerMap = layers.peekLayer(dy);

            for (Map.Entry<UnitPos, QcUnit> pointEntry : currentLayerMap.entrySet()) {
                int dx = pointEntry.getKey().x();
                int dz = pointEntry.getKey().z();
                int mask = masks.mask(dx, dy, dz);
                if ((mask & ComponentMask.ALL) == 0) {
                    continue;
                }
                boolean framework = (mask & ComponentMask.FRAMEWORK) != 0;
                boolean row = (mask & ComponentMask.ROW) != 0;
                boolean column = (mask & ComponentMask.COLUMN) != 0;
                boolean wall = (mask & ComponentMask.WALL) != 0;
                boolean floor = (mask & ComponentMask.FLOOR) != 0;

                ctx.beginUnit(dx, dy, dz, currentLayerMap);

                // Process all single blocks
                for (int x = 0; x < 9; x++)
                    for (int y = 0; y < 9; y++)
                        for (int z = 0; z < 9; z++) {
                            int boundaryCount = SpatialUtils.boundaryCount(x, y, z);
                            if (framework) palette.frameworkBlock().exportVoxel(ctx, x, y, z, boundaryCount);
                            if (row) palette.rowBlock().exportVoxel(ctx, x, y, z, boundaryCount);
                            if (column) palette.columnBlock().exportVoxel(ctx, x, y, z, boundaryCount);
                            if (wall) palette.wallBlock().exportVoxel(ctx, x, y, z, boundaryCount);
                            if (floor) palette.floorBlock().exportVoxel(ctx, x, y, z, boundaryCount);
                        }

                // Process the structures
                if ((mask & ComponentMask.INNER_COLUMN) != 0) palette.innerColumn().exportUnit(ctx);
                if ((mask & ComponentMask.INNER_WALL) != 0) palette.innerWall().exportUnit(ctx);
                if ((mask & ComponentMask.GATE) != 0) palette.gate().exportUnit(ctx);
                if ((mask & ComponentMask.OUTER_WALL) != 0) palette.outerWall().exportUnit(ctx);
            }
        }

        return blockMap;
    }

    /** Which components the unit at grid {@code (dx, dy, dz)} writes, as a {@link ComponentMask}. */
    @FunctionalInterface
    public interface UnitMask {
        int mask(int dx, int dy, int dz);
    }
}
