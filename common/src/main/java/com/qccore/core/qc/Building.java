package com.qccore.core.qc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;

/**
 * One QC building generated from a {@link Design} and a style, ported from qckit's
 * {@code building.py}: units on the 8-grid -> blocks, in the house style.
 *
 * <pre>
 *   QC role        here
 *   framework      frame on every vertical edge, beam on the horizontal edges at each storey plane
 *   floor          floor 7x7 at every storey plane (the storey below's ceiling)
 *   outer wall     exterior faces 1 thick ON the plane, glazed by the facade (mask or piece)
 *   inner wall     a party wall on the shared plane                 (partition "walls")
 *   gate           the same with a 3x3 passage at floor level      (partition "rooms")
 *   inner column   a plus-shaped capital at y1 and y7 round an open-hall crossing
 *   row            one block outside every storey plane of an exposed face, the roof plane included
 *   column         piers dense / 2x2: the outward quoin; thick: 2 out at every exposed vertical edge
 *   wall           the plinth ring one under the lowest floor
 * </pre>
 * plus doors on road faces, a stair flight between storeys, lights, sills, the roof and the crown of
 * thick columns.
 *
 * <p>Coordinates are plan-local: unit {@code (cx, s, cz)} ({@code s} = the editor layer) has its
 * origin at {@code (8cx, 8s, 8cz)}. The lowest layer of the building is its ground storey; a column
 * whose lowest unit stands higher is a <em>span</em> (a bridge or an arch over a road), and the road
 * under it stays the road's. Everything outside the building's own columns is outward decoration,
 * handed back in {@link #outward} so the district can drop what would land on a road or a neighbour.
 */
public final class Building {

    /** A unit of the building: grid column and layer. */
    public record Unit(int x, int s, int z) {
        Unit up() {
            return new Unit(x, s + 1, z);
        }
    }

    /** What a building needs to know about its surroundings. */
    public interface Context {
        /** A road cell lies at {@code (cx, layer, cz)}. */
        boolean road(int cx, int layer, int cz);

        /** Another building has a unit at {@code (cx, layer, cz)}. */
        boolean occupied(int cx, int layer, int cz);

        /** The highest top plane ({@code 8 * (layer + 1)}) another building reaches over a column, or {@code MIN_VALUE}. */
        int top(int cx, int cz);
    }

    /**
     * The partition override of a shared side ({@code open | wall | gate}), or {@code null} for the design's;
     * and the door override of a unit side ({@code here | never}, or {@code null}: the design places the doors).
     */
    @FunctionalInterface
    public interface Party {
        String override(Unit u, Side side);

        default String door(Unit u, Side side) {
            return null;
        }
    }

    private static final Comparator<Unit> ORDER = Comparator.comparingInt(Unit::x).thenComparingInt(Unit::s)
            .thenComparingInt(Unit::z);

    final String name;
    final Design t;
    final StyleReader m;
    final Context ctx;
    final Party party;
    final TreeSet<Unit> units = new TreeSet<>(ORDER);
    /** Column -> lowest layer. */
    final Map<Long, Integer> base = new HashMap<>();
    final int groundLayer;
    final Set<Long> cols = new HashSet<>();
    final Set<Long> allcols = new HashSet<>();

    /** The building's blocks, plan-local; insertion order is write order. */
    public final Map<BlockPos, String> blocks = new LinkedHashMap<>();
    /** Positions outside the building's own columns (cornice, sill, eave, pier). */
    public final Set<BlockPos> outward = new HashSet<>();
    /** Outward pier block -> what it overwrote ({@code null}: nothing). */
    public final Map<BlockPos, String> pier = new HashMap<>();
    /** Plan columns of thick columns: kept over roads. */
    public final Set<Long> thick = new HashSet<>();
    /** Thick columns that reach down to the ground storey: the road gives way under them. */
    public final Set<Long> groundThick = new HashSet<>();
    public final List<Door> doors = new ArrayList<>();
    final List<Door> flights = new ArrayList<>();
    /** Plan columns in front of a door: the road keeps them free of furniture. */
    public final Set<Long> openings = new HashSet<>();
    public final List<String> notes = new ArrayList<>();
    /** Relief elements of facade pieces, kept or dropped whole. */
    public final List<Set<BlockPos>> relief = new ArrayList<>();
    final Set<Door> pieceFaces = new HashSet<>();
    private final List<Object[]> thickTops = new ArrayList<>();

    /** A door (or flight) on a side of a unit. */
    public record Door(Unit u, Side side) {
    }

    public Building(String name, Set<Unit> units, Design design, StyleReader style, Context ctx, Party party) {
        this.name = name;
        this.t = design;
        this.m = style;
        this.ctx = ctx;
        this.party = party;
        this.units.addAll(units);
        int g = Integer.MAX_VALUE;
        for (Unit u : units) {
            base.merge(key(u.x, u.z), u.s, Math::min);
            g = Math.min(g, u.s);
        }
        this.groundLayer = g;
        for (Map.Entry<Long, Integer> entry : base.entrySet()) {
            int cx = kx(entry.getKey());
            int cz = kz(entry.getKey());
            for (int x = 0; x <= 8; x++) {
                for (int z = 0; z <= 8; z++) {
                    allcols.add(key(cx * 8 + x, cz * 8 + z));
                    if (entry.getValue() == groundLayer) {
                        cols.add(key(cx * 8 + x, cz * 8 + z));
                    }
                }
            }
        }
    }

    // -- topology ---------------------------------------------------------------------------

    boolean ground(Unit u) {
        return u.s == groundLayer;
    }

    int top() {
        int top = Integer.MIN_VALUE;
        for (Unit u : units) {
            top = Math.max(top, u.s);
        }
        return top;
    }

    Unit nb(Unit u, Side side) {
        Unit v = new Unit(u.x + side.dx, u.s, u.z + side.dz);
        return units.contains(v) ? v : null;
    }

    /** {@code inner | roof} (our own lower roof) {@code | blind} (a neighbour's wall) {@code | road | open}. */
    String faceKind(Unit u, Side side) {
        if (nb(u, side) != null) {
            return "inner";
        }
        int cx = u.x + side.dx;
        int cz = u.z + side.dz;
        Integer b = base.get(key(cx, cz));
        if (b != null && u.s >= b) {
            return "roof";
        }
        if (ctx.occupied(cx, u.s, cz)) {
            return "blind";
        }
        if (ctx.road(cx, u.s, cz)) {
            return "road";
        }
        return "open";
    }

    /** {@code open | wall | gate} for a shared side, looked up from either unit. */
    String partyKind(Unit u, Side side) {
        String own = party.override(u, side);
        if (own == null) {
            Unit v = nb(u, side);
            if (v != null) {
                own = party.override(v, side.opp());
            }
        }
        if (own != null) {
            return own;
        }
        return switch (t.str("partition", "rooms")) {
            case "open" -> "open";
            case "walls" -> "wall";
            default -> "gate";
        };
    }

    // -- coordinates ------------------------------------------------------------------------

    BlockPos at(Unit u, int x, int y, int z) {
        return new BlockPos(u.x * 8 + x, u.s * 8 + y, u.z * 8 + z);
    }

    BlockPos face(Unit u, Side side, int a, int y) {
        return switch (side) {
            case N -> at(u, a, y, 0);
            case S -> at(u, a, y, 8);
            case W -> at(u, 0, y, a);
            case E -> at(u, 8, y, a);
        };
    }

    BlockPos out(Unit u, Side side, int a, int y, int d) {
        BlockPos p = face(u, side, a, y);
        return p.offset(side.dx * d, 0, side.dz * d);
    }

    BlockPos inside(Unit u, Side side, int a, int y, int d) {
        return out(u, side, a, y, -d);
    }

    // -- writing ----------------------------------------------------------------------------

    void put(BlockPos p, String s) {
        if (s != null) {
            blocks.put(p, s);
            if (!cols.contains(key(p.getX(), p.getZ()))) {
                outward.add(p);
            }
        }
    }

    void putIfAbsent(BlockPos p, String s) {
        if (!blocks.containsKey(p)) {
            put(p, s);
        } else if (s != null && pier.containsKey(p) && pier.get(p) == null) {
            pier.put(p, s);
        }
    }

    private void set(BlockPos p, String s) {
        if (s != null) {
            blocks.put(p, s);
        }
    }

    // -- the build --------------------------------------------------------------------------

    public Building build() {
        if (units.isEmpty()) {
            return this;
        }
        for (Unit u : units) {
            floor(u);
        }
        for (Unit u : units) {
            for (Side side : Side.ALL) {
                side(u, side);
            }
        }
        for (Unit u : units) {
            framework(u);
        }
        capitals();
        placeDoors();
        if (t.flag("portal")) {
            int width = doorWidth();
            for (Door d : doors) {
                Facades.portal(this, d.u, d.side, width);
            }
        }
        if (t.flag("stairs")) {
            stairs();
        }
        if (!t.str("lights", "wall").equals("none")) {
            for (Unit u : units) {
                lights(u);
            }
        }
        if (t.flag("row") && m.get("row") != null) {
            rows();
        }
        String piers = t.str("piers", "normal");
        if (piers.equals("dense") || piers.equals("2x2")) {
            piers();
        } else if (piers.equals("thick")) {
            thickPiers();
        }
        if (t.flag("plinth") && m.get("plinth") != null) {
            plinth();
        }
        roofs();
        if (piers.equals("thick")) {
            crown();
        }
        return this;
    }

    private int doorWidth() {
        return t.integer("door_width", 1) == 3 || m.get("door") == null ? 3 : 1;
    }

    private void floor(Unit u) {
        String floor = m.get("floor");
        for (int y : new int[]{0, 8}) {
            for (int x = 1; x <= 7; x++) {
                for (int z = 1; z <= 7; z++) {
                    put(at(u, x, y, z), floor);
                }
            }
        }
    }

    private List<String> facade(Unit u) {
        if (ground(u)) {
            return t.rhythm("facade_ground");
        }
        return units.contains(u.up()) ? t.rhythm("facade") : t.rhythm("facade_top");
    }

    /**
     * The facade or piece for a face: blind faces their own, then the end units of a run of three or
     * more, then the storey's facade - a rhythm mirrored about the run's middle ({@code A|B} over
     * five units reads A B A B A).
     */
    String facadeName(Unit u, Side side, String k) {
        if (k.equals("blind")) {
            List<String> blind = t.rhythm("facade_blind");
            return blind.isEmpty() ? "blind" : blind.get(0);
        }
        List<String> f = facade(u);
        if (f.isEmpty()) {
            f = List.of("blind");
        }
        String end = t.get("facade_end");
        if (f.size() > 1 || end != null) {
            int[] run = runPosition(u, side);
            int i = run[0];
            int n = run[1];
            if (end != null && n >= 3 && (i == 0 || i == n - 1)) {
                return end;
            }
            return f.get(Math.min(i, n - 1 - i) % f.size());
        }
        return f.get(0);
    }

    /** {@code (i, n)}: u's place along the run of units sharing this exterior face kind. */
    int[] runPosition(Unit u, Side side) {
        Side along = side.along();
        String k = faceKind(u, side);
        int i = 0;
        Unit v = new Unit(u.x - along.dx, u.s, u.z - along.dz);
        while (units.contains(v) && faceKind(v, side).equals(k)) {
            i++;
            v = new Unit(v.x - along.dx, v.s, v.z - along.dz);
        }
        int n = i + 1;
        v = new Unit(u.x + along.dx, u.s, u.z + along.dz);
        while (units.contains(v) && faceKind(v, side).equals(k)) {
            n++;
            v = new Unit(v.x + along.dx, v.s, v.z + along.dz);
        }
        return new int[]{i, n};
    }

    private void side(Unit u, Side side) {
        String k = faceKind(u, side);
        if (k.equals("inner")) {
            if (side != Side.S && side != Side.E) {
                return;
            }
            String pk = partyKind(u, side);
            if (pk.equals("open")) {
                return;
            }
            String partyId = m.get("party");
            for (int a = 1; a <= 7; a++) {
                for (int y = 1; y <= 7; y++) {
                    if (pk.equals("gate") && 3 <= a && a <= 5 && y <= 3) {
                        continue;
                    }
                    put(face(u, side, a, y), partyId);
                }
            }
            return;
        }
        String name = facadeName(u, side, k);
        if (Facades.isPiece(name)) {
            Facades.place(this, u, side, k, name);
            pieceFaces.add(new Door(u, side));
            return;
        }
        Facades.Mask pattern = Facades.mask(name);
        if (pattern == null) {
            pattern = Facades.mask("blind");
        }
        String wall = ground(u) ? m.get("wall") : m.get("wall_upper");
        String glass = m.get("glass");
        for (int a = 1; a <= 7; a++) {
            for (int y = 1; y <= 7; y++) {
                put(face(u, side, a, y), pattern.glass(a, y) ? glass : wall);
            }
        }
        if (t.flag("sills") && !k.equals("blind")) {
            String sill = m.get("sill");
            if (sill == null) {
                return;
            }
            int lowest = Integer.MAX_VALUE;
            for (int y = 1; y <= 7; y++) {
                for (int a = 1; a <= 7; a++) {
                    if (pattern.glass(a, y)) {
                        lowest = Math.min(lowest, y);
                    }
                }
            }
            if (lowest != Integer.MAX_VALUE && lowest >= 2) {
                for (int a = 1; a <= 7; a++) {
                    if (pattern.glass(a, lowest)) {
                        putIfAbsent(out(u, side, a, lowest - 1, 1), BlockStr.slab(sill, "top"));
                    }
                }
            }
        }
    }

    private void framework(Unit u) {
        String frame = BlockStr.oriented(m.get("frame"), "y");
        String beam = m.get("beam");
        for (int x : new int[]{0, 8}) {
            for (int z : new int[]{0, 8}) {
                for (int y = 0; y <= 8; y++) {
                    put(at(u, x, y, z), frame);
                }
            }
        }
        for (int y : new int[]{0, 8}) {
            for (int a = 1; a <= 7; a++) {
                for (int z : new int[]{0, 8}) {
                    put(at(u, a, y, z), BlockStr.oriented(beam, "x"));
                }
                for (int x : new int[]{0, 8}) {
                    put(at(u, x, y, a), BlockStr.oriented(beam, "z"));
                }
            }
        }
    }

    /** QC inner column: round every crossing of four units that share no wall. */
    private void capitals() {
        for (Unit u : units) {
            Unit e = nb(u, Side.E);
            Unit s = nb(u, Side.S);
            Unit se = e == null ? null : nb(e, Side.S);
            if (e == null || s == null || se == null) {
                continue;
            }
            if (!partyKind(u, Side.E).equals("open") || !partyKind(u, Side.S).equals("open")
                    || !partyKind(e, Side.S).equals("open") || !partyKind(s, Side.E).equals("open")) {
                continue;
            }
            String capital = m.get("capital");
            int x = u.x * 8 + 8;
            int y0 = u.s * 8;
            int z = u.z * 8 + 8;
            for (int y : new int[]{1, 7}) {
                for (Side d : Side.ALL) {
                    put(new BlockPos(x + d.dx, y0 + y, z + d.dz), capital);
                }
            }
        }
    }

    // -- doors ------------------------------------------------------------------------------

    private static final Side[] DOOR_SIDES = {Side.S, Side.E, Side.W, Side.N};

    private List<Door> planDoors() {
        List<Unit> groundUnits = new ArrayList<>();
        for (Unit u : units) {
            if (ground(u)) {
                groundUnits.add(u);
            }
        }
        // the player's door overrides (tweak tool): "here" puts a door on a road or open ground face, "never" keeps one off
        List<Door> forced = new ArrayList<>();
        for (Unit u : units) {
            for (Side s : DOOR_SIDES) {
                if (!"here".equals(party.door(u, s))) {
                    continue;
                }
                String kind = faceKind(u, s);
                if (!ground(u)) {
                    notes.add("door at " + u.x + "," + u.s + "," + u.z + " " + s.facing + " left out: doors go on the ground storey");
                } else if (kind.equals("road") || kind.equals("open")) {
                    forced.add(new Door(u, s));
                } else {
                    notes.add("door at " + u.x + "," + u.s + "," + u.z + " " + s.facing + " left out: the face is " + kind);
                }
            }
        }
        List<Door> cands = new ArrayList<>();
        for (Side s : DOOR_SIDES) {
            for (Unit u : groundUnits) {
                if (faceKind(u, s).equals("road") && !"never".equals(party.door(u, s))) {
                    cands.add(new Door(u, s));
                }
            }
        }
        if (cands.isEmpty() && forced.isEmpty()) {
            for (Side s : DOOR_SIDES) {
                for (Unit u : groundUnits) {
                    if (faceKind(u, s).equals("open") && !"never".equals(party.door(u, s))) {
                        cands.add(new Door(u, s));
                    }
                }
            }
            if (!cands.isEmpty()) {
                notes.add("no road frontage: door on an open face");
            }
        }
        if (cands.isEmpty() && forced.isEmpty()) {
            notes.add("no door: every ground face is inner or blind");
            return List.of();
        }
        if (t.str("doors", "one").equals("every")) {
            Set<Unit> seen = new HashSet<>();
            List<Door> out = new ArrayList<>(forced);
            for (Door d : forced) {
                seen.add(d.u);
            }
            for (Door d : cands) {
                if (seen.add(d.u)) {
                    out.add(d);
                }
            }
            return out;
        }
        if (!forced.isEmpty() || cands.isEmpty()) {
            return forced;
        }
        Side side = cands.get(0).side;
        List<Unit> run = new ArrayList<>();
        for (Door d : cands) {
            if (d.side == side) {
                run.add(d.u);
            }
        }
        run.sort(ORDER);
        return List.of(new Door(run.get(run.size() / 2), side));
    }

    private void placeDoors() {
        int width = doorWidth();
        for (Door d : planDoors()) {
            doors.add(d);
            if (width == 3) {
                for (int a = 3; a <= 5; a++) {
                    for (int y = 1; y <= 3; y++) {
                        blocks.remove(face(d.u, d.side, a, y));
                    }
                }
            } else {
                String door = m.get("door");
                Side walkIn = d.side.opp();
                set(face(d.u, d.side, 4, 1), BlockStr.door(door, walkIn, "lower"));
                set(face(d.u, d.side, 4, 2), BlockStr.door(door, walkIn, "upper"));
            }
            for (int a = 3; a <= 5; a++) {
                for (int dd = 1; dd <= 3; dd++) {
                    BlockPos p = out(d.u, d.side, a, 0, dd);
                    openings.add(key(p.getX(), p.getZ()));
                }
            }
        }
    }

    // -- circulation ------------------------------------------------------------------------

    /** Connected units of layer s, through open sides and gates. */
    Map<Unit, Integer> groups(int s) {
        Map<Unit, Integer> gid = new HashMap<>();
        int n = 0;
        for (Unit u : units) {
            if (u.s != s || gid.containsKey(u)) {
                continue;
            }
            gid.put(u, n);
            Deque<Unit> todo = new ArrayDeque<>();
            todo.push(u);
            while (!todo.isEmpty()) {
                Unit q = todo.pop();
                for (Side side : Side.ALL) {
                    Unit v = nb(q, side);
                    if (v != null && !gid.containsKey(v) && !partyKind(q, side).equals("wall")) {
                        gid.put(v, n);
                        todo.push(v);
                    }
                }
            }
            n++;
        }
        return gid;
    }

    /**
     * One flight per storey change per connected group, and one more for every group of the storey
     * above that the first did not reach.
     */
    private void stairs() {
        Set<Door> doorSet = new HashSet<>(doors);
        int top = top();
        for (int s = groundLayer; s < top; s++) {
            Map<Unit, Integer> gid = groups(s);
            Map<Unit, Integer> gup = groups(s + 1);
            Set<Integer> done = new HashSet<>();
            Set<Integer> reached = new HashSet<>();
            List<Unit> order = new ArrayList<>(gid.keySet());
            order.sort(Comparator.comparingInt(Unit::x).thenComparingInt(Unit::z));
            for (Unit u : order) {
                Unit v = u.up();
                if (!units.contains(v) || done.contains(gid.get(u)) && reached.contains(gup.get(v))) {
                    continue;
                }
                Side best = null;
                int bestRank = Integer.MAX_VALUE;
                for (Side side : new Side[]{Side.W, Side.N, Side.E, Side.S}) {
                    if (doorSet.contains(new Door(u, side))) {
                        continue;
                    }
                    String kl = faceKind(u, side);
                    String ku = faceKind(v, side);
                    if (kl.equals("inner") && !partyKind(u, side).equals("wall")) {
                        continue;
                    }
                    if (ku.equals("inner") && !partyKind(v, side).equals("wall")) {
                        continue;
                    }
                    // (kl != blind, side letter): blind faces first, then E < N < S < W
                    int rank = (kl.equals("blind") ? 0 : 10) + "ENSW".indexOf(side.name());
                    if (rank < bestRank) {
                        bestRank = rank;
                        best = side;
                    }
                }
                if (best == null) {
                    continue;
                }
                flight(u, best);
                done.add(gid.get(u));
                reached.add(gup.get(v));
            }
            int groups = new HashSet<>(gid.values()).size();
            if (done.size() < groups) {
                notes.add("layer " + s + ": " + (groups - done.size()) + " group(s) with no stair up");
            }
        }
    }

    /** Seven steps up the inside of a side, then an eighth set into the floor plane, turning into the room. */
    private void flight(Unit u, Side side) {
        flights.add(new Door(u, side));
        Side rise = side == Side.N || side == Side.S ? Side.W : Side.N;
        String st = m.get("flight");
        for (int i = 1; i <= 7; i++) {
            int a = 8 - i;
            set(inside(u, side, a, i, 1), BlockStr.stair(st, rise));
            for (int h = 1; h <= 2; h++) {
                if (i + h <= 9) {
                    blocks.remove(inside(u, side, a, i + h, 1));
                }
            }
        }
        set(inside(u, side, 1, 8, 2), BlockStr.stair(st, side.opp()));
        for (int a = 1; a <= 3; a++) {
            blocks.remove(inside(u, side, a, 8, 1));
        }
        for (int h = 9; h <= 10; h++) {
            blocks.remove(inside(u, side, 1, h, 2));
        }
    }

    private void lights(Unit u) {
        String light = m.get("light");
        String lights = t.str("lights", "wall");
        int walled = 0;
        for (Side side : Side.ALL) {
            String k = faceKind(u, side);
            if (k.equals("inner") && partyKind(u, side).equals("open")) {
                continue;
            }
            walled++;
            if (!lights.equals("wall") || flights.contains(new Door(u, side)) || light == null) {
                continue;
            }
            BlockPos facePos = face(u, side, 4, 7);
            String fs = blocks.get(facePos);
            String glass = m.peek("glass");
            if (fs == null || glass != null && BlockStr.base(fs).equals(BlockStr.base(glass))) {
                continue;
            }
            String lb = BlockStr.base(light);
            if (lb.endsWith("wall_lantern") || lb.endsWith("wall_torch")) {
                BlockPos p = inside(u, side, 4, 7, 1);
                if (!blocks.containsKey(p)) {
                    set(p, BlockStr.wallLight(light, side.opp()));
                }
            } else if (lb.equals("minecraft:lantern")) {
                BlockPos p = inside(u, side, 4, 7, 1);
                if (!blocks.containsKey(p)) {
                    set(p, BlockStr.LANTERN_HANG);
                }
            } else if (pieceFaces.contains(new Door(u, side))) {
                Set<String> walls = new HashSet<>();
                for (String role : new String[]{"wall", "wall_upper"}) {
                    String id = m.peek(role);
                    if (id != null) {
                        walls.add(BlockStr.base(id));
                    }
                }
                for (String role : new String[]{"wall_mix", "wall_upper_mix"}) {
                    String mix = m.peek(role);
                    if (mix != null) {
                        for (String id : Facades.shares(mix).keySet()) {
                            walls.add(BlockStr.base(id));
                        }
                    }
                }
                for (int y : new int[]{6, 7}) {
                    BlockPos q = face(u, side, 4, y);
                    if (blocks.containsKey(q) && walls.contains(BlockStr.base(blocks.get(q)))) {
                        set(q, light);
                        break;
                    }
                }
            } else {
                set(face(u, side, 4, 6), light);
            }
        }
        if (walled < 2 || lights.equals("centre")) {
            BlockPos p = at(u, 4, 7, 4);
            if (!blocks.containsKey(p)) {
                String h = m.get("hang");
                set(p, h != null && BlockStr.base(h).equals("minecraft:lantern") ? BlockStr.LANTERN_HANG : h);
            }
        }
    }

    // -- skin -------------------------------------------------------------------------------

    /** The row one block out on every intermediate storey plane of an exposed face. */
    private void rows() {
        String row = m.get("row");
        for (Unit u : units) {
            Unit up = u.up();
            if (!units.contains(up)) {
                continue;
            }
            for (Side side : Side.ALL) {
                String k = faceKind(u, side);
                String ku = faceKind(up, side);
                if (k.equals("inner") || k.equals("blind") || ku.equals("inner") || ku.equals("blind")) {
                    continue;
                }
                for (int a = 0; a <= 8; a++) {
                    putIfAbsent(out(u, side, a, 8, 1), BlockStr.cornice(row, side));
                }
            }
        }
        for (int s = groundLayer; s < top(); s++) {
            for (int[] c : corners(s)) {
                Side sx = c[2] < 0 ? Side.W : Side.E;
                Side sz = c[3] < 0 ? Side.N : Side.S;
                int y = s * 8 + 8;
                if (BlockStr.cornice(row, sx).equals(blocks.get(new BlockPos(c[0] + sx.dx, y, c[1])))
                        && BlockStr.cornice(row, sz).equals(blocks.get(new BlockPos(c[0], y, c[1] + sz.dz)))) {
                    putIfAbsent(new BlockPos(c[0] + sx.dx, y, c[1] + sz.dz), BlockStr.corniceCorner(row, sx, sz));
                }
            }
        }
    }

    /** Convex corners of layer s's footprint: {@code (x, z, sx.dx, sz.dz)}. */
    List<int[]> corners(int s) {
        List<int[]> out = new ArrayList<>();
        for (Unit u : units) {
            if (u.s != s) {
                continue;
            }
            for (Side sx : new Side[]{Side.W, Side.E}) {
                for (Side sz : new Side[]{Side.N, Side.S}) {
                    if (nb(u, sx) != null || nb(u, sz) != null) {
                        continue;
                    }
                    out.add(new int[]{u.x * 8 + (sx == Side.W ? 0 : 8), u.z * 8 + (sz == Side.N ? 0 : 8), sx.dx, sz.dz});
                }
            }
        }
        return out;
    }

    private void piers() {
        String quoin = BlockStr.oriented(m.get("quoin"), "y");
        boolean twoByTwo = t.str("piers", "normal").equals("2x2");
        for (int s = groundLayer; s <= top(); s++) {
            for (int[] c : corners(s)) {
                int x = c[0];
                int z = c[1];
                List<long[]> pcols = new ArrayList<>();
                pcols.add(new long[]{x + c[2], z});
                pcols.add(new long[]{x, z + c[3]});
                if (twoByTwo) {
                    pcols.add(new long[]{x + c[2], z + c[3]});
                }
                for (long[] pc : pcols) {
                    int px = (int) pc[0];
                    int pz = (int) pc[1];
                    if (cols.contains(key(px, pz))) {
                        continue;
                    }
                    int y0 = s == groundLayer ? s * 8 : s * 8 + 1;
                    boolean last = true;
                    for (Unit u : units) {
                        if (u.s == s && u.x * 8 <= x && x <= u.x * 8 + 8 && u.z * 8 <= z && z <= u.z * 8 + 8
                                && units.contains(u.up())) {
                            last = false;
                        }
                    }
                    int y1 = last ? s * 8 + 7 : s * 8 + 8;
                    for (int y = y0; y <= y1; y++) {
                        BlockPos p = new BlockPos(px, y, pz);
                        if (!pier.containsKey(p)) {
                            pier.put(p, blocks.get(p));
                        }
                        put(p, quoin);
                    }
                }
            }
        }
    }

    /** Column -> {kind (0 arm, 1 diag), ox, oz, depth} of QCSimple's thick column at layer s. */
    private Map<Long, int[]> thickArms(int s) {
        Set<Long> foot = new HashSet<>();
        for (Unit u : units) {
            if (u.s == s) {
                foot.add(key(u.x, u.z));
            }
        }
        Set<Long> pts = new TreeSet<>();
        for (long c : foot) {
            for (int i = 0; i <= 1; i++) {
                for (int k = 0; k <= 1; k++) {
                    pts.add(key((kx(c) + i) * 8, (kz(c) + k) * 8));
                }
            }
        }
        Map<Long, int[]> out = new LinkedHashMap<>();
        for (long pt : pts) {
            int bx = kx(pt);
            int bz = kz(pt);
            int gx = Math.floorDiv(bx, 8);
            int gz = Math.floorDiv(bz, 8);
            Map<Integer, Boolean> q = new LinkedHashMap<>();
            for (int dx : new int[]{-1, 1}) {
                for (int dz : new int[]{-1, 1}) {
                    q.put(dx * 3 + dz, foot.contains(key(gx + Math.floorDiv(dx - 1, 2), gz + Math.floorDiv(dz - 1, 2))));
                }
            }
            for (int d : new int[]{-1, 1}) {
                if (!q.get(d * 3 - 1) && !q.get(d * 3 + 1)) {
                    out.put(key(bx + d, bz), new int[]{0, d, 0, 1});
                    out.put(key(bx + 2 * d, bz), new int[]{0, d, 0, 2});
                }
                if (!q.get(-3 + d) && !q.get(3 + d)) {
                    out.put(key(bx, bz + d), new int[]{0, 0, d, 1});
                    out.put(key(bx, bz + 2 * d), new int[]{0, 0, d, 2});
                }
            }
            int mine = 0;
            int mdx = 0;
            int mdz = 0;
            for (int dx : new int[]{-1, 1}) {
                for (int dz : new int[]{-1, 1}) {
                    if (q.get(dx * 3 + dz)) {
                        mine++;
                        mdx = dx;
                        mdz = dz;
                    }
                }
            }
            if (mine == 1) {
                out.put(key(bx - mdx, bz - mdz), new int[]{1, -mdx, -mdz, 1});
            }
        }
        return out;
    }

    private void thickPiers() {
        int top = top();
        String quoin = BlockStr.oriented(m.get("quoin"), "y");
        Map<Integer, Map<Long, int[]>> arms = new HashMap<>();
        for (int s = groundLayer; s <= top + 1; s++) {
            arms.put(s, thickArms(s));
        }
        for (int s = groundLayer + 1; s <= top; s++) {
            Map<Long, int[]> below = arms.get(s - 1);
            arms.get(s).keySet().removeIf(c -> !allcols.contains(c) && !below.containsKey(c));
        }
        for (int s = groundLayer; s <= top; s++) {
            Map<Long, int[]> sorted = new TreeMap<>(Comparator.comparingInt(Building::kx).thenComparingInt(Building::kz));
            sorted.putAll(arms.get(s));
            for (Map.Entry<Long, int[]> entry : sorted.entrySet()) {
                long c = entry.getKey();
                int px = kx(c);
                int pz = kz(c);
                boolean above = arms.get(s + 1).containsKey(c);
                int y0 = s == groundLayer ? s * 8 : s * 8 + 1;
                int y1 = above ? s * 8 + 8 : s * 8 + 7;
                if (!above) {
                    int[] k = entry.getValue();
                    thickTops.add(new Object[]{px, pz, s, k});
                }
                for (int y = y0; y <= y1; y++) {
                    BlockPos p = new BlockPos(px, y, pz);
                    if (!pier.containsKey(p)) {
                        pier.put(p, blocks.get(p));
                    }
                    put(p, quoin);
                }
                thick.add(c);
                if (s == groundLayer) {
                    groundThick.add(c);
                }
            }
        }
    }

    private void plinth() {
        String plinth = m.get("plinth");
        for (Unit u : units) {
            if (!ground(u)) {
                continue;
            }
            for (Side side : Side.ALL) {
                if (faceKind(u, side).equals("inner")) {
                    continue;
                }
                for (int a = 0; a <= 8; a++) {
                    putIfAbsent(face(u, side, a, -1), plinth);
                }
            }
        }
    }

    // -- roofs ------------------------------------------------------------------------------

    private void roofs() {
        String kind = t.str("roof", "hip");
        Map<Integer, Set<Unit>> byY = new TreeMap<>();
        for (Unit u : units) {
            if (!units.contains(u.up())) {
                byY.computeIfAbsent(u.s * 8 + 8, k -> new LinkedHashSet<>()).add(u);
            }
        }
        for (Map.Entry<Integer, Set<Unit>> entry : byY.entrySet()) {
            Set<Unit> left = new LinkedHashSet<>(entry.getValue());
            while (!left.isEmpty()) {
                Unit seed = left.iterator().next();
                left.remove(seed);
                Set<Unit> reg = new LinkedHashSet<>();
                reg.add(seed);
                Deque<Unit> todo = new ArrayDeque<>();
                todo.push(seed);
                while (!todo.isEmpty()) {
                    Unit q = todo.pop();
                    for (Side side : Side.ALL) {
                        Unit v = nb(q, side);
                        if (v != null && left.remove(v)) {
                            reg.add(v);
                            todo.push(v);
                        }
                    }
                }
                Roofs.build(kind, this, reg, entry.getKey());
            }
        }
    }

    static Set<Long> footprint(Iterable<Unit> us) {
        Set<Long> f = new HashSet<>();
        for (Unit u : us) {
            for (int x = 0; x <= 8; x++) {
                for (int z = 0; z <= 8; z++) {
                    f.add(key(u.x * 8 + x, u.z * 8 + z));
                }
            }
        }
        return f;
    }

    /** Columns a roof at plane Y must not touch: our taller units, a neighbour building within {@code reach}. */
    Set<Long> blocked(Set<Long> f, int y, int reach) {
        List<Unit> taller = new ArrayList<>();
        for (Unit u : units) {
            if (u.s * 8 >= y) {
                taller.add(u);
            }
        }
        Set<Long> blocked = footprint(taller);
        blocked.removeAll(f);
        Set<Long> around = new HashSet<>();
        for (long c : f) {
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    around.add(key(kx(c) + dx, kz(c) + dz));
                }
            }
        }
        for (long c : around) {
            if (f.contains(c) || cols.contains(c)) {
                continue;
            }
            int x = kx(c);
            int z = kz(c);
            for (int e = 0; e <= 1; e++) {
                for (int g = 0; g <= 1; g++) {
                    int cx = Math.floorDiv(x - e, 8);
                    int cz = Math.floorDiv(z - g, 8);
                    if (base.containsKey(key(cx, cz))) {
                        continue;
                    }
                    if (ctx.top(cx, cz) >= y) {
                        blocked.add(c);
                    }
                }
            }
        }
        return blocked;
    }

    /** The edge of F, inside corners included. */
    static Set<Long> ring(Set<Long> f) {
        Set<Long> out = new HashSet<>();
        for (long c : f) {
            if (!all8(f, c)) {
                out.add(c);
            }
        }
        return out;
    }

    private static boolean all8(Set<Long> f, long c) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!f.contains(key(kx(c) + dx, kz(c) + dz))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** F plus the n rings outside it, minus blocked columns, and no ring block against a taller unit. */
    static Set<Long> skirt(Set<Long> f, Set<Long> blocked, int n) {
        Set<Long> e = new HashSet<>(f);
        for (int i = 0; i < n; i++) {
            Set<Long> grown = new HashSet<>();
            for (long c : e) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        grown.add(key(kx(c) + dx, kz(c) + dz));
                    }
                }
            }
            grown.removeAll(blocked);
            Set<Long> kept = new HashSet<>();
            for (long c : grown) {
                boolean touches = false;
                for (Side s : Side.ALL) {
                    touches |= blocked.contains(key(kx(c) + s.dx, kz(c) + s.dz));
                }
                if (f.contains(c) || !touches) {
                    kept.add(c);
                }
            }
            e = kept;
        }
        return e;
    }

    /**
     * The roof-level cornice: the row one block outside the wall at plane Y on every column of
     * {@code ring}, turning convex corners on the diagonal and closing concave ones with an
     * inner-corner stair. Uses {@code row}, or {@code coping} where no row is chosen.
     */
    void roofCornice(Set<Long> f, Set<Long> ring, int y) {
        String sid = m.get("row");
        if (sid == null) {
            sid = m.get("coping");
        }
        if (sid == null) {
            return;
        }
        List<Long> sorted = new ArrayList<>(ring);
        sorted.sort(Comparator.comparingInt(Building::kx).thenComparingInt(Building::kz));
        for (long c : sorted) {
            int x = kx(c);
            int z = kz(c);
            List<Side> out = new ArrayList<>();
            for (Side s : Side.ALL) {
                if (f.contains(key(x - s.dx, z - s.dz))) {
                    out.add(s);
                }
            }
            String blk;
            if (out.size() == 1) {
                blk = BlockStr.cornice(sid, out.get(0));
            } else if (out.size() == 2 && out.get(0).dx != out.get(1).dx) {
                blk = BlockStr.corniceInner(sid, out.get(0), out.get(1));
            } else if (out.isEmpty()) {
                int found = 0;
                int fdx = 0;
                int fdz = 0;
                for (int dx : new int[]{-1, 1}) {
                    for (int dz : new int[]{-1, 1}) {
                        if (f.contains(key(x - dx, z - dz))) {
                            found++;
                            fdx = dx;
                            fdz = dz;
                        }
                    }
                }
                if (found != 1) {
                    continue;
                }
                blk = BlockStr.corniceCorner(sid, fdx > 0 ? Side.E : Side.W, fdz > 0 ? Side.S : Side.N);
            } else {
                continue;
            }
            putIfAbsent(new BlockPos(x, y, z), blk);
        }
    }

    /** The axis a gable's ridge runs along: {@code roof_axis} if set, else the region's longer side. */
    String ridgeAxis(Set<Long> f) {
        String ax = t.str("roof_axis", "auto");
        if (ax.equals("x") || ax.equals("z")) {
            return ax;
        }
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (long c : f) {
            minX = Math.min(minX, kx(c));
            maxX = Math.max(maxX, kx(c));
            minZ = Math.min(minZ, kz(c));
            maxZ = Math.max(maxZ, kz(c));
        }
        return maxX - minX >= maxZ - minZ ? "x" : "z";
    }

    /**
     * Every sloped roof. The kinds differ in how far a column is up the slope (hip and mansard from
     * the nearest edge in any direction; gable and gambrel across the ridge only, with gable walls)
     * and in the height profile (hip and gable one up per step to {@code roof_rise}; gambrel and
     * mansard two up per step for {@code roof_steep} steps, then one up or flat).
     */
    void pitched(Set<Unit> reg, int y, String kind) {
        Set<Long> f = footprint(reg);
        Set<Long> blocked = blocked(f, y, 2);
        String coping = m.get("coping");
        for (long c : ring(f)) {
            if (blocked.contains(c)) {
                continue;
            }
            List<Side> lower = new ArrayList<>();
            for (Side s : Side.ALL) {
                if (!f.contains(key(kx(c) + s.dx, kz(c) + s.dz))) {
                    lower.add(s);
                }
            }
            boolean against = false;
            for (Side s : lower) {
                against |= blocked.contains(key(kx(c) + s.dx, kz(c) + s.dz));
            }
            if (lower.isEmpty() || against) {
                continue;
            }
            put(new BlockPos(kx(c), y, kz(c)), BlockStr.stair(coping, lower.get(0)));
        }
        Set<Long> skirt1 = skirt(f, blocked, 1);
        skirt1.removeAll(f);
        roofCornice(f, skirt1, y);
        Set<Long> e = skirt(f, blocked, 2);
        boolean twoSlope = kind.equals("gable") || kind.equals("gambrel");
        Map<Long, Integer> d = new HashMap<>();
        List<Side> slopeDirs = new ArrayList<>();
        int stepX = 0;
        int stepZ = 0;
        if (twoSlope) {
            boolean alongX = ridgeAxis(f).equals("x");
            stepX = alongX ? 0 : 1;
            stepZ = alongX ? 1 : 0;
            double mid = 0;
            for (long c : f) {
                mid += alongX ? kx(c) : kz(c);
            }
            mid /= f.size();
            while (true) {
                Set<Long> lone = new HashSet<>();
                for (long c : e) {
                    if (f.contains(c)) {
                        continue;
                    }
                    int x = kx(c);
                    int z = kz(c);
                    long inner = alongX ? key(x + (x < mid ? 1 : -1), z) : key(x, z + (z < mid ? 1 : -1));
                    if (!e.contains(inner)) {
                        lone.add(c);
                    }
                }
                if (lone.isEmpty()) {
                    break;
                }
                e.removeAll(lone);
            }
            for (Side s : Side.ALL) {
                if (Math.abs(s.dx) == stepX && Math.abs(s.dz) == stepZ) {
                    slopeDirs.add(s);
                }
            }
            for (long c : e) {
                int least = Integer.MAX_VALUE;
                for (int sg : new int[]{1, -1}) {
                    int k = 0;
                    while (e.contains(key(kx(c) + sg * stepX * (k + 1), kz(c) + sg * stepZ * (k + 1)))) {
                        k++;
                    }
                    least = Math.min(least, k);
                }
                d.put(c, least + 1);
            }
        } else {
            slopeDirs.addAll(List.of(Side.ALL));
            List<Long> frontier = new ArrayList<>();
            for (long c : e) {
                if (!all8(e, c)) {
                    frontier.add(c);
                    d.put(c, 1);
                }
            }
            int k = 1;
            while (!frontier.isEmpty()) {
                List<Long> next = new ArrayList<>();
                for (long c : frontier) {
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            long n = key(kx(c) + dx, kz(c) + dz);
                            if (e.contains(n) && !d.containsKey(n)) {
                                d.put(n, k + 1);
                                next.add(n);
                            }
                        }
                    }
                }
                frontier = next;
                k++;
            }
        }
        int steep = Math.max(1, t.integer("roof_steep", 2));
        int rise = Math.max(1, t.integer("roof_rise", 6));
        Map<Long, Integer> h = new HashMap<>();
        for (long c : e) {
            int n = d.get(c);
            int p;
            if (kind.equals("gambrel") || kind.equals("mansard")) {
                if (n <= steep) {
                    p = 2 * n - 1;
                } else if (kind.equals("mansard")) {
                    p = 2 * steep - 1;
                } else {
                    p = Math.min(2 * steep - 1 + n - steep, Math.max(rise, 2 * steep - 1));
                }
            } else {
                p = Math.min(n, rise);
            }
            h.put(c, y + p);
        }
        String roof = m.get("roof");
        String roofFull = m.get("roof_full");
        String roofSlab = m.get("roof_slab");
        List<Long> order = new ArrayList<>(e);
        order.sort(Comparator.comparingInt(Building::kx).thenComparingInt(Building::kz));
        for (long c : order) {
            int x = kx(c);
            int z = kz(c);
            int hy = h.get(c);
            List<Side> low = new ArrayList<>();
            for (Side s : slopeDirs) {
                if (h.getOrDefault(key(x + s.dx, z + s.dz), y) < hy) {
                    low.add(s);
                }
            }
            String s;
            if (low.size() == 1) {
                s = BlockStr.stair(roof, low.get(0).opp());
            } else if (low.size() == 2 && low.get(0).dx != low.get(1).dx && low.get(0).dz != low.get(1).dz
                    && h.getOrDefault(key(x - low.get(0).dx - low.get(1).dx, z - low.get(0).dz - low.get(1).dz), y) > hy) {
                Side fac = low.get(0).opp();
                String shape = low.get(1).opp() == fac.ccw() ? "outer_left" : "outer_right";
                s = BlockStr.stair(roof, fac, "bottom", shape);
            } else if (low.isEmpty() && anyDiagonalLower(h, x, z, y, hy)) {
                s = roofFull;
            } else {
                s = BlockStr.slab(roofSlab, "bottom");
            }
            putIfAbsent(new BlockPos(x, hy, z), s);
            int under = hy;
            for (Side sd : slopeDirs) {
                Integer nh = h.get(key(x + sd.dx, z + sd.dz));
                if (nh != null) {
                    under = Math.min(under, nh);
                }
            }
            for (int yy = Math.max(under + 1, y + 1); yy < hy; yy++) {
                putIfAbsent(new BlockPos(x, yy, z), roofFull);
            }
        }
        if (twoSlope) {
            String gable = m.peek("gable");
            if (gable == null) {
                gable = m.get("wall_upper");
            }
            for (long c : f) {
                if (blocked.contains(c) || !h.containsKey(c)) {
                    continue;
                }
                int x = kx(c);
                int z = kz(c);
                long endA = key(x + stepZ, z + stepX);
                long endB = key(x - stepZ, z - stepX);
                if (f.contains(endA) && f.contains(endB)) {
                    continue;
                }
                // an end against a taller neighbour is that neighbour's wall, not a gable
                if (!f.contains(endA) && blocked.contains(endA) || !f.contains(endB) && blocked.contains(endB)) {
                    continue;
                }
                for (int yy = y + 1; yy < h.get(c); yy++) {
                    putIfAbsent(new BlockPos(x, yy, z), gable);
                }
            }
        }
    }

    private static boolean anyDiagonalLower(Map<Long, Integer> h, int x, int z, int y, int hy) {
        for (int dx : new int[]{-1, 1}) {
            for (int dz : new int[]{-1, 1}) {
                if (h.getOrDefault(key(x + dx, z + dz), y) < hy) {
                    return true;
                }
            }
        }
        return false;
    }

    void flat(Set<Unit> reg, int y, boolean garden) {
        Set<Long> f = footprint(reg);
        Set<Long> blocked = blocked(f, y, 1);
        Set<Long> skirt1 = skirt(f, blocked, 1);
        skirt1.removeAll(f);
        roofCornice(f, skirt1, y);
        String deck = m.get("deck");
        for (Unit u : reg) {
            for (int x = 1; x <= 7; x++) {
                for (int z = 1; z <= 7; z++) {
                    put(at(u, x, 8, z), deck);
                }
            }
            if (garden) {
                String bed = m.get("bed");
                for (int x = 3; x <= 5; x++) {
                    for (int z = 3; z <= 5; z++) {
                        put(at(u, x, 8, z), bed);
                    }
                }
                put(at(u, 4, 9, 4), BlockStr.leaves(m.get("plant")));
            }
        }
        Set<Long> edge = ring(f);
        edge.removeAll(blocked);
        String parapet = m.get("parapet");
        for (long c : edge) {
            putIfAbsent(new BlockPos(kx(c), y + 1, kz(c)), parapet);
        }
        for (long c : edge) {
            List<Side> conn = new ArrayList<>();
            for (Side s : Side.ALL) {
                if (edge.contains(key(kx(c) + s.dx, kz(c) + s.dz))) {
                    conn.add(s);
                }
            }
            if (conn.size() == 2 && conn.get(0).opp() != conn.get(1)) {
                putIfAbsent(new BlockPos(kx(c), y + 2, kz(c)), BlockStr.LANTERN);
            }
        }
    }

    /** No roof: the cornice still runs. */
    void bare(Set<Unit> reg, int y) {
        Set<Long> f = footprint(reg);
        Set<Long> skirt1 = skirt(f, blocked(f, y, 1), 1);
        skirt1.removeAll(f);
        roofCornice(f, skirt1, y);
    }

    /**
     * Every thick column ends the way the White Palace's do: the outer layer runs up to the roof
     * plane, the 1-out layer one more, the axis on the wall plane one more again - a stepped head in
     * {@code crown} - with an optional {@code finial} on each 1-out top.
     */
    private void crown() {
        String fin = m.peek("finial");
        String crownId = BlockStr.oriented(m.get("crown"), "y");
        Set<BlockPos> axes = new LinkedHashSet<>();
        int lift = Roofs.isPitched(t.str("roof", "hip")) ? 2 : 0;
        for (Object[] top : thickTops) {
            int px = (int) top[0];
            int pz = (int) top[1];
            int s = (int) top[2];
            int[] k = (int[]) top[3];
            boolean diag = k[0] == 1;
            int ox = k[1];
            int oz = k[2];
            int depth = k[3];
            boolean covered = false;
            for (Unit u : units) {
                int ax = px - ox * depth;
                int az = pz - oz * depth;
                if (u.s == s && u.x * 8 <= ax && ax <= u.x * 8 + 8 && u.z * 8 <= az && az <= u.z * 8 + 8
                        && units.contains(u.up())) {
                    covered = true;
                }
            }
            if (covered) {
                continue;
            }
            int y = s * 8 + 8;
            int topY = (diag || depth == 2 ? y : y + 1) + lift;
            for (int yy = y; yy <= topY; yy++) {
                put(new BlockPos(px, yy, pz), crownId);
            }
            if (diag) {
                BlockPos notch = new BlockPos(px, topY + 1, pz);
                blocks.remove(notch);
                outward.remove(notch);
                for (BlockPos q : new BlockPos[]{new BlockPos(px + ox, y + 1, pz), new BlockPos(px, y + 1, pz + oz),
                        new BlockPos(px + ox, y + 1, pz + oz)}) {
                    if (lift > 0 && blocks.containsKey(q) && !thick.contains(key(q.getX(), q.getZ()))) {
                        blocks.remove(q);
                        outward.remove(q);
                    }
                }
            }
            if (!diag && depth == 1) {
                axes.add(new BlockPos(px - ox, y, pz - oz));
                if (fin != null) {
                    put(new BlockPos(px, topY + 1, pz), fin);
                }
            }
        }
        for (BlockPos p : axes) {
            if (!blocks.containsKey(p)) {
                continue;
            }
            for (int yy = p.getY(); yy < p.getY() + 3 + lift; yy++) {
                put(new BlockPos(p.getX(), yy, p.getZ()), crownId);
            }
        }
    }

    // -- column keys ------------------------------------------------------------------------

    public static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    public static int kx(long key) {
        return (int) (key >> 32);
    }

    public static int kz(long key) {
        return (int) key;
    }

    /** The building's own ground columns (spans left out). */
    public Set<Long> cols() {
        return cols;
    }

    public Set<Unit> units() {
        return units;
    }

    public String name() {
        return name;
    }

    /** Plan-local position in front of a door, at walk level. */
    public BlockPos doorStep(Door d) {
        return out(d.u, d.side, 4, 1, 1);
    }

    /** Units no walk reaches from a door, through open sides and gates and up the flights. */
    public List<Unit> unreachable() {
        Set<Unit> seen = new HashSet<>();
        Deque<Unit> todo = new ArrayDeque<>();
        for (Door d : doors) {
            if (seen.add(d.u)) {
                todo.push(d.u);
            }
        }
        while (!todo.isEmpty()) {
            Unit u = todo.pop();
            List<Unit> next = new ArrayList<>();
            for (Side s : Side.ALL) {
                Unit v = nb(u, s);
                if (v != null && !partyKind(u, s).equals("wall")) {
                    next.add(v);
                }
            }
            for (Door f : flights) {
                if (f.u.equals(u)) {
                    next.add(u.up());
                }
                if (f.u.up().equals(u)) {
                    next.add(f.u);
                }
            }
            for (Unit v : next) {
                if (seen.add(v)) {
                    todo.push(v);
                }
            }
        }
        List<Unit> lost = new ArrayList<>();
        for (Unit u : units) {
            if (!seen.contains(u)) {
                lost.add(u);
            }
        }
        return lost;
    }
}
