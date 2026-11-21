package com.qccore.world;

import com.qccore.core.QcResult;
import com.qccore.core.QcUnit;
import com.qccore.core.UnitPos;
import com.qccore.core.palette.BlockPalette;
import com.qccore.core.plan.Cell;
import com.qccore.core.plan.ComponentMask;
import com.qccore.core.plan.PlanCompiler;
import com.qccore.core.plan.QcPlan;
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

    public static QcResult apply(ServerPlayer player, BlockPos anchor, QcPlan plan, BlockPalette palette) {
        if (!player.isCreative()) {
            return new QcResult(false, "creative mode is required to build into the world");
        }
        QcResult problem = validate(plan, palette);
        if (problem != null) {
            return problem;
        }
        ServerLevel level = player.serverLevel();
        PlanCompiler.Compiled compiled = PlanCompiler.compile(plan, palette, anchor,
                QcSurroundings.of(QcUnitIndex.get(level).units(level.dimension().location()).values(), anchor));
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
        QcWorldOps.clearBlocks(world, compiled.air(), undo);
        QcWorldOps.clearFootprint(world, fullOrigins(plan, anchor), undo);
        QcWorldOps.placeBlocks(world, compiled.blocks(), undo);
        QcWorldOps.fillAir(world, compiled.ground(), undo);

        QcUnitIndex index = QcUnitIndex.get(world);
        Map<BlockPos, QcUnitRecord> previous = new LinkedHashMap<>();
        for (int layer : plan.units().layerKeys()) {
            for (Map.Entry<UnitPos, QcUnit> entry : plan.units().peekLayer(layer).entrySet()) {
                BlockPos origin = PlanCompiler.origin(anchor, Cell.of(entry.getKey(), layer));
                previous.put(origin, index.units(dimension).get(origin));
                index.put(dimension, origin, QcUnitRecord.of(origin, entry.getValue()));
            }
        }
        // Road cells are recorded too, so a later plan joins them without a /qc scan. A building
        // record already standing at a road cell's origin is kept: the road never clears a footprint,
        // so that building is still there (and the prune drops it once it is mined).
        for (Cell cell : plan.roads().keySet()) {
            BlockPos origin = PlanCompiler.origin(anchor, cell);
            QcUnitRecord standing = index.units(dimension).get(origin);
            if (standing != null && standing.isBuilding()) {
                continue;
            }
            previous.put(origin, standing);
            index.put(dimension, origin, new QcUnitRecord(origin, 0, 0, QcUnitRecord.ROAD, 0));
        }
        QcUndoService.push(player, new QcUndoService.Entry(dimension, undo, previous));

        String message = "applied plan: " + plan.summary() + " at " + anchor.toShortString() + ", "
                + undo.size() + " block(s) changed (/qc undo reverts it)";
        if (!compiled.notes().isEmpty()) {
            message += "; note: " + compiled.notes().get(0)
                    + (compiled.notes().size() > 1 ? " (+" + (compiled.notes().size() - 1) + " more)" : "");
        }
        LOGGER.info("qccore: {} applied a plan ({}) at {} in {}, {} blocks changed", player.getName().getString(),
                plan.summary(), anchor, dimension, undo.size());
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
        for (Cell cell : plan.cells()) {
            if (cell.layer() < 1) {
                return new QcResult(false, "layer " + cell.layer() + " is not allowed (layers start at 1)");
            }
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
