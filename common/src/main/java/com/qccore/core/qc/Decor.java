package com.qccore.core.qc;

import com.qccore.core.UnitPos;
import com.qccore.core.UnitSide;
import com.qccore.core.plan.RoadKit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.CRC32;
import net.minecraft.core.BlockPos;

/**
 * Street life, ported from qckit's {@code decor.py}: small props on the ground round the buildings,
 * chosen per unit, so a district stops reading as stamped out. Everything stands on a road's floor
 * (one block up, in the road's own columns), never in a building's cell, never in a door's approach.
 *
 * <ul>
 *   <li><b>frontage</b>: each ground unit's face onto a road gets 0-2 props in the two lanes against
 *       it; the kind follows the building's character ({@link #registerCharacter}): a works yard stacks
 *       crates and logs, a house has planters and a bench, a hall benches and lamps, an arcade a stall;
 *       a door face gets potted plants beside the door; no prop twice running along a building;</li>
 *   <li><b>yards</b>: a road cell whose middle is soil (grass, moss, podzol, mycelium) is open ground:
 *       a tree, bushes, flowers;</li>
 *   <li><b>nodes</b>: a bend, tee or crossing of a paved street may get a fountain or a tree on its medallion;</li>
 *   <li><b>edges</b>: a street's side with nothing beyond it is a frontage too, facing in, a little sparser.</li>
 * </ul>
 * The blocks come from a style of kind {@link Roles#DECOR}; a prop whose roles are not all chosen is
 * never placed, so there is no default. A role may list several ids ({@code a,b,c}), picked per prop.
 * Seeded: the same plan and seed dress the same street every time (not qckit's exact sequence: Java's
 * random differs from Python's).
 */
public final class Decor {

    private static final String[] YARD = {"grass_block", "moss_block", "podzol", "mycelium"};
    private static final String[] SOIL = {"grass_block", "dirt", "coarse_dirt", "podzol", "moss_block",
            "rooted_dirt", "mud", "mycelium", "farmland"};

    private static final Map<String, Map<String, Integer>> CHARACTER = new LinkedHashMap<>();
    private static final Map<String, String> TEMPLATE_CHARACTER = new LinkedHashMap<>();
    private static final Map<String, List<String>> NEEDS = new LinkedHashMap<>();

    static {
        character("home", "planter=3,bench=2,pots=2,woodpile=2,bush=2,crates=1,lamp=1,flowers=2");
        character("trade", "stall=4,crates=3,bench=1,pots=2,planter=1,lamp=1");
        character("works", "crates=4,woodpile=3,lamp=2,bush=1,bench=1");
        character("civic", "bench=3,planter=3,bush=2,lamp=2,pots=1,flowers=1");
        character("street", "bench=2,lamp=2,planter=2,bush=2,crates=1,stall=1,woodpile=1,flowers=1");
        for (String t : new String[]{"house", "townhouse", "cottage", "loft", "qc_raw"}) {
            registerCharacter(t, "home");
        }
        for (String t : new String[]{"shop", "villa"}) {
            registerCharacter(t, "trade");
        }
        for (String t : new String[]{"warehouse", "workshop"}) {
            registerCharacter(t, "works");
        }
        for (String t : new String[]{"hall", "chapel", "academy", "library", "manor", "tower", "office"}) {
            registerCharacter(t, "civic");
        }
        NEEDS.put("pots", List.of("pot"));
        NEEDS.put("planter", List.of("planter", "flower"));
        NEEDS.put("bench", List.of("seat"));
        NEEDS.put("crates", List.of("goods"));
        NEEDS.put("woodpile", List.of("logs"));
        NEEDS.put("bush", List.of("bush"));
        NEEDS.put("lamp", List.of("post", "lamp"));
        NEEDS.put("stall", List.of("post", "awning", "goods"));
        NEEDS.put("flowers", List.of("flower"));
        NEEDS.put("tree", List.of("trunk", "crown"));
        NEEDS.put("fountain", List.of("basin"));
    }

    /** Registers (or replaces) a character: prop kind -> weight, as {@code kind=w,kind=w}. */
    public static void character(String name, String weights) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (String pair : weights.split(",")) {
            String[] kv = pair.split("=");
            map.put(kv[0].trim(), Integer.parseInt(kv[1].trim()));
        }
        CHARACTER.put(name, map);
    }

    /** Says which character a template's frontages have (unknown templates are {@code home}). */
    public static void registerCharacter(String template, String character) {
        TEMPLATE_CHARACTER.put(template, character);
    }

    // --- one dressing pass: one road layer -------------------------------------------------

    private final Map<BlockPos, String> out;
    private final Map<BlockPos, String> owner;
    private final Map<String, String> blocks = new LinkedHashMap<>();
    private final double density;
    private final int seed;
    private final int base;
    private final int layer;
    private final Map<Long, UnitPos> area;
    private final Map<Long, Integer> lane;
    private final Set<UnitPos> roadCells;
    private final Set<Long> openings;
    private final Map<String, Building> made;
    private final Map<String, String> templates;
    private final Set<UnitPos> occupied;
    private final Set<Long> used = new HashSet<>();
    final Map<String, Integer> count = new TreeMap<>();
    int placed;

    private Decor(Map<BlockPos, String> out, Map<BlockPos, String> owner, BlockChoice style, double density, int seed,
                  int layer, Set<UnitPos> roadCells, Set<Long> openings, Map<String, Building> made,
                  Map<String, String> templates, Set<UnitPos> occupied) {
        this.out = out;
        this.owner = owner;
        style.blocks().forEach((role, id) -> {
            if (id != null && !id.isBlank()) {
                this.blocks.put(role, id.trim());
            }
        });
        this.density = density;
        this.seed = seed;
        this.layer = layer;
        this.base = layer * 8;
        this.roadCells = roadCells;
        this.area = RoadKit.networkArea(roadCells);
        this.lane = RoadKit.lanes(area.keySet());
        this.openings = openings;
        this.made = made;
        this.templates = templates;
        this.occupied = occupied;
    }

    /**
     * Dresses one road layer into {@code out}/{@code owner} (in place, before the connection pass).
     *
     * @param occupied cells of this layer that hold a unit (any building): the map, for the edges
     * @return {kind: props} placed
     */
    static Map<String, Integer> dress(Map<BlockPos, String> out, Map<BlockPos, String> owner, BlockChoice style,
                                      double density, int seed, int layer, Set<UnitPos> roadCells, Set<Long> openings,
                                      Map<String, Building> made, Map<String, String> templates, Set<UnitPos> occupied) {
        Decor d = new Decor(out, owner, style, density, seed, layer, roadCells, openings, made, templates, occupied);
        d.frontages();
        d.yardsAndNodes();
        return d.count;
    }

    // -- helpers ----------------------------------------------------------------------------

    private Random rng(Object... key) {
        StringBuilder text = new StringBuilder().append(seed);
        for (Object k : key) {
            text.append('|').append(k);
        }
        CRC32 crc = new CRC32();
        crc.update(text.toString().getBytes());
        return new Random(crc.getValue());
    }

    private boolean can(String kind) {
        for (String role : NEEDS.get(kind)) {
            if (!blocks.containsKey(role)) {
                return false;
            }
        }
        return true;
    }

    private String pick(Random r, String role) {
        String[] ids = blocks.get(role).split(",");
        return ids[r.nextInt(ids.length)].trim();
    }

    private static <T> T choice(Random r, List<T> list) {
        return list.get(r.nextInt(list.size()));
    }

    private BlockPos p(int x, int y, int z) {
        return new BlockPos(x, base + y, z);
    }

    private String floor(int x, int z) {
        BlockPos at = p(x, 0, z);
        return "road".equals(owner.get(at)) ? out.get(at) : null;
    }

    private static boolean soil(String s, String[] kinds) {
        if (s == null) {
            return false;
        }
        String b = BlockStr.base(s);
        String name = b.substring(b.indexOf(':') + 1);
        for (String k : kinds) {
            if (k.equals(name)) {
                return true;
            }
        }
        return false;
    }

    /** A road column with its floor laid, not a door's approach, not taken. */
    private boolean groundOk(int x, int z) {
        long c = Building.key(x, z);
        return area.containsKey(c) && floor(x, z) != null && !openings.contains(c) && !used.contains(c);
    }

    private boolean fits(Map<BlockPos, String> cells) {
        for (BlockPos q : cells.keySet()) {
            if (!groundOk(q.getX(), q.getZ()) || out.containsKey(q)) {
                return false;
            }
        }
        return true;
    }

    private void commit(Map<BlockPos, String> cells, String who, String kind, int margin) {
        for (Map.Entry<BlockPos, String> e : cells.entrySet()) {
            out.put(e.getKey(), e.getValue());
            owner.put(e.getKey(), who);
            for (int dx = -margin; dx <= margin; dx++) {
                for (int dz = -margin; dz <= margin; dz++) {
                    used.add(Building.key(e.getKey().getX() + dx, e.getKey().getZ() + dz));
                }
            }
        }
        count.merge(kind, 1, Integer::sum);
        placed += cells.size();
    }

    // -- props at a frontage: (x0, z0) its a = 0 point on the line, `side` the way out ----------

    private record Front(int x0, int z0, Side side) {
        BlockPos at(int a, int d, int y, int base) {
            int ax = side == Side.N || side == Side.S ? 1 : 0;
            int az = 1 - ax;
            return new BlockPos(x0 + a * ax + d * side.dx, base + y, z0 + a * az + d * side.dz);
        }

        int along(BlockPos q) {
            return side == Side.N || side == Side.S ? q.getX() - x0 : q.getZ() - z0;
        }
    }

    /** {@code d0}: the lane the prop's back row stands in. */
    private Map<BlockPos, String> prop(String kind, Front fr, int a, Random r, int d0) {
        Map<BlockPos, String> c = new LinkedHashMap<>();
        String along = fr.side == Side.N || fr.side == Side.S ? "x" : "z";
        int o = d0 - 1;
        switch (kind) {
            case "pots" -> c.put(fr.at(a, 1 + o, 1, base), pick(r, "pot"));
            case "planter" -> {
                int n = r.nextBoolean() ? 2 : 3;
                for (int i = 0; i < n; i++) {
                    c.put(fr.at(a + i, 1 + o, 1, base), pick(r, "planter"));
                    if (r.nextDouble() < 0.85) {
                        c.put(fr.at(a + i, 1 + o, 2, base), pick(r, "flower"));
                    }
                }
            }
            case "bench" -> {
                String seat = pick(r, "seat");
                for (int i = 0; i < 2; i++) {
                    c.put(fr.at(a + i, 1 + o, 1, base), BlockStr.stair(seat, fr.side.opp()));
                }
                if (blocks.containsKey("pot") && r.nextDouble() < 0.5) {
                    c.put(fr.at(a + 2, 1 + o, 1, base), pick(r, "pot"));
                }
            }
            case "crates" -> {
                int n = r.nextBoolean() ? 2 : 3;
                for (int i = 0; i < n; i++) {
                    c.put(fr.at(a + i, 1 + o, 1, base), goods(r));
                }
                if (r.nextDouble() < 0.6) {
                    c.put(fr.at(a + r.nextInt(n), 1 + o, 2, base), goods(r));
                }
                if (r.nextDouble() < 0.4) {
                    c.put(fr.at(a + r.nextInt(n), 2 + o, 1, base), goods(r));
                }
            }
            case "woodpile" -> {
                String log = pick(r, "logs");
                int n = r.nextBoolean() ? 2 : 3;
                for (int i = 0; i < n; i++) {
                    c.put(fr.at(a + i, 1 + o, 1, base), BlockStr.oriented(log, along));
                }
                for (int i = 0; i < n - 1; i++) {
                    c.put(fr.at(a + i + (r.nextDouble() < 0.5 ? 1 : 0), 1 + o, 2, base), BlockStr.oriented(log, along));
                }
            }
            case "bush" -> {
                int n = r.nextBoolean() ? 1 : 2;
                for (int i = 0; i < n; i++) {
                    c.put(fr.at(a + i, 1 + o, 1, base), BlockStr.leaves(pick(r, "bush")));
                }
                if (r.nextDouble() < 0.4) {
                    c.put(fr.at(a, 1 + o, 2, base), BlockStr.leaves(pick(r, "bush")));
                }
            }
            case "lamp" -> {
                String post = pick(r, "post");
                c.put(fr.at(a, 1 + o, 1, base), post);
                c.put(fr.at(a, 1 + o, 2, base), post);
                c.put(fr.at(a, 1 + o, 3, base), lantern(r));
            }
            case "stall" -> {
                String post = pick(r, "post");
                String roof = pick(r, "awning");
                int w = r.nextBoolean() ? 2 : 3;
                for (int i = 0; i < w; i++) {
                    for (int d = 1; d <= 2; d++) {
                        c.put(fr.at(a + i, d + o, 3, base), BlockStr.base(roof).endsWith("_slab") ? BlockStr.slab(roof, "bottom") : roof);
                    }
                }
                for (int i : new int[]{0, w - 1}) {
                    c.put(fr.at(a + i, 1 + o, 1, base), goods(r));
                    c.put(fr.at(a + i, 2 + o, 1, base), post);
                    c.put(fr.at(a + i, 2 + o, 2, base), post);
                }
                if (w == 3) {
                    c.put(fr.at(a + 1, 2 + o, 1, base), goods(r));
                }
            }
            case "flowers" -> {
                int n = r.nextBoolean() ? 2 : 3;
                for (int i = 0; i < n; i++) {
                    for (int d = 1; d <= 2; d++) {
                        BlockPos q = fr.at(a + i, d + o, 1, base);
                        if (r.nextDouble() < 0.6 && soil(floor(q.getX(), q.getZ()), SOIL)) {
                            c.put(q, pick(r, "flower"));
                        }
                    }
                }
            }
            default -> {
            }
        }
        return c;
    }

    private String goods(Random r) {
        String g = pick(r, "goods");
        String b = BlockStr.base(g);
        if (b.equals("minecraft:barrel") && !BlockStr.hasState(g)) {
            return g + "[facing=up,open=false]";
        }
        if (b.equals("minecraft:hay_block") && !BlockStr.hasState(g)) {
            return g + "[axis=y]";
        }
        return g;
    }

    private String lantern(Random r) {
        String lamp = pick(r, "lamp");
        if (BlockStr.base(lamp).endsWith("lantern") && !BlockStr.hasState(lamp)) {
            return lamp + "[hanging=false,waterlogged=false]";
        }
        return lamp;
    }

    // -- frontage ---------------------------------------------------------------------------

    /** Every ground unit's face onto a road, then the outer edges of the streets. */
    private void frontages() {
        for (Map.Entry<String, Building> entry : new TreeMap<>(made).entrySet()) {
            String ch = entry.getKey();
            Building b = entry.getValue();
            if (b.groundLayer != layer) {
                continue;
            }
            String character = TEMPLATE_CHARACTER.getOrDefault(templates.get(ch), "home");
            Map<String, Integer> weights = new LinkedHashMap<>(CHARACTER.get(character));
            String ground = b.t.get("facade_ground") != null ? b.t.get("facade_ground") : b.t.get("facade");
            if ("arcade".equals(ground) || "shopfront".equals(ground)) {
                weights.merge("stall", 3, Integer::sum);
            }
            Set<Building.Door> doors = new HashSet<>(b.doors);
            Deque<String> recent = new ArrayDeque<>();
            for (Building.Unit u : b.units) {
                if (!b.ground(u) || b.base.get(Building.key(u.x(), u.z())) != b.groundLayer) {
                    continue;
                }
                for (Side side : Side.ALL) {
                    if (!b.faceKind(u, side).equals("road")) {
                        continue;
                    }
                    BlockPos origin = b.face(u, side, 0, 0);
                    front(new Front(origin.getX(), origin.getZ(), side), ch, weights,
                            doors.contains(new Building.Door(u, side)), recent, density,
                            "front", ch, u, side);
                }
            }
        }
        Deque<String> recent = new ArrayDeque<>();
        Map<String, Integer> weights = CHARACTER.get("street");
        for (UnitPos c : sorted(roadCells)) {
            if (isYard(c)) {
                continue;
            }
            for (Side s : Side.ALL) {
                UnitPos beyond = new UnitPos(c.x() + s.dx, c.z() + s.dz);
                if (roadCells.contains(beyond) || occupied.contains(beyond)) {
                    continue;
                }
                int ox = c.x() * 8;
                int oz = c.z() * 8;
                int x0 = s == Side.E ? ox + 8 : ox;
                int z0 = s == Side.S ? oz + 8 : oz;
                front(new Front(x0, z0, s.opp()), "road", weights, false, recent, density * 0.7, "edge", c, s);
            }
        }
    }

    private void front(Front fr, String who, Map<String, Integer> weights, boolean door, Deque<String> recent,
                       double density, Object... key) {
        Random r = rng(key);
        List<String> done = new ArrayList<>();
        if (door && can("pots") && r.nextDouble() < Math.min(1.0, density + 0.35)) {
            for (int a : new int[]{2, 6}) {
                if (r.nextDouble() > 0.7) {
                    continue;
                }
                for (int d0 = 1; d0 <= 2; d0++) {
                    Map<BlockPos, String> c = prop("pots", fr, a, rng(cat("pot", key, a)), d0);
                    if (fits(c)) {
                        commit(c, who, "pots", 0);
                        break;
                    }
                }
            }
            done.add("pots");
        }
        int n = 0;
        for (int i = 0; i < (door ? 1 : 2); i++) {
            n += r.nextDouble() < density ? 1 : 0;
        }
        for (int k = 0; k < n; k++) {
            List<String> kinds = new ArrayList<>();
            List<Double> w = new ArrayList<>();
            for (Map.Entry<String, Integer> e : weights.entrySet()) {
                if (e.getValue() > 0 && can(e.getKey()) && !done.contains(e.getKey())) {
                    kinds.add(e.getKey());
                    w.add(e.getValue() * (recent.contains(e.getKey()) ? 0.2 : 1.0));
                }
            }
            if (kinds.isEmpty()) {
                break;
            }
            double total = w.stream().mapToDouble(Double::doubleValue).sum();
            double roll = r.nextDouble() * total;
            String kind = kinds.get(kinds.size() - 1);
            for (int i = 0; i < kinds.size(); i++) {
                roll -= w.get(i);
                if (roll < 0) {
                    kind = kinds.get(i);
                    break;
                }
            }
            search:
            for (int d0 = 1; d0 <= 2; d0++) {
                List<Integer> as = new ArrayList<>(List.of(1, 2, 3, 4, 5, 6, 7));
                Collections.shuffle(as, r);
                for (int a : as) {
                    Map<BlockPos, String> c = prop(kind, fr, a, rng(cat("prop", key, kind + a)), d0);
                    if (c.isEmpty() || !fits(c)) {
                        continue;
                    }
                    boolean inside = true;
                    for (BlockPos q : c.keySet()) {
                        int along = fr.along(q);
                        inside &= along >= 1 && along <= 7;
                    }
                    if (inside) {
                        commit(c, who, kind, 1);
                        done.add(kind);
                        recent.addLast(kind);
                        if (recent.size() > 2) {
                            recent.removeFirst();
                        }
                        break search;
                    }
                }
            }
        }
    }

    private static Object[] cat(String head, Object[] key, Object tail) {
        Object[] out = new Object[key.length + 2];
        out[0] = head;
        System.arraycopy(key, 0, out, 1, key.length);
        out[out.length - 1] = tail;
        return out;
    }

    // -- open ground ------------------------------------------------------------------------

    private boolean isYard(UnitPos c) {
        return soil(floor(c.x() * 8 + 4, c.z() * 8 + 4), YARD);
    }

    private Map<BlockPos, String> tree(int cx, int cz, Random r) {
        String trunk = pick(r, "trunk");
        String crown = BlockStr.leaves(pick(r, "crown"));
        int h = r.nextInt(3) < 2 ? 3 : 4;
        Map<BlockPos, String> c = new LinkedHashMap<>();
        for (int y = 1; y <= h; y++) {
            c.put(p(cx, y, cz), BlockStr.oriented(trunk, "y"));
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) {
                    c.put(p(cx + dx, h, cz + dz), crown);
                }
                c.put(p(cx + dx, h + 1, cz + dz), crown);
            }
        }
        for (int[] d : new int[][]{{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            c.put(p(cx + d[0], h + 2, cz + d[1]), crown);
        }
        if (!groundOk(cx, cz)) {
            return null;
        }
        for (BlockPos q : c.keySet()) {
            long col = Building.key(q.getX(), q.getZ());
            if (out.containsKey(q) || !area.containsKey(col) || openings.contains(col)) {
                return null;
            }
        }
        return c;
    }

    private void yardsAndNodes() {
        for (UnitPos c : sorted(roadCells)) {
            Random r = rng("cell", c);
            int ox = c.x() * 8;
            int oz = c.z() * 8;
            if (isYard(c)) {
                if (can("tree") && r.nextDouble() < density * 0.5) {
                    for (int i = 0; i < 6; i++) {
                        int x = ox + 2 + r.nextInt(5);
                        int z = oz + 2 + r.nextInt(5);
                        if (lane.getOrDefault(Building.key(x, z), 0) >= 3) {
                            Map<BlockPos, String> t = tree(x, z, r);
                            if (t != null) {
                                commit(t, "road", "tree", 1);
                                break;
                            }
                        }
                    }
                }
                List<Long> cols = new ArrayList<>();
                area.forEach((col, cell) -> {
                    if (cell.equals(c)) {
                        cols.add(col);
                    }
                });
                cols.sort((a, b) -> a.equals(b) ? 0 : Building.kx(a) != Building.kx(b)
                        ? Integer.compare(Building.kx(a), Building.kx(b)) : Integer.compare(Building.kz(a), Building.kz(b)));
                for (long col : cols) {
                    int x = Building.kx(col);
                    int z = Building.kz(col);
                    if (!groundOk(x, z) || out.containsKey(p(x, 1, z))) {
                        continue;
                    }
                    if (lane.get(col) == 1 && besideBuilding(x, z)) {
                        continue;
                    }
                    double q = r.nextDouble();
                    if (soil(floor(x, z), SOIL) && can("flowers") && q < density * 0.22) {
                        out.put(p(x, 1, z), pick(r, "flower"));
                        owner.put(p(x, 1, z), "road");
                        count.merge("flowers", 1, Integer::sum);
                        placed++;
                    } else if (can("bush") && q > 1 - density * 0.05) {
                        Map<BlockPos, String> cc = Map.of(p(x, 1, z), BlockStr.leaves(pick(r, "bush")));
                        if (fits(cc)) {
                            commit(cc, "road", "bush", 0);
                        }
                    }
                }
                continue;
            }
            Set<UnitSide> arms = RoadKit.arms(c, roadCells);
            if (RoadKit.isNode(arms) && r.nextDouble() < density * 0.45) {
                int cx = ox + 4;
                int cz = oz + 4;
                List<String> opts = new ArrayList<>();
                for (String k : new String[]{"fountain", "tree"}) {
                    if (can(k)) {
                        opts.add(k);
                    }
                }
                if (opts.isEmpty() || !groundOk(cx, cz)) {
                    continue;
                }
                if (choice(r, opts).equals("tree")) {
                    Map<BlockPos, String> t = tree(cx, cz, r);
                    if (t != null) {
                        commit(t, "road", "tree", 0);
                    }
                } else {
                    String rim = pick(r, "basin");
                    Map<BlockPos, String> cells = new LinkedHashMap<>();
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx != 0 || dz != 0) {
                                cells.put(p(cx + dx, 1, cz + dz), rim);
                            }
                        }
                    }
                    cells.put(p(cx, 1, cz), "minecraft:water[level=0]");
                    if (fits(cells)) {
                        commit(cells, "road", "fountain", 0);
                    }
                }
            }
        }
    }

    private boolean besideBuilding(int x, int z) {
        for (Side s : Side.ALL) {
            long col = Building.key(x + s.dx, z + s.dz);
            for (Building b : made.values()) {
                if (b.allcols.contains(col)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<UnitPos> sorted(Set<UnitPos> cells) {
        List<UnitPos> out = new ArrayList<>(cells);
        out.sort((a, b) -> a.x() != b.x() ? Integer.compare(a.x(), b.x()) : Integer.compare(a.z(), b.z()));
        return out;
    }
}
