package com.qccore.core.qc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The role sets, one per kind of style: the roles of qckit's building template ({@code styles.py}
 * and the facade pieces) and of its lane-addressed road. A new kind of generator registers its own
 * set with {@link #register}; the block chooser lists whatever is registered.
 */
public final class Roles {

    public static final String BUILDING = "building";
    public static final String ROAD = "road";
    public static final String DECOR = "decor";

    private static final Map<String, List<Role>> SETS = new LinkedHashMap<>();

    static {
        register(BUILDING, List.of(
                Role.required("frame", "vertical edges of every unit: the piers (QC framework)"),
                Role.required("beam", "horizontal edges at the storey planes (QC framework)").fallsBackTo("frame"),
                Role.required("floor", "the 7x7 at every storey plane, floor below and ceiling above"),
                Role.required("wall", "exterior faces of the ground storey, on the boundary plane"),
                Role.required("wall_upper", "exterior faces above the ground storey").fallsBackTo("wall"),
                Role.required("party", "walls and gates between two units of one building").fallsBackTo("wall"),
                Role.required("glass", "windows and curtain glass (a full block reads best)"),
                Role.optional("row", "the cornice one block outside every storey plane").hint("stairs"),
                Role.required("coping", "stairs in the wall plane under a pitched roof").hint("stairs"),
                Role.required("roof", "pitched roof stairs").hint("stairs"),
                Role.required("roof_slab", "the flat top and ridge of a pitched roof").hint("slab"),
                Role.required("roof_full", "the valley and steep-face block of a pitched roof"),
                Role.optional("gable", "the gable walls of a gable or gambrel roof").fallsBackTo("wall_upper"),
                Role.optional("sill", "a slab under each window, one outside").hint("slab"),
                Role.required("capital", "the plus-shaped capital round an open-hall crossing"),
                Role.required("light", "wall light at walk+6 on every walled face").hint("light"),
                Role.required("hang", "the centre light of an open bay").hint("light"),
                Role.optional("door", "door id; unset = an open 3-high archway").hint("door"),
                Role.required("parapet", "the flat-roof parapet").hint("wall"),
                Role.required("deck", "the flat-roof surface").fallsBackTo("floor"),
                Role.required("bed", "the roof-garden bed"),
                Role.required("plant", "the plant on the roof-garden bed"),
                Role.required("flight", "the interior stair flights").fallsBackTo("coping").hint("stairs"),
                Role.optional("plinth", "a ring one under the lowest floor"),
                Role.required("quoin", "the outward pier mass (dense, 2x2 and thick piers)").fallsBackTo("frame"),
                Role.required("crown", "the stepped head of a thick column").fallsBackTo("quoin"),
                Role.optional("finial", "a light two above the roof on each crown (end_rod, lantern)").hint("light"),
                Role.required("accent", "facade pieces: jambs, keystones, mullions, timber").fallsBackTo("quoin"),
                Role.required("trim", "facade pieces: hoods, arch shoulders, canopies").fallsBackTo("row|coping").hint("stairs"),
                Role.optional("shutter", "open shutters beside framed windows").hint("trapdoor"),
                Role.required("rail", "the balcony railing").hint("fence"),
                Role.required("balcony", "the balcony floor").fallsBackTo("sill").hint("slab"),
                Role.optional("wall_mix", "weathered blocks through piece walls: id=share,id=share"),
                Role.optional("wall_upper_mix", "the same for a different upper wall: id=share,...")));
        List<Role> road = new ArrayList<>();
        String[] where = {"at the building face", "", "", "on the axis"};
        for (int lane = 1; lane <= 4; lane++) {
            road.add(Role.required("floor" + lane, "lane " + lane + " surface " + where[lane - 1]));
        }
        for (int lane = 1; lane <= 4; lane++) {
            road.add(Role.optional("joint" + lane, "lane " + lane + " on the joint line every 8 blocks"));
        }
        for (int lane = 1; lane <= 4; lane++) {
            road.add(Role.optional("over" + lane, "lane " + lane + " furniture at y1 (a hedge, a parapet)"));
        }
        for (int lane = 1; lane <= 4; lane++) {
            road.add(Role.optional("post" + lane, "lane " + lane + " post on the joint: ids bottom-up, comma separated"));
        }
        road.add(Role.optional("medallion", "the 3x3 at a bend, tee or crossing"));
        road.add(Role.optional("centre", "the medallion's middle block"));
        road.add(Role.optional("flight", "stairs where the street climbs to one a layer up (needed only then)").hint("stairs"));
        register(ROAD, road);
        String list = " (a,b,c picks one per prop)";
        register(DECOR, List.of(
                Role.optional("pot", "potted plants beside doors (minecraft:potted_*)" + list),
                Role.optional("planter", "soil of a raised bed (moss_block, rooted_dirt)" + list),
                Role.optional("flower", "small flowers or short grass on beds and soil" + list),
                Role.optional("seat", "a bench, back to the wall" + list).hint("stairs"),
                Role.optional("goods", "full blocks to stack: barrel, hay_block, composter" + list),
                Role.optional("logs", "a woodpile along the wall" + list),
                Role.optional("bush", "leaves: bushes" + list),
                Role.optional("post", "a lamp or stall post" + list).hint("fence"),
                Role.optional("lamp", "the lamp on a post" + list).hint("light"),
                Role.optional("awning", "a stall's roof (wool, slab)" + list),
                Role.optional("trunk", "a tree's trunk" + list),
                Role.optional("crown", "a tree's leaves" + list),
                Role.optional("basin", "a fountain's rim (full block, slab or wall)" + list)));
    }

    private Roles() {
    }

    /** Registers (or replaces) the roles of a kind of style. */
    public static void register(String kind, List<Role> roles) {
        SETS.put(kind, List.copyOf(roles));
    }

    public static List<Role> of(String kind) {
        return SETS.getOrDefault(kind, List.of());
    }

    public static Role role(String kind, String name) {
        for (Role role : of(kind)) {
            if (role.name().equals(name)) {
                return role;
            }
        }
        return null;
    }

    public static List<String> kinds() {
        return Collections.unmodifiableList(new ArrayList<>(SETS.keySet()));
    }
}
