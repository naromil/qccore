package com.qccore.core.plan;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.qccore.core.palette.BlockComponent;
import net.minecraft.block.BlockState;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.registry.Registries;

/**
 * Parses the block strings of road styles and the ground block: an id with an optional state, the
 * same syntax as {@code /setblock} ({@code oak_log[axis=x]}); the namespace defaults to
 * {@code minecraft}. Unknown ids give {@code null}, never a stand-in block.
 */
public final class BlockIds {

    private BlockIds() {
    }

    public static BlockState parse(String raw) {
        String id = BlockComponent.normalise(raw);
        if (id == null) {
            return null;
        }
        try {
            return BlockArgumentParser.block(Registries.BLOCK.getReadOnlyWrapper(), id, false).blockState();
        } catch (CommandSyntaxException | RuntimeException e) {
            return null;
        }
    }
}
