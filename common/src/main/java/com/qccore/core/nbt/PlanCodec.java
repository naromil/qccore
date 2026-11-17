package com.qccore.core.nbt;

import com.qccore.core.plan.Cell;
import com.qccore.core.plan.QcPlan;
import com.qccore.core.plan.RoadStyle;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * The plan wire and file format:
 * <pre>
 * {"units": {@link LayersCodec}, "masks":[{"x","y","z","m"}], "clears":[{"x","y","z"}],
 *  "clearMargin":int, "ground":String, "roads":[{"x","y","z","s":style}], "roadStyles":{name: style}}
 * </pre>
 * {@code y} is the editor layer. Masks equal to all nine components are not written.
 */
public final class PlanCodec {

    private PlanCodec() {
    }

    public static CompoundTag toNbt(QcPlan plan) {
        CompoundTag tag = new CompoundTag();
        tag.put("units", LayersCodec.toNbt(plan.units()));
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
        CompoundTag styles = new CompoundTag();
        for (Map.Entry<String, RoadStyle> entry : plan.roadStyles().entrySet()) {
            styles.put(entry.getKey(), entry.getValue().toNbt());
        }
        tag.put("roadStyles", styles);
        return tag;
    }

    public static QcPlan fromNbt(CompoundTag tag) {
        QcPlan plan = new QcPlan();
        plan.setUnits(LayersCodec.fromNbt(tag.getCompound("units")));
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
        CompoundTag styles = tag.getCompound("roadStyles");
        for (String name : styles.getAllKeys()) {
            plan.roadStyles().put(name, RoadStyle.fromNbt(styles.getCompound(name)));
        }
        return plan;
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
