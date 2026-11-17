package com.qccore.client;

import com.qccore.core.plan.BuildShape;
import com.qccore.core.plan.Cell;
import com.qccore.core.plan.ComponentMask;
import com.qccore.core.plan.Modifiers;
import com.qccore.core.plan.QcPlan;
import com.qccore.core.plan.RoadStyle;
import com.qccore.core.palette.BlockPalette;
import com.qccore.core.palette.Defaults;
import com.qccore.world.QcUnitRecord;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

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

	/** The plan being edited: units (layer -> unit cell), masks, cleared cells and roads. */
	public static QcPlan plan = new QcPlan();
	/** Bumped on every plan or palette change; the block preview recompiles when it moves. */
	public static int planVersion;
	/** Undo/redo of plan edits (not of the world: that is the server's {@code /qc undo}). */
	public static final QcHistory history = new QcHistory();

	/** What a right click does, and the shape it does it over. */
	public static QcTool tool = QcTool.UNITS;
	public static BuildShape shape = BuildShape.SINGLE;
	/** The points of the shape in progress (empty when no shape is started). */
	public static final List<Cell> points = new ArrayList<>();
	/** The components newly placed units build, see {@link ComponentMask}. */
	public static int componentMask = ComponentMask.ALL;
	/** Mirror and array modifiers applied to every shape. */
	public static final Modifiers modifiers = new Modifiers();
	/** The road style the road tool lays (a key of {@link #roadStyles}). */
	public static String roadStyle = "street";
	/** Road styles edited in the kit screen; copied into the plan when a road is laid. */
	public static final Map<String, RoadStyle> roadStyles = new LinkedHashMap<>();
	/** The template the template tool stamps (normalised), its name, and its rotation in quarter turns. */
	public static QcPlan template;
	public static String templateName = "";
	public static int templateRotation;
	/** Whether the compiled blocks of the plan are drawn as ghost blocks. */
	public static boolean blockPreview = true;
	/** The nine components the layout is built from, shared by the editor and the screens. */
	public static final BlockPalette palette = defaultPalette();
	/** The placed units the server reported, keyed by their origin corner. */
	public static final Map<BlockPos, QcUnitRecord> nearbyUnits = new HashMap<>();

	/** The dimension the reported units and the session belong to. */
	public static ResourceLocation dimension;

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
		plan = new QcPlan();
		history.clear();
		points.clear();
		planVersion++;
		nearbyUnits.clear();
		dimension = null;
		message = null;
		messageTicks = 0;
	}

	/** Records the plan before an edit so it can be undone; call before every mutation. */
	public static void checkpoint() {
		history.push(plan);
	}

	/** Marks the plan (or the palette) as changed. */
	public static void changed() {
		planVersion++;
	}

	private static BlockPalette defaultPalette() {
		BlockPalette palette = new BlockPalette();
		Defaults.applyTo(palette);
		return palette;
	}
}
