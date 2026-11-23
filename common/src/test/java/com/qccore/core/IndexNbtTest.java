package com.qccore.core;

import com.qccore.world.QcUnitIndex;
import com.qccore.world.QcUnitRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The persisted unit index: records of three dimensions survive a write/read round trip, and
 * unknown or malformed state reads back empty instead of throwing.
 */
class IndexNbtTest {

    private static final ResourceLocation OVERWORLD = new ResourceLocation("minecraft", "overworld");
    private static final ResourceLocation NETHER = new ResourceLocation("minecraft", "the_nether");
    private static final ResourceLocation END = new ResourceLocation("minecraft", "the_end");

    @Test
    void threeDimensionsSurviveARoundTrip() {
        QcUnitIndex index = new QcUnitIndex();
        index.put(OVERWORLD, new BlockPos(0, 64, 0), record(0, 64, 0, 0b1011, 0b0110));
        index.put(OVERWORLD, new BlockPos(8, 64, 0), record(8, 64, 0, 0b0010, 0));
        index.put(NETHER, new BlockPos(-8, 32, 16), record(-8, 32, 16, 0, 0b0001));
        index.put(END, new BlockPos(1000, 0, -1000), record(1000, 0, -1000, 0b0100, 0));

        assertEquals(4, index.totalUnits());

        QcUnitIndex read = QcUnitIndex.read(index.save(new CompoundTag()));

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

        QcUnitIndex read = QcUnitIndex.read(index.save(new CompoundTag()));

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
    void scannedKindAndDoorsSurviveAndOldRecordsReadAsBuildings() {
        QcUnitIndex index = new QcUnitIndex();
        BlockPos road = new BlockPos(0, 64, 8);
        BlockPos house = new BlockPos(0, 64, 0);
        index.put(OVERWORLD, road, new QcUnitRecord(road, 0, 0, QcUnitRecord.ROAD, 0));
        index.put(OVERWORLD, house, new QcUnitRecord(house, 0b0010, 0, QcUnitRecord.BUILDING, 0b0100));

        CompoundTag saved = index.save(new CompoundTag());
        QcUnitIndex read = QcUnitIndex.read(saved);
        assertEquals(QcUnitRecord.ROAD, read.units(OVERWORLD).get(road).kind());
        assertFalse(read.units(OVERWORLD).get(road).isBuilding());
        assertEquals(0b0100, read.units(OVERWORLD).get(house).doors());

        // A record written before kind/doors existed has neither key and reads as a door-less building.
        CompoundTag old = recordTag(new int[]{8, 64, 0}, 1, 0);
        assertFalse(old.contains("k") || old.contains("d"));
        QcUnitRecord legacy = QcUnitIndex.read(nbtWith(dimensionsWith(OVERWORLD, old))).units(OVERWORLD)
                .get(new BlockPos(8, 64, 0));
        assertTrue(legacy.isBuilding());
        assertEquals(0, legacy.doors());
        assertFalse(record(8, 64, 0, 1, 0).toNbt().contains("k"));
    }

    @Test
    void anAppliedPartKeepsItsDesignSignature() {
        QcUnitIndex index = new QcUnitIndex();
        BlockPos part = new BlockPos(0, 64, 0);
        BlockPos plain = new BlockPos(8, 64, 0);
        index.put(OVERWORLD, part, new QcUnitRecord(part, 0b0010, 0, QcUnitRecord.BUILDING, 0, -123456789));
        index.put(OVERWORLD, plain, new QcUnitRecord(plain, 0, 0));

        CompoundTag saved = index.save(new CompoundTag());
        QcUnitIndex read = QcUnitIndex.read(saved);
        assertEquals(-123456789, read.units(OVERWORLD).get(part).part());
        assertEquals(0, read.units(OVERWORLD).get(plain).part());
        assertEquals(new QcUnitRecord(plain, 0, 0).toNbt(), read.units(OVERWORLD).get(plain).toNbt(), "no p key when there is no design");
    }

    @Test
    void emptyAndMissingStateReadsEmpty() {
        assertEquals(0, QcUnitIndex.read(new CompoundTag()).totalUnits());
        assertEquals(0, QcUnitIndex.read(null).totalUnits());
        assertEquals(0, QcUnitIndex.read(nbtWith(new CompoundTag())).totalUnits());
        assertEquals(0, new QcUnitIndex().units(OVERWORLD).size());
    }

    @Test
    void malformedRecordsAndDimensionsAreSkipped() {
        CompoundTag dimensions = new CompoundTag();

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
        assertEquals(1, QcUnitIndex.read(index.save(new CompoundTag())).units(END).size());
    }

    private static QcUnitRecord record(int x, int y, int z, int walls, int gates) {
        return new QcUnitRecord(new BlockPos(x, y, z), walls, gates);
    }

    private static CompoundTag nbtWith(CompoundTag dimensions) {
        CompoundTag tag = new CompoundTag();
        tag.put("dimensions", dimensions);
        return tag;
    }

    private static CompoundTag dimensionTag(CompoundTag... records) {
        ListTag units = new ListTag();
        for (CompoundTag record : records) {
            units.add(record);
        }
        CompoundTag dimension = new CompoundTag();
        dimension.put("units", units);
        return dimension;
    }

    private static CompoundTag recordTag(int[] pos, int walls, int gates) {
        CompoundTag record = new CompoundTag();
        record.putIntArray("pos", pos);
        record.putByte("w", (byte) walls);
        record.putByte("g", (byte) gates);
        return record;
    }

    private static CompoundTag dimensionsWith(ResourceLocation dimension, CompoundTag record) {
        CompoundTag dimensions = new CompoundTag();
        dimensions.put(dimension.toString(), dimensionTag(record));
        return dimensions;
    }
}
