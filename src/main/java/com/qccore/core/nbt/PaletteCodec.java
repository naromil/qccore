package com.qccore.core.nbt;

import com.qccore.core.QcStructure;
import com.qccore.core.palette.BlockPalette;
import com.qccore.core.palette.QcComponent;
import net.minecraft.nbt.NbtCompound;

/**
 * The palette wire format: the five single block ids plus the four structures, so a client can ask
 * the server to build with exactly the configuration it shows.
 *
 * <p>Structures are sent as whole structure tags; the display path is not part of the payload and
 * is set to a nominal value on the receiving side.
 */
public final class PaletteCodec {

    private PaletteCodec() {
    }

    public static NbtCompound toNbt(BlockPalette palette) {
        NbtCompound tag = new NbtCompound();
        tag.putString("framework", palette.frameworkBlock().getRawId());
        tag.putString("row", palette.rowBlock().getRawId());
        tag.putString("column", palette.columnBlock().getRawId());
        tag.putString("wall", palette.wallBlock().getRawId());
        tag.putString("floor", palette.floorBlock().getRawId());

        putStructure(tag, "innerWall", palette.innerWall());
        putStructure(tag, "gate", palette.gate());
        putStructure(tag, "outerWall", palette.outerWall());
        putStructure(tag, "innerColumn", palette.innerColumn());

        return tag;
    }

    public static BlockPalette fromNbt(NbtCompound tag) {
        BlockPalette palette = new BlockPalette();

        palette.frameworkBlock().setId(tag.getString("framework"));
        palette.rowBlock().setId(tag.getString("row"));
        palette.columnBlock().setId(tag.getString("column"));
        palette.wallBlock().setId(tag.getString("wall"));
        palette.floorBlock().setId(tag.getString("floor"));

        restore(palette.innerWall(), tag.getCompound("innerWall"));
        restore(palette.gate(), tag.getCompound("gate"));
        restore(palette.outerWall(), tag.getCompound("outerWall"));
        restore(palette.innerColumn(), tag.getCompound("innerColumn"));

        return palette;
    }

    private static void putStructure(NbtCompound tag, String key, QcComponent component) {
        if (component.isEmpty()) {
            return;
        }
        NbtCompound structure = StructureNbt.writeStructure(component.getStructure());
        if (structure != null) {
            tag.put(key, structure);
        }
    }

    private static void restore(QcComponent component, NbtCompound structureTag) {
        if (structureTag == null || structureTag.isEmpty()) {
            component.setExtracted(null);
            return;
        }
        QcStructure structure = StructureNbt.parseStructure(structureTag);
        component.setStructure(structure, "[Sent by client]");
    }
}
