package com.qccore.core;

import net.minecraft.block.BlockState;

import java.util.List;

/**
 * A detached structure: its size triple and the block entries that make it up, with positions
 * relative to the structure origin. It carries no NBT, so the algorithm layer never has to know
 * about the file format.
 */
public record QcStructure(int sizeX, int sizeY, int sizeZ, List<Entry> entries) {

    public record Entry(int x, int y, int z, BlockState state) {
    }
}
