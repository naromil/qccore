package com.qccore.net;

import com.qccore.QCCore;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.function.Function;

/**
 * The six packets of the editor: build, align, plan-chunk and undo requests from the client, the unit
 * index and a result message back to it.
 *
 * <p>The packets are loader-neutral records: each one writes itself to a {@link FriendlyByteBuf} and
 * has a {@link Type} naming its channel id and reader. The loader glue registers every entry of
 * {@link #C2S} and {@link #S2C} with its own networking API (Fabric: one raw channel per id, so the
 * wire format is the record's {@code write} alone; Forge: one {@code SimpleChannel} with a
 * discriminator per type) and hands decoded packets to {@link QcServerNetworking#handle} or
 * {@code QCCoreClient.handle} on the game thread.
 */
public final class QcPackets {

    private QcPackets() {
    }

    /** A packet of this mod. */
    public interface QcPacket {

        void write(FriendlyByteBuf buf);

        Type<?> type();
    }

    /** The channel id, record class and reader of one packet. */
    public record Type<T extends QcPacket>(ResourceLocation id, Class<T> packetClass, Function<FriendlyByteBuf, T> reader) {

        public T read(FriendlyByteBuf buf) {
            return reader.apply(buf);
        }
    }

    /** Ask the server to build a layout with a palette. */
    public record QcBuildC2S(BlockPos anchor, CompoundTag layers, CompoundTag palette) implements QcPacket {

        public static final Type<QcBuildC2S> TYPE = new Type<>(QCCore.id("build"), QcBuildC2S.class,
                buf -> new QcBuildC2S(buf.readBlockPos(), buf.readNbt(), buf.readNbt()));

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(anchor);
            buf.writeNbt(layers);
            buf.writeNbt(palette);
        }

        @Override
        public Type<?> type() {
            return TYPE;
        }
    }

    /** Ask the server to move a placed building onto the lattice {@code (8x+a, 8y+b, 8z+c)}. */
    public record QcAlignC2S(BlockPos unit, int a, int b, int c, CompoundTag palette) implements QcPacket {

        public static final Type<QcAlignC2S> TYPE = new Type<>(QCCore.id("align"), QcAlignC2S.class,
                buf -> new QcAlignC2S(buf.readBlockPos(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readNbt()));

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(unit);
            buf.writeInt(a);
            buf.writeInt(b);
            buf.writeInt(c);
            buf.writeNbt(palette);
        }

        @Override
        public Type<?> type() {
            return TYPE;
        }
    }

    /** The units the server knows around a player: {@code {"units":[record, ...]}}. */
    public record QcIndexS2C(ResourceLocation dimension, CompoundTag units) implements QcPacket {

        public static final Type<QcIndexS2C> TYPE = new Type<>(QCCore.id("index"), QcIndexS2C.class,
                buf -> new QcIndexS2C(buf.readResourceLocation(), buf.readNbt()));

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeResourceLocation(dimension);
            buf.writeNbt(units);
        }

        @Override
        public Type<?> type() {
            return TYPE;
        }
    }

    /** The outcome of a build or align request. */
    public record QcResultS2C(boolean ok, String message) implements QcPacket {

        public static final Type<QcResultS2C> TYPE = new Type<>(QCCore.id("result"), QcResultS2C.class,
                buf -> new QcResultS2C(buf.readBoolean(), buf.readUtf()));

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeBoolean(ok);
            buf.writeUtf(message);
        }

        @Override
        public Type<?> type() {
            return TYPE;
        }
    }

    /**
     * One chunk of a gzip-compressed plan request. Vanilla caps a client payload at 32767 bytes, so a
     * request ({@code {"action","anchor":[x,y,z],"plan":PlanCodec,"palette":PaletteCodec}}) is split
     * into chunks of {@link #CHUNK} bytes; the server reassembles the chunks of one {@code transfer}.
     */
    public record QcPlanC2S(int transfer, int index, int total, byte[] data) implements QcPacket {

        public static final int CHUNK = 30000;

        public static final Type<QcPlanC2S> TYPE = new Type<>(QCCore.id("plan"), QcPlanC2S.class,
                buf -> new QcPlanC2S(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readByteArray(CHUNK)));

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(transfer);
            buf.writeVarInt(index);
            buf.writeVarInt(total);
            buf.writeByteArray(data);
        }

        @Override
        public Type<?> type() {
            return TYPE;
        }
    }

    /** Ask the server to undo the player's most recent applied plan. */
    public record QcUndoC2S() implements QcPacket {

        public static final Type<QcUndoC2S> TYPE = new Type<>(QCCore.id("undo"), QcUndoC2S.class, buf -> new QcUndoC2S());

        @Override
        public void write(FriendlyByteBuf buf) {
        }

        @Override
        public Type<?> type() {
            return TYPE;
        }
    }

    /** Every client-to-server packet, in registration order (Forge discriminators follow it). */
    public static final List<Type<?>> C2S = List.of(QcBuildC2S.TYPE, QcAlignC2S.TYPE, QcPlanC2S.TYPE, QcUndoC2S.TYPE);

    /** Every server-to-client packet, in registration order. */
    public static final List<Type<?>> S2C = List.of(QcIndexS2C.TYPE, QcResultS2C.TYPE);
}
