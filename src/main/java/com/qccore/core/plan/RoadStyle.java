package com.qccore.core.plan;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The blocks of one QC road, addressed by lane like qckit's {@code road_style}: lane 1 is at a
 * building face, lane 4 on the axis, so a straight street reads {@code 1 2 3 4 3 2 1} across.
 *
 * <p>There are no preset styles: every block is chosen by the player. {@code floor} needs all four
 * lanes; every other role is optional ({@code ""} or {@code null} leaves it out):
 * <ul>
 *   <li>{@code floor[lane]}: the surface at y 0;</li>
 *   <li>{@code joint[lane]}: the surface on a transverse cell plane (the light/joint line every
 *       8 blocks, both sides);</li>
 *   <li>{@code over[lane]}: furniture at y 1, continuous (a hedge, a parapet);</li>
 *   <li>{@code post[lane]}: a column standing on the joint, y 1 upward, blocks bottom-up;</li>
 *   <li>{@code medallion}: the 3x3 at the centre of a bend, tee or crossing; {@code centre} its
 *       middle block.</li>
 * </ul>
 * Ids may carry block states ({@code minecraft:oak_log[axis=x]}).
 */
public final class RoadStyle {

    public static final int LANES = 4;

    public final String[] floor = new String[LANES];
    public final String[] joint = new String[LANES];
    public final String[] over = new String[LANES];
    @SuppressWarnings("unchecked")
    public final List<String>[] post = new List[LANES];
    public String medallion = "";
    public String centre = "";

    public RoadStyle() {
        Arrays.fill(floor, "");
        Arrays.fill(joint, "");
        Arrays.fill(over, "");
        for (int i = 0; i < LANES; i++) {
            post[i] = new ArrayList<>();
        }
    }

    /** Whether every floor lane has a block, the one thing a road cannot do without. */
    public boolean isComplete() {
        for (String id : floor) {
            if (blank(id)) {
                return false;
            }
        }
        return true;
    }

    public RoadStyle copy() {
        return fromNbt(toNbt());
    }

    public static boolean blank(String id) {
        return id == null || id.isBlank();
    }

    public NbtCompound toNbt() {
        NbtCompound tag = new NbtCompound();
        tag.put("floor", strings(floor));
        tag.put("joint", strings(joint));
        tag.put("over", strings(over));
        NbtList posts = new NbtList();
        for (List<String> column : post) {
            posts.add(strings(column.toArray(new String[0])));
        }
        tag.put("post", posts);
        tag.putString("medallion", medallion == null ? "" : medallion);
        tag.putString("centre", centre == null ? "" : centre);
        return tag;
    }

    public static RoadStyle fromNbt(NbtCompound tag) {
        RoadStyle style = new RoadStyle();
        read(tag.getList("floor", NbtElement.STRING_TYPE), style.floor);
        read(tag.getList("joint", NbtElement.STRING_TYPE), style.joint);
        read(tag.getList("over", NbtElement.STRING_TYPE), style.over);
        NbtList posts = tag.getList("post", NbtElement.LIST_TYPE);
        for (int i = 0; i < LANES && i < posts.size(); i++) {
            NbtList column = posts.getList(i);
            for (int j = 0; j < column.size(); j++) {
                style.post[i].add(column.getString(j));
            }
        }
        style.medallion = tag.getString("medallion");
        style.centre = tag.getString("centre");
        return style;
    }

    private static NbtList strings(String[] values) {
        NbtList list = new NbtList();
        for (String value : values) {
            list.add(NbtString.of(value == null ? "" : value));
        }
        return list;
    }

    private static void read(NbtList list, String[] into) {
        for (int i = 0; i < into.length && i < list.size(); i++) {
            into[i] = list.getString(i);
        }
    }
}
