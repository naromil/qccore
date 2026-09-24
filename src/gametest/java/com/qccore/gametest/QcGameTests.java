package com.qccore.gametest;

import com.mojang.authlib.GameProfile;
import com.qccore.core.Alignment;
import com.qccore.core.QcResult;
import com.qccore.core.QcUnit;
import com.qccore.core.UnitLayers;
import com.qccore.core.UnitPos;
import com.qccore.core.palette.BlockPalette;
import com.qccore.core.palette.Defaults;
import com.qccore.world.QcAlignService;
import com.qccore.world.QcBuildService;
import com.qccore.world.QcUnitIndex;
import com.qccore.world.QcUnitRecord;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side tests for the two world mutations: a build writes the layout and registers the units,
 * a re-align moves the whole connected building, and a rejected request writes nothing at all.
 */
public class QcGameTests implements FabricGameTest {

    /** Wall flags of the two units of {@link TestFixtures#wallPair()}: east and west. */
    private static final int WEST_WALL_E = 0b0010;
    private static final int EAST_WALL_W = 0b1000;

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void buildPlacesUnitsAndRegistersRecords(TestContext ctx) {
        BlockPos anchor = testAnchor(ctx, 40, 0);
        BlockPos row = layerOne(anchor);
        BlockPalette palette = defaultPalette();
        ServerWorld world = ctx.getWorld();
        clearIndexArea(world, row);

        QcResult result = QcBuildService.build(ctx.createMockCreativeServerPlayerInWorld(), anchor, TestFixtures.wallPair(), palette);
        if (!result.ok()) {
            throw new AssertionError("build was refused: " + result.message());
        }

        if (world.getBlockState(row).getBlock() != Blocks.POLISHED_DEEPSLATE) {
            throw new AssertionError("no framework block at the unit origin " + row.toShortString());
        }
        if (!world.getBlockState(row.add(4, 4, 4)).isAir()) {
            throw new AssertionError("the cell interior was not cleared");
        }
        if (!TestFixtures.hasBlock(world, row.add(7, 1, 7), row.add(9, 7, 7), Blocks.DEEPSLATE_BRICKS)) {
            throw new AssertionError("no deepslate bricks in the shared wall region");
        }

        Map<BlockPos, QcUnitRecord> units = index(world);
        QcUnitRecord west = units.get(row);
        QcUnitRecord east = units.get(row.add(8, 0, 0));
        if (west == null || east == null) {
            throw new AssertionError("the index does not hold both units of the layout");
        }
        if (west.walls() != WEST_WALL_E || west.gates() != 0) {
            throw new AssertionError("unexpected west unit flags: walls=" + west.walls() + " gates=" + west.gates());
        }
        if (east.walls() != EAST_WALL_W || east.gates() != 0) {
            throw new AssertionError("unexpected east unit flags: walls=" + east.walls() + " gates=" + east.gates());
        }
        List<BlockPos> near = originsNear(units, row, 16);
        if (near.size() != 2) {
            throw new AssertionError("expected exactly two unit records around " + row.toShortString()
                    + " but found " + near.size() + ": " + near);
        }

        ctx.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void alignMovesWholeBuilding(TestContext ctx) {
        // Nudge the anchor off the lattice, so the re-align has something to do on every axis.
        BlockPos anchor = testAnchor(ctx, 40, 40);
        anchor = anchor.add(nudge(anchor.getX()), nudge(anchor.getY()), nudge(anchor.getZ()));
        BlockPos row = layerOne(anchor);
        BlockPalette palette = defaultPalette();
        ServerWorld world = ctx.getWorld();
        ServerPlayerEntity player = ctx.createMockCreativeServerPlayerInWorld();
        clearIndexArea(world, row);

        QcResult buildResult = QcBuildService.build(player, anchor, TestFixtures.wallPair(), palette);
        if (!buildResult.ok()) {
            throw new AssertionError("build was refused: " + buildResult.message());
        }

        QcResult result = QcAlignService.align(player, row, 0, 0, 0, palette);
        if (!result.ok()) {
            throw new AssertionError("align was refused: " + result.message());
        }
        if (!result.message().contains("moved 2 QC unit(s) by")) {
            throw new AssertionError("unexpected align message: " + result.message());
        }

        BlockPos shifted = row.add(
                Alignment.nearestDelta(row.getX(), 0),
                Alignment.nearestDelta(row.getY(), 0),
                Alignment.nearestDelta(row.getZ(), 0));
        if (shifted.equals(row)) {
            throw new AssertionError("the layout was already aligned, so nothing was tested");
        }

        if (!world.getBlockState(row).isAir()) {
            throw new AssertionError("the old unit origin was not cleared");
        }
        if (world.getBlockState(shifted).getBlock() != Blocks.POLISHED_DEEPSLATE) {
            throw new AssertionError("no framework block at the shifted unit origin " + shifted.toShortString());
        }

        Map<BlockPos, QcUnitRecord> units = index(world);
        if (units.containsKey(row)) {
            throw new AssertionError("the index still holds the old unit origin");
        }
        if (!units.containsKey(shifted) || !units.containsKey(shifted.add(8, 0, 0))) {
            throw new AssertionError("the index does not hold the shifted units");
        }

        ctx.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void buildRejectsLayerZeroAndNonCreative(TestContext ctx) {
        BlockPos anchor = testAnchor(ctx, 40, -40);
        BlockPos row = layerOne(anchor);
        BlockPalette palette = defaultPalette();
        ServerWorld world = ctx.getWorld();
        clearIndexArea(world, row);

        // Sample the cell and the shell around it, so a refused build cannot sneak a block in.
        List<BlockPos> samples = sampledPositions(row);
        List<BlockState> before = sampledStates(world, samples);

        UnitLayers layerZero = new UnitLayers();
        layerZero.layer(0).put(new UnitPos(0, 0), new QcUnit());
        QcResult layerResult = QcBuildService.build(ctx.createMockCreativeServerPlayerInWorld(), anchor, layerZero, palette);
        assertRefused(layerResult, "layer 0 is not allowed (layers start at 1)");

        QcResult survivalResult = QcBuildService.build(survivalPlayer(ctx), anchor, TestFixtures.wallPair(), palette);
        assertRefused(survivalResult, "creative mode is required to build into the world");

        List<BlockState> after = sampledStates(world, samples);
        for (int i = 0; i < samples.size(); i++) {
            if (!before.get(i).equals(after.get(i))) {
                throw new AssertionError("a refused build changed " + samples.get(i).toShortString()
                        + " from " + before.get(i) + " to " + after.get(i));
            }
        }

        ctx.complete();
    }

    /** The origin of the test area, 40 blocks above the empty structure. */
    private static BlockPos testAnchor(TestContext ctx, int y, int z) {
        return ctx.getAbsolutePos(BlockPos.ORIGIN).add(0, y, z);
    }

    /** The cell origin of layer 1: layers start at 1, so the first row sits eight blocks above the anchor. */
    private static BlockPos layerOne(BlockPos anchor) {
        return anchor.add(0, 8, 0);
    }

    private static ServerPlayerEntity survivalPlayer(TestContext ctx) {
        return new ServerPlayerEntity(ctx.getWorld().getServer(), ctx.getWorld(),
                new GameProfile(UUID.randomUUID(), "test-survival-player")) {
            @Override
            public boolean isCreative() {
                return false;
            }
        };
    }

    private static void assertRefused(QcResult result, String message) {
        if (result.ok()) {
            throw new AssertionError("the request was accepted: " + result.message());
        }
        if (!result.message().equals(message)) {
            throw new AssertionError("expected \"" + message + "\" but got \"" + result.message() + "\"");
        }
    }

    private static BlockPalette defaultPalette() {
        BlockPalette palette = new BlockPalette();
        Defaults.applyTo(palette);
        return palette;
    }

    private static Map<BlockPos, QcUnitRecord> index(ServerWorld world) {
        Identifier dimension = world.getRegistryKey().getValue();
        return QcUnitIndex.get(world).units(dimension);
    }

    /**
     * Drops the unit records of this test's area. The game test world is not recreated between runs
     * (loom's clearRunDirectory only covers client game tests), so a run has to start from a known
     * index state to stay deterministic.
     */
    private static void clearIndexArea(ServerWorld world, BlockPos center) {
        QcUnitIndex index = QcUnitIndex.get(world);
        Identifier dimension = world.getRegistryKey().getValue();
        for (BlockPos origin : List.copyOf(index.units(dimension).keySet())) {
            if (origin.isWithinDistance(center, 32)) {
                index.remove(dimension, origin);
            }
        }
    }

    private static List<BlockPos> originsNear(Map<BlockPos, QcUnitRecord> units, BlockPos center, int radius) {
        List<BlockPos> near = new ArrayList<>();
        for (BlockPos origin : units.keySet()) {
            if (origin.isWithinDistance(center, radius)) {
                near.add(origin);
            }
        }
        return near;
    }

    /** The unit corners, the cell interior and the shell of a two unit row. */
    private static List<BlockPos> sampledPositions(BlockPos row) {
        List<BlockPos> samples = new ArrayList<>();
        samples.add(row);
        samples.add(row.add(4, 0, 4));
        samples.add(row.add(4, 4, 4));
        samples.add(row.add(8, 4, 4));
        samples.add(row.add(16, 4, 4));
        samples.add(row.add(8, 8, 8));
        samples.add(row.add(8, 1, 8));
        samples.add(row.add(2, 9, 2));
        samples.add(row.add(-1, 4, 4));
        return samples;
    }

    private static List<BlockState> sampledStates(ServerWorld world, List<BlockPos> positions) {
        List<BlockState> states = new ArrayList<>();
        for (BlockPos pos : positions) {
            states.add(world.getBlockState(pos));
        }
        return states;
    }

    /** Shifts a coordinate off the lattice when it already sits on it. */
    private static int nudge(int value) {
        return Math.floorMod(value, 8) == 0 ? 1 : 0;
    }
}
