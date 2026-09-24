package com.qccore.core.palette;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * A component identified by a single block id instead of an uploaded structure: the framework,
 * row, column, wall and floor blocks.
 *
 * <p>Unlike the desktop tool a blank or unknown id is never silently replaced by
 * {@code minecraft:stone}: such a component simply exports nothing, and the build is refused with
 * the id that has to be fixed.
 */
public abstract class BlockComponent implements UnitComponent {

    private String id = "";

    public String getRawId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    /** The default state of the configured block, or {@code null} when the id is blank or unknown. */
    public BlockState state() {
        String normalised = normalise(id);
        if (normalised == null) {
            return null;
        }
        Identifier identifier = Identifier.tryParse(normalised);
        if (identifier == null) {
            return null;
        }
        Block block = Registries.BLOCK.getOrEmpty(identifier).orElse(null);
        return block == null ? null : block.getDefaultState();
    }

    public boolean isConfigured() {
        return state() != null;
    }

    /** Adopts the block a probe found as this component's id; a missing probe leaves the id alone. */
    protected void adoptProbe(BlockState probed) {
        if (probed != null) {
            setId(Registries.BLOCK.getId(probed.getBlock()).toString());
        }
    }

    /** Trims an id, drops a blank one and adds the default namespace when none is given. */
    public static String normalise(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.contains(":") ? trimmed : "minecraft:" + trimmed;
    }
}
