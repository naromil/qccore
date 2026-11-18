package com.qccore.core.nbt;

import com.qccore.core.UnitPos;
import com.qccore.core.plan.BuildingSpec;
import com.qccore.core.plan.Cell;
import com.qccore.core.plan.QcPlan;
import com.qccore.core.qc.BlockChoice;
import com.qccore.core.qc.Roles;
import com.qccore.core.qc.Templates;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * The plan wire and file format:
 * <pre>
 * {"units": {@link LayersCodec}, "unitBuildings":[{"x","y","z","b":key}],
 *  "buildings":{key: {"template","style","options":{k: v}}}, "styles":{name: {"kind","blocks":{role: id}}},
 *  "masks":[{"x","y","z","m"}], "clears":[{"x","y","z"}], "clearMargin":int, "ground":String,
 *  "roads":[{"x","y","z","s":style}]}
 * </pre>
 * {@code y} is the editor layer. Masks (legacy units only) equal to all nine components are not
 * written.
 *
 * <p>Plans from before the rework load too: their lane-list {@code "roadStyles"} become road styles,
 * and their units, which have no {@code "unitBuildings"}, become one {@code qcsimple} building
 * ({@link Templates#LEGACY}) built by the old exporter.
 */
public final class PlanCodec {

    private PlanCodec() {
    }

    public static CompoundTag toNbt(QcPlan plan) {
        CompoundTag tag = new CompoundTag();
        tag.put("units", LayersCodec.toNbt(plan.units()));
        ListTag tags = new ListTag();
        for (Map.Entry<Cell, String> entry : plan.unitBuildings().entrySet()) {
            CompoundTag unit = cell(entry.getKey());
            unit.putString("b", entry.getValue());
            tags.add(unit);
        }
        tag.put("unitBuildings", tags);
        CompoundTag buildings = new CompoundTag();
        plan.buildings().forEach((key, spec) -> buildings.put(key, spec.toNbt()));
        tag.put("buildings", buildings);
        CompoundTag styles = new CompoundTag();
        plan.styles().forEach((name, style) -> styles.put(name, style.toNbt()));
        tag.put("styles", styles);
        ListTag masks = new ListTag();
        for (Map.Entry<Cell, Integer> entry : plan.masks().entrySet()) {
            CompoundTag mask = cell(entry.getKey());
            mask.putInt("m", entry.getValue());
            masks.add(mask);
        }
        tag.put("masks", masks);
        ListTag clears = new ListTag();
        for (Cell cell : plan.clears()) {
            clears.add(cell(cell));
        }
        tag.put("clears", clears);
        tag.putInt("clearMargin", plan.clearMargin);
        tag.putString("ground", plan.ground == null ? "" : plan.ground);
        ListTag roads = new ListTag();
        for (Map.Entry<Cell, String> entry : plan.roads().entrySet()) {
            CompoundTag road = cell(entry.getKey());
            road.putString("s", entry.getValue());
            roads.add(road);
        }
        tag.put("roads", roads);
        return tag;
    }

    public static QcPlan fromNbt(CompoundTag tag) {
        QcPlan plan = new QcPlan();
        plan.setUnits(LayersCodec.fromNbt(tag.getCompound("units")));
        CompoundTag buildings = tag.getCompound("buildings");
        for (String key : buildings.getAllKeys()) {
            plan.buildings().put(key, BuildingSpec.fromNbt(buildings.getCompound(key)));
        }
        ListTag tags = tag.getList("unitBuildings", Tag.TAG_COMPOUND);
        for (int i = 0; i < tags.size(); i++) {
            CompoundTag unit = tags.getCompound(i);
            Cell cell = cell(unit);
            if (plan.hasUnit(cell)) {
                plan.unitBuildings().put(cell, unit.getString("b"));
            }
        }
        if (!tag.contains("unitBuildings") && plan.units().unitCount() > 0) {
            String key = plan.nextBuildingKey();
            plan.buildings().put(key, new BuildingSpec(Templates.LEGACY, ""));
            for (int layer : plan.units().layerKeys()) {
                for (UnitPos pos : plan.units().peekLayer(layer).keySet()) {
                    plan.unitBuildings().put(Cell.of(pos, layer), key);
                }
            }
        }
        CompoundTag styles = tag.getCompound("styles");
        for (String name : styles.getAllKeys()) {
            plan.styles().put(name, BlockChoice.fromNbt(styles.getCompound(name)));
        }
        ListTag masks = tag.getList("masks", Tag.TAG_COMPOUND);
        for (int i = 0; i < masks.size(); i++) {
            CompoundTag mask = masks.getCompound(i);
            Cell cell = cell(mask);
            if (plan.hasUnit(cell)) {
                plan.setMask(cell, mask.getInt("m"));
            }
        }
        ListTag clears = tag.getList("clears", Tag.TAG_COMPOUND);
        for (int i = 0; i < clears.size(); i++) {
            plan.clears().add(cell(clears.getCompound(i)));
        }
        plan.clearMargin = Math.max(0, Math.min(QcPlan.MAX_CLEAR_MARGIN,
                tag.contains("clearMargin") ? tag.getInt("clearMargin") : 2));
        plan.ground = tag.getString("ground");
        ListTag roads = tag.getList("roads", Tag.TAG_COMPOUND);
        for (int i = 0; i < roads.size(); i++) {
            CompoundTag road = roads.getCompound(i);
            plan.roads().put(cell(road), road.getString("s"));
        }
        CompoundTag old = tag.getCompound("roadStyles");
        for (String name : old.getAllKeys()) {
            plan.styles().putIfAbsent(name, legacyRoad(old.getCompound(name)));
        }
        return plan;
    }

    /** A pre-rework road style ({@code floor/joint/over} lane lists, {@code post} lists) as a road style. */
    static BlockChoice legacyRoad(CompoundTag tag) {
        BlockChoice style = new BlockChoice(Roles.ROAD);
        for (String role : new String[]{"floor", "joint", "over"}) {
            ListTag lanes = tag.getList(role, Tag.TAG_STRING);
            for (int i = 0; i < lanes.size() && i < 4; i++) {
                style.set(role + (i + 1), lanes.getString(i));
            }
        }
        ListTag posts = tag.getList("post", Tag.TAG_LIST);
        for (int i = 0; i < posts.size() && i < 4; i++) {
            ListTag column = posts.getList(i);
            StringBuilder ids = new StringBuilder();
            for (int j = 0; j < column.size(); j++) {
                if (!column.getString(j).isBlank()) {
                    ids.append(ids.length() == 0 ? "" : ",").append(column.getString(j));
                }
            }
            style.set("post" + (i + 1), ids.toString());
        }
        style.set("medallion", tag.getString("medallion"));
        style.set("centre", tag.getString("centre"));
        return style;
    }

    private static CompoundTag cell(Cell cell) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cell.x());
        tag.putInt("y", cell.layer());
        tag.putInt("z", cell.z());
        return tag;
    }

    private static Cell cell(CompoundTag tag) {
        return new Cell(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
    }
}
