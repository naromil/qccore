package com.qccore.core.qc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The geometry choices of one building, resolved from its template plus its own overrides
 * (qckit's {@code building.template(name, overrides)}). Every option is a string in the map form, so
 * plans, templates and the design screen share one representation; this class only reads them.
 *
 * <dl>
 *   <dt>partition</dt><dd>{@code open} (hall bays, capitals) | {@code rooms} (party wall + gate) | {@code walls}</dd>
 *   <dt>facade / facade_ground / facade_top</dt><dd>a mask or piece name ({@link Facades}); {@code a|b} is a
 *       rhythm mirrored about the run's middle</dd>
 *   <dt>facade_end / facade_blind</dt><dd>the end units of a run of three or more; faces against a taller neighbour</dd>
 *   <dt>roof</dt><dd>{@link Roofs} name; {@code roof_axis} x | z | auto, {@code roof_rise}, {@code roof_steep}</dd>
 *   <dt>piers</dt><dd>{@code normal} | {@code dense} | {@code 2x2} | {@code thick}</dd>
 *   <dt>doors / door_width</dt><dd>{@code one} | {@code every}; 1 (a door) or 3 (an archway)</dd>
 *   <dt>lights</dt><dd>{@code wall} | {@code centre} | {@code none}</dd>
 *   <dt>stairs, row, sills, plinth, portal</dt><dd>on/off</dd>
 * </dl>
 */
public final class Design {

    /** The option keys a building may override, in the order the design screen lists them. */
    public static final List<String> KEYS = List.of("partition", "facade_ground", "facade", "facade_top",
            "facade_end", "facade_blind", "roof", "roof_axis", "roof_rise", "roof_steep", "piers", "doors",
            "door_width", "lights", "stairs", "row", "sills", "plinth", "portal");

    private final Map<String, String> options;

    private Design(Map<String, String> options) {
        this.options = options;
    }

    /** The template's options with the overrides on top; unknown facade names are reported by {@link #problems()}. */
    public static Design of(String template, Map<String, String> overrides) {
        Map<String, String> map = new LinkedHashMap<>(Templates.options(template));
        if (overrides != null) {
            overrides.forEach((k, v) -> {
                if (v != null && !v.isBlank()) {
                    map.put(k, v.trim());
                }
            });
        }
        map.putIfAbsent("partition", "rooms");
        map.putIfAbsent("facade", "punched");
        map.putIfAbsent("facade_ground", map.get("facade"));
        map.putIfAbsent("facade_top", map.get("facade"));
        map.putIfAbsent("facade_blind", "blind");
        map.putIfAbsent("roof", "hip");
        map.putIfAbsent("piers", "normal");
        return new Design(map);
    }

    public String get(String key) {
        String v = options.get(key);
        return v == null || v.isBlank() || v.equals("none") && key.equals("facade_end") ? null : v;
    }

    public String str(String key, String fallback) {
        String v = get(key);
        return v == null ? fallback : v;
    }

    public boolean flag(String key) {
        String v = get(key);
        return v != null && (v.equals("true") || v.equals("on") || v.equals("1") || v.equals("yes"));
    }

    public int integer(String key, int fallback) {
        try {
            return Integer.parseInt(str(key, String.valueOf(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** A facade option as a rhythm (one or more names). */
    public List<String> rhythm(String key) {
        String v = get(key);
        List<String> out = new ArrayList<>();
        if (v != null) {
            for (String name : v.split("\\|")) {
                if (!name.isBlank()) {
                    out.add(name.trim());
                }
            }
        }
        return out;
    }

    public Map<String, String> asMap() {
        return options;
    }

    /** Option values that name nothing (an unknown facade, roof or pier kind). */
    public List<String> problems() {
        List<String> out = new ArrayList<>();
        for (String key : List.of("facade", "facade_ground", "facade_top", "facade_end", "facade_blind")) {
            for (String name : rhythm(key)) {
                if (!name.equals("none") && !Facades.exists(name)) {
                    out.add(key + "=" + name + " is not a facade");
                }
            }
        }
        if (!Roofs.exists(str("roof", "hip"))) {
            out.add("roof=" + str("roof", "") + " is not a roof");
        }
        if (!List.of("normal", "dense", "2x2", "thick").contains(str("piers", "normal"))) {
            out.add("piers=" + str("piers", "") + " is not a pier kind");
        }
        if (!List.of("open", "rooms", "walls").contains(str("partition", "rooms"))) {
            out.add("partition=" + str("partition", "") + " is not a partition");
        }
        return out;
    }
}
