package com.qccore.core;

import com.qccore.core.palette.BlockPalette;
import net.minecraft.block.BlockState;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * Everything a component needs while an opened structure is read back into the editor: the block
 * map that was read from disk, the layers being built, the grid origin and the flags that tell
 * which structures have been found so far.
 */
public final class UnitImportContext {

    private final Map<BlockPos, BlockState> blockMap;
    private final UnitLayers layers;
    private final BlockPalette palette;
    private final int originX;
    private final int originY;
    private final int originZ;

    private UnitGridStats stats = UnitGridStats.EMPTY;
    private boolean innerWallExtracted;
    private boolean gateExtracted;
    private boolean innerColumnExtracted;

    UnitImportContext(Map<BlockPos, BlockState> blockMap, UnitLayers layers,
                      BlockPalette palette, int originX, int originY, int originZ) {
        this.blockMap = blockMap;
        this.layers = layers;
        this.palette = palette;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
    }

    public BlockPalette palette() {
        return palette;
    }

    public Map<BlockPos, BlockState> blockMap() {
        return blockMap;
    }

    /** The unit layers being built, keyed by {@code dy + 1}. */
    public UnitLayers layers() {
        return layers;
    }

    public int originX() {
        return originX;
    }

    public int originY() {
        return originY;
    }

    public int originZ() {
        return originZ;
    }

    public int absX(int dx) {
        return originX + dx * 8;
    }

    public int absY(int dy) {
        return originY + dy * 8;
    }

    public int absZ(int dz) {
        return originZ + dz * 8;
    }

    /** The block state the framework component is configured with. */
    public BlockState frameworkState() {
        return palette.frameworkBlock().state();
    }

    public UnitGridStats stats() {
        return stats;
    }

    void setStats(UnitGridStats stats) {
        this.stats = stats;
    }

    /**
     * The corner one unit past the south-east unit of the grid, the anchor the single block ids are
     * probed from. {@code null} when the grid has no detected units.
     */
    public BlockPos southeastBase() {
        if (!stats.hasSoutheastCorner()) {
            return null;
        }
        return new BlockPos(
                originX + stats.maxUnitX() * 8 + 8,
                originY + stats.maxUnitY() * 8 + 8,
                originZ + stats.maxUnitZ() * 8 + 8);
    }

    /** The block state at that position, or the fallback when the position is empty. */
    public BlockState stateOrDefault(BlockPos pos, BlockState fallback) {
        BlockState state = blockMap.get(pos);
        return state != null ? state : fallback;
    }

    /**
     * Port of {@code BlockNBTConverter.extractStructure}: reads the box spanned by the two corners
     * back into a detached structure, rotating every entry and state so the result is the
     * unrotated original. Returns {@code null} when the box holds nothing.
     */
    public QcStructure extractStructure(int x, int y, int z, int X, int Y, int Z, BlockRotation rotation) {
        Map<BlockPos, BlockState> extracted = new LinkedHashMap<>();

        int minX = min(x, X), maxX = max(x, X);
        int minY = min(y, Y), maxY = max(y, Y);
        int minZ = min(z, Z), maxZ = max(z, Z);

        for (int absX = minX; absX <= maxX; absX++) {
            for (int absY = minY; absY <= maxY; absY++) {
                for (int absZ = minZ; absZ <= maxZ; absZ++) {
                    BlockState state = blockMap.get(new BlockPos(absX, absY, absZ));
                    if (state == null) {
                        continue;
                    }

                    BlockPos originalPos = new BlockPos(absX - minX, absY - minY, absZ - minZ).rotate(rotation);
                    extracted.put(originalPos, state.rotate(rotation));
                }
            }
        }

        if (extracted.isEmpty()) {
            return null;
        }
        return rebase(extracted);
    }

    /** Rebuilds the extracted map around its minimum corner, like the export does for a file. */
    private static QcStructure rebase(Map<BlockPos, BlockState> blockMap) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        for (BlockPos pos : blockMap.keySet()) {
            minX = min(pos.getX(), minX);
            minY = min(pos.getY(), minY);
            minZ = min(pos.getZ(), minZ);
            maxX = max(pos.getX(), maxX);
            maxY = max(pos.getY(), maxY);
            maxZ = max(pos.getZ(), maxZ);
        }

        List<QcStructure.Entry> entries = new ArrayList<>(blockMap.size());
        for (Map.Entry<BlockPos, BlockState> entry : blockMap.entrySet()) {
            BlockPos pos = entry.getKey();
            entries.add(new QcStructure.Entry(pos.getX() - minX, pos.getY() - minY, pos.getZ() - minZ, entry.getValue()));
        }
        return new QcStructure(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1, entries);
    }

    /** Whether anything is stored inside the box spanned by the two corners. */
    public boolean hasAnyBlock(int x, int y, int z, int X, int Y, int Z) {
        return hasAnyBlock(x, y, z, X, Y, Z, (BlockState) null);
    }

    /** Whether anything other than {@code noBlock} is stored inside the box spanned by the two corners. */
    public boolean hasAnyBlock(int x, int y, int z, int X, int Y, int Z, BlockState noBlock) {
        int minX = min(x, X), maxX = max(x, X);
        int minY = min(y, Y), maxY = max(y, Y);
        int minZ = min(z, Z), maxZ = max(z, Z);

        for (int absX = minX; absX <= maxX; absX++) {
            for (int absY = minY; absY <= maxY; absY++) {
                for (int absZ = minZ; absZ <= maxZ; absZ++) {
                    BlockState state = blockMap.get(new BlockPos(absX, absY, absZ));
                    if (state != null && state != noBlock) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public boolean innerWallExtracted() {
        return innerWallExtracted;
    }

    public void markInnerWallExtracted() {
        innerWallExtracted = true;
    }

    public boolean gateExtracted() {
        return gateExtracted;
    }

    public void markGateExtracted() {
        gateExtracted = true;
    }

    public boolean innerColumnExtracted() {
        return innerColumnExtracted;
    }

    public void markInnerColumnExtracted() {
        innerColumnExtracted = true;
    }
}
