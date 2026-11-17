package com.qccore.net;

import com.qccore.core.QcResult;
import com.qccore.world.QcUndoService;
import com.qccore.world.QcUnitIndex;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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

    /** Registers {@code /qc}; the loader glue calls this from its command-registration event. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("qc")
                        .then(Commands.literal("info")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> info(context.getSource())))
                        .then(Commands.literal("undo")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> undo(context.getSource())))
                        .then(Commands.literal("redo")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> redo(context.getSource())))
                        .then(Commands.literal("forget")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> forget(context.getSource(), DEFAULT_RADIUS))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 512))
                                        .executes(context -> forget(context.getSource(), IntegerArgumentType.getInteger(context, "radius"))))));
    }

    private static int info(CommandSourceStack source) {
        ServerLevel world = source.getLevel();
        ResourceLocation dimension = world.dimension().location();
        Map<BlockPos, QcUnitRecord> units = QcUnitIndex.get(world).units(dimension);

        BlockPos pos = BlockPos.containing(source.getPosition());
        int nearby = 0;
        for (BlockPos origin : units.keySet()) {
            if (origin.closerThan(pos, INFO_RADIUS)) {
                nearby++;
            }
        }

        int total = units.size();
        int close = nearby;
        source.sendSuccess(() -> Component.literal(total + " QC unit(s) in " + dimension), false);
        source.sendSuccess(() -> Component.literal(close + " within " + INFO_RADIUS + " blocks of " + pos.getX() + " " + pos.getY()
                + " " + pos.getZ()), false);
        return total;
    }

    private static int undo(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        QcResult result = QcUndoService.undo(player);
        if (!result.ok()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(result.message()), false);
        QcServerNetworking.push(player);
        return 1;
    }

    private static int redo(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        QcResult result = QcUndoService.redo(player);
        if (!result.ok()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(result.message()), false);
        QcServerNetworking.push(player);
        return 1;
    }

    private static int forget(CommandSourceStack source, int radius) {
        ServerLevel world = source.getLevel();
        ResourceLocation dimension = world.dimension().location();
        QcUnitIndex index = QcUnitIndex.get(world);
        BlockPos pos = BlockPos.containing(source.getPosition());

        List<BlockPos> forgotten = new ArrayList<>();
        for (BlockPos origin : index.units(dimension).keySet()) {
            if (origin.closerThan(pos, radius)) {
                forgotten.add(origin);
            }
        }
        for (BlockPos origin : forgotten) {
            index.remove(dimension, origin);
        }

        int count = forgotten.size();
        source.sendSuccess(() -> Component.literal("forgot " + count + " QC unit(s)"), false);
        return count;
    }
}
