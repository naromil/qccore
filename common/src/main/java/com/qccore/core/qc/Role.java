package com.qccore.core.qc;

/**
 * One block role a generator fills: what it is, whether it may stay unset, and which chosen role
 * an unset one repeats (qckit's role-to-role defaults in {@code styles.py}; never a preset block).
 *
 * @param name     the role key ({@code frame}, {@code floor1}, ...)
 * @param about    one line for the block chooser
 * @param optional unset = that part is left out; a required role that is reached unset is an error
 * @param fallback the role an unset one repeats, or {@code null}
 * @param hint     the kind of block that reads right there ({@code stairs}, {@code slab}, ...), or {@code ""}
 */
public record Role(String name, String about, boolean optional, String fallback, String hint) {

    public static Role required(String name, String about) {
        return new Role(name, about, false, null, "");
    }

    public static Role optional(String name, String about) {
        return new Role(name, about, true, null, "");
    }

    public Role fallsBackTo(String role) {
        return new Role(name, about, optional, role, hint);
    }

    public Role hint(String kind) {
        return new Role(name, about, optional, fallback, kind);
    }
}
