package com.qccore.core;

import com.qccore.core.palette.BlockPalette;
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

                ctx.beginUnit(dx, dy, dz, currentLayerMap);

                // Process all single blocks
                for (int x = 0; x < 9; x++)
                    for (int y = 0; y < 9; y++)
                        for (int z = 0; z < 9; z++) {
                            int boundaryCount = SpatialUtils.boundaryCount(x, y, z);
                            palette.frameworkBlock().exportVoxel(ctx, x, y, z, boundaryCount);
                            palette.rowBlock().exportVoxel(ctx, x, y, z, boundaryCount);
                            palette.columnBlock().exportVoxel(ctx, x, y, z, boundaryCount);
                            palette.wallBlock().exportVoxel(ctx, x, y, z, boundaryCount);
                            palette.floorBlock().exportVoxel(ctx, x, y, z, boundaryCount);
                        }

                // Process the structures
                palette.innerColumn().exportUnit(ctx);
                palette.innerWall().exportUnit(ctx);
                palette.gate().exportUnit(ctx);
                palette.outerWall().exportUnit(ctx);
            }
        }

        return blockMap;
    }
}
