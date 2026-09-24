package com.qccore.core;

import com.qccore.core.palette.BlockPalette;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

import java.util.Map;

/**
 * Entry point for the two conversions between the editor's layer model and a block map: the export
 * drives {@link UnitExporter}, the import drives {@link UnitImporter}.
 */
public final class UnitBlockConverter {

    private UnitBlockConverter() {
    }

    /** Main logic that converts a map of units into a map of blocks. */
    public static Map<BlockPos, BlockState> convertLayersToBlockMap(UnitLayers layers, BlockPalette palette, BlockPos origin) {
        return UnitExporter.export(layers, palette, origin);
    }

    /** Main logic that converts a map of blocks into a map of units, configuring the palette on the way. */
    public static UnitLayers convertBlockMapToLayers(Map<BlockPos, BlockState> blockMap, BlockPalette palette) {
        return UnitImporter.importLayers(blockMap, palette, UnitGrid.CENTER_X, UnitGrid.CENTER_Z);
    }
}
