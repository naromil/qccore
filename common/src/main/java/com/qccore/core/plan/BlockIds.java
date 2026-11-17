package com.qccore.core.plan;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.qccore.core.palette.BlockComponent;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

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
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), id, false).blockState();
        } catch (CommandSyntaxException | RuntimeException e) {
            return null;
        }
    }
}
