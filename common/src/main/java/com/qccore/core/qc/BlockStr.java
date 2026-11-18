package com.qccore.core.qc;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;

/**
 * Block-state strings, ported from qckit's {@code blocks.py}: orientation helpers for stairs,
 * slabs, doors, logs and lights, the cornice shapes of the row, and the connection pass that spells
 * out the sides of every pane, bar, fence and wall.
 *
 * <p>Everything here works on id strings ({@code minecraft:oak_stairs[facing=north,...]}) and never
 * touches the block registry; {@link com.qccore.core.plan.BlockIds#parseLenient} turns them into
 * states and drops a property the block does not have, so a hint written for the wrong kind of
 * block is harmless.
 */
public final class BlockStr {

    public static final String LANTERN = "minecraft:lantern[hanging=false,waterlogged=false]";
    public static final String LANTERN_HANG = "minecraft:lantern[hanging=true,waterlogged=false]";

    private static final String[] AXIS_SUFFIX = {"_log", "_wood", "_stem", "_hyphae", "basalt", "quartz_pillar",
            "purpur_pillar", "hay_block", "bone_block", "froglight", "_pillar"};
    private static final Set<String> AXIS_IDS = Set.of("minecraft:deepslate", "minecraft:infested_deepslate",
            "minecraft:bamboo_block", "minecraft:stripped_bamboo_block", "minecraft:muddy_mangrove_roots",
            "minecraft:ochre_froglight", "minecraft:verdant_froglight", "minecraft:pearlescent_froglight");
    private static final String[] NOT_STURDY = {"stairs", "slab", "lantern", "torch", "door", "trapdoor", "carpet",
            "button", "pressure_plate", "sign", "lavender", "flower", "tulip", "poppy", "grass", "fern", "azalea",
            "sapling", "water", "chain", "rail", "track", "candle", "pot", "bush", "leaves", "fence_gate", "ladder",
            "vine", "snow"};

    private BlockStr() {
    }

    /** The id without its state, with the {@code minecraft:} namespace added when missing. */
    public static String base(String s) {
        int bracket = s.indexOf('[');
        String id = (bracket < 0 ? s : s.substring(0, bracket)).trim();
        return id.contains(":") ? id : "minecraft:" + id;
    }

    public static boolean hasState(String s) {
        return s.indexOf('[') >= 0;
    }

    /** A stair whose tall back faces {@code side}. */
    public static String stair(String id, Side side, String half, String shape) {
        if (id == null) {
            return null;
        }
        return id + "[facing=" + side.facing + ",half=" + half + ",shape=" + shape + ",waterlogged=false]";
    }

    public static String stair(String id, Side side) {
        return stair(id, side, "bottom", "straight");
    }

    public static String slab(String id, String type) {
        if (id == null) {
            return null;
        }
        return id + "[type=" + type + ",waterlogged=false]";
    }

    /** A door; {@code side} is the way a player faces walking in through it. */
    public static String door(String id, Side side, String half) {
        if (id == null) {
            return null;
        }
        return id + "[facing=" + side.facing + ",half=" + half + ",hinge=left,open=false,powered=false]";
    }

    /** A wall-hung light facing {@code side} (away from its wall); other lights pass through. */
    public static String wallLight(String id, Side side) {
        if (id == null) {
            return null;
        }
        String b = base(id);
        if (b.equals("amendments:wall_lantern")) {
            return id + "[attachment=block,facing=" + side.facing + ",light_level=15,lit=true,waterlogged=false]";
        }
        if (b.endsWith("wall_torch")) {
            return id + "[facing=" + side.facing + "]";
        }
        return id;
    }

    /** Gives an axis block its axis; anything else (or anything with a state) passes through. */
    public static String oriented(String id, String axis) {
        if (id == null || hasState(id)) {
            return id;
        }
        String b = base(id);
        if (b.startsWith("rechiseled:")) {
            return id;
        }
        if (b.startsWith("create:") && b.endsWith("_pillar")) {
            return id + "[axis=" + axis + ",east=false,north=false,south=false,west=false]";
        }
        for (String suffix : AXIS_SUFFIX) {
            if (b.endsWith(suffix)) {
                return id + "[axis=" + axis + "]";
            }
        }
        return AXIS_IDS.contains(b) ? id + "[axis=" + axis + "]" : id;
    }

    /** Leaves placed by hand never decay. */
    public static String leaves(String id) {
        if (id == null || hasState(id) || !id.contains("leaves")) {
            return id;
        }
        return id + "[distance=1,persistent=true,waterlogged=false]";
    }

    /** The row one block outside a wall: stairs upside down with their back to the wall. */
    public static String cornice(String id, Side out) {
        if (id == null) {
            return null;
        }
        String b = base(id);
        if (b.endsWith("_stairs")) {
            return stair(id, out.opp(), "top", "straight");
        }
        if (b.endsWith("_slab")) {
            return slab(id, "top");
        }
        return id;
    }

    /** The row on the diagonal outside a convex corner with outward sides {@code sx} (W/E) and {@code sz} (N/S). */
    public static String corniceCorner(String id, Side sx, Side sz) {
        if (id == null) {
            return null;
        }
        if (base(id).endsWith("_stairs")) {
            Side fac = sx.opp();
            return stair(id, fac, "top", sz.opp() == fac.ccw() ? "outer_left" : "outer_right");
        }
        return cornice(id, sx);
    }

    /** The row in a concave corner, standing outside the {@code s1} and the {@code s2} face. */
    public static String corniceInner(String id, Side s1, Side s2) {
        if (id == null) {
            return null;
        }
        if (base(id).endsWith("_stairs")) {
            Side fac = s1.opp();
            return stair(id, fac, "top", s2.opp() == fac.ccw() ? "inner_left" : "inner_right");
        }
        return cornice(id, s1);
    }

    /** The state the game should not have to guess: leaves persistent, axis blocks upright. */
    public static String complete(String s) {
        if (hasState(s)) {
            return s;
        }
        if (s.contains("leaves")) {
            return leaves(s);
        }
        return oriented(s, "y");
    }

    // --- the connection pass -------------------------------------------------------------------

    /** {@code pane}, {@code fence}, {@code wall} or {@code null}. */
    public static String kind(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        String b = base(s);
        if (b.endsWith("_pane") || b.endsWith("_bars")) {
            return "pane";
        }
        if (b.endsWith("_fence")) {
            return "fence";
        }
        if (b.endsWith("_wall") && !(b.endsWith("_sign") || b.endsWith("_banner") || b.endsWith("_torch")
                || b.endsWith("_head") || b.endsWith("_skull") || b.endsWith("_fan"))) {
            return "wall";
        }
        return null;
    }

    public static boolean sturdy(String s) {
        if (s == null || kind(s) != null) {
            return false;
        }
        String b = base(s);
        for (String t : NOT_STURDY) {
            if (b.contains(t)) {
                return false;
            }
        }
        return true;
    }

    private static boolean joins(String k, String other) {
        String ok = kind(other);
        return switch (k) {
            case "pane" -> "pane".equals(ok) || "wall".equals(ok) || sturdy(other);
            case "fence" -> "fence".equals(ok) || sturdy(other);
            default -> "wall".equals(ok) || "pane".equals(ok) || sturdy(other);
        };
    }

    /**
     * Spells out the connections of every bare pane, bars, fence and wall in {@code blocks} (in
     * place); blocks that already carry a state are left alone. Returns how many were fixed.
     */
    public static int connect(Map<BlockPos, String> blocks) {
        Map<BlockPos, String> fixed = new HashMap<>();
        for (Map.Entry<BlockPos, String> entry : blocks.entrySet()) {
            String s = entry.getValue();
            String k = kind(s);
            if (k == null || hasState(s)) {
                continue;
            }
            BlockPos p = entry.getKey();
            boolean n = joins(k, blocks.get(p.offset(0, 0, -1)));
            boolean e = joins(k, blocks.get(p.offset(1, 0, 0)));
            boolean so = joins(k, blocks.get(p.offset(0, 0, 1)));
            boolean w = joins(k, blocks.get(p.offset(-1, 0, 0)));
            if (k.equals("wall")) {
                boolean straight = n && so && !e && !w || e && w && !n && !so;
                boolean up = !straight || blocks.containsKey(p.above());
                fixed.put(p, s + "[east=" + low(e) + ",north=" + low(n) + ",south=" + low(so) + ",up=" + up
                        + ",waterlogged=false,west=" + low(w) + "]");
            } else {
                fixed.put(p, s + "[east=" + e + ",north=" + n + ",south=" + so + ",waterlogged=false,west=" + w + "]");
            }
        }
        blocks.putAll(fixed);
        return fixed.size();
    }

    private static String low(boolean connected) {
        return connected ? "low" : "none";
    }
}
