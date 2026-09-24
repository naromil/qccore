package com.qccore.core;

import com.qccore.core.palette.BlockPalette;
import net.minecraft.block.BlockState;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;

import java.util.Map;

/**
 * Everything a component needs while the export walks the layers: the map it writes into, the
 * unit that is currently being exported and the queries that decide where a block may go.
 */
public final class UnitExportContext {

    private final UnitLayers layers;
    private final Map<BlockPos, BlockState> blockMap;
    private final BlockPalette palette;
    private final BlockPos origin;

    private int dx;
    private int dy;
    private int dz;
    private Map<UnitPos, QcUnit> layerMap;

    UnitExportContext(UnitLayers layers, Map<BlockPos, BlockState> blockMap, BlockPalette palette, BlockPos origin) {
        this.layers = layers;
        this.blockMap = blockMap;
        this.palette = palette;
        this.origin = origin;
    }

    void beginUnit(int dx, int dy, int dz, Map<UnitPos, QcUnit> layerMap) {
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
        this.layerMap = layerMap;
    }

    public BlockPalette palette() {
        return palette;
    }

    public int dx() {
        return dx;
    }

    public int dy() {
        return dy;
    }

    public int dz() {
        return dz;
    }

    /** The unit map of the layer that is currently being exported. */
    public Map<UnitPos, QcUnit> layerMap() {
        return layerMap;
    }

    public Map<BlockPos, BlockState> blockMap() {
        return blockMap;
    }

    /** The world position unit (0, 0) with grid (0, 0, 0) is anchored at. */
    public BlockPos origin() {
        return origin;
    }

    public int absX(int x) {
        return origin.getX() + dx * 8 + x;
    }

    public int absY(int y) {
        return origin.getY() + dy * 8 + y;
    }

    public int absZ(int z) {
        return origin.getZ() + dz * 8 + z;
    }

    public BlockPos abs(int x, int y, int z) {
        return new BlockPos(absX(x), absY(y), absZ(z));
    }

    /** Writes a block, replacing whatever sits at that position. */
    public void put(BlockPos pos, BlockState state) {
        if (state == null) {
            return;
        }
        blockMap.put(pos, state);
    }

    /** Writes a block only when that position is still empty. */
    public void putIfAbsent(BlockPos pos, BlockState state) {
        if (state == null) {
            return;
        }
        if (!blockMap.containsKey(pos)) {
            blockMap.put(pos, state);
        }
    }

    /**
     * Writes a structure at {@code pos}: every entry is rotated around the structure origin and the
     * block state is rotated with it. {@code preserve} keeps blocks that are already there.
     */
    public void putStructure(BlockPos pos, QcStructure structure, BlockRotation rotation, boolean preserve) {
        if (structure == null) {
            throw new IllegalArgumentException("structure cannot be null.");
        }
        for (QcStructure.Entry entry : structure.entries()) {
            BlockPos rotated = new BlockPos(entry.x(), entry.y(), entry.z()).rotate(rotation);
            BlockPos absolutePos = pos.add(rotated);

            BlockState state = entry.state().rotate(rotation);

            if (!preserve || blockMap.get(absolutePos) == null) {
                blockMap.put(absolutePos, state);
            }
        }
    }

    /**
     * Whether a relative position of the current unit reaches into empty space, i.e. no unit sits
     * there to attach a block to.
     */
    public boolean isFree(int x, int y, int z) {
        return SpatialUtils.isValidPlacement(layers, x, y, z, dx, dy, dz);
    }

    public boolean unitExists(int dx, int dy, int dz) {
        return layers.contains(dx, dy, dz);
    }
}
