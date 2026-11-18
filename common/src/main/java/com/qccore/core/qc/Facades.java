package com.qccore.core.qc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;
import net.minecraft.core.BlockPos;

/**
 * Outer-wall designs, ported from qckit: the flat masks of {@code building.FACADES} (glass or wall
 * per block of the 7x7 face) and the pieces of {@code facades.py}, which design the face in depth -
 * the wall plane plus up to two layers of relief outside it.
 *
 * <p>Face coordinates: {@code a} 1..7 along the face ({@code +a} is east on a north/south face,
 * south on a west/east face), {@code y} 1..7 up the storey, {@code d} outward. The corner posts and
 * the storey planes belong to the framework, so a piece never writes them in the wall plane. Relief
 * is registered element by element and the district drops an element whole where it would land on
 * a road below y 4 or inside a neighbour.
 *
 * <p>Both kinds live in one registry; {@link #registerMask} and {@link #registerPiece} add more.
 */
public final class Facades {

    /** A flat facade: glass at {@code (a, y)}? */
    @FunctionalInterface
    public interface Mask {
        boolean glass(int a, int y);
    }

    /** A facade designed in depth. */
    @FunctionalInterface
    public interface Piece {
        void draw(Face f);
    }

    private static final Map<String, Mask> MASKS = new LinkedHashMap<>();
    private static final Map<String, Piece> PIECES = new LinkedHashMap<>();

    static {
        registerMask("blind", (a, y) -> false);
        registerMask("punched", (a, y) -> 3 <= a && a <= 5 && 2 <= y && y <= 4);
        registerMask("tall", (a, y) -> 3 <= a && a <= 5 && 2 <= y && y <= 6);
        registerMask("band", (a, y) -> 2 <= a && a <= 6 && 2 <= y && y <= 5);
        registerMask("curtain", (a, y) -> true);
        registerMask("shopfront", (a, y) -> y <= 4);
        registerMask("clerestory", (a, y) -> 2 <= a && a <= 6 && 5 <= y && y <= 6);

        registerPiece("framed", Facades::framed);
        registerPiece("arched", Facades::arched);
        registerPiece("twin", Facades::twin);
        registerPiece("lancet", Facades::lancet);
        registerPiece("oculus", Facades::oculus);
        registerPiece("bay", Facades::bay);
        registerPiece("balcony", Facades::balcony);
        registerPiece("french", Facades::french);
        registerPiece("timber", Facades::timber);
        registerPiece("arcade", Facades::arcade);
        registerPiece("mullioned", Facades::mullioned);
        registerPiece("pilastered", Facades::pilastered);
        registerPiece("louvred", Facades::louvred);
        registerPiece("diaper", Facades::diaper);
    }

    private Facades() {
    }

    public static void registerMask(String name, Mask mask) {
        MASKS.put(name, mask);
    }

    public static void registerPiece(String name, Piece piece) {
        PIECES.put(name, piece);
    }

    public static boolean exists(String name) {
        return MASKS.containsKey(name) || PIECES.containsKey(name);
    }

    public static boolean isPiece(String name) {
        return PIECES.containsKey(name);
    }

    public static Mask mask(String name) {
        return MASKS.get(name);
    }

    /** Every facade name: masks first, then pieces. */
    public static List<String> names() {
        List<String> names = new ArrayList<>(MASKS.keySet());
        names.addAll(PIECES.keySet());
        return names;
    }

    /** Draws a piece on one exterior face. */
    static void place(Building b, Building.Unit u, Side side, String kind, String name) {
        Face f = new Face(b, u, side, kind);
        PIECES.get(name).draw(f);
        f.close();
    }

    // --- the face writer ---------------------------------------------------------------------

    /** Writes one exterior face of unit {@code u} on {@code side}. */
    public static final class Face {
        final Building b;
        final Building.Unit u;
        final Side side;
        final String kind;
        private final String wallId;
        private final Set<BlockPos> group = new HashSet<>();

        Face(Building b, Building.Unit u, Side side, String kind) {
            this.b = b;
            this.u = u;
            this.side = side;
            this.kind = kind;
            this.wallId = b.ground(u) ? b.m.get("wall") : b.m.get("wall_upper");
        }

        BlockPos pos(int a, int y, int d) {
            return d != 0 ? b.out(u, side, a, y, d) : b.face(u, side, a, y);
        }

        /** {@code out | in | +a | -a} as a side. */
        Side word(String w) {
            return switch (w) {
                case "out" -> side;
                case "in" -> side.opp();
                case "+a" -> side.along();
                default -> side.along().opp();
            };
        }

        String axisA() {
            return side == Side.N || side == Side.S ? "x" : "z";
        }

        boolean ground() {
            return b.ground(u);
        }

        void put(int a, int y, String s, int d) {
            BlockPos p = pos(a, y, d);
            if (d > 0) {
                if (b.blocks.containsKey(p)) {
                    return;
                }
                group.add(p);
            }
            b.put(p, s);
        }

        void wall(int a, int y) {
            String s = wallId;
            String mix = s != null && s.equals(b.m.peek("wall")) ? b.m.peek("wall_mix") : b.m.peek("wall_upper_mix");
            if (mix != null && s != null) {
                BlockPos p = pos(a, y, 0);
                CRC32 crc = new CRC32();
                crc.update(("(" + p.getX() + ", " + p.getY() + ", " + p.getZ() + ")").getBytes());
                double h = (crc.getValue() % 10000) / 10000.0;
                double acc = 0.0;
                for (Map.Entry<String, Double> share : shares(mix).entrySet()) {
                    acc += share.getValue();
                    if (h < acc) {
                        s = share.getKey();
                        break;
                    }
                }
            }
            b.put(pos(a, y, 0), s);
        }

        void glass(int a, int y) {
            put(a, y, b.m.get("glass"), 0);
        }

        void glass(int a, int y, int d) {
            put(a, y, b.m.get("glass"), d);
        }

        void accent(int a, int y) {
            accent(a, y, 0, "y");
        }

        void accent(int a, int y, int d, String axis) {
            put(a, y, BlockStr.oriented(b.m.get("accent"), axis.equals("a") ? axisA() : axis), d);
        }

        void stair(int a, int y, String back, String half, int d) {
            stair(a, y, back, half, d, "trim");
        }

        void stair(int a, int y, String back, String half, int d, String role) {
            String id = b.m.get(role);
            put(a, y, id == null ? null : BlockStr.stair(id, word(back), half, "straight"), d);
        }

        /** An upside-down stair run one out, back to the wall: a hood mould. */
        void hood(int a0, int a1, int y) {
            close();
            for (int a = a0; a <= a1; a++) {
                stair(a, y, "in", "top", 1);
            }
            close();
        }

        void sill(int a0, int a1, int y) {
            String sill = b.m.get("sill");
            if (sill != null) {
                close();
                for (int a = a0; a <= a1; a++) {
                    put(a, y, BlockStr.slab(sill, "top"), 1);
                }
                close();
            }
        }

        /** Open trapdoor shutters either side of a window; {@code false} when no shutter is chosen. */
        boolean shutters(int a0, int a1, int y0, int y1) {
            String sid = b.m.get("shutter");
            if (sid == null) {
                return false;
            }
            close();
            for (int a : new int[]{a0 - 1, a1 + 1}) {
                for (int y = y0; y <= y1; y++) {
                    put(a, y, sid + "[facing=" + side.facing + ",half=bottom,open=true,powered=false,waterlogged=false]", 1);
                }
            }
            close();
            return true;
        }

        /** Ends the current relief element: the district keeps or drops each one whole. */
        void close() {
            if (!group.isEmpty()) {
                b.relief.add(new HashSet<>(group));
                group.clear();
            }
        }
    }

    /** {@code id=share,id=share} -> ordered shares (qckit sorts the mix by id). */
    static Map<String, Double> shares(String mix) {
        Map<String, Double> out = new java.util.TreeMap<>();
        for (String part : mix.split(",")) {
            int eq = part.lastIndexOf('=');
            if (eq <= 0) {
                continue;
            }
            try {
                out.put(part.substring(0, eq).trim(), Double.parseDouble(part.substring(eq + 1).trim()));
            } catch (NumberFormatException ignored) {
                // a malformed share is skipped
            }
        }
        return out;
    }

    // --- the pieces --------------------------------------------------------------------------

    private static void fill(Face f) {
        for (int a = 1; a <= 7; a++) {
            for (int y = 1; y <= 7; y++) {
                f.wall(a, y);
            }
        }
    }

    static void framed(Face f) {
        fill(f);
        for (int a = 3; a <= 5; a++) {
            for (int y = 2; y <= 4; y++) {
                f.glass(a, y);
            }
        }
        if (!f.shutters(3, 5, 2, 4)) {
            for (int y = 2; y <= 4; y++) {
                f.accent(2, y);
                f.accent(6, y);
            }
        }
        f.accent(4, 5);
        f.sill(3, 5, 1);
        f.hood(2, 6, 5);
    }

    static void arched(Face f) {
        fill(f);
        for (int a = 3; a <= 5; a++) {
            for (int y = 2; y <= 4; y++) {
                f.glass(a, y);
            }
        }
        f.glass(4, 5);
        f.stair(3, 5, "-a", "top", 0);
        f.stair(5, 5, "+a", "top", 0);
        f.accent(4, 6);
        for (int y = 2; y <= 4; y++) {
            f.accent(2, y);
            f.accent(6, y);
        }
        f.sill(3, 5, 1);
    }

    static void twin(Face f) {
        fill(f);
        for (int a : new int[]{2, 3, 5, 6}) {
            for (int y = 2; y <= 5; y++) {
                f.glass(a, y);
            }
        }
        for (int y = 2; y <= 5; y++) {
            f.accent(4, y);
        }
        f.sill(2, 3, 1);
        f.sill(5, 6, 1);
        f.hood(1, 7, 6);
    }

    static void lancet(Face f) {
        fill(f);
        int[][] lights = {{2, 4}, {4, 5}, {6, 4}};
        for (int[] light : lights) {
            for (int y = 2; y <= light[1]; y++) {
                f.glass(light[0], y);
            }
            f.accent(light[0], light[1] + 1);
        }
        f.sill(2, 6, 1);
    }

    static void oculus(Face f) {
        fill(f);
        int[][] glass = {{4, 3}, {3, 4}, {4, 4}, {5, 4}, {4, 5}};
        for (int[] g : glass) {
            f.glass(g[0], g[1]);
        }
        int[][] accent = {{4, 2}, {3, 3}, {5, 3}, {2, 4}, {6, 4}, {3, 5}, {5, 5}, {4, 6}};
        for (int[] g : accent) {
            f.accent(g[0], g[1]);
        }
    }

    static void bay(Face f) {
        if (f.ground() && f.kind.equals("road")) {
            framed(f);
            return;
        }
        fill(f);
        for (int a = 3; a <= 5; a++) {
            for (int y = 2; y <= 5; y++) {
                f.glass(a, y);
                f.glass(a, y, 1);
            }
        }
        for (int y = 1; y <= 5; y++) {
            f.accent(2, y, 1, "y");
            f.accent(6, y, 1, "y");
        }
        for (int a = 3; a <= 5; a++) {
            f.accent(a, 1, 1, "a");
        }
        for (int a = 2; a <= 6; a++) {
            f.stair(a, 6, "in", "bottom", 1);
        }
        f.accent(4, 6);
    }

    static void french(Face f) {
        fill(f);
        for (int a = 3; a <= 5; a++) {
            for (int y = 1; y <= 4; y++) {
                f.glass(a, y);
            }
        }
        f.accent(4, 5);
        for (int y = 1; y <= 4; y++) {
            f.accent(2, y);
            f.accent(6, y);
        }
        f.hood(2, 6, 5);
    }

    static void balcony(Face f) {
        french(f);
        if (f.ground()) {
            return;
        }
        String bal = f.b.m.get("balcony");
        String rail = f.b.m.get("rail");
        for (int a = 2; a <= 6; a++) {
            for (int d = 1; d <= 2; d++) {
                f.put(a, 0, bal == null ? null : BlockStr.slab(bal, "top"), d);
            }
            f.put(a, 1, rail, 2);
        }
        f.put(2, 1, rail, 1);
        f.put(6, 1, rail, 1);
        for (int a : new int[]{2, 6}) {
            f.stair(a, -1, "in", "top", 1);
        }
    }

    static void timber(Face f) {
        String[] rows = {"WWWAWWW", "WGGAGGW", "WGGAGGW", "AAAAAAA", "WAWAWAW", "WAWAWAW", "WWWAWWW"};
        for (int i = 0; i < rows.length; i++) {
            int y = 7 - i;
            for (int a = 1; a <= 7; a++) {
                char ch = rows[i].charAt(a - 1);
                if (ch == 'G') {
                    f.glass(a, y);
                } else if (ch == 'W') {
                    f.wall(a, y);
                } else {
                    f.accent(a, y, 0, y == 4 ? "a" : "y");
                }
            }
        }
        f.sill(2, 3, 4);
        f.sill(5, 6, 4);
    }

    static void arcade(Face f) {
        fill(f);
        for (int a = 2; a <= 6; a++) {
            for (int y = 1; y <= 4; y++) {
                f.glass(a, y);
            }
        }
        for (int a = 3; a <= 5; a++) {
            f.glass(a, 5);
        }
        f.glass(4, 6);
        f.stair(2, 5, "-a", "top", 0);
        f.stair(6, 5, "+a", "top", 0);
        f.stair(3, 6, "-a", "top", 0);
        f.stair(5, 6, "+a", "top", 0);
        f.accent(4, 7);
    }

    static void mullioned(Face f) {
        for (int a = 1; a <= 7; a++) {
            for (int y = 1; y <= 7; y++) {
                f.glass(a, y);
            }
        }
        for (int y = 1; y <= 7; y++) {
            f.accent(4, y);
        }
        for (int a = 1; a <= 7; a++) {
            if (a != 4) {
                f.accent(a, 5, 0, "a");
            }
        }
    }

    static void pilastered(Face f) {
        framed(f);
        for (int y = 1; y <= 6; y++) {
            f.accent(1, y, 1, "y");
            f.accent(7, y, 1, "y");
        }
        f.stair(1, 7, "in", "top", 1);
        f.stair(7, 7, "in", "top", 1);
    }

    static void louvred(Face f) {
        fill(f);
        for (int a = 2; a <= 6; a++) {
            for (int y = 5; y <= 6; y++) {
                if (a == 3 || a == 5) {
                    f.accent(a, y);
                } else {
                    f.glass(a, y);
                }
            }
        }
        for (int a = 1; a <= 7; a++) {
            f.accent(a, 4, 0, "a");
        }
        f.hood(1, 7, 7);
    }

    static void diaper(Face f) {
        for (int a = 1; a <= 7; a++) {
            for (int y = 1; y <= 7; y++) {
                if ((a + y) % 4 == 0 || Math.floorMod(a - y, 4) == 0) {
                    f.accent(a, y);
                } else {
                    f.wall(a, y);
                }
            }
        }
    }

    /** The door bay's dress: jambs, lintel, transom and canopy, or a round head round an archway. */
    static void portal(Building b, Building.Unit u, Side side, int width) {
        Face f = new Face(b, u, side, "road");
        if (width == 1) {
            for (int y = 1; y <= 3; y++) {
                f.accent(3, y);
                f.accent(5, y);
            }
            f.glass(4, 3);
            for (int a = 3; a <= 5; a++) {
                f.accent(a, 4, 0, "a");
            }
            for (int a = 3; a <= 5; a++) {
                f.stair(a, 5, "in", "bottom", 1);
            }
        } else {
            for (int y = 1; y <= 4; y++) {
                f.accent(2, y);
                f.accent(6, y);
            }
            f.stair(3, 3, "-a", "top", 0);
            f.stair(5, 3, "+a", "top", 0);
            for (int a : new int[]{3, 5}) {
                f.accent(a, 4, 0, "a");
            }
            f.accent(4, 4);
            for (int a = 2; a <= 6; a++) {
                f.stair(a, 5, "in", "bottom", 1);
            }
        }
        f.close();
    }
}
