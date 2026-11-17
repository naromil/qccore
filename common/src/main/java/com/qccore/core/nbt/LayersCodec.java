package com.qccore.core.nbt;

import com.qccore.core.QcStructure;
import com.qccore.core.UnitLayers;
import com.qccore.core.QcUnit;
import com.qccore.core.UnitPos;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * The layer wire format: {@code {"layers":[{"y":<int>,"units":[{"x":<int>,"z":<int>,"w":<byte
 * walls>,"g":<byte gates>},...]},...]}}.
 *
 * <p>Bit layout of {@code w} and {@code g}, used by the index records and the overlay renderer as
 * well: <b>bit0 = NORTH, bit1 = EAST, bit2 = SOUTH, bit3 = WEST</b>.
 */
public final class LayersCodec {

    private LayersCodec() {
    }

    public static CompoundTag toNbt(UnitLayers layers) {
        ListTag layerList = new ListTag();

        for (int dy : layers.layerKeys()) {
            CompoundTag layerTag = new CompoundTag();
            layerTag.putInt("y", dy);

            ListTag units = new ListTag();
            for (Map.Entry<UnitPos, QcUnit> unitEntry : layers.peekLayer(dy).entrySet()) {
                CompoundTag unitTag = new CompoundTag();
                unitTag.putInt("x", unitEntry.getKey().x());
                unitTag.putInt("z", unitEntry.getKey().z());
                unitTag.putByte("w", (byte) bits(unitEntry.getValue(), false));
                unitTag.putByte("g", (byte) bits(unitEntry.getValue(), true));
                units.add(unitTag);
            }

            layerTag.put("units", units);
            layerList.add(layerTag);
        }

        CompoundTag root = new CompoundTag();
        root.put("layers", layerList);
        return root;
    }

    /** Reads the layer wire format; missing keys give an empty result and malformed entries are skipped. */
    public static UnitLayers fromNbt(CompoundTag tag) {
        UnitLayers layers = new UnitLayers();
        if (tag == null) {
            return layers;
        }

        ListTag layerList = tag.getList("layers", Tag.TAG_COMPOUND);
        for (int i = 0; i < layerList.size(); i++) {
            CompoundTag layerTag = layerList.getCompound(i);
            if (!layerTag.contains("y", Tag.TAG_INT)) {
                continue;
            }

            Map<UnitPos, QcUnit> layer = layers.layer(layerTag.getInt("y"));
            ListTag units = layerTag.getList("units", Tag.TAG_COMPOUND);
            for (int j = 0; j < units.size(); j++) {
                CompoundTag unitTag = units.getCompound(j);
                if (!unitTag.contains("x", Tag.TAG_INT) || !unitTag.contains("z", Tag.TAG_INT)) {
                    continue;
                }

                QcUnit unit = new QcUnit();
                apply(unit, unitTag.getByte("w") & 0xFF, unitTag.getByte("g") & 0xFF);
                layer.put(new UnitPos(unitTag.getInt("x"), unitTag.getInt("z")), unit);
            }
        }

        return layers;
    }

    /** The wall or gate bit mask of a unit. */
    public static int bits(QcUnit unit, boolean gates) {
        int bits = 0;
        if (gates ? unit.isGateN() : unit.hasWallN()) bits |= 1;
        if (gates ? unit.isGateE() : unit.hasWallE()) bits |= 1 << 1;
        if (gates ? unit.isGateS() : unit.hasWallS()) bits |= 1 << 2;
        if (gates ? unit.isGateW() : unit.hasWallW()) bits |= 1 << 3;
        return bits;
    }

    /** Sets the wall and gate flags of a unit from the two bit masks. */
    public static void apply(QcUnit unit, int walls, int gates) {
        unit.setWallN((walls & 1) != 0);
        unit.setWallE((walls & (1 << 1)) != 0);
        unit.setWallS((walls & (1 << 2)) != 0);
        unit.setWallW((walls & (1 << 3)) != 0);

        unit.setGateN((gates & 1) != 0);
        unit.setGateE((gates & (1 << 1)) != 0);
        unit.setGateS((gates & (1 << 2)) != 0);
        unit.setGateW((gates & (1 << 3)) != 0);
    }
}
