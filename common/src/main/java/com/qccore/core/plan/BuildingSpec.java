package com.qccore.core.plan;

import com.qccore.core.qc.Design;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;

/**
 * One building of a plan, the in-game counterpart of qckit's {@code Plan.building(ch, template,
 * blocks=..., **overrides)}: the template it is built from, the name of the style that holds its
 * blocks, and its own overrides of the template's {@link Design} options. The units it owns are
 * tagged with its key in {@link QcPlan#unitBuildings()}.
 */
public final class BuildingSpec {

    public String template;
    public String style;
    public final Map<String, String> options = new LinkedHashMap<>();

    public BuildingSpec(String template, String style) {
        this.template = template;
        this.style = style;
    }

    public Design design() {
        return Design.of(template, options);
    }

    public BuildingSpec copy() {
        BuildingSpec copy = new BuildingSpec(template, style);
        copy.options.putAll(options);
        return copy;
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("template", template == null ? "" : template);
        tag.putString("style", style == null ? "" : style);
        CompoundTag map = new CompoundTag();
        options.forEach(map::putString);
        tag.put("options", map);
        return tag;
    }

    public static BuildingSpec fromNbt(CompoundTag tag) {
        BuildingSpec spec = new BuildingSpec(tag.getString("template"), tag.getString("style"));
        CompoundTag map = tag.getCompound("options");
        for (String key : map.getAllKeys()) {
            spec.options.put(key, map.getString(key));
        }
        return spec;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof BuildingSpec other && java.util.Objects.equals(other.template, template)
                && java.util.Objects.equals(other.style, style) && other.options.equals(options);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(template, style, options);
    }
}
