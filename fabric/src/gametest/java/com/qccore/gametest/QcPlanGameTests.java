package com.qccore.gametest;

import com.qccore.core.QcResult;
import com.qccore.core.palette.BlockPalette;
import com.qccore.core.palette.Defaults;
import com.qccore.core.plan.Cell;
import com.qccore.core.plan.ComponentMask;
import com.qccore.core.plan.PlanCompiler;
import com.qccore.core.plan.QcPlan;
import com.qccore.core.plan.BuildingSpec;
import com.qccore.core.qc.BlockChoice;
import com.qccore.core.qc.Roles;
import com.qccore.world.QcPlanService;
import com.qccore.world.QcUndoService;
import com.qccore.world.QcScanService;
import com.qccore.world.QcUnitIndex;
import com.qccore.world.QcUnitRecord;
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
        BlockChoice style = new BlockChoice(Roles.ROAD);
        style.set("floor1", "minecraft:smooth_stone");
        style.set("floor2", "minecraft:stone_bricks");
        style.set("floor3", "minecraft:stone_bricks");
        style.set("floor4", "minecraft:polished_andesite");
        style.set("joint1", "minecraft:sea_lantern");
        plan.styles().put("street", style);
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
                "road style 'nowhere' does not exist");

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

    /** A qckit house with real blocks: oriented logs, stairs, a door, the roof; then a style left empty is refused. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void qckitHouseAppliesWithRealBlocks(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos anchor = ctx.absolutePos(BlockPos.ZERO).offset(0, 40, 200);
        QcPlan plan = new QcPlan();
        plan.buildings().put("A", new BuildingSpec("house", "brick"));
        BlockChoice brick = new BlockChoice(Roles.BUILDING);
        brick.set("frame", "spruce_log");
        brick.set("floor", "spruce_planks");
        brick.set("wall", "bricks");
        brick.set("glass", "glass");
        brick.set("row", "stone_brick_stairs");
        brick.set("coping", "stone_brick_stairs");
        brick.set("roof", "dark_oak_stairs");
        brick.set("roof_slab", "dark_oak_slab");
        brick.set("roof_full", "dark_oak_planks");
        brick.set("sill", "stone_brick_slab");
        brick.set("light", "lantern");
        brick.set("door", "spruce_door");
        plan.styles().put("brick", brick);
        BlockChoice street = new BlockChoice(Roles.ROAD);
        for (int lane = 1; lane <= 4; lane++) {
            street.set("floor" + lane, "stone_bricks");
        }
        plan.styles().put("street", street);
        plan.addUnit(new Cell(0, 1, 0), "A");
        plan.addUnit(new Cell(1, 1, 0), "A");
        plan.addUnit(new Cell(0, 2, 0), "A");
        plan.addUnit(new Cell(1, 2, 0), "A");
        plan.roads().put(new Cell(0, 1, 1), "street");
        plan.roads().put(new Cell(1, 1, 1), "street");
        BlockChoice props = new BlockChoice(Roles.DECOR);
        props.set("pot", "potted_fern");
        props.set("seat", "spruce_stairs");
        props.set("goods", "barrel,hay_block");
        props.set("post", "spruce_fence");
        props.set("lamp", "lantern");
        props.set("awning", "red_wool");
        props.set("planter", "moss_block");
        props.set("flower", "poppy");
        props.set("basin", "stone_brick_wall");
        plan.styles().put("props", props);
        plan.decorStyle = "props";
        plan.decorDensity = 100;
        BlockPalette palette = new BlockPalette();
        Defaults.applyTo(palette);
        ServerPlayer player = ctx.makeMockServerPlayerInLevel();

        PlanCompiler.Compiled compiled = PlanCompiler.compile(plan, palette, anchor);
        if (!compiled.problems().isEmpty()) {
            throw new AssertionError("the house did not compile: " + compiled.problems());
        }
        QcResult result = QcPlanService.apply(player, anchor, plan, palette);
        if (!result.ok()) {
            throw new AssertionError("apply was refused: " + result.message());
        }
        BlockPos origin = anchor.offset(0, 8, 0);
        BlockState post = world.getBlockState(origin.offset(0, 4, 0));
        if (!post.is(Blocks.SPRUCE_LOG) || post.getValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS)
                != net.minecraft.core.Direction.Axis.Y) {
            throw new AssertionError("the corner post is not an upright spruce log: " + post);
        }
        BlockState beam = world.getBlockState(origin.offset(4, 0, 0));
        if (!beam.is(Blocks.SPRUCE_LOG) || beam.getValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS)
                != net.minecraft.core.Direction.Axis.X) {
            throw new AssertionError("the beam along x does not lie along x: " + beam);
        }
        expect(world, origin.offset(4, 0, 4), Blocks.SPRUCE_PLANKS, "floor");
        boolean roof = false;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-2, 17, -2), origin.offset(18, 24, 10))) {
            roof |= world.getBlockState(pos).is(Blocks.DARK_OAK_STAIRS);
        }
        if (!roof) {
            throw new AssertionError("no hip roof above the top storey");
        }
        boolean door = false;
        for (int x = 0; x <= 16; x++) {
            door |= world.getBlockState(origin.offset(x, 1, 8)).is(Blocks.SPRUCE_DOOR);
        }
        if (!door) {
            throw new AssertionError("no door on the road face");
        }
        expect(world, anchor.offset(4, 8, 12), Blocks.STONE_BRICKS, "road in front of the house");
        boolean decor = false;
        for (BlockPos pos : BlockPos.betweenClosed(anchor.offset(-1, 9, 9), anchor.offset(17, 12, 16))) {
            net.minecraft.world.level.block.state.BlockState state = world.getBlockState(pos);
            decor |= state.is(Blocks.POTTED_FERN) || state.is(Blocks.BARREL) || state.is(Blocks.HAY_BLOCK)
                    || state.is(Blocks.SPRUCE_STAIRS) || state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.SPRUCE_FENCE);
        }
        if (!decor) {
            throw new AssertionError("no street decor in front of the house");
        }
        scanFindsTheHouse(world, anchor);
        if (!QcUndoService.undo(player).ok()) {
            throw new AssertionError("could not undo the house");
        }

        plan.styles().put("brick", new BlockChoice(Roles.BUILDING));
        QcResult refused = QcPlanService.apply(player, anchor, plan, palette);
        if (refused.ok() || !refused.message().startsWith("style 'brick' has no block for frame, beam, floor, wall")) {
            throw new AssertionError("an empty style was not refused as expected: " + refused.message());
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

    /** /qc scan on the applied house, with the index emptied first: it finds the four units, the door and the road. */
    private static void scanFindsTheHouse(ServerLevel world, BlockPos anchor) {
        ResourceLocation dimension = world.dimension().location();
        Map<BlockPos, QcUnitRecord> before = new java.util.LinkedHashMap<>(QcUnitIndex.get(world).units(dimension));
        clearIndex(world, anchor);
        QcResult scanned = QcScanService.scan(world, anchor.offset(8, 12, 8), 32);
        if (!scanned.ok()) {
            throw new AssertionError("scan found nothing: " + scanned.message());
        }
        Map<BlockPos, QcUnitRecord> units = QcUnitIndex.get(world).units(dimension);
        int[][] cells = {{0, 8, 0}, {8, 8, 0}, {0, 16, 0}, {8, 16, 0}};
        int doors = 0;
        for (int[] c : cells) {
            QcUnitRecord record = units.get(anchor.offset(c[0], c[1], c[2]));
            if (record == null || !record.isBuilding()) {
                throw new AssertionError("scan missed the unit at +" + c[0] + "," + c[1] + "," + c[2] + ": " + scanned.message());
            }
            doors |= record.doors();
        }
        QcUnitRecord west = units.get(anchor.offset(0, 8, 0));
        if (((west.walls() | west.gates()) & 2) == 0) {
            throw new AssertionError("no partition flag on the shared east side: " + west);
        }
        if ((doors & 4) == 0) {
            throw new AssertionError("no door found on the south (road) face: " + scanned.message());
        }
        QcUnitRecord road = units.get(anchor.offset(0, 8, 8));
        if (road == null || road.kind() != QcUnitRecord.ROAD) {
            throw new AssertionError("road cell in front not found: " + scanned.message());
        }
        clearIndex(world, anchor);
        before.forEach((origin, record) -> QcUnitIndex.get(world).put(dimension, origin, record));
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
