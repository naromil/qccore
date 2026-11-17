package com.qccore.core.plan;

import com.qccore.core.QcUnit;
import com.qccore.core.UnitExporter;
import com.qccore.core.UnitPos;
import com.qccore.core.palette.BlockPalette;
import java.util.ArrayList;
import java.util.HashSet;
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
 *   <li>Units: the masked export of {@link UnitExporter} (all units count as neighbours, each
 *       writes only its own components).</li>
 *   <li>Roads: {@link RoadKit} per layer, giving way to every column a unit block already stands on
 *       and never overwriting a unit block.</li>
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

    /** The compiled plan. {@code blocks} keeps insertion order: units, then roads. */
    public record Compiled(Map<BlockPos, BlockState> blocks, Set<BlockPos> air, Map<BlockPos, BlockState> ground,
                           List<BlockPos> unitOrigins, List<String> problems) {

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
        List<String> problems = new ArrayList<>();
        Map<BlockPos, BlockState> blocks = UnitExporter.export(plan.units(), palette, anchor,
                (dx, dy, dz) -> plan.mask(new Cell(dx, dy, dz)));

        List<BlockPos> origins = new ArrayList<>();
        for (int layer : plan.units().layerKeys()) {
            for (UnitPos pos : plan.units().peekLayer(layer).keySet()) {
                origins.add(origin(anchor, new Cell(pos.x(), layer, pos.z())));
            }
        }

        Set<BlockPos> air = new LinkedHashSet<>();
        Map<Integer, Map<UnitPos, String>> roadLayers = plan.roadLayers();
        for (Map.Entry<Integer, Map<UnitPos, String>> layerEntry : roadLayers.entrySet()) {
            int layer = layerEntry.getKey();
            BlockPos base = anchor.offset(0, layer * 8, 0);
            Map<UnitPos, RoadStyle> cells = new LinkedHashMap<>();
            for (Map.Entry<UnitPos, String> road : layerEntry.getValue().entrySet()) {
                RoadStyle style = plan.roadStyles().get(road.getValue());
                if (style == null || !style.isComplete()) {
                    problems.add("road style '" + road.getValue() + "' is missing or has an empty floor lane");
                    continue;
                }
                cells.put(road.getKey(), style);
            }
            Set<Long> columns = new HashSet<>();
            for (BlockPos pos : blocks.keySet()) {
                if (pos.getY() == base.getY()) {
                    columns.add(RoadKit.key(pos.getX() - base.getX(), pos.getZ() - base.getZ()));
                }
            }
            Map<BlockPos, String> road = RoadKit.build(cells, columns);
            Set<String> unknown = new LinkedHashSet<>();
            for (Map.Entry<BlockPos, String> entry : road.entrySet()) {
                BlockState state = BlockIds.parse(entry.getValue());
                if (state == null) {
                    unknown.add(entry.getValue());
                    continue;
                }
                blocks.putIfAbsent(base.offset(entry.getKey()), state);
            }
            for (String id : unknown) {
                problems.add("road block '" + id + "' is not a block");
            }
            for (long column : RoadKit.networkArea(cells.keySet()).keySet()) {
                for (int y = 1; y <= 7; y++) {
                    air.add(base.offset(RoadKit.x(column), y, RoadKit.z(column)));
                }
            }
        }

        Map<BlockPos, BlockState> ground = new LinkedHashMap<>();
        BlockState groundState = RoadStyle.blank(plan.ground) ? null : BlockIds.parse(plan.ground);
        if (!RoadStyle.blank(plan.ground) && groundState == null) {
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
        return new Compiled(blocks, air, ground, origins, problems);
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
}
