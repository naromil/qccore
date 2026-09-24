package com.qccore.gametest;

import com.qccore.core.UnitBlockConverter;
import com.qccore.core.nbt.StructureNbt;
import com.qccore.core.palette.BlockPalette;
import com.qccore.core.palette.Defaults;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Golden baseline for the NBT export path: layers -> block map -> root compound tag.
 *
 * <p>The test exports a fixed fixture through {@link UnitBlockConverter#convertLayersToBlockMap}
 * and {@link StructureNbt#write}, renders the resulting blocks as sorted {@code "x y z blockName"}
 * text and compares it against {@code /golden/unit-export-baseline.txt}. A mismatch means the export
 * behavior changed, so the difference must be explained before the baseline is touched.
 *
 * <p>This is a game test because the export needs the block registry, which only exists inside a
 * running game. The generated dump is kept in the run directory as
 * {@code qccore-golden/export-actual.txt} so it can be diffed outside the game.
 */
public class StructureExportGoldenTest implements FabricGameTest {

    private static final String BASELINE = "/golden/unit-export-baseline.txt";

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void exportDumpMatchesGoldenBaseline(TestContext ctx) {
        String dump = generateExportDump();

        long lineCount = dump.lines().count();
        if (lineCount <= 1000) {
            throw new AssertionError("Export dump is suspiciously small: " + lineCount + " line(s)");
        }
        if (!dump.contains("minecraft:polished_deepslate")) {
            throw new AssertionError("Export dump is missing the framework blocks");
        }
        if (!dump.contains("minecraft:deepslate_bricks")) {
            throw new AssertionError("Export dump is missing the inner/outer wall and gate blocks");
        }

        TestFixtures.assertMatchesGolden(BASELINE, dump, "export");
        ctx.complete();
    }

    private static String generateExportDump() {
        // Deterministic setup: the built-in defaults, no custom ids and no custom structures.
        BlockPalette palette = new BlockPalette();
        Defaults.applyTo(palette);

        NbtCompound root = StructureNbt.write(
                UnitBlockConverter.convertLayersToBlockMap(TestFixtures.buildFixtureLayers(), palette, BlockPos.ORIGIN));
        if (root == null) {
            throw new AssertionError("The export produced no blocks at all");
        }

        NbtList paletteTag = root.getList("palette", NbtElement.COMPOUND_TYPE);
        NbtList blocks = root.getList("blocks", NbtElement.COMPOUND_TYPE);

        List<ExportedBlock> exported = new ArrayList<>(blocks.size());
        for (int i = 0; i < blocks.size(); i++) {
            NbtCompound block = blocks.getCompound(i);
            NbtList pos = block.getList("pos", NbtElement.INT_TYPE);
            int stateIndex = block.getInt("state");
            exported.add(new ExportedBlock(pos.getInt(0), pos.getInt(1), pos.getInt(2),
                    paletteTag.getCompound(stateIndex).getString("Name")));
        }

        // Sort explicitly: the dump must not depend on hash map iteration order.
        exported.sort(Comparator.comparingInt(ExportedBlock::x)
                .thenComparingInt(ExportedBlock::y)
                .thenComparingInt(ExportedBlock::z)
                .thenComparing(ExportedBlock::name));

        StringBuilder dump = new StringBuilder();
        for (ExportedBlock block : exported) {
            dump.append(block.x()).append(' ')
                    .append(block.y()).append(' ')
                    .append(block.z()).append(' ')
                    .append(block.name()).append('\n');
        }
        return dump.toString();
    }

    private record ExportedBlock(int x, int y, int z, String name) {
    }
}
