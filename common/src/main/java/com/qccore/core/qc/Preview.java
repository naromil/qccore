package com.qccore.core.qc;

import com.qccore.core.plan.BuildingSpec;
import com.qccore.core.plan.Cell;
import com.qccore.core.plan.QcPlan;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What the screens draw as small previews: a sample of one style (the block chooser, as the
 * player fills it in) and a template filled with default blocks (the template screen).
 *
 * <p>The default blocks are the brick-and-spruce house and the high street of the QC Shorts build
 * {@code qc_brickworks.py} in the worldedit repo. They exist for previews only and are never written
 * into a plan: the player's own choice always wins, and an unset role in the plan stays unset (the
 * user, 2026-11-19: defaults are fine "as long as" players keep "freedom over their block choice").
 * Registry-free, so the samples are JUnit-tested.
 */
public final class Preview {

    /** The style name a sample building is built with. */
    public static final String STYLE = "preview";
    /** The style name of a sample's road. */
    public static final String ROAD_STYLE = "preview_road";

    private static final Map<String, String> BUILDING = new LinkedHashMap<>();
    private static final Map<String, String> ROAD = new LinkedHashMap<>();

    static {
        // qc_brickworks.py HOUSE (glass: vanilla instead of create:framed_glass; rail: not set there)
        BUILDING.put("frame", "minecraft:stripped_spruce_log");
        BUILDING.put("beam", "minecraft:spruce_log");
        BUILDING.put("floor", "minecraft:spruce_planks");
        BUILDING.put("wall", "minecraft:bricks");
        BUILDING.put("glass", "minecraft:glass");
        BUILDING.put("row", "minecraft:brick_stairs");
        BUILDING.put("coping", "minecraft:brick_stairs");
        BUILDING.put("roof", "minecraft:dark_oak_stairs");
        BUILDING.put("roof_slab", "minecraft:dark_oak_slab");
        BUILDING.put("roof_full", "minecraft:dark_oak_planks");
        BUILDING.put("sill", "minecraft:spruce_slab");
        BUILDING.put("capital", "minecraft:spruce_planks");
        BUILDING.put("light", "minecraft:shroomlight");
        BUILDING.put("hang", "minecraft:lantern");
        BUILDING.put("door", "minecraft:spruce_door");
        BUILDING.put("parapet", "minecraft:brick_wall");
        BUILDING.put("flight", "minecraft:spruce_stairs");
        BUILDING.put("bed", "minecraft:moss_block");
        BUILDING.put("plant", "minecraft:azalea");
        BUILDING.put("rail", "minecraft:spruce_fence");
        // qc_brickworks.py HIGH_STREET
        ROAD.put("floor1", "minecraft:polished_granite");
        ROAD.put("floor2", "minecraft:packed_mud");
        ROAD.put("floor3", "minecraft:packed_mud");
        ROAD.put("floor4", "minecraft:mud_bricks");
        ROAD.put("joint1", "minecraft:shroomlight");
        ROAD.put("medallion", "minecraft:polished_granite");
        ROAD.put("centre", "minecraft:shroomlight");
    }

    private Preview() {
    }

    /** The default blocks of a kind of style ({@code building} or {@code road}); empty for any other kind. */
    public static BlockChoice defaults(String kind) {
        BlockChoice choice = new BlockChoice(kind);
        Map<String, String> source = Roles.BUILDING.equals(kind) ? BUILDING : Roles.ROAD.equals(kind) ? ROAD : Map.of();
        source.forEach(choice::set);
        return choice;
    }

    /**
     * A copy of {@code plan} whose styles have every role the player left unset filled with the
     * default block of that role; the blocks the player chose are kept. For the template preview:
     * a template's styles are empty.
     */
    public static QcPlan withDefaults(QcPlan plan) {
        QcPlan copy = plan.copy();
        for (Map.Entry<String, BlockChoice> entry : copy.styles().entrySet()) {
            BlockChoice style = entry.getValue().copy();
            BlockChoice fill = defaults(style.kind());
            for (Map.Entry<String, String> role : fill.blocks().entrySet()) {
                if (style.get(role.getKey()) == null) {
                    style.set(role.getKey(), role.getValue());
                }
            }
            entry.setValue(style);
        }
        for (BuildingSpec spec : copy.buildings().values()) {
            copy.styles().computeIfAbsent(spec.style, k -> defaults(Roles.BUILDING));
        }
        for (Map.Entry<Cell, String> road : copy.roads().entrySet()) {
            copy.styles().computeIfAbsent(road.getValue(), k -> defaults(Roles.ROAD));
        }
        return copy;
    }

    /**
     * A sample that shows one style as it stands: for a building style, a two-storey building two
     * units wide of {@code template} (with the building's own {@code options}) with a default road in
     * front of it; for a road style, a crossing with a short arm each way. Roles the style leaves
     * unset stay out of the sample, so it shows exactly what the player has chosen. {@code null} for
     * a decor style.
     */
    public static QcPlan sample(BlockChoice style, String template, Map<String, String> options) {
        QcPlan plan = new QcPlan();
        if (Roles.ROAD.equals(style.kind())) {
            plan.styles().put(ROAD_STYLE, style.copy());
            for (int i = -1; i <= 1; i++) {
                plan.roads().put(new Cell(i, 1, 0), ROAD_STYLE);
                plan.roads().put(new Cell(0, 1, i), ROAD_STYLE);
            }
            return plan;
        }
        if (!Roles.BUILDING.equals(style.kind())) {
            return null;
        }
        String name = template == null || !Templates.exists(template) || Templates.LEGACY.equals(template) ? "house" : template;
        BuildingSpec spec = new BuildingSpec(name, STYLE);
        if (name.equals(template) && options != null) {
            spec.options.putAll(options);
        }
        plan.buildings().put("A", spec);
        plan.styles().put(STYLE, style.copy());
        plan.styles().put(ROAD_STYLE, defaults(Roles.ROAD));
        for (int x = 0; x < 2; x++) {
            for (int s = 1; s <= 2; s++) {
                plan.addUnit(new Cell(x, s, 0), "A");
            }
        }
        for (int x = -1; x <= 2; x++) {
            plan.roads().put(new Cell(x, 1, 1), ROAD_STYLE);
        }
        return plan;
    }
}
