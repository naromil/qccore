package com.qccore.core;

import com.qccore.core.palette.BlockPalette;
import com.qccore.core.palette.Gate;
import com.qccore.core.palette.InnerColumn;
import com.qccore.core.palette.InnerStructure;
import com.qccore.core.palette.InnerWall;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns an opened structure back into the editor's layer model: it detects the unit grid, asks
 * every component for its own blocks and finally centres the layout on the editor grid.
 */
public final class UnitImporter {

    private UnitImporter() {
    }

    public static UnitLayers importLayers(Map<BlockPos, BlockState> blockMap, BlockPalette palette,
                                          int centerX, int centerZ) {
        UnitLayers layers = new UnitLayers();

        // 1. Guessing the framework block and the grid origin
        GridOrigin origin = palette.frameworkBlock().detectGrid(blockMap);

        UnitImportContext ctx = new UnitImportContext(blockMap, layers, palette, origin.x(), origin.y(), origin.z());

        // 2. Converting to layers itself
        scanUnits(ctx);

        // 3. Extract the rest of single blocks and structures
        palette.columnBlock().extract(ctx);
        palette.rowBlock().extract(ctx);
        palette.floorBlock().extract(ctx);
        palette.wallBlock().extract(ctx);
        palette.outerWall().extract(ctx);

        // 4. Detect inner walls, gates and inner columns
        InnerWall innerWall = palette.innerWall();
        Gate gate = palette.gate();
        InnerStructure.scanSides(ctx, (scanCtx, absX, absY, absZ, side, isGate) -> {
            if (isGate) {
                gate.onDetectedSide(scanCtx, absX, absY, absZ, side);
            } else {
                innerWall.onDetectedSide(scanCtx, absX, absY, absZ, side);
            }
        });
        if (!ctx.innerWallExtracted()) {
            innerWall.setExtracted(null);
        }
        if (!ctx.gateExtracted()) {
            gate.setExtracted(null);
        }

        InnerColumn innerColumn = palette.innerColumn();
        innerColumn.extract(ctx);
        if (!ctx.innerColumnExtracted()) {
            innerColumn.setExtracted(null);
        }

        // 5. Center the layers to the center of the canvas
        centerLayers(ctx, centerX, centerZ);

        return layers;
    }

    /** Builds the unit layers from the framework voxels and records the grid statistics. */
    private static void scanUnits(UnitImportContext ctx) {
        Map<BlockPos, BlockState> blockMap = ctx.blockMap();
        UnitLayers layers = ctx.layers();
        BlockState frameworkState = ctx.frameworkState();

        int maxX = -1, maxY = -1, maxZ = -1; // The southeast corner unit coordinate in the highest layer

        int sumDx = 0, sumDz = 0;
        int count = 0;

        for (Map.Entry<BlockPos, BlockState> entry : blockMap.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState blockState = entry.getValue();

            // Ensure block is the corner of a framework with the lowest (x, y, z)
            if (blockState != frameworkState) continue;
            if ((pos.getX() - ctx.originX()) % 8 != 0 || (pos.getY() - ctx.originY()) % 8 != 0 || (pos.getZ() - ctx.originZ()) % 8 != 0) continue;

            boolean flag = true;

            for (int i = 0; i < 9; i++)
                for (int j = 0; j < 9; j++)
                    for (int k = 0; k < 9; k++) {

                        BlockState targetBlockState = blockMap.get(pos.add(i, j, k));

                        if (SpatialUtils.boundaryCount(i, j, k) >= 2) {
                            if ((j == 0 || j == 8) && targetBlockState != frameworkState) {
                                flag = false;
                                break;

                            } else if (j > 0 && j < 8 && targetBlockState == null) {
                                flag = false;
                                break;
                            }
                        }
                    }

            if (!flag) continue;

            // Calculate unit coordinates by dividing absolute position by 8
            int dx = Math.floorDiv(pos.getX() - ctx.originX(), 8);
            int dy = Math.floorDiv(pos.getY() - ctx.originY(), 8);
            int dz = Math.floorDiv(pos.getZ() - ctx.originZ(), 8);

            sumDx += dx;
            sumDz += dz;
            count++;

            // Track maximum coordinates for block config extraction
            if (dy > maxY || (dy == maxY && (dz > maxZ || (dz == maxZ && dx > maxX)))) {
                maxX = dx;
                maxY = dy;
                maxZ = dz;
            }

            // Initialize layer map if needed
            Map<UnitPos, QcUnit> currentLayer = layers.layer(dy + 1);
            UnitPos unitPos = new UnitPos(dx, dz);

            // Create unit if it doesn't exist
            if (!currentLayer.containsKey(unitPos)) {
                currentLayer.put(unitPos, new QcUnit());
            }
        }

        int avgDx = count > 0 ? sumDx / count : 0;
        int avgDz = count > 0 ? sumDz / count : 0;

        ctx.setStats(new UnitGridStats(maxX, maxY, maxZ, avgDx, avgDz, count));
    }

    /** Moves every unit so the imported layout ends up around the middle of the editor canvas. */
    private static void centerLayers(UnitImportContext ctx, int centerX, int centerZ) {
        UnitGridStats stats = ctx.stats();

        for (int layerKey : new ArrayList<>(ctx.layers().layerKeys())) {
            Map<UnitPos, QcUnit> layer = ctx.layers().peekLayer(layerKey);
            Map<UnitPos, QcUnit> newLayer = new LinkedHashMap<>();
            for (Map.Entry<UnitPos, QcUnit> entry : layer.entrySet()) {
                UnitPos unitPos = entry.getKey();
                newLayer.put(new UnitPos(centerX - stats.avgDx() + unitPos.x(), centerZ - stats.avgDz() + unitPos.z()), entry.getValue());
            }
            ctx.layers().setLayer(layerKey, newLayer);
        }
    }
}
