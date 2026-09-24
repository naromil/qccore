package com.qccore.gametest;

import com.qccore.core.QcStructure;
import com.qccore.core.nbt.PaletteCodec;
import com.qccore.core.palette.BlockPalette;
import com.qccore.core.palette.Defaults;
import com.qccore.core.palette.QcComponent;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;

/**
 * The palette wire format: the five block ids and the four default structures survive a round trip,
 * and an unconfigured palette comes back empty.
 *
 * <p>A game test, because the structures carry block states and those need the block registry.
 */
public class PaletteCodecTest implements FabricGameTest {

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void defaultPaletteRoundTrips(TestContext ctx) {
        BlockPalette palette = new BlockPalette();
        Defaults.applyTo(palette);

        BlockPalette read = PaletteCodec.fromNbt(PaletteCodec.toNbt(palette));

        assertIds(palette, read);
        assertStructure("inner wall", palette.innerWall(), read.innerWall(), 7, 7, 3);
        assertStructure("gate", palette.gate(), read.gate(), 7, 7, 3);
        assertStructure("outer wall", palette.outerWall(), read.outerWall(), 7, 7, 3);
        assertStructure("inner column", palette.innerColumn(), read.innerColumn(), 3, 7, 3);

        ctx.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void unconfiguredPaletteRoundTripsEmpty(TestContext ctx) {
        BlockPalette read = PaletteCodec.fromNbt(PaletteCodec.toNbt(new BlockPalette()));

        if (read.isConfigured()) {
            throw new AssertionError("an unconfigured palette came back configured");
        }
        if (!read.innerWall().isEmpty() || !read.gate().isEmpty() || !read.outerWall().isEmpty()
                || !read.innerColumn().isEmpty()) {
            throw new AssertionError("an unconfigured palette came back with structures");
        }
        if (!"[Not Configured]".equals(read.innerWall().getPath())) {
            throw new AssertionError("unexpected path for an absent structure: " + read.innerWall().getPath());
        }

        ctx.complete();
    }

    private static void assertIds(BlockPalette expected, BlockPalette actual) {
        require(expected.frameworkBlock().getRawId().equals(actual.frameworkBlock().getRawId()), "framework");
        require(expected.rowBlock().getRawId().equals(actual.rowBlock().getRawId()), "row");
        require(expected.columnBlock().getRawId().equals(actual.columnBlock().getRawId()), "column");
        require(expected.wallBlock().getRawId().equals(actual.wallBlock().getRawId()), "wall");
        require(expected.floorBlock().getRawId().equals(actual.floorBlock().getRawId()), "floor");
    }

    private static void assertStructure(String name, QcComponent expected, QcComponent actual,
                                        int sizeX, int sizeY, int sizeZ) {
        QcStructure structure = actual.getStructure();
        if (structure == null) {
            throw new AssertionError(name + " structure did not survive the round trip");
        }
        if (structure.sizeX() != sizeX || structure.sizeY() != sizeY || structure.sizeZ() != sizeZ) {
            throw new AssertionError(name + " structure is " + structure.sizeX() + "x" + structure.sizeY() + "x"
                    + structure.sizeZ() + " instead of " + sizeX + "x" + sizeY + "x" + sizeZ);
        }
        if (!expected.getStructure().entries().equals(structure.entries())) {
            throw new AssertionError(name + " structure entries changed: " + expected.getStructure().entries().size()
                    + " expected, " + structure.entries().size() + " read back");
        }
        if (!"[Sent by client]".equals(actual.getPath())) {
            throw new AssertionError(name + " structure path is " + actual.getPath());
        }
    }

    private static void require(boolean condition, String name) {
        if (!condition) {
            throw new AssertionError(name + " block id did not survive the round trip");
        }
    }
}
