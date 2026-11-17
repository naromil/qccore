package com.qccore.core.nbt;

import com.qccore.core.QcStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
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
    public static CompoundTag write(Map<BlockPos, BlockState> blockMap) {
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

        ListTag size = new ListTag();
        size.add(IntTag.valueOf(maxX - minX + 1));
        size.add(IntTag.valueOf(maxY - minY + 1));
        size.add(IntTag.valueOf(maxZ - minZ + 1));

        LinkedHashMap<BlockState, Integer> palette = new LinkedHashMap<>();
        ListTag blocks = new ListTag();

        for (Map.Entry<BlockPos, BlockState> pointEntry : blockMap.entrySet()) {
            BlockPos pos = pointEntry.getKey();

            Integer stateIndex = palette.get(pointEntry.getValue());
            if (stateIndex == null) {
                stateIndex = palette.size();
                palette.put(pointEntry.getValue(), stateIndex);
            }

            CompoundTag blockCompound = new CompoundTag();
            ListTag posTag = new ListTag();
            posTag.add(IntTag.valueOf(pos.getX() - minX));
            posTag.add(IntTag.valueOf(pos.getY() - minY));
            posTag.add(IntTag.valueOf(pos.getZ() - minZ));
            blockCompound.put("pos", posTag);
            blockCompound.putInt("state", stateIndex); // Map back to the palette
            blocks.add(blockCompound);
        }

        ListTag paletteTag = new ListTag();
        for (BlockState state : palette.keySet()) {
            paletteTag.add(NbtUtils.writeBlockState(state));
        }

        // 4. Assemble Root
        CompoundTag rootCompound = new CompoundTag();
        rootCompound.put("size", size);
        rootCompound.put("palette", paletteTag);
        rootCompound.put("blocks", blocks);
        rootCompound.put("entities", new ListTag()); // Leave entities empty for now
        NbtUtils.addCurrentDataVersion(rootCompound);

        return rootCompound;
    }

    /** Converts a root compound tag back into a map of block positions to block states. */
    public static Map<BlockPos, BlockState> read(CompoundTag rootCompoundTag) {
        if (rootCompoundTag == null) {
            throw new IllegalArgumentException("rootCompoundTag cannot be null.");
        }

        Map<BlockPos, BlockState> blockMap = new LinkedHashMap<>();

        ListTag palette = rootCompoundTag.getList("palette", Tag.TAG_COMPOUND);
        ListTag blocks = rootCompoundTag.getList("blocks", Tag.TAG_COMPOUND);

        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag block = blocks.getCompound(i);
            int[] pos = readTriple(block, "pos");
            int stateIndex = block.getInt("state");
            if (pos == null || stateIndex < 0 || stateIndex >= palette.size()) {
                continue;
            }

            BlockState state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), palette.getCompound(stateIndex));
            blockMap.put(new BlockPos(pos[0], pos[1], pos[2]), state);
        }

        return blockMap;
    }

    /** Reads a structure tag into a detached structure; positions stay as they are written. */
    public static QcStructure parseStructure(CompoundTag tag) {
        if (!tag.contains("size")) {
            throw new IllegalArgumentException("missing 'size'");
        }
        int[] size = readTriple(tag, "size");
        if (size == null) {
            throw new IllegalArgumentException("'size' must hold three ints");
        }

        ListTag palette = tag.getList("palette", Tag.TAG_COMPOUND);
        ListTag blocks = tag.getList("blocks", Tag.TAG_COMPOUND);

        List<QcStructure.Entry> entries = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag block = blocks.getCompound(i);
            int[] pos = readTriple(block, "pos");
            int stateIndex = block.getInt("state");
            if (pos == null || stateIndex < 0 || stateIndex >= palette.size()) {
                continue;
            }

            BlockState state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), palette.getCompound(stateIndex));
            entries.add(new QcStructure.Entry(pos[0], pos[1], pos[2], state));
        }

        return new QcStructure(size[0], size[1], size[2], entries);
    }

    /** Writes a detached structure in the same shape as {@link #write}. */
    public static CompoundTag writeStructure(QcStructure structure) {
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
    public static String validateComponentFile(CompoundTag tag) {
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

        ListTag palette = tag.getList("palette", Tag.TAG_COMPOUND);
        for (int i = 0; i < palette.size(); i++) {
            String name = palette.getCompound(i).getString("Name");
            ResourceLocation identifier = ResourceLocation.tryParse(name);
            if (identifier == null || BuiltInRegistries.BLOCK.getOptional(identifier).isEmpty()) {
                return "unknown block '" + name + "' in palette";
            }
        }

        return null;
    }

    public static CompoundTag readFile(Path file) {
        try {
            return NbtIo.readCompressed(file.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void writeFile(CompoundTag tag, Path file) {
        try {
            NbtIo.writeCompressed(tag, file.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Reads a three int triple, written either as an int list or as an int array. */
    private static int[] readTriple(CompoundTag tag, String key) {
        if (tag.contains(key, Tag.TAG_INT_ARRAY)) {
            int[] array = tag.getIntArray(key);
            return array.length == 3 ? array : null;
        }
        ListTag list = tag.getList(key, Tag.TAG_INT);
        if (list.size() != 3) {
            return null;
        }
        return new int[]{list.getInt(0), list.getInt(1), list.getInt(2)};
    }
}
