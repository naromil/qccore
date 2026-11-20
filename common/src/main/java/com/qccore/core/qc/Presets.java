package com.qccore.core.qc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Preset block choices: the styles of the QC Shorts batch builds in the worldedit repo
 * ({@code builds/qc_*.py}, their {@code blocks={...}} dicts), selectable in the block chooser next to
 * the player's own kits.
 *
 * <p>A preset is only a starting point. Loading one copies its blocks into the selected style, where
 * every role stays the player's to change, and it never fills a role the batch left unset: those stay
 * open and are named until the player picks them (the user, 2026-11-19: presets are fine "as long as"
 * players keep "freedom over their block choice"). The ids are the batches' own, except Create's
 * {@code create:framed_glass}, which is vanilla {@code minecraft:glass} here. Lane dicts become
 * {@code floor1..4}/{@code joint1..4}/... roles and {@code wall_mix} dicts {@code id=share} lists.
 *
 * <p>A registry like {@link Templates} and {@link Roles}: another preset is a {@link #register} call,
 * and the chooser lists whatever is registered. Registry-free, so it is JUnit-tested.
 */
public final class Presets {

    /** A registered preset: its name, where it came from and its blocks. */
    public record Preset(String name, String kind, String source, Map<String, String> blocks) {
        /** A fresh style holding the preset's blocks, for the player to edit. */
        public BlockChoice choice() {
            BlockChoice choice = new BlockChoice(kind);
            blocks.forEach(choice::set);
            return choice;
        }
    }

    private static final Map<String, Preset> PRESETS = new LinkedHashMap<>();

    static {
        register("brickworks_house", Roles.BUILDING, "qc_brickworks.py HOUSE",
                "frame", "minecraft:stripped_spruce_log",
                "beam", "minecraft:spruce_log",
                "floor", "minecraft:spruce_planks",
                "wall", "minecraft:bricks",
                "glass", "minecraft:glass",
                "row", "minecraft:brick_stairs",
                "coping", "minecraft:brick_stairs",
                "roof", "minecraft:dark_oak_stairs",
                "roof_slab", "minecraft:dark_oak_slab",
                "roof_full", "minecraft:dark_oak_planks",
                "sill", "minecraft:spruce_slab",
                "capital", "minecraft:spruce_planks",
                "light", "minecraft:shroomlight",
                "hang", "minecraft:lantern",
                "door", "minecraft:spruce_door",
                "parapet", "minecraft:brick_wall",
                "flight", "minecraft:spruce_stairs",
                "bed", "minecraft:moss_block",
                "plant", "minecraft:azalea");
        register("brickworks_high_street", Roles.ROAD, "qc_brickworks.py HIGH_STREET",
                "floor1", "minecraft:polished_granite",
                "floor2", "minecraft:packed_mud",
                "floor3", "minecraft:packed_mud",
                "floor4", "minecraft:mud_bricks",
                "joint1", "minecraft:shroomlight",
                "medallion", "minecraft:polished_granite",
                "centre", "minecraft:shroomlight");
        register("alpine_village_chalet", Roles.BUILDING, "qc_alpine_village.py CHALET",
                "frame", "minecraft:stripped_spruce_log",
                "beam", "minecraft:stripped_spruce_log",
                "quoin", "minecraft:stripped_spruce_log",
                "floor", "minecraft:spruce_planks",
                "wall", "minecraft:cobblestone",
                "wall_mix", "minecraft:mossy_cobblestone=0.15",
                "wall_upper", "minecraft:birch_planks",
                "glass", "minecraft:glass",
                "row", "minecraft:dark_oak_stairs",
                "coping", "minecraft:dark_oak_stairs",
                "roof", "minecraft:deepslate_tile_stairs",
                "roof_slab", "minecraft:deepslate_tile_slab",
                "roof_full", "minecraft:deepslate_tiles",
                "sill", "minecraft:spruce_slab",
                "capital", "minecraft:spruce_planks",
                "light", "minecraft:shroomlight",
                "hang", "minecraft:lantern",
                "door", "minecraft:spruce_door",
                "parapet", "minecraft:cobblestone_wall",
                "flight", "minecraft:spruce_stairs",
                "bed", "minecraft:moss_block",
                "plant", "minecraft:azalea",
                "accent", "minecraft:dark_oak_log",
                "trim", "minecraft:dark_oak_stairs",
                "shutter", "minecraft:dark_oak_trapdoor",
                "rail", "minecraft:spruce_fence",
                "balcony", "minecraft:spruce_slab");
        register("alpine_village_lane", Roles.ROAD, "qc_alpine_village.py LANE",
                "floor1", "minecraft:mossy_cobblestone",
                "floor2", "minecraft:cobblestone",
                "floor3", "minecraft:coarse_dirt",
                "floor4", "minecraft:coarse_dirt",
                "joint1", "minecraft:shroomlight",
                "medallion", "minecraft:cobblestone",
                "centre", "minecraft:shroomlight");
        register("sandstone_bazaar_house", Roles.BUILDING, "qc_sandstone_bazaar.py HOUSE",
                "frame", "minecraft:stripped_acacia_log",
                "beam", "minecraft:acacia_log",
                "floor", "minecraft:acacia_planks",
                "wall", "minecraft:smooth_sandstone",
                "glass", "minecraft:glass",
                "row", "minecraft:sandstone_stairs",
                "coping", "minecraft:smooth_sandstone_stairs",
                "roof", "minecraft:red_sandstone_stairs",
                "roof_slab", "minecraft:cut_red_sandstone_slab",
                "roof_full", "minecraft:cut_red_sandstone",
                "sill", "minecraft:acacia_slab",
                "capital", "minecraft:cut_sandstone",
                "light", "minecraft:ochre_froglight",
                "hang", "minecraft:lantern",
                "door", "minecraft:acacia_door",
                "parapet", "minecraft:sandstone_wall",
                "flight", "minecraft:acacia_stairs",
                "deck", "minecraft:smooth_sandstone",
                "bed", "minecraft:moss_block",
                "plant", "minecraft:flowering_azalea");
        register("sandstone_bazaar_street", Roles.ROAD, "qc_sandstone_bazaar.py STREET",
                "floor1", "minecraft:cut_red_sandstone",
                "floor2", "minecraft:smooth_sandstone",
                "floor3", "minecraft:smooth_sandstone",
                "floor4", "minecraft:cut_sandstone",
                "joint1", "minecraft:ochre_froglight",
                "medallion", "minecraft:chiseled_red_sandstone",
                "centre", "minecraft:ochre_froglight");
        register("deepslate_foundry_houses", Roles.BUILDING, "qc_deepslate_foundry.py HOUSES",
                "frame", "minecraft:stripped_spruce_log",
                "beam", "minecraft:spruce_log",
                "floor", "minecraft:spruce_planks",
                "wall", "minecraft:deepslate_bricks",
                "glass", "minecraft:glass",
                "row", "minecraft:deepslate_brick_stairs",
                "coping", "minecraft:deepslate_brick_stairs",
                "roof", "minecraft:deepslate_tile_stairs",
                "roof_slab", "minecraft:deepslate_tile_slab",
                "roof_full", "minecraft:deepslate_tiles",
                "sill", "minecraft:spruce_slab",
                "capital", "minecraft:spruce_planks",
                "light", "minecraft:shroomlight",
                "hang", "minecraft:lantern",
                "door", "minecraft:spruce_door",
                "parapet", "minecraft:deepslate_brick_wall",
                "flight", "minecraft:spruce_stairs",
                "bed", "minecraft:moss_block",
                "plant", "minecraft:azalea",
                "accent", "minecraft:dark_oak_log",
                "trim", "minecraft:spruce_stairs",
                "wall_mix", "minecraft:cracked_deepslate_bricks=0.08",
                "wall_upper", "minecraft:packed_mud",
                "party", "minecraft:deepslate_bricks",
                "quoin", "minecraft:deepslate_bricks",
                "shutter", "minecraft:spruce_trapdoor");
        register("deepslate_foundry_street", Roles.ROAD, "qc_deepslate_foundry.py STREET",
                "floor1", "minecraft:smooth_stone",
                "floor2", "minecraft:deepslate_bricks",
                "floor3", "minecraft:cobbled_deepslate",
                "floor4", "minecraft:deepslate_tiles",
                "joint1", "minecraft:glowstone",
                "medallion", "minecraft:polished_deepslate",
                "centre", "minecraft:glowstone");
        register("cherry_gardens_tea_house", Roles.BUILDING, "qc_cherry_gardens.py TEA_HOUSE",
                "frame", "minecraft:stripped_cherry_log",
                "beam", "minecraft:cherry_log",
                "floor", "minecraft:cherry_planks",
                "wall", "minecraft:white_terracotta",
                "glass", "minecraft:glass",
                "row", "minecraft:cherry_stairs",
                "coping", "minecraft:cherry_stairs",
                "roof", "minecraft:polished_blackstone_brick_stairs",
                "roof_slab", "minecraft:polished_blackstone_brick_slab",
                "roof_full", "minecraft:polished_blackstone_bricks",
                "sill", "minecraft:cherry_slab",
                "capital", "minecraft:cherry_planks",
                "light", "minecraft:pearlescent_froglight",
                "hang", "minecraft:lantern",
                "door", "minecraft:cherry_door",
                "parapet", "minecraft:polished_blackstone_brick_wall",
                "flight", "minecraft:cherry_stairs",
                "bed", "minecraft:moss_block",
                "plant", "minecraft:flowering_azalea",
                "accent", "minecraft:cherry_log",
                "trim", "minecraft:cherry_stairs",
                "shutter", "minecraft:cherry_trapdoor",
                "rail", "minecraft:cherry_fence");
        register("cherry_gardens_paths", Roles.ROAD, "qc_cherry_gardens.py PATHS",
                "floor1", "minecraft:mossy_stone_bricks",
                "floor2", "minecraft:stone_bricks",
                "floor3", "minecraft:stone_bricks",
                "floor4", "minecraft:polished_andesite",
                "joint1", "minecraft:pearlescent_froglight",
                "medallion", "minecraft:polished_andesite",
                "centre", "minecraft:pearlescent_froglight");
        register("white_citadel_stone", Roles.BUILDING, "qc_white_citadel.py STONE",
                "frame", "minecraft:tuff",
                "beam", "minecraft:tuff",
                "quoin", "minecraft:tuff",
                "floor", "minecraft:spruce_planks",
                "deck", "minecraft:polished_diorite",
                "wall", "minecraft:calcite",
                "glass", "minecraft:glass",
                "row", "minecraft:polished_diorite_stairs",
                "coping", "minecraft:polished_diorite_stairs",
                "roof", "minecraft:prismarine_brick_stairs",
                "roof_slab", "minecraft:prismarine_brick_slab",
                "roof_full", "minecraft:prismarine_bricks",
                "sill", "minecraft:polished_diorite_slab",
                "capital", "minecraft:tuff",
                "light", "minecraft:sea_lantern",
                "hang", "minecraft:lantern",
                "door", "minecraft:spruce_door",
                "parapet", "minecraft:diorite_wall",
                "flight", "minecraft:spruce_stairs",
                "bed", "minecraft:moss_block",
                "plant", "minecraft:azalea",
                "finial", "minecraft:lantern",
                "accent", "minecraft:tuff",
                "trim", "minecraft:polished_diorite_stairs",
                "shutter", "minecraft:spruce_trapdoor");
        register("white_citadel_road", Roles.ROAD, "qc_white_citadel.py ROAD",
                "floor1", "minecraft:polished_andesite",
                "floor2", "minecraft:andesite",
                "floor3", "minecraft:andesite",
                "floor4", "minecraft:polished_andesite",
                "joint1", "minecraft:sea_lantern",
                "medallion", "minecraft:polished_andesite",
                "centre", "minecraft:sea_lantern");
        register("canal_harbour_merchant", Roles.BUILDING, "qc_canal_harbour.py MERCHANT",
                "frame", "minecraft:stripped_spruce_log",
                "beam", "minecraft:spruce_log",
                "floor", "minecraft:spruce_planks",
                "wall", "minecraft:calcite",
                "glass", "minecraft:glass",
                "row", "minecraft:polished_diorite_stairs",
                "coping", "minecraft:polished_diorite_stairs",
                "roof", "minecraft:dark_oak_stairs",
                "roof_slab", "minecraft:dark_oak_slab",
                "roof_full", "minecraft:dark_oak_planks",
                "sill", "minecraft:spruce_slab",
                "capital", "minecraft:spruce_planks",
                "light", "minecraft:sea_lantern",
                "hang", "minecraft:lantern",
                "door", "minecraft:spruce_door",
                "parapet", "minecraft:diorite_wall",
                "flight", "minecraft:spruce_stairs",
                "bed", "minecraft:moss_block",
                "plant", "minecraft:azalea",
                "accent", "minecraft:polished_diorite",
                "trim", "minecraft:polished_diorite_stairs",
                "rail", "minecraft:diorite_wall",
                "balcony", "minecraft:polished_diorite_slab",
                "shutter", "minecraft:spruce_trapdoor");
        register("canal_harbour_quay", Roles.ROAD, "qc_canal_harbour.py QUAY",
                "floor1", "minecraft:polished_andesite",
                "floor2", "minecraft:stone_bricks",
                "floor3", "minecraft:stone_bricks",
                "floor4", "minecraft:smooth_stone",
                "joint1", "minecraft:sea_lantern",
                "medallion", "minecraft:stone_bricks",
                "centre", "minecraft:sea_lantern");
        register("college_quads_stone", Roles.BUILDING, "qc_college_quads.py STONE",
                "frame", "minecraft:cut_sandstone",
                "beam", "minecraft:cut_sandstone",
                "quoin", "minecraft:cut_sandstone",
                "floor", "minecraft:spruce_planks",
                "wall", "minecraft:dripstone_block",
                "glass", "minecraft:glass",
                "row", "minecraft:smooth_sandstone_stairs",
                "coping", "minecraft:smooth_sandstone_stairs",
                "roof", "minecraft:deepslate_brick_stairs",
                "roof_slab", "minecraft:deepslate_brick_slab",
                "roof_full", "minecraft:deepslate_bricks",
                "sill", "minecraft:smooth_sandstone_slab",
                "capital", "minecraft:chiseled_sandstone",
                "light", "minecraft:shroomlight",
                "hang", "minecraft:lantern",
                "door", "minecraft:spruce_door",
                "parapet", "minecraft:sandstone_wall",
                "flight", "minecraft:spruce_stairs",
                "bed", "minecraft:moss_block",
                "plant", "minecraft:azalea",
                "finial", "minecraft:lantern",
                "accent", "minecraft:cut_sandstone",
                "trim", "minecraft:smooth_sandstone_stairs",
                "shutter", "minecraft:spruce_trapdoor");
        register("college_quads_street", Roles.ROAD, "qc_college_quads.py STREET",
                "floor1", "minecraft:stone_bricks",
                "floor2", "minecraft:andesite",
                "floor3", "minecraft:andesite",
                "floor4", "minecraft:andesite",
                "joint1", "minecraft:shroomlight",
                "medallion", "minecraft:stone_bricks",
                "centre", "minecraft:shroomlight");
    }

    private Presets() {
    }

    /** Registers (or replaces) a preset; {@code roleAndId} alternates role names and block ids. */
    public static void register(String name, String kind, String source, String... roleAndId) {
        if (roleAndId.length % 2 != 0) {
            throw new IllegalArgumentException("preset " + name + ": roles and ids must come in pairs");
        }
        Map<String, String> blocks = new LinkedHashMap<>();
        for (int i = 0; i < roleAndId.length; i += 2) {
            blocks.put(roleAndId[i], roleAndId[i + 1]);
        }
        PRESETS.put(name, new Preset(name, kind, source, Collections.unmodifiableMap(blocks)));
    }

    /** The names of the presets of one kind of style, in registration order. */
    public static List<String> names(String kind) {
        List<String> out = new ArrayList<>();
        for (Preset preset : PRESETS.values()) {
            if (preset.kind().equals(kind)) {
                out.add(preset.name());
            }
        }
        return Collections.unmodifiableList(out);
    }

    /** A preset by name, or {@code null}. */
    public static Preset get(String name) {
        return PRESETS.get(name);
    }

    /** All presets, in registration order. */
    public static List<Preset> all() {
        return List.copyOf(PRESETS.values());
    }
}
