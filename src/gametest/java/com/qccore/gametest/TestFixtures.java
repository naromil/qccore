package com.qccore.gametest;

import com.qccore.core.QcUnit;
import com.qccore.core.UnitLayers;
import com.qccore.core.UnitPos;
import net.minecraft.block.Block;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The shared editor fixtures and golden baseline plumbing.
 *
 * <p>The block registry is only available inside a running game, so these live with the game tests
 * and read their baselines from the mod's own resources ({@code /golden/...}).
 */
final class TestFixtures {

    private static final int CONTEXT_LINES = 3;
    private static final int MAX_DIFF_LINES_PER_SIDE = 40;

    private TestFixtures() {
    }

    /**
     * Fixture that covers every component of the export: framework, rows, columns, floors, outer
     * walls, inner walls, gates and inner columns, on three layers with neighbours, an L-shaped
     * group and an isolated unit.
     */
    static UnitLayers buildFixtureLayers() {
        UnitLayers layers = new UnitLayers();

        // Group 1 (layers 1 and 2): 2x2 unit block at (3,3)-(4,4) carrying every wall and gate case.
        Map<UnitPos, QcUnit> layer1 = flaggedSquare(3, 3);
        Map<UnitPos, QcUnit> layer2 = flaggedSquare(3, 3);

        // Group 2 (layer 3): isolated unit at (10,10), all four faces exposed.
        Map<UnitPos, QcUnit> layer3 = new LinkedHashMap<>();
        layer3.put(new UnitPos(10, 10), new QcUnit());

        // Group 3 (layer 1): L-shaped 3-unit group at (20,20),(21,20),(20,21), plain squares.
        layer1.put(new UnitPos(20, 20), new QcUnit());
        layer1.put(new UnitPos(21, 20), new QcUnit());
        layer1.put(new UnitPos(20, 21), new QcUnit());

        // Group 4 (layer 2): 2x2 group of plain squares with no conflicting walls at (14,14)-(15,15),
        // so the inner column actually gets placed. Layer 2 only, to also cover a layer that has no
        // neighbour below it.
        layer2.put(new UnitPos(14, 14), new QcUnit());
        layer2.put(new UnitPos(15, 14), new QcUnit());
        layer2.put(new UnitPos(14, 15), new QcUnit());
        layer2.put(new UnitPos(15, 15), new QcUnit());

        layers.setLayer(1, layer1);
        layers.setLayer(2, layer2);
        layers.setLayer(3, layer3);
        return layers;
    }

    /**
     * 2x2 unit block (with the given north-west corner) carrying, for both shared wall
     * orientations, one plain wall pair and one gate pair.
     */
    static Map<UnitPos, QcUnit> flaggedSquare(int dx, int dz) {
        QcUnit northWest = new QcUnit();
        northWest.setWallE(true);
        northWest.setWallS(true);

        QcUnit northEast = new QcUnit();
        northEast.setWallW(true);
        northEast.setWallS(true);
        northEast.setGateS(true);

        QcUnit southWest = new QcUnit();
        southWest.setWallN(true);
        southWest.setWallE(true);
        southWest.setGateE(true);

        QcUnit southEast = new QcUnit();
        southEast.setWallW(true);
        southEast.setGateW(true);
        southEast.setWallN(true);
        southEast.setGateN(true);

        Map<UnitPos, QcUnit> units = new LinkedHashMap<>();
        units.put(new UnitPos(dx, dz), northWest);
        units.put(new UnitPos(dx + 1, dz), northEast);
        units.put(new UnitPos(dx, dz + 1), southWest);
        units.put(new UnitPos(dx + 1, dz + 1), southEast);
        return units;
    }

    /** Two units sharing one wall: the smallest layout that places an inner wall. */
    static UnitLayers wallPair() {
        UnitLayers layers = new UnitLayers();
        QcUnit west = new QcUnit();
        west.setWallE(true);
        QcUnit east = new QcUnit();
        east.setWallW(true);
        Map<UnitPos, QcUnit> layer = layers.layer(1);
        layer.put(new UnitPos(0, 0), west);
        layer.put(new UnitPos(1, 0), east);
        return layers;
    }

    /** Reads a golden baseline from the mod resources. */
    static String readGolden(String resource) {
        try (InputStream stream = TestFixtures.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("golden baseline " + resource + " is missing from the mod resources");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("could not read golden baseline " + resource, e);
        }
    }

    /**
     * Compares a generated dump against its baseline and keeps the dump on disk so it can be
     * diffed from outside the game.
     */
    static void assertMatchesGolden(String resource, String actual, String label) {
        writeActual(label, actual);

        List<String> expected = toLines(readGolden(resource));
        List<String> observed = toLines(actual);
        if (expected.equals(observed)) {
            return;
        }

        int prefix = 0;
        while (prefix < expected.size() && prefix < observed.size() && expected.get(prefix).equals(observed.get(prefix))) {
            prefix++;
        }
        int suffix = 0;
        while (suffix < expected.size() - prefix && suffix < observed.size() - prefix
                && expected.get(expected.size() - 1 - suffix).equals(observed.get(observed.size() - 1 - suffix))) {
            suffix++;
        }

        StringBuilder message = new StringBuilder();
        message.append(label).append(" no longer matches ").append(resource).append(".\n")
                .append("Golden lines: ").append(expected.size()).append(", actual lines: ").append(observed.size())
                .append(". First difference at line ").append(prefix + 1).append(".\n");

        int contextBefore = Math.min(CONTEXT_LINES, prefix);
        for (int i = prefix - contextBefore; i < prefix; i++) {
            message.append("  ").append(expected.get(i)).append('\n');
        }
        int expectedChanged = expected.size() - suffix - prefix;
        int observedChanged = observed.size() - suffix - prefix;
        for (int i = 0; i < Math.min(expectedChanged, MAX_DIFF_LINES_PER_SIDE); i++) {
            message.append("- ").append(expected.get(prefix + i)).append('\n');
        }
        for (int i = 0; i < Math.min(observedChanged, MAX_DIFF_LINES_PER_SIDE); i++) {
            message.append("+ ").append(observed.get(prefix + i)).append('\n');
        }
        if (expectedChanged > MAX_DIFF_LINES_PER_SIDE || observedChanged > MAX_DIFF_LINES_PER_SIDE) {
            message.append("... diff truncated: ").append(expectedChanged).append(" golden-only line(s), ")
                    .append(observedChanged).append(" actual-only line(s)\n");
        }

        throw new AssertionError(message.toString());
    }

    /** Writes the dump of a failing run next to the run directory, for a plain file diff. */
    private static void writeActual(String label, String actual) {
        try {
            Path directory = Path.of("qccore-golden");
            Files.createDirectories(directory);
            Files.writeString(directory.resolve(label + "-actual.txt"), actual, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("could not write the actual dump for " + label, e);
        }
    }

    private static List<String> toLines(String text) {
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        if (normalized.endsWith("\n")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return List.of(normalized.split("\n", -1));
    }

    /** Whether any block of the box spanned by the two corners is {@code block}. */
    static boolean hasBlock(ServerWorld world, BlockPos from, BlockPos to, Block block) {
        for (int x = Math.min(from.getX(), to.getX()); x <= Math.max(from.getX(), to.getX()); x++) {
            for (int y = Math.min(from.getY(), to.getY()); y <= Math.max(from.getY(), to.getY()); y++) {
                for (int z = Math.min(from.getZ(), to.getZ()); z <= Math.max(from.getZ(), to.getZ()); z++) {
                    if (world.getBlockState(new BlockPos(x, y, z)).isOf(block)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** A sorted copy of the layer keys, so a dump never depends on the map's iteration order. */
    static List<Integer> sortedLayers(UnitLayers layers) {
        List<Integer> keys = new ArrayList<>(layers.layerKeys());
        keys.sort(Integer::compareTo);
        return keys;
    }
}
