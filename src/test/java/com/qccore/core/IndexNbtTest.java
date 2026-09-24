package com.qccore.core;

import com.qccore.world.QcUnitIndex;
import com.qccore.world.QcUnitRecord;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The persisted unit index: records of three dimensions survive a write/read round trip, and
 * unknown or malformed state reads back empty instead of throwing.
 */
class IndexNbtTest {

    private static final Identifier OVERWORLD = new Identifier("minecraft", "overworld");
    private static final Identifier NETHER = new Identifier("minecraft", "the_nether");
    private static final Identifier END = new Identifier("minecraft", "the_end");

    @Test
    void threeDimensionsSurviveARoundTrip() {
        QcUnitIndex index = new QcUnitIndex();
        index.put(OVERWORLD, new BlockPos(0, 64, 0), record(0, 64, 0, 0b1011, 0b0110));
        index.put(OVERWORLD, new BlockPos(8, 64, 0), record(8, 64, 0, 0b0010, 0));
        index.put(NETHER, new BlockPos(-8, 32, 16), record(-8, 32, 16, 0, 0b0001));
        index.put(END, new BlockPos(1000, 0, -1000), record(1000, 0, -1000, 0b0100, 0));

        assertEquals(4, index.totalUnits());

        QcUnitIndex read = QcUnitIndex.read(index.writeNbt(new NbtCompound()));

        assertEquals(4, read.totalUnits());
        assertEquals(index.units(OVERWORLD), read.units(OVERWORLD));
        assertEquals(index.units(NETHER), read.units(NETHER));
        assertEquals(index.units(END), read.units(END));
        assertEquals(record(-8, 32, 16, 0, 0b0001), read.units(NETHER).get(new BlockPos(-8, 32, 16)));
    }

    @Test
    void recordsKeepTheirFlags() {
        QcUnitIndex index = new QcUnitIndex();
        index.put(OVERWORLD, new BlockPos(16, 64, 16), record(16, 64, 16, 0b1111, 0b1111));
        index.put(OVERWORLD, new BlockPos(0, 64, 0), record(0, 64, 0, 0b0000, 0b0000));

        QcUnitIndex read = QcUnitIndex.read(index.writeNbt(new NbtCompound()));

        QcUnitRecord flagged = read.units(OVERWORLD).get(new BlockPos(16, 64, 16));
        assertEquals(0b1111, flagged.walls());
        assertEquals(0b1111, flagged.gates());
        QcUnit unit = flagged.toUnit();
        assertTrue(unit.hasWallN() && unit.hasWallE() && unit.hasWallS() && unit.hasWallW());
        assertTrue(unit.isGateN() && unit.isGateE() && unit.isGateS() && unit.isGateW());

        QcUnit empty = read.units(OVERWORLD).get(new BlockPos(0, 64, 0)).toUnit();
        assertFalse(empty.hasAnyN() || empty.hasAnyS() || empty.hasAnyE() || empty.hasAnyW());
    }

    @Test
    void emptyAndMissingStateReadsEmpty() {
        assertEquals(0, QcUnitIndex.read(new NbtCompound()).totalUnits());
        assertEquals(0, QcUnitIndex.read(null).totalUnits());
        assertEquals(0, QcUnitIndex.read(nbtWith(new NbtCompound())).totalUnits());
        assertEquals(0, new QcUnitIndex().units(OVERWORLD).size());
    }

    @Test
    void malformedRecordsAndDimensionsAreSkipped() {
        NbtCompound dimensions = new NbtCompound();

        // A dimension key that is not an identifier, holding a well formed record.
        dimensions.put("not a dimension id", dimensionTag(recordTag(new int[]{1, 2, 3}, 1, 0)));

        // A record without a position, and one whose position is not a triple.
        dimensions.put(NETHER.toString(), dimensionTag(recordTag(new int[0], 1, 1), recordTag(new int[]{4, 5}, 2, 2)));

        assertEquals(0, QcUnitIndex.read(nbtWith(dimensions)).totalUnits());
    }

    @Test
    void recordsOfUnknownDimensionsCanBeAddedLater() {
        QcUnitIndex index = new QcUnitIndex();
        assertTrue(index.units(END).isEmpty());

        index.put(END, new BlockPos(0, 0, 0), record(0, 0, 0, 1, 0));
        assertEquals(1, index.units(END).size());
        assertEquals(1, QcUnitIndex.read(index.writeNbt(new NbtCompound())).units(END).size());
    }

    private static QcUnitRecord record(int x, int y, int z, int walls, int gates) {
        return new QcUnitRecord(new BlockPos(x, y, z), walls, gates);
    }

    private static NbtCompound nbtWith(NbtCompound dimensions) {
        NbtCompound tag = new NbtCompound();
        tag.put("dimensions", dimensions);
        return tag;
    }

    private static NbtCompound dimensionTag(NbtCompound... records) {
        NbtList units = new NbtList();
        for (NbtCompound record : records) {
            units.add(record);
        }
        NbtCompound dimension = new NbtCompound();
        dimension.put("units", units);
        return dimension;
    }

    private static NbtCompound recordTag(int[] pos, int walls, int gates) {
        NbtCompound record = new NbtCompound();
        record.putIntArray("pos", pos);
        record.putByte("w", (byte) walls);
        record.putByte("g", (byte) gates);
        return record;
    }
}
