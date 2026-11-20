package com.qccore.core.qc;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;

/**
 * A style: the blocks chosen for one kind of generator, role by role ({@code frame ->
 * minecraft:spruce_log}). qckit's "blocks chosen for this build": no default block stands in for
 * a role (a {@link Presets} entry is copied in only when the player loads it); an unset role either
 * repeats another chosen role ({@link Role#fallback()}) or, if optional, leaves its part out. A template is saved with every style's blocks stripped
 * ({@link #stripped()}), so the same geometry can be filled with any blocks later.
 *
 * <p>Ids may carry states ({@code oak_log[axis=x]}); the namespace defaults to {@code minecraft}.
 */
public final class BlockChoice {

    private final String kind;
    private final Map<String, String> blocks = new LinkedHashMap<>();

    public BlockChoice(String kind) {
        this.kind = kind == null || kind.isBlank() ? Roles.BUILDING : kind;
    }

    public String kind() {
        return kind;
    }

    /** The chosen blocks, by role; live. */
    public Map<String, String> blocks() {
        return blocks;
    }

    /** The block chosen for a role itself, or {@code null}. */
    public String get(String role) {
        String id = blocks.get(role);
        return id == null || id.isBlank() ? null : id.trim();
    }

    public void set(String role, String id) {
        if (id == null || id.isBlank()) {
            blocks.remove(role);
        } else {
            blocks.put(role, id.trim());
        }
    }

    public boolean isEmpty() {
        return blocks.isEmpty();
    }

    /** The block a role resolves to: its own, else its fallbacks in order, else {@code null}. */
    public String resolve(String role) {
        String own = get(role);
        if (own != null) {
            return own;
        }
        Role spec = Roles.role(kind, role);
        if (spec == null || spec.fallback() == null) {
            return null;
        }
        for (String source : spec.fallback().split("\\|")) {
            String id = resolve(source);
            if (id != null && (!spec.hint().equals("stairs") || BlockStr.base(id).endsWith("_stairs"))) {
                return id;
            }
        }
        return null;
    }

    /** Required roles of this kind that resolve to nothing. */
    public Set<String> missing() {
        Set<String> out = new LinkedHashSet<>();
        for (Role role : Roles.of(kind)) {
            if (!role.optional() && resolve(role.name()) == null) {
                out.add(role.name());
            }
        }
        return out;
    }

    public BlockChoice copy() {
        BlockChoice copy = new BlockChoice(kind);
        copy.blocks.putAll(blocks);
        return copy;
    }

    /** The same kind with no blocks: what a template keeps of a style. */
    public BlockChoice stripped() {
        return new BlockChoice(kind);
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("kind", kind);
        CompoundTag map = new CompoundTag();
        blocks.forEach(map::putString);
        tag.put("blocks", map);
        return tag;
    }

    public static BlockChoice fromNbt(CompoundTag tag) {
        BlockChoice choice = new BlockChoice(tag.getString("kind"));
        CompoundTag map = tag.getCompound("blocks");
        for (String role : map.getAllKeys()) {
            choice.set(role, map.getString(role));
        }
        return choice;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof BlockChoice other && other.kind.equals(kind) && other.blocks.equals(blocks);
    }

    @Override
    public int hashCode() {
        return kind.hashCode() * 31 + blocks.hashCode();
    }
}
