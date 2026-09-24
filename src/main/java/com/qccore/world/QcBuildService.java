package com.qccore.world;

import com.qccore.core.QcResult;
import com.qccore.core.QcUnit;
import com.qccore.core.UnitBlockConverter;
import com.qccore.core.UnitLayers;
import com.qccore.core.UnitPos;
import com.qccore.core.palette.BlockPalette;
import com.qccore.core.palette.QcComponent;
import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The single place that validates a build request before touching the world. Every failure message
 * is user-visible, so the wording is part of the contract.
 */
public final class QcBuildService {

    private static final Logger LOGGER = LoggerFactory.getLogger("qccore");
    private static final int MAX_UNITS = 4096;

    private QcBuildService() {
    }

    public static QcResult build(ServerPlayerEntity player, BlockPos anchor, UnitLayers layers, BlockPalette palette) {
        if (!player.isCreative()) {
            return new QcResult(false, "creative mode is required to build into the world");
        }
        if (layers.unitCount() == 0) {
            return new QcResult(false, "the layout is empty");
        }
        if (layers.unitCount() > MAX_UNITS) {
            return new QcResult(false, "the layout has " + layers.unitCount() + " QC units; the limit is " + MAX_UNITS);
        }
        for (int layer : layers.layerKeys()) {
            if (layer < 1) {
                return new QcResult(false, "layer " + layer + " is not allowed (layers start at 1)");
            }
        }
        if (!palette.isConfigured()) {
            return new QcResult(false, "framework block id '" + describeId(palette.frameworkBlock().getRawId()) + "' is not a block");
        }

        QcResult sizeError = validateComponentSize("inner wall", palette.innerWall(), "7x7x1 or 7x7x3");
        if (sizeError == null) {
            sizeError = validateComponentSize("gate", palette.gate(), "7x7x1 or 7x7x3");
        }
        if (sizeError == null) {
            sizeError = validateComponentSize("outer wall", palette.outerWall(), "7x7x0..3");
        }
        if (sizeError == null) {
            sizeError = validateComponentSize("inner column", palette.innerColumn(), "3x7x3");
        }
        if (sizeError != null) {
            return sizeError;
        }

        ServerWorld world = player.getServerWorld();
        Map<BlockPos, BlockState> blockMap = UnitBlockConverter.convertLayersToBlockMap(layers, palette, anchor);
        if (blockMap.isEmpty()) {
            return new QcResult(false, "the layout produced no blocks");
        }

        // The cells are checked through their two extreme corners: the world is a convex box, so a
        // cell fits exactly when both corners do, and no 9x9x9 position list has to be built.
        Set<BlockPos> positions = new HashSet<>(blockMap.keySet());
        for (BlockPos origin : origins(anchor, layers)) {
            positions.add(origin);
            positions.add(origin.add(8, 8, 8));
        }
        if (!QcWorldOps.fits(world, positions)) {
            return new QcResult(false, "the layout does not fit inside this world's height (y " + minY(positions) + ".." + maxY(positions) + ")");
        }

        apply(world, anchor, layers, blockMap);

        int units = layers.unitCount();
        int layersUsed = layers.layerKeys().size();
        Identifier dimension = world.getRegistryKey().getValue();
        String message = "built " + units + " QC unit(s) on " + layersUsed + " layer(s) at " + anchor.toShortString();
        LOGGER.info("qccore: {} built {} units at {} in {}", player.getName().getString(), units, anchor, dimension);
        return new QcResult(true, message);
    }

    /** Clears the new footprint, writes the layout and registers every unit in the index. */
    static void apply(ServerWorld world, BlockPos anchor, UnitLayers layers, BlockPalette palette) {
        Map<BlockPos, BlockState> blockMap = UnitBlockConverter.convertLayersToBlockMap(layers, palette, anchor);
        apply(world, anchor, layers, blockMap);
    }

    static void apply(ServerWorld world, BlockPos anchor, UnitLayers layers, Map<BlockPos, BlockState> blockMap) {
        QcWorldOps.clearFootprint(world, origins(anchor, layers));
        QcWorldOps.placeBlocks(world, blockMap);

        QcUnitIndex index = QcUnitIndex.get(world);
        Identifier dimension = world.getRegistryKey().getValue();
        for (int dy : layers.layerKeys()) {
            for (Map.Entry<UnitPos, QcUnit> unitEntry : layers.peekLayer(dy).entrySet()) {
                BlockPos origin = anchor.add(unitEntry.getKey().x() * 8, dy * 8, unitEntry.getKey().z() * 8);
                index.put(dimension, origin, QcUnitRecord.of(origin, unitEntry.getValue()));
            }
        }
    }

    /** The origin corner of every unit cell of the layout, anchored at {@code anchor}. */
    static List<BlockPos> origins(BlockPos anchor, UnitLayers layers) {
        List<BlockPos> origins = new ArrayList<>(layers.unitCount());
        for (int dy : layers.layerKeys()) {
            for (UnitPos pos : layers.peekLayer(dy).keySet()) {
                origins.add(anchor.add(pos.x() * 8, dy * 8, pos.z() * 8));
            }
        }
        return origins;
    }

    private static QcResult validateComponentSize(String name, QcComponent component, String expected) {
        if (component.isEmpty() || component.isValidSize(component.getSizeX(), component.getSizeY(), component.getSizeZ())) {
            return null;
        }
        return new QcResult(false, name + " structure is " + component.getSizeX() + "x" + component.getSizeY() + "x"
                + component.getSizeZ() + "; expected " + expected);
    }

    private static String describeId(String id) {
        return id == null || id.isBlank() ? "<empty>" : id;
    }

    private static int minY(Set<BlockPos> positions) {
        int min = Integer.MAX_VALUE;
        for (BlockPos pos : positions) {
            min = Math.min(min, pos.getY());
        }
        return min;
    }

    private static int maxY(Set<BlockPos> positions) {
        int max = Integer.MIN_VALUE;
        for (BlockPos pos : positions) {
            max = Math.max(max, pos.getY());
        }
        return max;
    }
}
