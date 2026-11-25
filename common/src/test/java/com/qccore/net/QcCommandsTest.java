package com.qccore.net;

import com.qccore.world.QcUnitRecord;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** {@code /qc info} tells plan-built, scanned (with doors) and road records apart. */
class QcCommandsTest {

    @Test
    void summaryCountsEachKind() {
        List<QcUnitRecord> records = List.of(
                new QcUnitRecord(new BlockPos(0, 64, 0), 3, 0, QcUnitRecord.BUILDING, 0, 4242),
                new QcUnitRecord(new BlockPos(8, 64, 0), 3, 0, QcUnitRecord.BUILDING, 0, 4242),
                new QcUnitRecord(new BlockPos(16, 64, 0), 1, 0, QcUnitRecord.BUILDING, 4, 0),
                new QcUnitRecord(new BlockPos(24, 64, 0), 0, 0),
                QcUnitRecord.road(new BlockPos(0, 64, 8), null));

        assertEquals("4 building unit(s) (2 built from plans, 1 with a door), 1 road cell(s)", QcCommands.summary(records));
    }

    @Test
    void summaryOfScannedBuildingsOnly() {
        List<QcUnitRecord> records = List.of(new QcUnitRecord(new BlockPos(0, 64, 0), 3, 0));

        assertEquals("1 building unit(s), 0 road cell(s)", QcCommands.summary(records));
    }

    @Test
    void summaryOfNothingPointsAtScan() {
        assertEquals("no QC unit records (/qc scan reads standing buildings)", QcCommands.summary(List.of()));
    }
}
