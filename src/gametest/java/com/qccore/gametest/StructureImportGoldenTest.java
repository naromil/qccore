package com.qccore.gametest;

import com.qccore.core.QcUnit;
import com.qccore.core.UnitBlockConverter;
import com.qccore.core.UnitLayers;
import com.qccore.core.UnitPos;
import com.qccore.core.nbt.StructureNbt;
import com.qccore.core.palette.BlockPalette;
import com.qccore.core.palette.Defaults;
import com.qccore.core.palette.QcComponent;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Golden baseline for the NBT import path: layers -> block map -> root compound tag -> block map -> layers.
 *
 * <p>The test exports the shared fixture, feeds the resulting root compound tag back through
 * {@link UnitBlockConverter#convertBlockMapToLayers}, and compares a sorted dump of the layers it
 * gets back plus the block configuration the import detected against
 * {@code /golden/unit-import-baseline.txt}.
 *
 * <p>Extracted structures are dumped as their size triple and path only: the palette ordering
 * inside an extracted tag is not deterministic and must not be pinned. Like the export baseline this
 * is a game test, because the import needs the block registry.
 */
public class StructureImportGoldenTest implements FabricGameTest {

    private static final String BASELINE = "/golden/unit-import-baseline.txt";

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void importDumpMatchesGoldenBaseline(TestContext ctx) {
        String dump = generateImportDump();

        long lineCount = dump.lines().count();
        if (lineCount <= 10) {
            throw new AssertionError("Import dump is suspiciously small: " + lineCount + " line(s)");
        }
        if (!dump.contains("walls=1") || !dump.contains("gates=1")) {
            throw new AssertionError("Import dump is missing the wall and gate flags");
        }
        if (!dump.contains("innerWall=7x7x3") || !dump.contains("outerWall=7x7x3") || !dump.contains("innerColumn=3x7x3")) {
            throw new AssertionError("Import dump is missing the extracted structures");
        }

        TestFixtures.assertMatchesGolden(BASELINE, dump, "import");
        ctx.complete();
    }

    private static String generateImportDump() {
        // Deterministic setup: the built-in defaults, no custom ids and no custom structures.
        BlockPalette palette = new BlockPalette();
        Defaults.applyTo(palette);

        NbtCompound root = StructureNbt.write(
                UnitBlockConverter.convertLayersToBlockMap(TestFixtures.buildFixtureLayers(), palette, BlockPos.ORIGIN));
        if (root == null) {
            throw new AssertionError("The export produced no blocks at all");
        }
        Map<BlockPos, BlockState> readBack = StructureNbt.read(root);
        UnitLayers layers = UnitBlockConverter.convertBlockMapToLayers(readBack, palette);

        StringBuilder dump = new StringBuilder();

        // One line per unit, layers ascending and units sorted by (x, z).
        for (int layerKey : TestFixtures.sortedLayers(layers)) {
            Map<UnitPos, QcUnit> layer = layers.peekLayer(layerKey);
            List<UnitPos> positions = layer.keySet().stream()
                    .sorted(Comparator.comparingInt(UnitPos::x).thenComparingInt(UnitPos::z))
                    .toList();
            for (UnitPos pos : positions) {
                QcUnit unit = layer.get(pos);
                dump.append("L").append(layerKey).append(" U").append(pos.x()).append(',').append(pos.z())
                        .append(" walls=")
                        .append(flag(unit.hasWallN())).append(flag(unit.hasWallW()))
                        .append(flag(unit.hasWallS())).append(flag(unit.hasWallE()))
                        .append(" gates=")
                        .append(flag(unit.isGateN())).append(flag(unit.isGateW()))
                        .append(flag(unit.isGateS())).append(flag(unit.isGateE()))
                        .append('\n');
            }
        }

        // One line per component, in palette order.
        dump.append("framework=").append(palette.frameworkBlock().getRawId()).append('\n');
        dump.append("row=").append(palette.rowBlock().getRawId()).append('\n');
        dump.append("column=").append(palette.columnBlock().getRawId()).append('\n');
        dump.append("floor=").append(palette.floorBlock().getRawId()).append('\n');
        dump.append("wall=").append(palette.wallBlock().getRawId()).append('\n');
        dump.append("innerWall=").append(describeStructure(palette.innerWall())).append('\n');
        dump.append("gate=").append(describeStructure(palette.gate())).append('\n');
        dump.append("outerWall=").append(describeStructure(palette.outerWall())).append('\n');
        dump.append("innerColumn=").append(describeStructure(palette.innerColumn())).append('\n');

        return dump.toString();
    }

    private static String describeStructure(QcComponent component) {
        if (component.getStructure() == null) {
            return "EMPTY";
        }
        return component.getSizeX() + "x" + component.getSizeY() + "x" + component.getSizeZ() + " " + component.getPath();
    }

    private static String flag(boolean value) {
        return value ? "1" : "0";
    }
}
