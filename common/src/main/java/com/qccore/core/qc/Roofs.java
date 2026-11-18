package com.qccore.core.qc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The roof kinds a building's top storeys can carry, by name. Each region of connected top units at
 * one plane gets one roof. The pitched kinds share {@link Building#pitched}; a new kind is one
 * {@link #register} call.
 */
public final class Roofs {

    /** Builds one roof over a region of top units whose roof plane is {@code y}. */
    @FunctionalInterface
    public interface Roof {
        void build(Building b, Set<Building.Unit> region, int y);
    }

    private static final Map<String, Roof> ROOFS = new LinkedHashMap<>();
    private static final List<String> PITCHED = new ArrayList<>();

    static {
        registerPitched("hip");
        registerPitched("gable");
        registerPitched("gambrel");
        registerPitched("mansard");
        register("flat", (b, region, y) -> b.flat(region, y, false));
        register("garden", (b, region, y) -> b.flat(region, y, true));
        register("none", Building::bare);
    }

    private Roofs() {
    }

    public static void register(String name, Roof roof) {
        ROOFS.put(name, roof);
    }

    private static void registerPitched(String name) {
        PITCHED.add(name);
        register(name, (b, region, y) -> b.pitched(region, y, name));
    }

    public static boolean exists(String name) {
        return ROOFS.containsKey(name);
    }

    public static boolean isPitched(String name) {
        return PITCHED.contains(name);
    }

    public static List<String> names() {
        return new ArrayList<>(ROOFS.keySet());
    }

    static void build(String name, Building b, Set<Building.Unit> region, int y) {
        Roof roof = ROOFS.get(name);
        (roof == null ? ROOFS.get("none") : roof).build(b, region, y);
    }
}
