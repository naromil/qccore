package com.qccore.world;

import com.qccore.core.Alignment;
import com.qccore.core.QcResult;
import com.qccore.core.QcUnit;
import com.qccore.core.UnitBlockConverter;
import com.qccore.core.UnitLayers;
import com.qccore.core.UnitPos;
import com.qccore.core.palette.BlockPalette;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Re-aligns a whole connected building to a lattice.
 *
 * <p>The building is rebuilt from the flags recorded in the index and the palette the client sent,
 * so a re-align re-applies the current component configuration and discards hand edits inside the
 * building's footprint.
 */
public final class QcAlignService {

    private static final Logger LOGGER = LoggerFactory.getLogger("qccore");
    private static final int MAX_UNITS = 4096;

    /**
     * Radius the stale-record prune covers. A building can be larger than its anchor, so the prune
     * is generous instead of exact: it only drops records whose origin block was mined away.
     */
    private static final int PRUNE_RADIUS = 128;

    private QcAlignService() {
    }

    public static QcResult align(ServerPlayer player, BlockPos unitOrigin, int a, int b, int c, BlockPalette palette) {
        if (!player.isCreative()) {
            return new QcResult(false, "creative mode is required to re-align a building");
        }

        ServerLevel world = player.serverLevel();
        ResourceLocation dimension = world.dimension().location();
        QcUnitIndex index = QcUnitIndex.get(world);
        index.prune(world, unitOrigin, PRUNE_RADIUS);

        Map<BlockPos, QcUnitRecord> units = index.units(dimension);
        QcUnitRecord target = units.get(unitOrigin);
        if (target == null || !target.isBuilding()) {
            return new QcResult(false, "no QC unit recorded at " + unitOrigin.toShortString());
        }

        // BFS the connected building over the unit lattice.
        Map<BlockPos, QcUnitRecord> building = new LinkedHashMap<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        building.put(unitOrigin, target);
        queue.add(unitOrigin);

        while (!queue.isEmpty() && building.size() < MAX_UNITS) {
            BlockPos current = queue.poll();
            for (Direction direction : Direction.values()) {
                BlockPos neighbour = current.relative(direction, 8);
                if (building.containsKey(neighbour)) {
                    continue;
                }
                QcUnitRecord record = units.get(neighbour);
                if (record == null || !record.isBuilding()) {
                    continue;                       // scanned road cells are not part of a building
                }
                building.put(neighbour, record);
                queue.add(neighbour);
            }
        }

        BlockPos oldAnchor = anchor(building);
        int dx = Alignment.nearestDelta(oldAnchor.getX(), a);
        int dy = Alignment.nearestDelta(oldAnchor.getY(), b);
        int dz = Alignment.nearestDelta(oldAnchor.getZ(), c);
        if (dx == 0 && dy == 0 && dz == 0) {
            return new QcResult(false, "already aligned to (8x+" + a + ", 8y+" + b + ", 8z+" + c + ")");
        }

        BlockPos newAnchor = oldAnchor.offset(dx, dy, dz);
        UnitLayers layers = new UnitLayers();
        for (Map.Entry<BlockPos, QcUnitRecord> entry : building.entrySet()) {
            BlockPos origin = entry.getKey();
            QcUnit unit = entry.getValue().toUnit();
            int layer = (origin.getY() - oldAnchor.getY()) / 8;
            UnitPos pos = new UnitPos((origin.getX() - oldAnchor.getX()) / 8, (origin.getZ() - oldAnchor.getZ()) / 8);
            layers.layer(layer).put(pos, unit);
        }

        // Everything is checked before the old building is cleared: an unconfigured palette or a
        // shift out of the world's height would otherwise erase it without a replacement.
        QcResult paletteError = QcBuildService.validatePalette(palette);
        if (paletteError != null) {
            return paletteError;
        }
        Map<BlockPos, BlockState> blockMap = UnitBlockConverter.convertLayersToBlockMap(layers, palette, newAnchor);
        if (blockMap.isEmpty()) {
            return new QcResult(false, "the layout produced no blocks");
        }
        Set<BlockPos> positions = new HashSet<>(blockMap.keySet());
        for (BlockPos origin : QcBuildService.origins(newAnchor, layers)) {
            positions.add(origin);
            positions.add(origin.offset(8, 8, 8));
        }
        if (!QcWorldOps.fits(world, positions)) {
            return new QcResult(false, "the aligned building would not fit inside this world's height");
        }

        // The old cells have to go before the rebuild writes the new ones, so nothing of the old
        // building survives next to it.
        QcWorldOps.clearFootprint(world, building.keySet());
        QcBuildService.apply(world, newAnchor, layers, blockMap);
        for (BlockPos oldOrigin : building.keySet()) {
            index.remove(dimension, oldOrigin);
        }

        int moved = building.size();
        String message = "moved " + moved + " QC unit(s) by (" + dx + ", " + dy + ", " + dz + ") to align to (8x+" + a
                + ", 8y+" + b + ", 8z+" + c + ")";
        LOGGER.info("qccore: {} {}", player.getName().getString(), message);
        return new QcResult(true, message);
    }

    /** The component-wise minimum of the building's unit origins. */
    private static BlockPos anchor(Map<BlockPos, QcUnitRecord> building) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        for (BlockPos origin : building.keySet()) {
            minX = Math.min(minX, origin.getX());
            minY = Math.min(minY, origin.getY());
            minZ = Math.min(minZ, origin.getZ());
        }
        return new BlockPos(minX, minY, minZ);
    }
}
