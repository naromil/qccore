package com.qccore.core.qc;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Reads a {@link BlockChoice} for a generator and remembers what it asked for: every role the
 * design actually reached, and the required ones that had no block. qckit raises on the first
 * unchosen role; here the generator keeps going (writing nothing for it) so the player sees every
 * missing role at once.
 */
public final class StyleReader {

    private final String name;
    private final BlockChoice choice;
    private final Set<String> reached = new LinkedHashSet<>();
    private final Set<String> missing = new LinkedHashSet<>();

    public StyleReader(String name, BlockChoice choice) {
        this.name = name;
        this.choice = choice == null ? new BlockChoice(Roles.BUILDING) : choice;
    }

    /** The block for a role; {@code null} when it is unset (recorded as missing unless optional). */
    public String get(String role) {
        reached.add(role);
        String id = choice.resolve(role);
        if (id == null) {
            Role spec = Roles.role(choice.kind(), role);
            if (spec == null || !spec.optional()) {
                missing.add(role);
            }
        }
        return id;
    }

    /** The block for a role without counting it as missing when unset (qckit's {@code "x" in m}). */
    public String peek(String role) {
        reached.add(role);
        return choice.resolve(role);
    }

    public String name() {
        return name;
    }

    public Set<String> reached() {
        return reached;
    }

    public Set<String> missing() {
        return missing;
    }
}
