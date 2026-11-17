package com.qccore.core.nbt;

import com.qccore.core.plan.Cell;
import com.qccore.core.plan.QcPlan;
import com.qccore.core.plan.RoadStyle;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.util.Map;

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

    public static NbtCompound toNbt(QcPlan plan) {
        NbtCompound tag = new NbtCompound();
        tag.put("units", LayersCodec.toNbt(plan.units()));
        NbtList masks = new NbtList();
        for (Map.Entry<Cell, Integer> entry : plan.masks().entrySet()) {
            NbtCompound mask = cell(entry.getKey());
            mask.putInt("m", entry.getValue());
            masks.add(mask);
        }
        tag.put("masks", masks);
        NbtList clears = new NbtList();
        for (Cell cell : plan.clears()) {
            clears.add(cell(cell));
        }
        tag.put("clears", clears);
        tag.putInt("clearMargin", plan.clearMargin);
        tag.putString("ground", plan.ground == null ? "" : plan.ground);
        NbtList roads = new NbtList();
        for (Map.Entry<Cell, String> entry : plan.roads().entrySet()) {
            NbtCompound road = cell(entry.getKey());
            road.putString("s", entry.getValue());
            roads.add(road);
        }
        tag.put("roads", roads);
        NbtCompound styles = new NbtCompound();
        for (Map.Entry<String, RoadStyle> entry : plan.roadStyles().entrySet()) {
            styles.put(entry.getKey(), entry.getValue().toNbt());
        }
        tag.put("roadStyles", styles);
        return tag;
    }

    public static QcPlan fromNbt(NbtCompound tag) {
        QcPlan plan = new QcPlan();
        plan.setUnits(LayersCodec.fromNbt(tag.getCompound("units")));
        NbtList masks = tag.getList("masks", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < masks.size(); i++) {
            NbtCompound mask = masks.getCompound(i);
            Cell cell = cell(mask);
            if (plan.hasUnit(cell)) {
                plan.setMask(cell, mask.getInt("m"));
            }
        }
        NbtList clears = tag.getList("clears", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < clears.size(); i++) {
            plan.clears().add(cell(clears.getCompound(i)));
        }
        plan.clearMargin = Math.max(0, Math.min(QcPlan.MAX_CLEAR_MARGIN,
                tag.contains("clearMargin") ? tag.getInt("clearMargin") : 2));
        plan.ground = tag.getString("ground");
        NbtList roads = tag.getList("roads", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < roads.size(); i++) {
            NbtCompound road = roads.getCompound(i);
            plan.roads().put(cell(road), road.getString("s"));
        }
        NbtCompound styles = tag.getCompound("roadStyles");
        for (String name : styles.getKeys()) {
            plan.roadStyles().put(name, RoadStyle.fromNbt(styles.getCompound(name)));
        }
        return plan;
    }

    private static NbtCompound cell(Cell cell) {
        NbtCompound tag = new NbtCompound();
        tag.putInt("x", cell.x());
        tag.putInt("y", cell.layer());
        tag.putInt("z", cell.z());
        return tag;
    }

    private static Cell cell(NbtCompound tag) {
        return new Cell(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
    }
}
