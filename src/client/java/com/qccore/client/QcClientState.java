package com.qccore.client;

import com.qccore.core.UnitLayers;
import com.qccore.core.palette.BlockPalette;
import com.qccore.core.palette.Defaults;
import com.qccore.world.QcUnitRecord;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;

/**
 * The client-side editor session: the layout being edited, the palette the player configured, the
 * lattice setting and the units the server reported around the player.
 *
 * <p>Single instance of mutable static state, the client-side counterpart of QCSimple's
 * {@code EditorState} singleton.
 */
public final class QcClientState {
	/** Whether the in-world editor is active (crosshair captures the mouse, overlay renders). */
	public static boolean active;
	/** Whether the unit outlines are drawn while the editor is active. */
	public static boolean overlayVisible = true;

	/** Origin of the session lattice: unit {@code (dx, dy, dz)} starts at {@code anchor + 8 * (dx, dy, dz)}. */
	public static BlockPos anchor;
	/** Requested lattice residues {@code (8x + a, 8y + b, 8z + c)}; {@code null} means a free lattice. */
	public static Integer alignA;
	public static Integer alignB;
	public static Integer alignC;

	/** The layout being edited: layer -> unit cell. */
	public static UnitLayers layers = new UnitLayers();
	/** The nine components the layout is built from, shared by the editor and the screens. */
	public static final BlockPalette palette = defaultPalette();
	/** The placed units the server reported, keyed by their origin corner. */
	public static final Map<BlockPos, QcUnitRecord> nearbyUnits = new HashMap<>();

	/** The dimension the reported units and the session belong to. */
	public static Identifier dimension;

	/** The pending HUD message and the ticks it stays on screen. */
	public static String message;
	public static int messageTicks;

	private QcClientState() {
	}

	/** Shows {@code text} on the HUD for three seconds. */
	public static void say(String text) {
		message = text;
		messageTicks = 60;
	}

	/** Drops the whole session, as on a world or dimension change. The configured palette survives. */
	public static void reset() {
		active = false;
		overlayVisible = true;
		anchor = null;
		alignA = null;
		alignB = null;
		alignC = null;
		layers = new UnitLayers();
		nearbyUnits.clear();
		dimension = null;
		message = null;
		messageTicks = 0;
	}

	private static BlockPalette defaultPalette() {
		BlockPalette palette = new BlockPalette();
		Defaults.applyTo(palette);
		return palette;
	}
}
