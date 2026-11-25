package com.qccore.net;

import com.qccore.core.QcResult;
import com.qccore.world.QcScanService;
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
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.qccore.world.QcUnitRecord;

/**
 * The {@code /qc} command: what the index knows, how to scan standing QC units into it, how to forget a
 * region of it and how to undo and redo applied plans. Every child needs permission level 2; {@code info}, {@code scan} and {@code forget} work from
 * the dedicated server console as well, {@code undo} and {@code redo} need a player. A player's
 * {@code forget} is undoable like an apply; one from the console is not.
 */
public final class QcCommands {

    private static final int DEFAULT_RADIUS = 64;

    private QcCommands() {
    }

    /** Registers {@code /qc}; the loader glue calls this from its command-registration event. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("qc")
                        .then(Commands.literal("info")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> info(context.getSource(), DEFAULT_RADIUS))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 512))
                                        .executes(context -> info(context.getSource(), IntegerArgumentType.getInteger(context, "radius")))))
                        .then(Commands.literal("scan")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> scan(context.getSource(), QcScanService.DEFAULT_RADIUS))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(8, QcScanService.MAX_RADIUS))
                                        .executes(context -> scan(context.getSource(), IntegerArgumentType.getInteger(context, "radius")))))
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

    /**
     * Counts the records round the source with the same radius and shape as {@code /qc forget}, so
     * {@code /qc info 20} shows exactly what {@code /qc forget 20} would drop; for a player it also
     * says how many steps {@code /qc undo} and {@code /qc redo} hold.
     */
    private static int info(CommandSourceStack source, int radius) {
        ServerLevel world = source.getLevel();
        ResourceLocation dimension = world.dimension().location();
        Map<BlockPos, QcUnitRecord> units = QcUnitIndex.get(world).units(dimension);

        BlockPos pos = BlockPos.containing(source.getPosition());
        List<QcUnitRecord> nearby = new ArrayList<>();
        for (Map.Entry<BlockPos, QcUnitRecord> unit : units.entrySet()) {
            if (unit.getKey().closerThan(pos, radius)) {
                nearby.add(unit.getValue());
            }
        }

        int total = units.size();
        source.sendSuccess(() -> Component.literal(total + " QC unit record(s) in " + dimension), false);
        source.sendSuccess(() -> Component.literal("within " + radius + " blocks of " + pos.getX() + " " + pos.getY()
                + " " + pos.getZ() + ": " + summary(nearby)), false);
        if (source.getEntity() instanceof ServerPlayer player) {
            int undo = QcUndoService.depth(player);
            int redo = QcUndoService.redoDepth(player);
            source.sendSuccess(() -> Component.literal("/qc undo: " + undo + " step(s), /qc redo: " + redo + " step(s)"), false);
        }
        return total;
    }

    /**
     * One line describing {@code records}: building units (how many an apply built from a plan, which
     * later parts join on, and how many with a door the scan found) and road cells.
     */
    static String summary(Collection<QcUnitRecord> records) {
        if (records.isEmpty()) {
            return "no QC unit records (/qc scan reads standing buildings)";
        }
        int buildings = 0, planned = 0, doors = 0, roads = 0;
        for (QcUnitRecord record : records) {
            if (!record.isBuilding()) {
                roads++;
                continue;
            }
            buildings++;
            if (record.part() != 0) planned++;
            if (record.doors() != 0) doors++;
        }
        List<String> details = new ArrayList<>();
        if (planned > 0) details.add(planned + " built from plans");
        if (doors > 0) details.add(doors + " with a door");
        return buildings + " building unit(s)" + (details.isEmpty() ? "" : " (" + String.join(", ", details) + ")")
                + ", " + roads + " road cell(s)";
    }

    private static int scan(CommandSourceStack source, int radius) {
        QcResult result = QcScanService.scan(source.getLevel(), BlockPos.containing(source.getPosition()), radius);
        if (!result.ok()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(result.message()), false);
        if (source.getEntity() instanceof ServerPlayer player) {
            QcServerNetworking.push(player);
        }
        return 1;
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
        BlockPos pos = BlockPos.containing(source.getPosition());
        Map<BlockPos, QcUnitRecord> forgotten = QcUnitIndex.get(world).removeWithin(dimension, pos, radius);

        int count = forgotten.size();
        boolean undoable = count > 0 && source.getEntity() instanceof ServerPlayer;
        if (source.getEntity() instanceof ServerPlayer player) {
            QcUndoService.pushForgotten(player, dimension, forgotten);
        }
        // Clients keep the records they were last pushed: without a fresh push the overlay, the
        // preview and road/part joins would go on using what was just forgotten.
        if (count > 0) {
            for (ServerPlayer player : world.players()) {
                QcServerNetworking.push(player);
            }
        }
        String message = "forgot " + count + " QC unit(s) within " + radius + " blocks"
                + (undoable ? " (/qc undo brings them back)" : "");
        source.sendSuccess(() -> Component.literal(message), false);
        return count;
    }
}
