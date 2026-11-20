package com.qccore.world;

import com.qccore.core.QcResult;
import com.qccore.core.scan.QcScanner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * {@code /qc scan}: reads the QC units standing round a position (built by hand, by worldedit's qckit or by
 * an earlier install) with {@link QcScanner} and adds them to {@link QcUnitIndex}, so the editor shows them
 * and later plans know their grid, party walls, doors and the road cells in front of them.
 *
 * <p>Read-only towards the world. Records the mod wrote itself are kept as they are; a scan only adds
 * positions the index does not know yet. Unloaded chunks read as air, so only loaded ground is scanned.
 */
public final class QcScanService {

    public static final int DEFAULT_RADIUS = 32;
    public static final int MAX_RADIUS = 64;
    /** Blocks scanned below and above the centre. */
    private static final int DOWN = 16;
    private static final int UP = 40;

    private QcScanService() {
    }

    public static QcResult scan(ServerLevel world, BlockPos center, int radius) {
        int r = Math.max(8, Math.min(MAX_RADIUS, radius));
        int minY = Math.max(world.getMinBuildHeight(), center.getY() - DOWN);
        int maxY = Math.min(world.getMaxBuildHeight() - 1, center.getY() + UP);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        QcScanner scanner = new QcScanner((x, y, z) -> {
            cursor.set(x, y, z);
            if (!world.hasChunkAt(cursor)) {
                return "minecraft:air";
            }
            BlockState state = world.getBlockState(cursor);
            return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        }, center.getX() - r, minY, center.getZ() - r, center.getX() + r, maxY, center.getZ() + r);

        QcScanner.Result result = scanner.scan();
        if (result == null) {
            return new QcResult(false, "no QC building found within " + r + " blocks (a unit needs all 12 framework edges)");
        }

        ResourceLocation dimension = world.dimension().location();
        QcUnitIndex index = QcUnitIndex.get(world);
        int added = 0, known = 0, doors = 0;
        for (QcScanner.Found unit : result.units()) {
            BlockPos origin = new BlockPos(unit.x(), unit.y(), unit.z());
            if (unit.doors() != 0) doors += Integer.bitCount(unit.doors());
            if (index.units(dimension).containsKey(origin)) {
                known++;
                continue;
            }
            int kind = unit.kind() == QcScanner.Kind.ROAD ? QcUnitRecord.ROAD : QcUnitRecord.BUILDING;
            index.put(dimension, origin, new QcUnitRecord(origin, unit.walls(), unit.gates(), kind, unit.doors()));
            added++;
        }
        return new QcResult(true, "scanned grid (8x+" + result.ax() + ", 8y+" + result.ay() + ", 8z+" + result.az() + "): "
                + result.count(QcScanner.Kind.BUILDING) + " building unit(s), " + doors + " door(s), "
                + result.count(QcScanner.Kind.ROAD) + " road cell(s); " + added + " added to the index"
                + (known > 0 ? ", " + known + " already known" : ""));
    }
}
