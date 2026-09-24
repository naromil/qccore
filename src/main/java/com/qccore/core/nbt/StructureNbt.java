package com.qccore.core.nbt;

import com.qccore.core.QcStructure;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * The vanilla structure tag format: {@code {DataVersion,size,palette,blocks,entities}}.
 *
 * <p>{@code size} and the per-block {@code pos} are written as int lists, the shape the desktop
 * tool reads and writes. On the way in both the list and the int-array form are accepted, so files
 * written by structure blocks load as well.
 */
public final class StructureNbt {

    private StructureNbt() {
    }

    /**
     * Converts a block map into a complete root compound tag, rebasing every position on the
     * minimum corner. Returns {@code null} when the map is empty, so callers can report
     * "nothing to save" instead of writing a degenerate structure.
     */
    public static NbtCompound write(Map<BlockPos, BlockState> blockMap) {
        if (blockMap.isEmpty()) {
            return null;
        }

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        for (BlockPos pos : blockMap.keySet()) {
            minX = min(pos.getX(), minX);
            minY = min(pos.getY(), minY);
            minZ = min(pos.getZ(), minZ);
            maxX = max(pos.getX(), maxX);
            maxY = max(pos.getY(), maxY);
            maxZ = max(pos.getZ(), maxZ);
        }

        NbtList size = new NbtList();
        size.add(NbtInt.of(maxX - minX + 1));
        size.add(NbtInt.of(maxY - minY + 1));
        size.add(NbtInt.of(maxZ - minZ + 1));

        LinkedHashMap<BlockState, Integer> palette = new LinkedHashMap<>();
        NbtList blocks = new NbtList();

        for (Map.Entry<BlockPos, BlockState> pointEntry : blockMap.entrySet()) {
            BlockPos pos = pointEntry.getKey();

            Integer stateIndex = palette.get(pointEntry.getValue());
            if (stateIndex == null) {
                stateIndex = palette.size();
                palette.put(pointEntry.getValue(), stateIndex);
            }

            NbtCompound blockCompound = new NbtCompound();
            NbtList posTag = new NbtList();
            posTag.add(NbtInt.of(pos.getX() - minX));
            posTag.add(NbtInt.of(pos.getY() - minY));
            posTag.add(NbtInt.of(pos.getZ() - minZ));
            blockCompound.put("pos", posTag);
            blockCompound.putInt("state", stateIndex); // Map back to the palette
            blocks.add(blockCompound);
        }

        NbtList paletteTag = new NbtList();
        for (BlockState state : palette.keySet()) {
            paletteTag.add(NbtHelper.fromBlockState(state));
        }

        // 4. Assemble Root
        NbtCompound rootCompound = new NbtCompound();
        rootCompound.put("size", size);
        rootCompound.put("palette", paletteTag);
        rootCompound.put("blocks", blocks);
        rootCompound.put("entities", new NbtList()); // Leave entities empty for now
        NbtHelper.putDataVersion(rootCompound);

        return rootCompound;
    }

    /** Converts a root compound tag back into a map of block positions to block states. */
    public static Map<BlockPos, BlockState> read(NbtCompound rootCompoundTag) {
        if (rootCompoundTag == null) {
            throw new IllegalArgumentException("rootCompoundTag cannot be null.");
        }

        Map<BlockPos, BlockState> blockMap = new LinkedHashMap<>();

        NbtList palette = rootCompoundTag.getList("palette", NbtElement.COMPOUND_TYPE);
        NbtList blocks = rootCompoundTag.getList("blocks", NbtElement.COMPOUND_TYPE);

        for (int i = 0; i < blocks.size(); i++) {
            NbtCompound block = blocks.getCompound(i);
            int[] pos = readTriple(block, "pos");
            int stateIndex = block.getInt("state");
            if (pos == null || stateIndex < 0 || stateIndex >= palette.size()) {
                continue;
            }

            BlockState state = NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(), palette.getCompound(stateIndex));
            blockMap.put(new BlockPos(pos[0], pos[1], pos[2]), state);
        }

        return blockMap;
    }

    /** Reads a structure tag into a detached structure; positions stay as they are written. */
    public static QcStructure parseStructure(NbtCompound tag) {
        if (!tag.contains("size")) {
            throw new IllegalArgumentException("missing 'size'");
        }
        int[] size = readTriple(tag, "size");
        if (size == null) {
            throw new IllegalArgumentException("'size' must hold three ints");
        }

        NbtList palette = tag.getList("palette", NbtElement.COMPOUND_TYPE);
        NbtList blocks = tag.getList("blocks", NbtElement.COMPOUND_TYPE);

        List<QcStructure.Entry> entries = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            NbtCompound block = blocks.getCompound(i);
            int[] pos = readTriple(block, "pos");
            int stateIndex = block.getInt("state");
            if (pos == null || stateIndex < 0 || stateIndex >= palette.size()) {
                continue;
            }

            BlockState state = NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(), palette.getCompound(stateIndex));
            entries.add(new QcStructure.Entry(pos[0], pos[1], pos[2], state));
        }

        return new QcStructure(size[0], size[1], size[2], entries);
    }

    /** Writes a detached structure in the same shape as {@link #write}. */
    public static NbtCompound writeStructure(QcStructure structure) {
        if (structure == null) {
            return null;
        }
        Map<BlockPos, BlockState> blockMap = new LinkedHashMap<>();
        for (QcStructure.Entry entry : structure.entries()) {
            blockMap.put(new BlockPos(entry.x(), entry.y(), entry.z()), entry.state());
        }
        return write(blockMap);
    }

    /**
     * Whether a file can be used as a component: {@code null} means yes, otherwise the message that
     * explains what is wrong with it.
     */
    public static String validateComponentFile(NbtCompound tag) {
        if (tag == null) {
            return "the file holds no structure";
        }
        if (!tag.contains("size")) {
            return "missing 'size'";
        }
        int[] size = readTriple(tag, "size");
        if (size == null) {
            return "'size' must hold three ints";
        }

        NbtList palette = tag.getList("palette", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < palette.size(); i++) {
            String name = palette.getCompound(i).getString("Name");
            Identifier identifier = Identifier.tryParse(name);
            if (identifier == null || Registries.BLOCK.getOrEmpty(identifier).isEmpty()) {
                return "unknown block '" + name + "' in palette";
            }
        }

        return null;
    }

    public static NbtCompound readFile(Path file) {
        try {
            return NbtIo.readCompressed(file.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void writeFile(NbtCompound tag, Path file) {
        try {
            NbtIo.writeCompressed(tag, file.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Reads a three int triple, written either as an int list or as an int array. */
    private static int[] readTriple(NbtCompound tag, String key) {
        if (tag.contains(key, NbtElement.INT_ARRAY_TYPE)) {
            int[] array = tag.getIntArray(key);
            return array.length == 3 ? array : null;
        }
        NbtList list = tag.getList(key, NbtElement.INT_TYPE);
        if (list.size() != 3) {
            return null;
        }
        return new int[]{list.getInt(0), list.getInt(1), list.getInt(2)};
    }
}
