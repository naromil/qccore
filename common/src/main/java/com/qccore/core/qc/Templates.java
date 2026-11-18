package com.qccore.core.qc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The named building templates of qckit's {@code building.TEMPLATES}: geometry choices only, the
 * blocks come from the building's style. A template is a map of {@link Design} options, so a
 * building can override any of them and a new template is one {@link #register} call.
 *
 * <p>{@link #LEGACY} is not a qckit template: it routes a building through the old QCSimple
 * nine-component exporter and the session palette, so plans and layouts made before the rework
 * still build.
 */
public final class Templates {

    /** The QCSimple pipeline (nine components, session palette); kept for old plans only. */
    public static final String LEGACY = "qcsimple";

    private static final Map<String, Map<String, String>> TEMPLATES = new LinkedHashMap<>();

    static {
        String base = "doors=one,door_width=1,lights=wall,stairs=true,row=true,plinth=false,roof_rise=6";
        register("house", base + ",partition=rooms,facade=punched,roof=hip,piers=normal,sills=true");
        register("shop", base + ",partition=rooms,facade=punched,facade_ground=shopfront,roof=hip,piers=normal,"
                + "doors=every,sills=true");
        register("hall", base + ",partition=open,facade=band,roof=hip,piers=dense,door_width=3,sills=true");
        register("office", base + ",partition=open,facade=curtain,roof=flat,piers=normal,sills=false");
        register("warehouse", base + ",partition=open,facade=clerestory,roof=hip,piers=dense,door_width=3,"
                + "row=false,sills=false,roof_rise=4");
        register("tower", base + ",partition=rooms,facade=tall,roof=hip,piers=2x2,sills=true,roof_rise=8");
        register("academy", base + ",partition=rooms,facade=punched,roof=garden,piers=2x2,sills=true");
        register("townhouse", base + ",partition=rooms,facade_ground=framed,facade=bay|balcony,facade_top=twin,"
                + "roof=hip,piers=2x2,sills=false,portal=true");
        register("villa", base + ",partition=rooms,facade_ground=arcade,facade=arched|balcony,facade_top=arched,"
                + "roof=garden,piers=dense,sills=false,portal=true");
        register("chapel", base + ",partition=open,facade=lancet,facade_end=oculus,roof=hip,piers=2x2,door_width=3,"
                + "sills=false,roof_rise=8,portal=true");
        register("manor", base + ",partition=rooms,facade_ground=pilastered,facade=framed|twin,facade_end=bay,"
                + "facade_top=arched,roof=hip,piers=2x2,sills=false,portal=true");
        register("cottage", base + ",partition=rooms,facade_ground=framed,facade=timber,roof=hip,piers=normal,"
                + "sills=false,roof_rise=5,portal=true");
        register("workshop", base + ",partition=open,facade_ground=framed,facade=louvred,facade_blind=diaper,"
                + "roof=hip,piers=dense,door_width=3,sills=false,roof_rise=4,portal=true");
        register("library", base + ",partition=open,facade_ground=arcade,facade=arched,facade_top=oculus,roof=flat,"
                + "piers=2x2,door_width=3,sills=false,portal=true");
        register("loft", base + ",partition=open,facade=mullioned,facade_end=framed,roof=flat,piers=normal,"
                + "sills=false,portal=true");
        register("qc_raw", base + ",partition=rooms,facade=blind,roof=none,piers=dense,door_width=3,lights=none,"
                + "stairs=false,sills=false,plinth=true,roof_rise=0");
    }

    private Templates() {
    }

    /** Registers a template from {@code key=value} pairs separated by commas ({@code |} separates a rhythm). */
    public static void register(String name, String options) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String pair : options.split(",")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                map.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
            }
        }
        TEMPLATES.put(name, map);
    }

    public static boolean exists(String name) {
        return TEMPLATES.containsKey(name) || LEGACY.equals(name);
    }

    /** Template names, qckit's first, the legacy pipeline last. */
    public static List<String> names() {
        List<String> names = new ArrayList<>(TEMPLATES.keySet());
        names.add(LEGACY);
        return names;
    }

    /** The template's own options; empty for an unknown name or the legacy pipeline. */
    public static Map<String, String> options(String name) {
        Map<String, String> options = TEMPLATES.get(name);
        return options == null ? Map.of() : options;
    }
}
