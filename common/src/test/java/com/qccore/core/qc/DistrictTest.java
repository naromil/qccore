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
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
