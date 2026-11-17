package com.qccore.net;

import com.qccore.QCCore;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * The four packets of the editor: a build and an align request from the client, the unit index and
 * a result message back to it.
 */
public final class QcPackets {

    private QcPackets() {
    }

    /** Ask the server to build a layout with a palette. */
    public record QcBuildC2S(BlockPos anchor, NbtCompound layers, NbtCompound palette) implements FabricPacket {

        public static final PacketType<QcBuildC2S> TYPE = PacketType.create(QCCore.id("build"),
                buf -> new QcBuildC2S(buf.readBlockPos(), buf.readNbt(), buf.readNbt()));

        @Override
        public void write(PacketByteBuf buf) {
            buf.writeBlockPos(anchor);
            buf.writeNbt(layers);
            buf.writeNbt(palette);
        }

        @Override
        public PacketType<?> getType() {
            return TYPE;
        }
    }

    /** Ask the server to move a placed building onto the lattice {@code (8x+a, 8y+b, 8z+c)}. */
    public record QcAlignC2S(BlockPos unit, int a, int b, int c, NbtCompound palette) implements FabricPacket {

        public static final PacketType<QcAlignC2S> TYPE = PacketType.create(QCCore.id("align"),
                buf -> new QcAlignC2S(buf.readBlockPos(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readNbt()));

        @Override
        public void write(PacketByteBuf buf) {
            buf.writeBlockPos(unit);
            buf.writeInt(a);
            buf.writeInt(b);
            buf.writeInt(c);
            buf.writeNbt(palette);
        }

        @Override
        public PacketType<?> getType() {
            return TYPE;
        }
    }

    /** The units the server knows around a player: {@code {"units":[record, ...]}}. */
    public record QcIndexS2C(Identifier dimension, NbtCompound units) implements FabricPacket {

        public static final PacketType<QcIndexS2C> TYPE = PacketType.create(QCCore.id("index"),
                buf -> new QcIndexS2C(buf.readIdentifier(), buf.readNbt()));

        @Override
        public void write(PacketByteBuf buf) {
            buf.writeIdentifier(dimension);
            buf.writeNbt(units);
        }

        @Override
        public PacketType<?> getType() {
            return TYPE;
        }
    }

    /** The outcome of a build or align request. */
    public record QcResultS2C(boolean ok, String message) implements FabricPacket {

        public static final PacketType<QcResultS2C> TYPE = PacketType.create(QCCore.id("result"),
                buf -> new QcResultS2C(buf.readBoolean(), buf.readString()));

        @Override
        public void write(PacketByteBuf buf) {
            buf.writeBoolean(ok);
            buf.writeString(message);
        }

        @Override
        public PacketType<?> getType() {
            return TYPE;
        }
    }

    /**
     * One chunk of a gzip-compressed plan request. Vanilla caps a client payload at 32767 bytes, so a
     * request ({@code {"action","anchor":[x,y,z],"plan":PlanCodec,"palette":PaletteCodec}}) is split
     * into chunks of {@link #CHUNK} bytes; the server reassembles the chunks of one {@code transfer}.
     */
    public record QcPlanC2S(int transfer, int index, int total, byte[] data) implements FabricPacket {

        public static final int CHUNK = 30000;

        public static final PacketType<QcPlanC2S> TYPE = PacketType.create(QCCore.id("plan"),
                buf -> new QcPlanC2S(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readByteArray(CHUNK)));

        @Override
        public void write(PacketByteBuf buf) {
            buf.writeVarInt(transfer);
            buf.writeVarInt(index);
            buf.writeVarInt(total);
            buf.writeByteArray(data);
        }

        @Override
        public PacketType<?> getType() {
            return TYPE;
        }
    }

    /** Ask the server to undo the player's most recent applied plan. */
    public record QcUndoC2S() implements FabricPacket {

        public static final PacketType<QcUndoC2S> TYPE = PacketType.create(QCCore.id("undo"), buf -> new QcUndoC2S());

        @Override
        public void write(PacketByteBuf buf) {
        }

        @Override
        public PacketType<?> getType() {
            return TYPE;
        }
    }

    /** Ask the server to redo the player's most recently undone plan apply. */
    public record QcRedoC2S() implements FabricPacket {

        public static final PacketType<QcRedoC2S> TYPE = PacketType.create(QCCore.id("redo"), buf -> new QcRedoC2S());

        @Override
        public void write(PacketByteBuf buf) {
        }

        @Override
        public PacketType<?> getType() {
            return TYPE;
        }
    }
}
