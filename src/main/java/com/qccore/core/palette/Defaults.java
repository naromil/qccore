package com.qccore.core.palette;

import com.qccore.core.QcStructure;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * The built-in block configuration: the uniform deepslate/spruce look a fresh palette starts from,
 * plus the helper that turns the desktop tool's uniform cuboid maps into a {@link QcStructure}.
 */
public final class Defaults {

    private Defaults() {
    }

    /**
     * Port of {@code generateSimpleStructureTag} for a single block: every non-negative entry of
     * {@code map[x][y][z]} becomes a block, the map's extents are the structure size.
     */
    public static QcStructure simple(BlockState state, byte[][][] map) {
        if (state == null) {
            return null;
        }
        if (map == null || map.length == 0 || map[0].length == 0 || map[0][0].length == 0) {
            return null;
        }
        int x = map.length;
        int y = map[0].length;
        int z = map[0][0].length;

        List<QcStructure.Entry> entries = new ArrayList<>();
        for (int i = 0; i < x; i++) {
            for (int j = 0; j < y; j++) {
                for (int k = 0; k < z; k++) {
                    if (map[i][j][k] < 0) {
                        continue;
                    }
                    entries.add(new QcStructure.Entry(i, j, k, state));
                }
            }
        }
        return new QcStructure(x, y, z, entries);
    }

    /** Installs the built-in ids and the four built-in structures on a palette. */
    public static void applyTo(BlockPalette palette) {
        palette.applyDefaultConfig();
    }

    /** The default state of a block id, or {@code null} when the id is blank or unknown. */
    static BlockState state(String id) {
        String normalised = BlockComponent.normalise(id);
        if (normalised == null) {
            return null;
        }
        Identifier identifier = Identifier.tryParse(normalised);
        if (identifier == null) {
            return null;
        }
        return Registries.BLOCK.getOrEmpty(identifier).map(Block::getDefaultState).orElse(null);
    }
}
