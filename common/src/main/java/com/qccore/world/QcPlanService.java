package com.qccore.world;

import com.qccore.core.QcResult;
import com.qccore.core.QcUnit;
import com.qccore.core.UnitPos;
import com.qccore.core.palette.BlockPalette;
import com.qccore.core.plan.Cell;
import com.qccore.core.plan.ComponentMask;
import com.qccore.core.plan.Join;
import com.qccore.core.plan.PlanCompiler;
import com.qccore.core.plan.QcPlan;
import com.qccore.core.plan.RoadJoin;
import com.qccore.core.plan.Surroundings;
import com.qccore.core.qc.District;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Applies a whole plan (qckit buildings and roads, legacy QCSimple units, terrain clearing, ground
 * levelling) in one validated step, and records it for {@link QcUndoService}. Nothing is written
 * before every check has passed; every failure message is user-visible.
 *
 * <p>Write order: the cleared terrain, then the footprint of every qckit unit and of every legacy
 * unit that builds all nine components (a legacy unit planned with only some components is added to
 * what stands there), then the blocks, then the ground fill.
 */
public final class QcPlanService {

    private static final Logger LOGGER = LoggerFactory.getLogger("qccore");

    static final int MAX_UNITS = 4096;
    static final int MAX_CELLS = 4096;

    private QcPlanService() {
    }

    public static QcResult apply(ServerPlayer player, BlockPos anchor, QcPlan planned, BlockPalette palette) {
        if (!player.isCreative()) {
            return new QcResult(false, "creative mode is required to build into the world");
        }
        QcResult problem = validate(planned, palette);
        if (problem != null) {
            return problem;
        }
        ServerLevel level = player.serverLevel();
        Surroundings around = QcSurroundings.of(QcUnitIndex.get(level).units(level.dimension().location()).values(), anchor);
        // standing parts of the same building are rebuilt with the new part (Join)
        QcPlan plan = Join.join(planned, around);
        int rebuilt = Join.joined(planned, plan).size();
        PlanCompiler.Compiled compiled = PlanCompiler.compile(plan, palette, anchor, around);
        if (!compiled.problems().isEmpty()) {
            return new QcResult(false, compiled.problems().get(0));
        }
        if (compiled.blocks().isEmpty() && compiled.air().isEmpty() && compiled.ground().isEmpty()) {
            return new QcResult(false, "the plan produced no blocks");
        }

        ServerLevel world = player.serverLevel();
        Set<BlockPos> positions = new HashSet<>(compiled.blocks().keySet());
        for (BlockPos origin : compiled.unitOrigins()) {
            positions.add(origin);
            positions.add(origin.offset(8, 8, 8));
        }
        if (!QcWorldOps.fits(world, positions)) {
            return new QcResult(false, "the plan does not fit inside this world's height");
        }

        ResourceLocation dimension = world.dimension().location();
        Map<BlockPos, BlockState> undo = new LinkedHashMap<>();
        if (rebuilt > 0) {
            QcWorldOps.clearBlocks(world, stale(world, plan, Join.joined(planned, plan), palette, anchor, around, compiled), undo);
        }
        QcWorldOps.clearBlocks(world, compiled.air(), undo);
        QcWorldOps.clearFootprint(world, fullOrigins(plan, anchor),
                QcUnitIndex.get(world).units(world.dimension().location()).keySet(), otherCells(plan, anchor), undo);
        QcWorldOps.placeBlocks(world, compiled.blocks(), undo);
        QcWorldOps.fillAir(world, compiled.ground(), undo);

        QcUnitIndex index = QcUnitIndex.get(world);
        Map<BlockPos, QcUnitRecord> previous = new LinkedHashMap<>();
        for (int layer : plan.units().layerKeys()) {
            for (Map.Entry<UnitPos, QcUnit> entry : plan.units().peekLayer(layer).entrySet()) {
                BlockPos origin = PlanCompiler.origin(anchor, Cell.of(entry.getKey(), layer));
                previous.put(origin, index.units(dimension).get(origin));
                int part = District.generates(plan, Cell.of(entry.getKey(), layer))
                        ? Join.signature(plan, plan.building(Cell.of(entry.getKey(), layer))) : 0;
                index.put(dimension, origin, QcUnitRecord.of(origin, entry.getValue(), part));
            }
        }
        // Road cells are recorded too, with their road style, so a later plan joins them without a
        // /qc scan and a later road rebuilds them in their own style. The standing road cells this
        // road rebuilt (RoadJoin) are recorded again, so one that had no style yet keeps the style it
        // now stands in. A building record already standing at a road cell's origin is kept: the road
        // never clears a footprint, so that building is still there (and the prune drops it once it
        // is mined).
        QcPlan roads = RoadJoin.join(plan, around);
        for (Map.Entry<Cell, String> road : roads.roads().entrySet()) {
            BlockPos origin = PlanCompiler.origin(anchor, road.getKey());
            QcUnitRecord standing = index.units(dimension).get(origin);
            if (standing != null && standing.isBuilding()) {
                continue;
            }
            QcUnitRecord laid = QcUnitRecord.road(origin, roads.styles().get(road.getValue()));
            if (laid.equals(standing)) {
                continue;
            }
            previous.put(origin, standing);
            index.put(dimension, origin, laid);
        }
        QcUndoService.push(player, new QcUndoService.Entry(dimension, undo, previous));

        String message = "applied plan: " + planned.summary() + " at " + anchor.toShortString() + ", "
                + undo.size() + " block(s) changed (/qc undo reverts it)";
        if (rebuilt > 0) {
            message += "; rebuilt " + rebuilt + " standing unit(s) of the same building so the parts join";
        }
        if (!compiled.notes().isEmpty()) {
            message += "; note: " + compiled.notes().get(0)
                    + (compiled.notes().size() > 1 ? " (+" + (compiled.notes().size() - 1) + " more)" : "");
        }
        LOGGER.info("qccore: {} applied a plan ({}) at {} in {}, {} blocks changed", player.getName().getString(),
                planned.summary(), anchor, dimension, undo.size());
        return new QcResult(true, message);
    }

    /** The plan-level checks; {@code null} when the plan may be applied or exported. */
    public static QcResult validate(QcPlan plan, BlockPalette palette) {
        if (plan.isEmpty()) {
            return new QcResult(false, "the plan is empty");
        }
        int units = plan.units().unitCount();
        if (units > MAX_UNITS) {
            return new QcResult(false, "the plan has " + units + " QC units; the limit is " + MAX_UNITS);
        }
        if (plan.clears().size() > MAX_CELLS) {
            return new QcResult(false, "the plan clears " + plan.clears().size() + " cells; the limit is " + MAX_CELLS);
        }
        if (plan.roads().size() > MAX_CELLS) {
            return new QcResult(false, "the plan has " + plan.roads().size() + " road cells; the limit is " + MAX_CELLS);
        }
        if (!PlanCompiler.hasLegacyUnits(plan)) {
            return null;
        }
        boolean framework = false;
        for (int layer : plan.units().layerKeys()) {
            for (UnitPos pos : plan.units().peekLayer(layer).keySet()) {
                Cell cell = Cell.of(pos, layer);
                framework |= !District.generates(plan, cell) && ComponentMask.has(plan.mask(cell), ComponentMask.FRAMEWORK);
            }
        }
        if (framework && !palette.isConfigured()) {
            String id = palette.frameworkBlock().getRawId();
            return new QcResult(false, "framework block id '" + (id == null || id.isBlank() ? "<empty>" : id) + "' is not a block");
        }
        if (units > 0) {
            QcResult size = QcBuildService.validateComponentSize("inner wall", palette.innerWall(), "7x7x1 or 7x7x3");
            if (size == null) {
                size = QcBuildService.validateComponentSize("gate", palette.gate(), "7x7x1 or 7x7x3");
            }
            if (size == null) {
                size = QcBuildService.validateComponentSize("outer wall", palette.outerWall(), "7x7x0..3");
            }
            if (size == null) {
                size = QcBuildService.validateComponentSize("inner column", palette.innerColumn(), "3x7x3");
            }
            if (size != null) {
                return size;
            }
        }
        return null;
    }

    /**
     * Blocks the rebuilt standing part wrote outside the cells when it was built alone (eaves, roof
     * ends, facade pieces) that the joined build no longer writes. Only blocks still exactly as that
     * build left them are taken back; inside the plan's unit cells the footprint clear does it.
     */
    private static List<BlockPos> stale(ServerLevel world, QcPlan plan, Set<Cell> rebuilt, BlockPalette palette,
            BlockPos anchor, Surroundings around, PlanCompiler.Compiled joined) {
        Set<BlockPos> cells = new HashSet<>(fullOrigins(plan, anchor));
        List<BlockPos> out = new ArrayList<>();
        PlanCompiler.Compiled alone = PlanCompiler.compile(Join.alone(plan, rebuilt), palette, anchor, around);
        for (Map.Entry<BlockPos, BlockState> entry : alone.blocks().entrySet()) {
            BlockPos pos = entry.getKey();
            if (!joined.blocks().containsKey(pos) && !insideCells(cells, pos, anchor)
                    && world.getBlockState(pos).equals(entry.getValue())) {
                out.add(pos);
            }
        }
        return out;
    }

    private static boolean insideCells(Set<BlockPos> origins, BlockPos pos, BlockPos anchor) {
        int x = Math.floorDiv(pos.getX() - anchor.getX(), 8), y = Math.floorDiv(pos.getY() - anchor.getY(), 8),
                z = Math.floorDiv(pos.getZ() - anchor.getZ(), 8);
        for (int dx = 0; dx >= -1; dx--) {
            for (int dy = 0; dy >= -1; dy--) {
                for (int dz = 0; dz >= -1; dz--) {
                    BlockPos origin = anchor.offset((x + dx) * 8, (y + dy) * 8, (z + dz) * 8);
                    if (origins.contains(origin) && pos.getX() - origin.getX() <= 8
                            && pos.getY() - origin.getY() <= 8 && pos.getZ() - origin.getZ() <= 8) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Origins of the plan's road and cleared cells, which a unit's shell clear stays out of. */
    private static List<BlockPos> otherCells(QcPlan plan, BlockPos anchor) {
        List<BlockPos> origins = new ArrayList<>();
        for (Cell cell : plan.roads().keySet()) {
            origins.add(PlanCompiler.origin(anchor, cell));
        }
        for (Cell cell : plan.clears()) {
            origins.add(PlanCompiler.origin(anchor, cell));
        }
        return origins;
    }

    /** Origins of the qckit units and the full legacy units: only those get their footprint cleared. */
    private static List<BlockPos> fullOrigins(QcPlan plan, BlockPos anchor) {
        List<BlockPos> origins = new ArrayList<>();
        for (int layer : plan.units().layerKeys()) {
            for (UnitPos pos : plan.units().peekLayer(layer).keySet()) {
                Cell cell = Cell.of(pos, layer);
                if (District.generates(plan, cell) || plan.mask(cell) == ComponentMask.ALL) {
                    origins.add(PlanCompiler.origin(anchor, cell));
                }
            }
        }
        return origins;
    }
}
