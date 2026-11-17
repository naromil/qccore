package com.qccore.net;

import com.qccore.core.QcResult;
import com.qccore.world.QcUndoService;
import com.qccore.world.QcUnitIndex;
import net.minecraft.server.network.ServerPlayerEntity;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.qccore.world.QcUnitRecord;

/**
 * The {@code /qc} command: what the index knows, how to forget a region of it and how to undo and
 * redo applied plans. Every child needs permission level 2; {@code info} and {@code forget} work from
 * the dedicated server console as well, {@code undo} and {@code redo} need a player.
 */
public final class QcCommands {

    private static final int DEFAULT_RADIUS = 64;
    private static final int INFO_RADIUS = 64;

    private QcCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                CommandManager.literal("qc")
                        .then(CommandManager.literal("info")
                                .requires(source -> source.hasPermissionLevel(2))
                                .executes(context -> info(context.getSource())))
                        .then(CommandManager.literal("undo")
                                .requires(source -> source.hasPermissionLevel(2))
                                .executes(context -> undo(context.getSource())))
                        .then(CommandManager.literal("redo")
                                .requires(source -> source.hasPermissionLevel(2))
                                .executes(context -> redo(context.getSource())))
                        .then(CommandManager.literal("forget")
                                .requires(source -> source.hasPermissionLevel(2))
                                .executes(context -> forget(context.getSource(), DEFAULT_RADIUS))
                                .then(CommandManager.argument("radius", IntegerArgumentType.integer(1, 512))
                                        .executes(context -> forget(context.getSource(), IntegerArgumentType.getInteger(context, "radius")))))));
    }

    private static int info(ServerCommandSource source) {
        ServerWorld world = source.getWorld();
        Identifier dimension = world.getRegistryKey().getValue();
        Map<BlockPos, QcUnitRecord> units = QcUnitIndex.get(world).units(dimension);

        BlockPos pos = BlockPos.ofFloored(source.getPosition());
        int nearby = 0;
        for (BlockPos origin : units.keySet()) {
            if (origin.isWithinDistance(pos, INFO_RADIUS)) {
                nearby++;
            }
        }

        int total = units.size();
        int close = nearby;
        source.sendFeedback(() -> Text.literal(total + " QC unit(s) in " + dimension), false);
        source.sendFeedback(() -> Text.literal(close + " within " + INFO_RADIUS + " blocks of " + pos.getX() + " " + pos.getY()
                + " " + pos.getZ()), false);
        return total;
    }

    private static int undo(ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        QcResult result = QcUndoService.undo(player);
        if (!result.ok()) {
            source.sendError(Text.literal(result.message()));
            return 0;
        }
        source.sendFeedback(() -> Text.literal(result.message()), false);
        QcServerNetworking.push(player);
        return 1;
    }

    private static int redo(ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        QcResult result = QcUndoService.redo(player);
        if (!result.ok()) {
            source.sendError(Text.literal(result.message()));
            return 0;
        }
        source.sendFeedback(() -> Text.literal(result.message()), false);
        QcServerNetworking.push(player);
        return 1;
    }

    private static int forget(ServerCommandSource source, int radius) {
        ServerWorld world = source.getWorld();
        Identifier dimension = world.getRegistryKey().getValue();
        QcUnitIndex index = QcUnitIndex.get(world);
        BlockPos pos = BlockPos.ofFloored(source.getPosition());

        List<BlockPos> forgotten = new ArrayList<>();
        for (BlockPos origin : index.units(dimension).keySet()) {
            if (origin.isWithinDistance(pos, radius)) {
                forgotten.add(origin);
            }
        }
        for (BlockPos origin : forgotten) {
            index.remove(dimension, origin);
        }

        int count = forgotten.size();
        source.sendFeedback(() -> Text.literal("forgot " + count + " QC unit(s)"), false);
        return count;
    }
}
