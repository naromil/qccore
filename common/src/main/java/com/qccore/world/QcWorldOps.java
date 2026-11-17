package com.qccore.world;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The single place that touches world blocks: clearing a building's footprint, placing a layout and
 * pre-flighting whether it fits into the world at all.
 */
public final class QcWorldOps {

    /** A cell spans 0..8, i.e. nine blocks, while units are spaced eight blocks apart. */
    private static final int CELL_SIZE = 9;
    private static final int SHELL_THICKNESS = 2;

    private static final int FLAGS = Block.UPDATE_ALL | Block.UPDATE_KNOWN_SHAPE;

    private QcWorldOps() {
    }

    /**
     * Replaces the footprint of the given units with air: the 9x9x9 cell of every unit, plus the
     * two-block-thick shell just outside every face that has no neighbouring unit in the set.
     * Positions that belong to another unit's cell are left alone.
     */
    static void clearFootprint(ServerLevel world, Collection<BlockPos> unitOrigins) {
        clearFootprint(world, unitOrigins, null);
    }

    /** {@link #clearFootprint(ServerLevel, Collection)} recording every previous state into {@code undo}. */
    static void clearFootprint(ServerLevel world, Collection<BlockPos> unitOrigins, Map<BlockPos, BlockState> undo) {
        Set<BlockPos> origins = new HashSet<>(unitOrigins);
        Map<Long, List<BlockPos>> buckets = bucketize(origins);

        for (BlockPos origin : origins) {
            for (int x = 0; x < CELL_SIZE; x++) {
                for (int y = 0; y < CELL_SIZE; y++) {
                    for (int z = 0; z < CELL_SIZE; z++) {
                        clear(world, origin.offset(x, y, z), undo);
                    }
                }
            }

            for (Direction direction : Direction.values()) {
                if (origins.contains(origin.relative(direction, 8))) {
                    continue;
                }

                int sign = direction.getAxisDirection().getStep();
                for (int t = CELL_SIZE; t < CELL_SIZE + SHELL_THICKNESS; t++) {
                    for (int a = 0; a < CELL_SIZE; a++) {
                        for (int b = 0; b < CELL_SIZE; b++) {
                            BlockPos pos = switch (direction.getAxis()) {
                                case X -> new BlockPos(origin.getX() + sign * t, origin.getY() + a, origin.getZ() + b);
                                case Y -> new BlockPos(origin.getX() + a, origin.getY() + sign * t, origin.getZ() + b);
                                case Z -> new BlockPos(origin.getX() + a, origin.getY() + b, origin.getZ() + sign * t);
                            };
                            if (insideAnyCell(buckets, pos)) {
                                continue;
                            }
                            clear(world, pos, undo);
                        }
                    }
                }
            }
        }
    }

    /** Writes every block of the layout, skipping positions outside the world. */
    static void placeBlocks(ServerLevel world, Map<BlockPos, BlockState> blockMap) {
        placeBlocks(world, blockMap, null);
    }

    /** {@link #placeBlocks(ServerLevel, Map)} recording every previous state into {@code undo}. */
    static void placeBlocks(ServerLevel world, Map<BlockPos, BlockState> blockMap, Map<BlockPos, BlockState> undo) {
        for (Map.Entry<BlockPos, BlockState> entry : blockMap.entrySet()) {
            BlockPos pos = entry.getKey();
            if (!Level.isInSpawnableBounds(pos) || pos.getY() < world.getMinBuildHeight() || pos.getY() >= world.getMaxBuildHeight()) {
                continue;
            }
            record(world, pos, undo);
            world.setBlock(pos, entry.getValue(), FLAGS);
        }
    }

    /** Sets every position to air (terrain clearing), recording the previous states into {@code undo}. */
    static void clearBlocks(ServerLevel world, Collection<BlockPos> positions, Map<BlockPos, BlockState> undo) {
        for (BlockPos pos : positions) {
            clear(world, pos, undo);
        }
    }

    /**
     * Places the blocks only where the world is air or replaceable (water, grass, snow layers): the
     * ground levelling of cleared cells never overwrites solid terrain.
     */
    static void fillAir(ServerLevel world, Map<BlockPos, BlockState> blockMap, Map<BlockPos, BlockState> undo) {
        for (Map.Entry<BlockPos, BlockState> entry : blockMap.entrySet()) {
            BlockPos pos = entry.getKey();
            if (!Level.isInSpawnableBounds(pos) || pos.getY() < world.getMinBuildHeight() || pos.getY() >= world.getMaxBuildHeight()
                    || !world.getBlockState(pos).canBeReplaced()) {
                continue;
            }
            record(world, pos, undo);
            world.setBlock(pos, entry.getValue(), FLAGS);
        }
    }

    /** Writes recorded states back (an undo), in the order given. */
    static void restore(ServerLevel world, Map<BlockPos, BlockState> states) {
        for (Map.Entry<BlockPos, BlockState> entry : states.entrySet()) {
            BlockPos pos = entry.getKey();
            if (!Level.isInSpawnableBounds(pos) || pos.getY() < world.getMinBuildHeight() || pos.getY() >= world.getMaxBuildHeight()) {
                continue;
            }
            world.setBlock(pos, entry.getValue(), FLAGS);
        }
    }

    /** Whether every position can be written at all, so a build never half-applies. */
    static boolean fits(ServerLevel world, Collection<BlockPos> positions) {
        for (BlockPos pos : positions) {
            if (!Level.isInSpawnableBounds(pos) || pos.getY() < world.getMinBuildHeight() || pos.getY() >= world.getMaxBuildHeight()) {
                return false;
            }
        }
        return true;
    }

    private static void clear(ServerLevel world, BlockPos pos, Map<BlockPos, BlockState> undo) {
        if (!Level.isInSpawnableBounds(pos) || pos.getY() < world.getMinBuildHeight() || pos.getY() >= world.getMaxBuildHeight()) {
            return;
        }
        if (undo != null && world.getBlockState(pos).isAir()) {
            return;
        }
        record(world, pos, undo);
        world.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
    }

    /** Keeps the first state seen at a position, i.e. the state before this whole operation. */
    private static void record(ServerLevel world, BlockPos pos, Map<BlockPos, BlockState> undo) {
        if (undo != null && !undo.containsKey(pos)) {
            undo.put(pos.immutable(), world.getBlockState(pos));
        }
    }

    /**
     * Whether a position lies inside the 9x9x9 cell of any of the given origins. The origins are
     * bucketed by {@code coord >> 3}, and a cell can reach into at most two buckets per axis, so one
     * bucket lookup is enough to find every candidate.
     */
    private static boolean insideAnyCell(Map<Long, List<BlockPos>> buckets, BlockPos pos) {
        List<BlockPos> candidates = buckets.get(bucketKey(pos));
        if (candidates == null) {
            return false;
        }
        for (BlockPos origin : candidates) {
            int dx = pos.getX() - origin.getX();
            int dy = pos.getY() - origin.getY();
            int dz = pos.getZ() - origin.getZ();
            if (dx >= 0 && dx < CELL_SIZE && dy >= 0 && dy < CELL_SIZE && dz >= 0 && dz < CELL_SIZE) {
                return true;
            }
        }
        return false;
    }

    private static Map<Long, List<BlockPos>> bucketize(Collection<BlockPos> origins) {
        Map<Long, List<BlockPos>> buckets = new HashMap<>();
        for (BlockPos origin : origins) {
            for (int x = 0; x <= 1; x++) {
                for (int y = 0; y <= 1; y++) {
                    for (int z = 0; z <= 1; z++) {
                        long key = bucketKey(bucket(origin.getX(), x), bucket(origin.getY(), y), bucket(origin.getZ(), z));
                        buckets.computeIfAbsent(key, k -> new ArrayList<>()).add(origin);
                    }
                }
            }
        }
        return buckets;
    }

    private static int bucket(int coord, int offset) {
        return (coord >> 3) + offset;
    }

    private static long bucketKey(BlockPos pos) {
        return bucketKey(pos.getX() >> 3, pos.getY() >> 3, pos.getZ() >> 3);
    }

    private static long bucketKey(int x, int y, int z) {
        return ((long) (x & 0x7FFFFF) << 39) | ((long) (z & 0x7FFFFF) << 16) | (y & 0xFFFF);
    }
}
