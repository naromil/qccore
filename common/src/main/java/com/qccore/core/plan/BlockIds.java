package com.qccore.core.plan;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.qccore.core.palette.BlockComponent;
import java.util.Optional;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Parses block strings: an id with an optional state, the same syntax as {@code /setblock}
 * ({@code oak_log[axis=x]}); the namespace defaults to {@code minecraft}. Unknown ids give
 * {@code null}, never a stand-in block.
 *
 * <p>{@link #parse} is strict (what the player types); {@link #parseLenient} is for generated
 * strings: it applies each property the block has and silently drops the rest, so an orientation
 * hint written for the wrong kind of block (an axis on a plain block, a stair shape on a full block)
 * never loses the block.
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

    /** The block of a string, its properties applied where the block has them; {@code null} for an unknown id. */
    public static BlockState parseLenient(String raw) {
        String id = BlockComponent.normalise(raw);
        if (id == null) {
            return null;
        }
        int bracket = id.indexOf('[');
        String name = bracket < 0 ? id : id.substring(0, bracket);
        ResourceLocation location = ResourceLocation.tryParse(name.trim());
        if (location == null) {
            return null;
        }
        Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(location);
        if (block.isEmpty()) {
            return null;
        }
        BlockState state = block.get().defaultBlockState();
        if (bracket < 0) {
            return state;
        }
        String props = id.substring(bracket + 1, id.endsWith("]") ? id.length() - 1 : id.length());
        for (String pair : props.split(",")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            Property<?> property = block.get().getStateDefinition().getProperty(pair.substring(0, eq).trim());
            if (property != null) {
                state = with(state, property, pair.substring(eq + 1).trim());
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property, String value) {
        Optional<T> parsed = property.getValue(value);
        return parsed.isPresent() ? state.setValue(property, parsed.get()) : state;
    }
}
