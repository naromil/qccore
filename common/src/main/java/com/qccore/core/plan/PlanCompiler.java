package com.qccore.core.plan;

import com.qccore.core.QcUnit;
import com.qccore.core.UnitExporter;
import com.qccore.core.UnitLayers;
import com.qccore.core.UnitPos;
import com.qccore.core.palette.BlockPalette;
import com.qccore.core.qc.District;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Turns a {@link QcPlan} into world blocks at an anchor, the one conversion shared by the server's
 * apply, the client's block preview and the {@code .nbt} export.
 *
 * <ol>
 *   <li>Buildings and roads: {@link District}, the qckit generator, for every unit of a qckit
 *       building and every road cell, parsed leniently ({@link BlockIds#parseLenient}).</li>
 *   <li>Legacy units (buildings of template {@code qcsimple}, or units with no building): the
 *       masked export of {@link UnitExporter} with the session palette, as before the rework; they
 *       never overwrite a generated block.</li>
 *   <li>Clears: local y 1..8 of every cleared cell plus the margin outside its exposed sides and
 *       above it, and the 7 blocks of headroom over every road column, minus whatever the plan
 *       places there.</li>
 *   <li>Ground: the floor plane (local y 0) of every cleared cell without a unit or road, to be
 *       filled where the world is air.</li>
 * </ol>
 */
public final class PlanCompiler {

    private PlanCompiler() {
    }

    /**
     * The compiled plan. {@code blocks} keeps insertion order. {@code problems} stop an apply;
     * {@code notes} are worth showing; {@code reached}/{@code missing} are the roles each style's
     * buildings and roads used, and the required ones with no block.
     */
    public record Compiled(Map<BlockPos, BlockState> blocks, Set<BlockPos> air, Map<BlockPos, BlockState> ground,
                           List<BlockPos> unitOrigins, List<String> problems, List<String> notes,
                           Map<String, Set<String>> reached, Map<String, Set<String>> missing) {

        /** A structure-style block map: blocks, then ground, then (optionally) air. */
        public Map<BlockPos, BlockState> forExport(boolean includeAir) {
            Map<BlockPos, BlockState> out = new LinkedHashMap<>(blocks);
            ground.forEach(out::putIfAbsent);
            if (includeAir) {
                for (BlockPos pos : air) {
                    out.putIfAbsent(pos, Blocks.AIR.defaultBlockState());
                }
            }
            return out;
        }
    }

    public static Compiled compile(QcPlan plan, BlockPalette palette, BlockPos anchor) {
        return compile(plan, palette, anchor, Surroundings.NONE);
    }

    /**
     * Compiles the plan where it meets what already stands: roads join the standing road cells of
     * {@code around} and keep its doors' approaches free. The export uses {@link Surroundings#NONE}.
     */
    public static Compiled compile(QcPlan planned, BlockPalette palette, BlockPos anchor, Surroundings around) {
        // standing road cells a new road meets are rebuilt with it, so the network looks built at once
        QcPlan plan = RoadJoin.join(planned, around);
        List<String> problems = new ArrayList<>();
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();

        // 1. qckit buildings and roads
        District.Result district = District.build(plan, around);
        problems.addAll(district.problems());
        Set<String> unknown = new LinkedHashSet<>();
        for (Map.Entry<BlockPos, String> entry : district.blocks().entrySet()) {
            BlockState state = BlockIds.parseLenient(entry.getValue());
            if (state == null) {
                unknown.add(entry.getValue().contains("[") ? entry.getValue().substring(0, entry.getValue().indexOf('['))
                        : entry.getValue());
                continue;
            }
            blocks.put(anchor.offset(entry.getKey()), state);
        }
        for (String id : unknown) {
            problems.add("'" + id + "' is not a block");
        }

        // 2. legacy QCSimple units
        UnitLayers legacy = new UnitLayers();
        List<BlockPos> origins = new ArrayList<>();
        for (int layer : plan.units().layerKeys()) {
            for (Map.Entry<UnitPos, QcUnit> entry : plan.units().peekLayer(layer).entrySet()) {
                Cell cell = Cell.of(entry.getKey(), layer);
                origins.add(origin(anchor, cell));
                if (!District.generates(plan, cell)) {
                    legacy.layer(layer).put(entry.getKey(), entry.getValue());
                }
            }
        }
        if (legacy.unitCount() > 0) {
            UnitExporter.export(legacy, palette, anchor, (dx, dy, dz) -> plan.mask(new Cell(dx, dy, dz)))
                    .forEach(blocks::putIfAbsent);
        }

        // 3. clears and road headroom
        Set<BlockPos> air = new LinkedHashSet<>();
        for (Map.Entry<Integer, Map<UnitPos, String>> layerEntry : plan.roadLayers().entrySet()) {
            BlockPos base = anchor.offset(0, layerEntry.getKey() * 8, 0);
            for (long column : RoadKit.networkArea(layerEntry.getValue().keySet(),
                    around.joinedRoads(plan, layerEntry.getKey())).keySet()) {
                for (int y = 1; y <= 7; y++) {
                    air.add(base.offset(RoadKit.x(column), y, RoadKit.z(column)));
                }
            }
        }
        Map<BlockPos, BlockState> ground = new LinkedHashMap<>();
        boolean noGround = plan.ground == null || plan.ground.isBlank();
        BlockState groundState = noGround ? null : BlockIds.parse(plan.ground);
        if (!noGround && groundState == null) {
            problems.add("ground block '" + plan.ground + "' is not a block");
        }
        int margin = Math.max(0, Math.min(QcPlan.MAX_CLEAR_MARGIN, plan.clearMargin));
        for (Cell cell : plan.clears()) {
            BlockPos o = origin(anchor, cell);
            int west = plan.clears().contains(cell.offset(-1, 0, 0)) ? 0 : margin;
            int east = plan.clears().contains(cell.offset(1, 0, 0)) ? 0 : margin;
            int north = plan.clears().contains(cell.offset(0, 0, -1)) ? 0 : margin;
            int south = plan.clears().contains(cell.offset(0, 0, 1)) ? 0 : margin;
            int top = plan.clears().contains(cell.offset(0, 1, 0)) ? 0 : margin;
            for (int x = -west; x <= 8 + east; x++) {
                for (int z = -north; z <= 8 + south; z++) {
                    for (int y = 1; y <= 8 + top; y++) {
                        air.add(o.offset(x, y, z));
                    }
                }
            }
            if (groundState != null && !plan.hasUnit(cell) && !plan.roads().containsKey(cell)) {
                for (int x = 0; x <= 8; x++) {
                    for (int z = 0; z <= 8; z++) {
                        ground.put(o.offset(x, 0, z), groundState);
                    }
                }
            }
        }
        air.removeAll(blocks.keySet());
        ground.keySet().removeAll(blocks.keySet());
        ground.keySet().removeAll(air);
        return new Compiled(blocks, air, ground, origins, problems, district.notes(), district.reached(),
                district.missing());
    }

    /** World origin of a cell: {@code anchor + 8 * (x, layer, z)}. */
    public static BlockPos origin(BlockPos anchor, Cell cell) {
        return anchor.offset(cell.x() * 8, cell.layer() * 8, cell.z() * 8);
    }

    /** The unit of a cell, or {@code null}. */
    public static QcUnit unit(QcPlan plan, Cell cell) {
        Map<UnitPos, QcUnit> layer = plan.units().peekLayer(cell.layer());
        return layer == null ? null : layer.get(cell.pos());
    }

    /** Whether the plan has units the legacy QCSimple exporter builds. */
    public static boolean hasLegacyUnits(QcPlan plan) {
        for (int layer : plan.units().layerKeys()) {
            for (UnitPos pos : plan.units().peekLayer(layer).keySet()) {
                if (!District.generates(plan, Cell.of(pos, layer))) {
                    return true;
                }
            }
        }
        return false;
    }
}
