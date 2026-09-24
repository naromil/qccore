package com.qccore.core;

import com.qccore.core.nbt.LayersCodec;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The layer wire format: the bit layout of the wall and gate masks, and a round trip over a playlist
 * of layouts.
 */
class LayersCodecTest {

    @Test
    void bitLayoutIsNorthEastSouthWest() {
        QcUnit unit = new QcUnit();
        unit.setWallN(true);
        unit.setWallE(true);
        unit.setWallW(true);
        unit.setGateS(true);
        unit.setGateE(true);

        assertEquals(0b1011, LayersCodec.bits(unit, false));
        assertEquals(0b0110, LayersCodec.bits(unit, true));

        QcUnit applied = new QcUnit();
        LayersCodec.apply(applied, 0b1011, 0b0110);
        assertTrue(applied.hasWallN() && applied.hasWallE() && applied.hasWallW() && !applied.hasWallS());
        assertTrue(applied.isGateS() && applied.isGateE() && !applied.isGateN() && !applied.isGateW());
    }

    @Test
    void playlistRoundTripsThroughTheWireFormat() {
        for (UnitLayers layers : playlist()) {
            UnitLayers read = LayersCodec.fromNbt(LayersCodec.toNbt(layers));
            assertEquals(describe(layers), describe(read));
        }
    }

    @Test
    void missingAndMalformedEntriesAreTolerated() {
        assertEquals(0, LayersCodec.fromNbt(new NbtCompound()).unitCount());
        assertEquals(0, LayersCodec.fromNbt(null).unitCount());

        NbtCompound tag = new NbtCompound();
        NbtList layerList = new NbtList();

        NbtCompound withoutLayerIndex = new NbtCompound();
        NbtCompound oneUnit = new NbtCompound();
        oneUnit.putInt("x", 3);
        oneUnit.putInt("z", 4);
        NbtList units = new NbtList();
        units.add(oneUnit);
        withoutLayerIndex.put("units", units);
        layerList.add(withoutLayerIndex);

        NbtCompound valid = new NbtCompound();
        valid.putInt("y", 2);
        NbtList validUnits = new NbtList();
        NbtCompound incomplete = new NbtCompound();
        incomplete.putInt("x", 1);
        validUnits.add(incomplete);
        NbtCompound complete = new NbtCompound();
        complete.putInt("x", 5);
        complete.putInt("z", 6);
        complete.putByte("w", (byte) 3);
        complete.putByte("g", (byte) 4);
        validUnits.add(complete);
        valid.put("units", validUnits);
        layerList.add(valid);

        tag.put("layers", layerList);

        UnitLayers read = LayersCodec.fromNbt(tag);
        assertEquals(1, read.unitCount());
        assertEquals(1, read.layerKeys().size());
        QcUnit unit = read.peekLayer(2).get(new UnitPos(5, 6));
        assertEquals(3, LayersCodec.bits(unit, false));
        assertEquals(4, LayersCodec.bits(unit, true));
    }

    /** A playlist of layouts that covers the empty case, several layers and every flag. */
    private static List<UnitLayers> playlist() {
        List<UnitLayers> playlist = new ArrayList<>();
        playlist.add(new UnitLayers());

        UnitLayers single = new UnitLayers();
        single.layer(1).put(new UnitPos(0, 0), new QcUnit());
        playlist.add(single);

        UnitLayers layers = new UnitLayers();
        Map<UnitPos, QcUnit> first = layers.layer(1);
        QcUnit plain = new QcUnit();
        first.put(new UnitPos(-2, 3), plain);
        QcUnit flagged = new QcUnit();
        flagged.setWallN(true);
        flagged.setGateW(true);
        flagged.setGateS(true);
        first.put(new UnitPos(4, -5), flagged);

        Map<UnitPos, QcUnit> second = layers.layer(3);
        QcUnit gates = new QcUnit();
        gates.setWallE(true);
        gates.setWallS(true);
        gates.setGateN(true);
        second.put(new UnitPos(7, 7), gates);

        Map<UnitPos, QcUnit> third = layers.layer(9);
        third.put(new UnitPos(0, 0), new QcUnit());
        third.put(new UnitPos(1, 0), new QcUnit());
        playlist.add(layers);

        return playlist;
    }

    private static String describe(UnitLayers layers) {
        StringBuilder text = new StringBuilder();
        for (int layer : layers.layerKeys()) {
            text.append("L").append(layer).append(':');
            for (Map.Entry<UnitPos, QcUnit> entry : layers.peekLayer(layer).entrySet()) {
                QcUnit unit = entry.getValue();
                text.append(' ').append(entry.getKey().x()).append(',').append(entry.getKey().z())
                        .append('[').append(LayersCodec.bits(unit, false)).append('/').append(LayersCodec.bits(unit, true)).append(']');
            }
            text.append('\n');
        }
        return text.toString();
    }

    @Test
    void wireFormatHoldsOneListPerLayer() {
        NbtCompound tag = LayersCodec.toNbt(playlist().get(2));
        NbtList layers = tag.getList("layers", NbtElement.COMPOUND_TYPE);
        assertEquals(3, layers.size());
        assertEquals(1, layers.getCompound(0).getInt("y"));
        assertEquals(3, layers.getCompound(1).getInt("y"));
        assertEquals(9, layers.getCompound(2).getInt("y"));

        NbtList units = layers.getCompound(0).getList("units", NbtElement.COMPOUND_TYPE);
        assertEquals(2, units.size());
        NbtCompound first = units.getCompound(0);
        assertEquals(-2, first.getInt("x"));
        assertEquals(3, first.getInt("z"));
        assertEquals(NbtElement.BYTE_TYPE, first.get("w").getType());
    }

    @Test
    void layerEntriesKeepTheirInsertionOrder() {
        UnitLayers layers = new UnitLayers();
        Map<UnitPos, QcUnit> layer = layers.layer(1);
        layer.put(new UnitPos(9, 9), new QcUnit());
        layer.put(new UnitPos(1, 1), new QcUnit());
        layer.put(new UnitPos(5, 5), new QcUnit());

        NbtList units = LayersCodec.toNbt(layers).getList("layers", NbtElement.COMPOUND_TYPE)
                .getCompound(0).getList("units", NbtElement.COMPOUND_TYPE);
        assertEquals(9, units.getCompound(0).getInt("x"));
        assertEquals(1, units.getCompound(1).getInt("x"));
        assertEquals(5, units.getCompound(2).getInt("x"));
    }
}
