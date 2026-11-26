package com.qccore.core.qc;

import com.qccore.core.QcUnit;
import com.qccore.core.UnitPos;
import com.qccore.core.UnitSide;
import com.qccore.core.nbt.LayersCodec;
import com.qccore.core.nbt.PlanCodec;
import com.qccore.core.plan.BuildingSpec;
import com.qccore.core.plan.Cell;
import com.qccore.core.plan.Flags;
import com.qccore.core.plan.QcPlan;
import com.qccore.core.plan.RoadKit;
import com.qccore.core.plan.Surroundings;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** The qckit port: buildings from a design and a style, roads round them, the merge rules. */
class DistrictTest {

    /** A block for every building role, of the shape each role expects. */
    static BlockChoice fullStyle() {
        BlockChoice style = new BlockChoice(Roles.BUILDING);
        for (Role role : Roles.of(Roles.BUILDING)) {
            String id = switch (role.hint()) {
                case "stairs" -> "minecraft:" + role.name() + "_stairs";
                case "slab" -> "minecraft:" + role.name() + "_slab";
                case "door" -> "minecraft:oak_door";
                case "trapdoor" -> "minecraft:oak_trapdoor";
                case "fence" -> "minecraft:oak_fence";
                case "wall" -> "minecraft:stone_brick_wall";
                case "light" -> "minecraft:lantern";
                default -> "minecraft:" + role.name() + "_block";
            };
            style.set(role.name(), id);
        }
        style.set("wall_mix", "minecraft:mossy_block=0.2");
        style.set("wall_upper_mix", null);
        return style;
    }

    static BlockChoice road() {
        BlockChoice road = new BlockChoice(Roles.ROAD);
        for (int lane = 1; lane <= 4; lane++) {
            road.set("floor" + lane, "minecraft:lane" + lane);
        }
        road.set("joint1", "minecraft:sea_lantern");
        return road;
    }

    /** A {@code w x d} building of {@code storeys} on layer 1 at the origin, a road row south of it. */
    static QcPlan house(String template, int w, int d, int storeys) {
        QcPlan plan = new QcPlan();
        plan.buildings().put("A", new BuildingSpec(template, "s"));
        plan.styles().put("s", fullStyle());
        plan.styles().put("street", road());
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                for (int s = 1; s <= storeys; s++) {
                    plan.addUnit(new Cell(x, s, z), "A");
                }
            }
        }
        for (int x = -1; x <= w; x++) {
            plan.roads().put(new Cell(x, 1, d), "street");
        }
        return plan;
    }

    @Test
    void aHouseBuildsItsFrameFloorWallsDoorAndRoof() {
        District.Result result = District.build(house("house", 2, 1, 2));
        assertTrue(result.problems().isEmpty(), result.problems().toString());
        Map<BlockPos, String> b = result.blocks();
        // framework: corner posts upright, unit (0, 1, 0) has its origin at (0, 8, 0)
        assertEquals("minecraft:frame_block", BlockStr.base(b.get(new BlockPos(0, 9, 0))));
        assertEquals("minecraft:floor_block", b.get(new BlockPos(4, 8, 4)));
        // one door on the road face (south, z = 8), mid-run
        long doors = b.values().stream().filter(s -> s.startsWith("minecraft:oak_door")).count();
        assertEquals(2, doors, "a 1-wide door is two blocks");
        // the road lies on the layer-1 floor plane, 7 wide, south of the house
        assertEquals("minecraft:lane4", b.get(new BlockPos(4, 8, 12)));
        // a hip roof above the top storey plane (y = 24)
        assertTrue(b.keySet().stream().anyMatch(p -> p.getY() > 24 && b.get(p).contains("roof_stairs")));
        assertFalse(b.values().stream().anyMatch(s -> s.equals("minecraft:mossy_block")),
                "a wall mix weathers facade pieces only, not flat masks");
        // a townhouse's framed ground floor is a piece: the mix shows there
        Map<BlockPos, String> town = District.build(house("townhouse", 3, 1, 2)).blocks();
        assertTrue(town.values().stream().anyMatch(s -> s.equals("minecraft:mossy_block")));
    }

    /** Layers are offsets from the session anchor: a plan moved below layer 1 builds the same blocks, lower (T16). */
    @Test
    void aPlanBelowLayerOneBuildsTheSameBlocksLower() {
        QcPlan high = house("house", 2, 2, 2);
        QcPlan low = high.transformed(0, false, false, 0, -3, 0);
        assertTrue(low.cells().stream().allMatch(cell -> cell.layer() <= 0), "the moved plan should sit below layer 1");
        District.Result a = District.build(high);
        District.Result b = District.build(low);
        assertTrue(a.problems().isEmpty(), a.problems().toString());
        assertEquals(a.problems(), b.problems());
        Map<BlockPos, String> shifted = new java.util.LinkedHashMap<>();
        a.blocks().forEach((pos, id) -> shifted.put(pos.below(24), id));
        assertEquals(shifted, b.blocks());
    }

    /**
     * A gable end against a taller neighbour is that neighbour's wall, not a gable (qckit
     * building.py `pitched`): the gable triangle stays off the shared wall plane, so the
     * neighbour's upper face keeps its own blocks.
     */
    @Test
    void aGableEndAgainstATallerNeighbourBuildsNoGable() {
        QcPlan plan = new QcPlan();
        plan.buildings().put("A", new BuildingSpec("house", "s"));
        plan.buildings().put("B", new BuildingSpec("house", "s"));
        plan.buildings().get("A").options.put("roof", "gable");
        plan.buildings().get("A").options.put("roof_axis", "x");
        plan.styles().put("s", fullStyle());
        plan.styles().put("street", road());
        plan.addUnit(new Cell(0, 1, 0), "A");
        plan.addUnit(new Cell(1, 1, 0), "B");
        plan.addUnit(new Cell(1, 2, 0), "B");
        for (int x = -1; x <= 2; x++) {
            plan.roads().put(new Cell(x, 1, 1), "street");
        }
        District.Result result = District.build(withoutDecor(plan));
        assertTrue(result.problems().isEmpty(), result.problems().toString());
        Map<BlockPos, String> b = result.blocks();
        // A's roof plane is y = 16; its free west end (x = 0) gets a gable wall
        assertTrue(b.entrySet().stream().anyMatch(e -> e.getKey().getX() == 0 && e.getKey().getY() > 16
                && e.getValue().equals("minecraft:gable_block")), "the free end keeps its gable");
        // its east end (x = 8) is B's upper west face: no gable there
        for (Map.Entry<BlockPos, String> e : b.entrySet()) {
            if (e.getKey().getX() == 8 && e.getKey().getY() > 16) {
                assertNotEquals("minecraft:gable_block", e.getValue(), "gable in B's wall at " + e.getKey());
            }
        }
    }

    @Test
    void everyTemplateRoofAndPierBuildsWithoutMissingRoles() {
        for (String template : Templates.names()) {
            if (template.equals(Templates.LEGACY)) {
                continue;
            }
            for (String roof : Roofs.names()) {
                for (String piers : new String[]{"normal", "dense", "2x2", "thick"}) {
                    QcPlan plan = house(template, 3, 2, 3);
                    plan.buildings().get("A").options.put("roof", roof);
                    plan.buildings().get("A").options.put("piers", piers);
                    District.Result result = District.build(plan);
                    String what = template + "/" + roof + "/" + piers;
                    assertTrue(result.problems().isEmpty(), what + ": " + result.problems());
                    assertFalse(result.blocks().isEmpty(), what);
                }
            }
        }
    }

    @Test
    void unchosenRolesAreReportedTogetherAndNothingFallsBackToAPreset() {
        QcPlan plan = house("house", 1, 1, 1);
        plan.styles().put("s", new BlockChoice(Roles.BUILDING));
        District.Result result = District.build(plan);
        assertEquals(1, result.problems().size(), result.problems().toString());
        assertTrue(result.missing().get("s").containsAll(java.util.List.of("frame", "floor", "wall", "glass")));
        assertTrue(result.reached().get("s").contains("roof"), "the reached roles drive the block chooser");
        assertFalse(result.blocks().containsKey(new BlockPos(0, 9, 0)), "an unchosen frame writes nothing");
        // optional roles stay off without a complaint; fallbacks repeat a chosen role
        BlockChoice partial = fullStyle();
        partial.set("row", null);
        partial.set("beam", null);
        assertEquals(partial.get("frame"), partial.resolve("beam"));
        plan.styles().put("s", partial);
        assertTrue(District.build(plan).problems().isEmpty());
    }

    @Test
    void partitionsFollowTheDesignUnlessAFlagOverridesThem() {
        QcPlan plan = house("house", 2, 1, 1);
        // house = rooms: a gate in the shared plane x = 8, passage at a 3..5, y 1..3
        Map<BlockPos, String> b = District.build(plan).blocks();
        assertEquals("minecraft:party_block", b.get(new BlockPos(8, 8 + 5, 2)));
        assertNull(b.get(new BlockPos(8, 8 + 2, 4)), "the gate's passage is open");
        // gate flag alone = open
        QcUnit west = plan.units().peekLayer(1).get(new UnitPos(0, 0));
        QcUnit east = plan.units().peekLayer(1).get(new UnitPos(1, 0));
        Flags.setGate(west, UnitSide.EAST, true);
        Flags.setGate(east, UnitSide.WEST, true);
        assertEquals("open", QcPlan.partyOverride(west, UnitSide.EAST));
        b = District.build(plan).blocks();
        assertNull(b.get(new BlockPos(8, 8 + 5, 2)), "an open override takes the party wall away");
    }

    @Test
    void neighbouringBuildingsShareTheirPlaneAndSeeBlindFaces() {
        QcPlan plan = house("house", 1, 1, 1);
        plan.buildings().put("B", new BuildingSpec("hall", "s"));
        plan.addUnit(new Cell(1, 1, 0), "B");
        plan.addUnit(new Cell(1, 2, 0), "B");
        District.Result result = District.build(plan);
        assertTrue(result.problems().isEmpty(), result.problems().toString());
        assertTrue(result.blocks().containsKey(new BlockPos(8, 12, 4)), "the shared plane is built once");
    }

    @Test
    void templatesKeepGeometryAndStyleNamesButNoBlocks() {
        QcPlan plan = house("townhouse", 2, 1, 2);
        QcPlan template = plan.stripped();
        assertTrue(template.styles().get("s").isEmpty());
        assertEquals(Roles.ROAD, template.styles().get("street").kind());
        assertEquals("townhouse", template.buildings().get("A").template);
        assertFalse(District.build(template).problems().isEmpty(), "an unfilled template cannot be applied");

        QcPlan target = house("house", 1, 1, 1);
        QcPlan stamped = template.withFreshBuildings(target);
        assertFalse(stamped.buildings().containsKey("A"));
        target.merge(stamped);
        assertEquals(2, target.buildings().size(), "stamping makes a second building");
        assertNotEquals(template.styles().get("s"), target.styles().get("s"), "chosen blocks win over an empty slot");
    }

    @Test
    void plansFromBeforeTheReworkLoadAsOneLegacyBuilding() {
        QcPlan old = new QcPlan();
        old.addUnit(new Cell(0, 1, 0), 3);
        CompoundTag tag = new CompoundTag();
        tag.put("units", LayersCodec.toNbt(old.units()));
        CompoundTag street = new CompoundTag();
        net.minecraft.nbt.ListTag floor = new net.minecraft.nbt.ListTag();
        for (int i = 1; i <= 4; i++) {
            floor.add(net.minecraft.nbt.StringTag.valueOf("minecraft:lane" + i));
        }
        street.put("floor", floor);
        CompoundTag styles = new CompoundTag();
        styles.put("street", street);
        tag.put("roadStyles", styles);
        QcPlan back = PlanCodec.fromNbt(tag);
        String key = back.building(new Cell(0, 1, 0));
        assertEquals(Templates.LEGACY, back.buildings().get(key).template);
        assertFalse(District.generates(back, new Cell(0, 1, 0)));
        assertEquals("minecraft:lane3", back.styles().get("street").get("floor3"));
    }

    static BlockChoice decor() {
        BlockChoice d = new BlockChoice(Roles.DECOR);
        d.set("pot", "minecraft:potted_fern");
        d.set("planter", "minecraft:moss_block");
        d.set("flower", "minecraft:poppy,minecraft:dandelion");
        d.set("seat", "minecraft:oak_stairs");
        d.set("goods", "minecraft:barrel");
        d.set("logs", "minecraft:oak_log");
        d.set("bush", "minecraft:oak_leaves");
        d.set("post", "minecraft:oak_fence");
        d.set("lamp", "minecraft:lantern");
        d.set("awning", "minecraft:red_wool");
        d.set("trunk", "minecraft:oak_log");
        d.set("crown", "minecraft:oak_leaves");
        d.set("basin", "minecraft:stone_bricks");
        return d;
    }

    /** A row of three houses facing a street, the street wider at a crossing, and a grass yard. */
    static QcPlan street() {
        QcPlan plan = house("house", 3, 1, 1);
        for (int x = -1; x <= 3; x++) {
            plan.roads().put(new Cell(x, 1, 2), "street");
        }
        plan.roads().put(new Cell(1, 1, 3), "street");
        BlockChoice yard = road();
        for (int lane = 1; lane <= 4; lane++) {
            yard.set("floor" + lane, "minecraft:grass_block");
        }
        yard.set("joint1", null);
        plan.styles().put("yard", yard);
        plan.roads().put(new Cell(4, 1, 1), "yard");
        plan.styles().put("props", decor());
        plan.decorStyle = "props";
        plan.decorDensity = 100;
        return plan;
    }

    @Test
    void decorDressesTheStreetsOnTheRoadAndNeverInADoorway() {
        QcPlan plan = street();
        District.Result result = District.build(plan);
        assertTrue(result.problems().isEmpty(), result.problems().toString());
        Map<BlockPos, String> bare = District.build(withoutDecor(plan)).blocks();
        int props = 0;
        for (Map.Entry<BlockPos, String> e : result.blocks().entrySet()) {
            if (!bare.containsKey(e.getKey())) {
                props++;
                BlockPos below = e.getKey().below();
                assertTrue(e.getKey().getY() > 8, "decor stands above the road plane: " + e.getKey());
                if (e.getKey().getY() == 9) {
                    assertTrue(result.blocks().containsKey(below), "a prop stands on the road: " + e.getKey());
                }
            }
        }
        assertTrue(props > 10, "a dense street gets props, got " + props);
        assertTrue(result.blocks().values().stream().anyMatch(s -> s.startsWith("minecraft:oak_leaves")),
                "the grass yard gets a tree or a bush");
        assertEquals(result.blocks(), District.build(street()).blocks(), "the same seed dresses the same street");
        plan.decorSeed = 7;
        assertNotEquals(result.blocks(), District.build(plan).blocks(), "another seed, another arrangement");
        // the door's approach (3 wide, 3 deep in front of the door) stays clear of props
        for (Map.Entry<BlockPos, String> e : result.blocks().entrySet()) {
            if (e.getValue().startsWith("minecraft:oak_door") && e.getValue().contains("half=lower")) {
                BlockPos door = e.getKey();
                for (int a = -1; a <= 1; a++) {
                    for (int d = 1; d <= 3; d++) {
                        assertFalse(!bare.containsKey(door.offset(a, 0, d)) && result.blocks().containsKey(door.offset(a, 0, d)),
                                "a prop in the doorway at " + door.offset(a, 0, d));
                    }
                }
            }
        }
    }

    @Test
    void decorPutsNothingOnALiquidRoadFloor() {
        // qckit f1fc3d6 (T20): a field road's water medallion got trees with their trunks in the channel
        QcPlan plan = street();
        BlockChoice street = plan.styles().get("street");
        street.set("medallion", "minecraft:water");
        street.set("centre", "minecraft:water");
        BlockChoice canal = plan.styles().get("yard");
        for (int lane = 1; lane <= 4; lane++) {
            canal.set("floor" + lane, "minecraft:water");
        }
        for (int seed = 0; seed < 8; seed++) {
            plan.decorSeed = seed;
            District.Result result = District.build(plan);
            assertTrue(result.problems().isEmpty(), result.problems().toString());
            Map<BlockPos, String> bare = District.build(withoutDecor(plan)).blocks();
            for (Map.Entry<BlockPos, String> e : result.blocks().entrySet()) {
                String below = result.blocks().get(e.getKey().below());
                if (!bare.containsKey(e.getKey()) && below != null && bare.containsKey(e.getKey().below())) {
                    assertFalse(BlockStr.base(below).endsWith(":water") || BlockStr.base(below).endsWith(":lava"),
                            "seed " + seed + ": " + e.getValue() + " stands on " + below + " at " + e.getKey());
                }
            }
        }
    }

    /** An apply records the door sides the generator gave each cell (T22, review #40). */
    @Test
    void aHouseReportsTheSidesOfItsDoors() {
        District.Result result = District.build(house("house", 2, 1, 2));
        assertFalse(result.doors().isEmpty(), "the house has a door");
        for (Map.Entry<Cell, Integer> e : result.doors().entrySet()) {
            assertEquals(1, e.getKey().layer(), "doors stand on the ground storey: " + e.getKey());
            assertEquals(0, e.getKey().z(), "the house is one unit deep: " + e.getKey());
            assertEquals(0b0100, (int) e.getValue(), "the door opens south onto the road: " + e.getKey());
        }
    }

    /** A cell the plan rebuilds (a joined part) is not held back by the door it was recorded with. */
    @Test
    void aRebuiltCellIgnoresItsOwnRecordedDoor() {
        QcPlan plan = withoutDecor(street());
        Map<BlockPos, String> alone = District.build(plan).blocks();
        Map<Cell, Integer> doors = District.build(plan).doors();
        Set<Cell> cells = new java.util.HashSet<>(plan.cells());
        Surroundings around = new Surroundings(Set.of(), doors, cells);
        assertEquals(alone, District.build(plan, around).blocks(),
                "the plan's own recorded doors must not change how it rebuilds itself");
    }

    /** New units of a standing part's own design may grow in front of its door; others may not. */
    @Test
    void doorFrontsLeaveOutTheDoorsOfTheActiveDesign() {
        Cell door = new Cell(0, 1, 0);
        Surroundings around = new Surroundings(Set.of(), Map.of(door, 0b0100), Set.of(door),
                Map.of(door, new Surroundings.Part(42, 0, 0)));
        assertEquals(Set.of(new Cell(0, 1, 1)), around.doorFronts());
        assertEquals(Set.of(new Cell(0, 1, 1)), around.doorFronts(7));
        assertTrue(around.doorFronts(42).isEmpty());
    }

    @Test
    void decorKeepsTheApproachOfAStandingDoorFree() {
        // a standing building south of the street at (0, 1, 3), its door on the north side (bit 0)
        QcPlan plan = street();
        Surroundings around = new Surroundings(Set.of(), Map.of(new Cell(0, 1, 3), 0b0001), Set.of(new Cell(0, 1, 3)));
        Set<Long> approach = around.doorOpenings(1);
        for (int seed = 0; seed < 8; seed++) {
            plan.decorSeed = seed;
            District.Result result = District.build(plan, around);
            assertTrue(result.problems().isEmpty(), result.problems().toString());
            Map<BlockPos, String> bare = District.build(withoutDecor(plan), around).blocks();
            for (Map.Entry<BlockPos, String> e : result.blocks().entrySet()) {
                BlockPos p = e.getKey();
                if (!bare.containsKey(p) && approach.contains(RoadKit.key(p.getX(), p.getZ()))) {
                    fail("seed " + seed + ": " + e.getValue() + " in front of the standing door at " + p);
                }
            }
        }
    }

    @Test
    void anEmptyDecorStylePlacesNothingAndAMissingOneIsReported() {
        QcPlan plan = street();
        plan.styles().put("props", new BlockChoice(Roles.DECOR));
        assertEquals(District.build(withoutDecor(plan)).blocks(), District.build(plan).blocks());
        plan.decorStyle = "nowhere";
        assertFalse(District.build(plan).problems().isEmpty());
    }

    private static QcPlan withoutDecor(QcPlan plan) {
        QcPlan copy = plan.copy();
        copy.decorStyle = "";
        return copy;
    }
}
