package com.qccore.gametest;

import com.qccore.core.QcResult;
import com.qccore.core.palette.BlockPalette;
import com.qccore.core.palette.Defaults;
import com.qccore.core.plan.Cell;
import com.qccore.core.plan.ComponentMask;
import com.qccore.core.plan.PlanCompiler;
import com.qccore.core.plan.QcPlan;
import com.qccore.core.plan.RoadStyle;
import com.qccore.world.QcPlanService;
import com.qccore.world.QcUndoService;
import com.qccore.world.QcUnitIndex;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The planning pipeline in a real world: a plan with a full unit, a framework-only unit, a cleared
 * cell and a road is applied in one step, the world undo puts every block and record back, and the
 * world redo re-applies it.
 */
public class QcPlanGameTests implements FabricGameTest {

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void applyPlanThenUndo(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos anchor = ctx.absolutePos(BlockPos.ZERO).offset(0, 40, 80);
        ResourceLocation dimension = world.dimension().location();
        clearIndex(world, anchor.offset(0, 8, 0));

        // terrain in the cell to clear, and below the road
        BlockPos rock = anchor.offset(8 * 3 + 4, 8 + 4, 4);
        world.setBlockAndUpdate(rock, Blocks.STONE.defaultBlockState());
        BlockPos roadFloor = anchor.offset(8 * 5 + 4, 8, 4);
        world.setBlockAndUpdate(roadFloor, Blocks.DIRT.defaultBlockState());

        QcPlan plan = new QcPlan();
        plan.addUnit(new Cell(0, 1, 0), ComponentMask.ALL);
        plan.addUnit(new Cell(1, 1, 0), ComponentMask.FRAMEWORK);
        plan.clears().add(new Cell(3, 1, 0));
        plan.clearMargin = 1;
        RoadStyle style = new RoadStyle();
        style.floor[0] = "minecraft:smooth_stone";
        style.floor[1] = "minecraft:stone_bricks";
        style.floor[2] = "minecraft:stone_bricks";
        style.floor[3] = "minecraft:polished_andesite";
        style.joint[0] = "minecraft:sea_lantern";
        plan.roadStyles().put("street", style);
        plan.roads().put(new Cell(5, 1, 0), "street");
        plan.roads().put(new Cell(5, 1, 1), "street");

        BlockPalette palette = new BlockPalette();
        Defaults.applyTo(palette);
        ServerPlayer player = ctx.makeMockServerPlayerInLevel();
        Map<BlockPos, BlockState> before = new java.util.HashMap<>();
        for (BlockPos pos : PlanCompiler.compile(plan, palette, anchor).blocks().keySet()) {
            before.put(pos, world.getBlockState(pos));
        }

        QcResult result = QcPlanService.apply(player, anchor, plan, palette);
        if (!result.ok()) {
            throw new AssertionError("apply was refused: " + result.message());
        }
        BlockPos full = anchor.offset(0, 8, 0);
        expect(world, full, Blocks.POLISHED_DEEPSLATE, "framework of the full unit");
        expect(world, full.offset(4, 0, 4), Blocks.SPRUCE_PLANKS, "floor of the full unit");
        BlockPos frameOnly = anchor.offset(8, 8, 0);
        expect(world, frameOnly.offset(8, 0, 0), Blocks.POLISHED_DEEPSLATE, "framework of the framework-only unit");
        if (world.getBlockState(frameOnly.offset(4, 0, 4)).is(Blocks.SPRUCE_PLANKS)) {
            throw new AssertionError("the framework-only unit laid a floor");
        }
        if (!world.getBlockState(rock).isAir()) {
            throw new AssertionError("the cleared cell still holds terrain at " + rock.toShortString());
        }
        expect(world, roadFloor, Blocks.POLISHED_ANDESITE, "road axis lane");
        expect(world, anchor.offset(8 * 5 + 1, 8, 8), Blocks.SEA_LANTERN, "road joint light");
        if (!world.getBlockState(anchor.offset(8 * 5, 8, 0)).isAir()) {
            throw new AssertionError("the road wrote its corner post");
        }
        Map<BlockPos, ?> records = QcUnitIndex.get(world).units(dimension);
        if (!records.containsKey(full) || !records.containsKey(frameOnly)) {
            throw new AssertionError("the applied units were not registered");
        }
        if (QcUndoService.depth(player) != 1) {
            throw new AssertionError("expected one undo step, found " + QcUndoService.depth(player));
        }

        QcResult undo = QcUndoService.undo(player);
        if (!undo.ok()) {
            throw new AssertionError("undo was refused: " + undo.message());
        }
        expect(world, rock, Blocks.STONE, "restored terrain");
        expect(world, roadFloor, Blocks.DIRT, "restored ground under the road");
        for (Map.Entry<BlockPos, BlockState> entry : before.entrySet()) {
            if (!world.getBlockState(entry.getKey()).equals(entry.getValue())) {
                throw new AssertionError("undo left " + world.getBlockState(entry.getKey()) + " at "
                        + entry.getKey().toShortString() + " instead of " + entry.getValue());
            }
        }
        Map<BlockPos, ?> after = QcUnitIndex.get(world).units(dimension);
        if (after.containsKey(full) || after.containsKey(frameOnly)) {
            throw new AssertionError("undo left the unit records behind");
        }

        // redo puts the apply back, and undoing again restores the world once more
        if (QcUndoService.redoDepth(player) != 1) {
            throw new AssertionError("expected one redo step, found " + QcUndoService.redoDepth(player));
        }
        QcResult redo = QcUndoService.redo(player);
        if (!redo.ok()) {
            throw new AssertionError("redo was refused: " + redo.message());
        }
        expect(world, full, Blocks.POLISHED_DEEPSLATE, "framework of the full unit after redo");
        expect(world, roadFloor, Blocks.POLISHED_ANDESITE, "road axis lane after redo");
        if (!world.getBlockState(rock).isAir()) {
            throw new AssertionError("redo did not clear the terrain at " + rock.toShortString());
        }
        Map<BlockPos, ?> redone = QcUnitIndex.get(world).units(dimension);
        if (!redone.containsKey(full) || !redone.containsKey(frameOnly)) {
            throw new AssertionError("redo did not register the units again");
        }
        if (QcUndoService.depth(player) != 1 || QcUndoService.redoDepth(player) != 0) {
            throw new AssertionError("after redo expected 1 undo / 0 redo, found " + QcUndoService.depth(player)
                    + " / " + QcUndoService.redoDepth(player));
        }
        QcResult again = QcUndoService.undo(player);
        if (!again.ok()) {
            throw new AssertionError("second undo was refused: " + again.message());
        }
        expect(world, rock, Blocks.STONE, "terrain restored by the second undo");
        for (Map.Entry<BlockPos, BlockState> entry : before.entrySet()) {
            if (!world.getBlockState(entry.getKey()).equals(entry.getValue())) {
                throw new AssertionError("the second undo left " + world.getBlockState(entry.getKey()) + " at "
                        + entry.getKey().toShortString() + " instead of " + entry.getValue());
            }
        }

        // a fresh apply drops what could be redone
        QcPlan small = new QcPlan();
        small.addUnit(new Cell(0, 1, 0), ComponentMask.FRAMEWORK);
        QcResult reapply = QcPlanService.apply(player, anchor, small, palette);
        if (!reapply.ok()) {
            throw new AssertionError("the second apply was refused: " + reapply.message());
        }
        if (QcUndoService.redoDepth(player) != 0) {
            throw new AssertionError("a new apply kept " + QcUndoService.redoDepth(player) + " redo step(s)");
        }
        assertRefused(QcUndoService.redo(player), "nothing to redo");
        if (!QcUndoService.undo(player).ok()) {
            throw new AssertionError("could not undo the second apply");
        }
        world.setBlockAndUpdate(rock, Blocks.AIR.defaultBlockState());
        world.setBlockAndUpdate(roadFloor, Blocks.AIR.defaultBlockState());
        ctx.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void planChecksAreUserVisible(GameTestHelper ctx) {
        BlockPalette palette = new BlockPalette();
        Defaults.applyTo(palette);
        BlockPos anchor = ctx.absolutePos(BlockPos.ZERO).offset(0, 40, 120);
        ServerPlayer player = ctx.makeMockServerPlayerInLevel();

        assertRefused(QcPlanService.apply(player, anchor, new QcPlan(), palette), "the plan is empty");

        QcPlan ground = new QcPlan();
        ground.clears().add(new Cell(0, 0, 0));
        assertRefused(QcPlanService.apply(player, anchor, ground, palette), "layer 0 is not allowed (layers start at 1)");

        QcPlan road = new QcPlan();
        road.roads().put(new Cell(0, 1, 0), "nowhere");
        assertRefused(QcPlanService.apply(player, anchor, road, palette),
                "road style 'nowhere' is missing or has an empty floor lane");

        QcPlan noFrame = new QcPlan();
        noFrame.addUnit(new Cell(0, 1, 0), ComponentMask.FLOOR);
        BlockPalette blank = new BlockPalette();
        Defaults.applyTo(blank);
        blank.frameworkBlock().setId("");
        QcResult floorOnly = QcPlanService.validate(noFrame, blank);
        if (floorOnly != null) {
            throw new AssertionError("a floor-only plan needs no framework, but got: " + floorOnly.message());
        }
        noFrame.setMask(new Cell(0, 1, 0), ComponentMask.ALL);
        assertRefused(QcPlanService.apply(player, anchor, noFrame, blank), "framework block id '<empty>' is not a block");

        Set<BlockPos> untouched = PlanCompiler.compile(noFrame, palette, anchor).blocks().keySet();
        for (BlockPos pos : untouched) {
            if (!ctx.getLevel().getBlockState(pos).isAir()) {
                throw new AssertionError("a refused plan wrote " + pos.toShortString());
            }
        }
        ctx.succeed();
    }

    private static void expect(ServerLevel world, BlockPos pos, net.minecraft.world.level.block.Block block, String what) {
        BlockState state = world.getBlockState(pos);
        if (!state.is(block)) {
            throw new AssertionError(what + ": expected " + block + " at " + pos.toShortString() + " but found " + state);
        }
    }

    private static void assertRefused(QcResult result, String message) {
        if (result.ok()) {
            throw new AssertionError("the plan was accepted: " + result.message());
        }
        if (!result.message().equals(message)) {
            throw new AssertionError("expected \"" + message + "\" but got \"" + result.message() + "\"");
        }
    }

    private static void clearIndex(ServerLevel world, BlockPos center) {
        QcUnitIndex index = QcUnitIndex.get(world);
        ResourceLocation dimension = world.dimension().location();
        for (BlockPos origin : List.copyOf(index.units(dimension).keySet())) {
            if (origin.closerThan(center, 64)) {
                index.remove(dimension, origin);
            }
        }
    }
}
