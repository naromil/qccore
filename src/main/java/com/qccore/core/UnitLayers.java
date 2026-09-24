package com.qccore.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The multi-layer QC Unit map: layer index -> unit position -> unit.
 *
 * <p>The layers are kept in a {@link TreeMap} and every layer in a {@link LinkedHashMap}, so both
 * the layer order (ascending) and the unit order (insertion order) are fixed by the container
 * instead of hash table internals.
 */
public final class UnitLayers {

    private final TreeMap<Integer, LinkedHashMap<UnitPos, QcUnit>> layers = new TreeMap<>();

    /** The unit map of the requested layer, created on demand. */
    public Map<UnitPos, QcUnit> layer(int dy) {
        return layers.computeIfAbsent(dy, k -> new LinkedHashMap<>());
    }

    /** The unit map of the requested layer, or {@code null} when that layer does not exist. */
    public Map<UnitPos, QcUnit> peekLayer(int dy) {
        return layers.get(dy);
    }

    /** Whether a unit sits at the given grid position. */
    public boolean contains(int dx, int dy, int dz) {
        Map<UnitPos, QcUnit> layer = layers.get(dy);
        return layer != null && layer.containsKey(new UnitPos(dx, dz));
    }

    public boolean isEmpty() {
        return layers.isEmpty();
    }

    public int unitCount() {
        int count = 0;
        for (Map<UnitPos, QcUnit> layer : layers.values()) {
            count += layer.size();
        }
        return count;
    }

    /** The layer indices, ascending. */
    public Set<Integer> layerKeys() {
        return layers.keySet();
    }

    public void setLayer(int dy, Map<UnitPos, QcUnit> layer) {
        layers.put(dy, new LinkedHashMap<>(layer));
    }

    /** A deep copy: new layers, new unit maps and new units. */
    public UnitLayers copy() {
        UnitLayers copy = new UnitLayers();
        for (Map.Entry<Integer, LinkedHashMap<UnitPos, QcUnit>> entry : layers.entrySet()) {
            LinkedHashMap<UnitPos, QcUnit> layerCopy = new LinkedHashMap<>();
            for (Map.Entry<UnitPos, QcUnit> unitEntry : entry.getValue().entrySet()) {
                layerCopy.put(unitEntry.getKey(), new QcUnit(unitEntry.getValue()));
            }
            copy.layers.put(entry.getKey(), layerCopy);
        }
        return copy;
    }
}
