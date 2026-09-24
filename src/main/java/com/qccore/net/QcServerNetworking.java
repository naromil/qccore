package com.qccore.net;

import com.qccore.core.QcResult;
import com.qccore.core.UnitLayers;
import com.qccore.core.nbt.LayersCodec;
import com.qccore.core.nbt.PaletteCodec;
import com.qccore.core.palette.BlockPalette;
import com.qccore.net.QcPackets.QcAlignC2S;
import com.qccore.net.QcPackets.QcBuildC2S;
import com.qccore.net.QcPackets.QcIndexS2C;
import com.qccore.net.QcPackets.QcResultS2C;
import com.qccore.world.QcAlignService;
import com.qccore.world.QcBuildService;
import com.qccore.world.QcUnitIndex;
import com.qccore.world.QcUnitRecord;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The server end of the editor: it receives build and align requests and keeps every client's view
 * of the placed QC Units up to date.
 */
public final class QcServerNetworking {

    private static final Logger LOGGER = LoggerFactory.getLogger("qccore");

    private static final int PUSH_RADIUS = 128;
    private static final int PUSH_LIMIT = 4096;
    private static final int PUSH_INTERVAL_TICKS = 40;
    private static final int PUSH_MOVE_DISTANCE = 16;

    private static final Map<UUID, BlockPos> LAST_PUSH = new HashMap<>();
    private static int tick;

    private QcServerNetworking() {
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(QcBuildC2S.TYPE, (packet, player, sender) ->
                player.server.execute(() -> {
                    try {
                        UnitLayers layers = LayersCodec.fromNbt(packet.layers());
                        BlockPalette palette = PaletteCodec.fromNbt(packet.palette());
                        QcResult result = QcBuildService.build(player, packet.anchor(), layers, palette);
                        if (!result.ok()) {
                            LOGGER.warn("qccore: build request from {} refused: {}", player.getName().getString(), result.message());
                        }
                        ServerPlayNetworking.send(player, new QcResultS2C(result.ok(), result.message()));
                        if (result.ok()) {
                            push(player);
                        }
                    } catch (RuntimeException e) {
                        LOGGER.warn("qccore: rejected build request from {}: {}", player.getName().getString(), e.toString());
                        ServerPlayNetworking.send(player, new QcResultS2C(false, "the build request was malformed"));
                    }
                }));

        ServerPlayNetworking.registerGlobalReceiver(QcAlignC2S.TYPE, (packet, player, sender) ->
                player.server.execute(() -> {
                    try {
                        BlockPalette palette = PaletteCodec.fromNbt(packet.palette());
                        QcResult result = QcAlignService.align(player, packet.unit(), packet.a(), packet.b(), packet.c(), palette);
                        if (!result.ok()) {
                            LOGGER.warn("qccore: align request from {} refused: {}", player.getName().getString(), result.message());
                        }
                        ServerPlayNetworking.send(player, new QcResultS2C(result.ok(), result.message()));
                        if (result.ok()) {
                            push(player);
                        }
                    } catch (RuntimeException e) {
                        LOGGER.warn("qccore: rejected align request from {}: {}", player.getName().getString(), e.toString());
                        ServerPlayNetworking.send(player, new QcResultS2C(false, "the align request was malformed"));
                    }
                }));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> push(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST_PUSH.remove(handler.getPlayer().getUuid()));

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tick++;
            if (tick % PUSH_INTERVAL_TICKS != 0) {
                return;
            }
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                BlockPos last = LAST_PUSH.get(player.getUuid());
                if (last == null || !last.isWithinDistance(player.getBlockPos(), PUSH_MOVE_DISTANCE)) {
                    push(player);
                }
            }
        });
    }

    /**
     * Sends every unit within {@link #PUSH_RADIUS} blocks of the player, nearest first and capped at
     * {@link #PUSH_LIMIT}. Stale records are pruned before the snapshot is taken, so the client never
     * receives a record that was just dropped.
     */
    public static void push(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Identifier dimension = world.getRegistryKey().getValue();
        BlockPos center = player.getBlockPos();

        QcUnitIndex index = QcUnitIndex.get(world);
        index.prune(world, center, PUSH_RADIUS);

        ServerPlayNetworking.send(player, new QcIndexS2C(dimension, snapshot(index.units(dimension), center)));
        LAST_PUSH.put(player.getUuid(), center);
    }

    private static NbtCompound snapshot(Map<BlockPos, QcUnitRecord> units, BlockPos center) {
        List<QcUnitRecord> nearby = new ArrayList<>();
        for (Map.Entry<BlockPos, QcUnitRecord> entry : units.entrySet()) {
            if (entry.getKey().isWithinDistance(center, PUSH_RADIUS)) {
                nearby.add(entry.getValue());
            }
        }
        nearby.sort(Comparator.comparingDouble(record -> record.origin().getSquaredDistance(center)));

        NbtList list = new NbtList();
        for (int i = 0; i < nearby.size() && i < PUSH_LIMIT; i++) {
            list.add(nearby.get(i).toNbt());
        }

        NbtCompound tag = new NbtCompound();
        tag.put("units", list);
        return tag;
    }
}
