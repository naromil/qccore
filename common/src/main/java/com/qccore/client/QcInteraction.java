package com.qccore.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.qccore.core.Alignment;
import com.qccore.core.QcUnit;
import com.qccore.core.UnitPos;
import com.qccore.core.UnitSide;
import com.qccore.core.plan.BuildShape;
import com.qccore.core.plan.Cell;
import com.qccore.core.plan.Flags;
import com.qccore.core.plan.Modifiers;
import com.qccore.core.plan.BuildingSpec;
import com.qccore.core.plan.QcPlan;
import com.qccore.core.qc.BlockChoice;
import com.qccore.core.qc.Roles;
import com.qccore.core.qc.Templates;
import com.qccore.world.QcSurroundings;
import com.qccore.world.QcUnitRecord;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Everything the in-world editor does with the crosshair: entering the mode, deriving the cursor
 * cell from the crosshair, the build shapes and tools, and the mouse actions the input mixin
 * forwards here. Rendering lives in {@link QcOverlayRenderer}.
 *
 * <p>Every action edits the plan in {@link QcClientState#plan}, never the world: the plan is applied
 * with the build key (or exported with the save key) once the player is happy with it, and each
 * edit is a step of {@link QcClientState#history}.
 */
public final class QcInteraction {
	private static final String FACE_MESSAGE = "QC: look at a side face to place walls";
	private static final String PAIR_MESSAGE = "QC: both QC units must exist to attach a wall";
	private static final String GATE_MESSAGE = "QC: no wall on this face; Shift+RMB sets one, then Ctrl+RMB makes it a gate";
	private static final String NO_UNIT_MESSAGE = "QC: no placed QC Unit in the crosshair";
	private static final String WORLD_MESSAGE = "QC: editor reset (changed dimension)";

	/** How close a reported unit has to be before the anchor adopts its lattice. */
	private static final double LATTICE_RANGE = 48.0;

	/** Multi-point shapes take one point per fresh press (see {@link QcUseGate}). */
	private static final QcUseGate USE_GATE = new QcUseGate();

	/**
	 * Where the crosshair points, in session unit coordinates. {@code layer} is the layer the editor
	 * works on: for a placing tool aimed at a block's top face, the layer whose floor plane is nearest
	 * that block ({@link Alignment#nearestLayer}), otherwise the aimed block's own layer, which is
	 * always {@code hitLayer}.
	 */
	public record Cursor(UnitPos cell, int layer, int hitLayer, @Nullable Direction face, boolean blockHit) {
	}

	private QcInteraction() {
	}

	/**
	 * Enters the editor with the anchor at the player, so layer 1 is the player's own cell. While a
	 * plan exists the anchor is kept instead: plan cells are relative to it, so moving it would drag
	 * the planned units along with the player ({@code Y} still re-anchors on purpose).
	 */
	public static void enterEditorMode(Minecraft client) {
		if (client.player == null || client.level == null) {
			return;
		}
		QcSavedStyles.restoreOnceAndSay(QcClientState.plan.styles());
		if (QcClientState.anchor != null && !QcClientState.plan.isEmpty()) {
			QcClientState.active = true;
			QcClientState.dimension = client.level.dimension().location();
			QcClientState.say("plan kept in place; Y re-anchors it at the crosshair");
			QcClientState.changed();
			return;
		}
		int x = Mth.floor(client.player.getX());
		int y = Mth.floor(client.player.getY());
		int z = Mth.floor(client.player.getZ());
		QcClientState.anchor = new BlockPos(x, Math.floorDiv(y, 8) * 8 - 8, z);
		QcClientState.active = true;
		QcClientState.dimension = client.level.dimension().location();
		adoptNearbyLattice();
		QcClientState.changed();
	}

	/** Moves the session anchor to the crosshair; works for block hits and misses alike. */
	public static void reanchor(Minecraft client) {
		HitResult target = client.hitResult;
		if (target == null) {
			return;
		}
		Vec3 reference = target.getLocation();
		QcClientState.anchor = new BlockPos(Mth.floor(reference.x),
				Mth.floor(reference.y) - 8, Mth.floor(reference.z));
		adoptNearbyLattice();
		QcClientState.points.clear();
		QcClientState.changed();
	}

	/**
	 * Snaps the anchor onto the configured lattice, or onto the lattice of the nearest reported
	 * unit. With neither, the anchor stays free and units can be placed anywhere.
	 */
	public static void adoptNearbyLattice() {
		BlockPos anchor = QcClientState.anchor;
		if (anchor == null) {
			return;
		}
		Integer a = QcClientState.alignA;
		Integer b = QcClientState.alignB;
		Integer c = QcClientState.alignC;
		if (a != null || b != null || c != null) {
			QcClientState.anchor = new BlockPos(
					a == null ? anchor.getX() : Alignment.snap(anchor.getX(), a),
					b == null ? anchor.getY() : Alignment.snap(anchor.getY(), b),
					c == null ? anchor.getZ() : Alignment.snap(anchor.getZ(), c));
			return;
		}
		QcUnitRecord nearest = null;
		double closest = LATTICE_RANGE * LATTICE_RANGE;
		for (QcUnitRecord record : QcClientState.nearbyUnits.values()) {
			double distance = record.origin().distSqr(anchor);
			if (distance <= closest) {
				closest = distance;
				nearest = record;
			}
		}
		if (nearest == null) {
			return;
		}
		BlockPos residues = Alignment.residues(nearest.origin());
		QcClientState.anchor = new BlockPos(
				Alignment.snap(anchor.getX(), residues.getX()),
				Alignment.snap(anchor.getY(), residues.getY()),
				Alignment.snap(anchor.getZ(), residues.getZ()));
	}

	/** The unit cell, layer and hit face under the crosshair, or {@code null} outside a session. */
	@Nullable
	public static Cursor cursor(Minecraft client) {
		if (!QcClientState.active || client.hitResult == null || QcClientState.anchor == null) {
			return null;
		}
		HitResult target = client.hitResult;
		Direction face = null;
		BlockPos reference;
		// a miss is a BlockHitResult too (type MISS, its "face" the look direction): only a real hit has a face
		boolean blockHit = target.getType() == HitResult.Type.BLOCK && target instanceof BlockHitResult;
		if (blockHit) {
			BlockHitResult hit = (BlockHitResult) target;
			reference = hit.getBlockPos();
			face = hit.getDirection();
		} else {
			reference = BlockPos.containing(target.getLocation());
		}
		BlockPos anchor = QcClientState.anchor;
		UnitPos cell = new UnitPos(Math.floorDiv(reference.getX() - anchor.getX(), 8),
				Math.floorDiv(reference.getZ() - anchor.getZ(), 8));
		int hitLayer = Math.floorDiv(reference.getY() - anchor.getY(), 8);
		// ground or a roof aimed from above: place on the nearest floor plane, so ground at local y 5..7
		// takes the layer above instead of refusing (layer 0) or burying the unit
		int layer = blockHit && face == Direction.UP && isPlacingTool(QcClientState.tool)
				? Alignment.nearestLayer(reference.getY(), anchor.getY())
				: hitLayer;
		return new Cursor(cell, layer, hitLayer, face, blockHit);
	}

	/**
	 * The cell the next shape point lands in: for a placing tool the hit block offset by the hit
	 * face (so aiming at a unit's side places next to it), otherwise the aimed cell; the layer is
	 * the cursor's (the nearest floor plane for a placing tool on a top face). The third point of a box picks its height along the vertical line
	 * of the second point, wherever the crosshair ray passes closest to it.
	 */
	@Nullable
	public static Cell target(Minecraft client) {
		Cursor cursor = cursor(client);
		if (cursor == null) {
			return null;
		}
		List<Cell> points = QcClientState.points;
		BuildShape shape = activeShape();
		if (points.size() == 2 && shape.points() == 3) {
			return new Cell(points.get(1).x(), heightLayer(client, points.get(1)), points.get(1).z());
		}
		UnitPos cell = cursor.cell();
		if (isPlacingTool(QcClientState.tool) && cursor.blockHit() && client.hitResult instanceof BlockHitResult hit) {
			BlockPos reference = hit.getBlockPos().relative(cursor.face());
			BlockPos anchor = QcClientState.anchor;
			cell = new UnitPos(Math.floorDiv(reference.getX() - anchor.getX(), 8),
					Math.floorDiv(reference.getZ() - anchor.getZ(), 8));
		}
		return Cell.of(cell, cursor.layer());
	}

	/** The shape the current tool uses: a template is always stamped with one click. */
	public static BuildShape activeShape() {
		return QcClientState.tool == QcTool.TEMPLATE ? BuildShape.SINGLE : QcClientState.shape;
	}

	/** The cells the shape in progress would cover if the crosshair point were the next click. */
	public static Set<Cell> previewCells(Minecraft client) {
		Cell next = target(client);
		List<Cell> points = new java.util.ArrayList<>(QcClientState.points);
		if (next != null) {
			points.add(next);
		}
		if (points.isEmpty()) {
			return Set.of();
		}
		if (QcClientState.tool == QcTool.TEMPLATE) {
			return templateCells(points.get(0));
		}
		return QcClientState.modifiers.apply(activeShape().cells(points));
	}

	/**
	 * Runs one mouse button press. A single-cell shape repeats while the button is held, which is
	 * what makes drag-painting work; a multi-point shape takes one point per fresh press.
	 */
	public static void click(boolean useKey) {
		Minecraft client = Minecraft.getInstance();
		Cursor cursor = cursor(client);
		if (cursor == null) {
			return;
		}
		boolean control = modifierDown(client, GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL);
		boolean shift = modifierDown(client, GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT);
		if (control || shift) {
			edit(() -> toggleWall(cursor, useKey, control));
			return;
		}
		if (!useKey) {
			if (!QcClientState.points.isEmpty()) {
				QcClientState.points.clear();
				QcClientState.say("QC: shape cancelled");
				return;
			}
			Set<Cell> cells = Set.of(Cell.of(cursor.cell(), cursor.layer()));
			edit(() -> erase(cells));
			return;
		}
		BuildShape shape = activeShape();
		Cell point = target(client);
		if (point == null || !USE_GATE.accept(shape.points() > 1)) {
			return;
		}
		QcClientState.points.add(point);
		if (QcClientState.points.size() < shape.points()) {
			QcClientState.say("QC: " + shape.label() + " point " + QcClientState.points.size() + "/" + shape.points()
					+ (QcClientState.points.size() == 2 ? " - aim up or down for the height" : "") + " (LMB cancels)");
			return;
		}
		List<Cell> points = List.copyOf(QcClientState.points);
		QcClientState.points.clear();
		if (QcClientState.tool == QcTool.TEMPLATE) {
			edit(() -> stampTemplate(points.get(0)));
			return;
		}
		Set<Cell> cells = QcClientState.modifiers.apply(shape.cells(points));
		edit(() -> runTool(QcClientState.tool, cells));
	}

	/** Runs a plan edit; when it changed something, the plan before it becomes an undo step. */
	private static void edit(java.util.function.BooleanSupplier action) {
		QcPlan before = QcClientState.plan.copy();
		if (action.getAsBoolean()) {
			QcClientState.history.push(before);
			QcClientState.changed();
		}
	}

	/** Applies a tool to a set of cells; returns whether the plan changed. */
	static boolean runTool(QcTool tool, Set<Cell> cells) {
		QcPlan plan = QcClientState.plan;
		// any layer, below the anchor too: layers are only offsets from it (the world height limit is the server's)
		Set<Cell> valid = new LinkedHashSet<>(cells);
		switch (tool) {
			case UNITS -> {
				if (valid.isEmpty()) {
					return false;
				}
				Set<Cell> fronts = standingDoorFronts();
				int kept = 0;
				for (java.util.Iterator<Cell> it = valid.iterator(); it.hasNext(); ) {
					if (fronts.contains(it.next())) {
						it.remove();
						kept++;
					}
				}
				if (kept > 0) {
					QcClientState.say("QC: kept " + kept + " cell(s) free in front of a standing door");
				}
				if (valid.isEmpty()) {
					return false;
				}
				String building = ensureBuilding();
				boolean changed = false;
				for (Cell cell : valid) {
					if (!plan.hasUnit(cell)) {
						plan.roads().remove(cell);
						plan.addUnit(cell, building);
						changed = true;
					}
				}
				return changed;
			}
			case ERASE -> {
				return erase(cells);
			}
			case ASSIGN -> {
				boolean changed = false;
				String building = null;
				for (Cell cell : valid) {
					if (plan.hasUnit(cell)) {
						if (building == null) {
							building = ensureBuilding();
						}
						changed |= !building.equals(plan.unitBuildings().put(cell, building));
					}
				}
				plan.pruneBuildings();
				return changed;
			}
			case WALLS, GATES, OPEN -> {
				if (valid.size() < 2) {
					QcClientState.say("QC: a partition needs a shape of two or more units");
					return false;
				}
				QcPlan.Partition partition = tool == QcTool.WALLS ? QcPlan.Partition.WALL
						: tool == QcTool.GATES ? QcPlan.Partition.GATE : QcPlan.Partition.OPEN;
				int changed = plan.partition(valid, partition);
				QcClientState.say("QC: " + changed + " shared side(s) set to " + partition.name().toLowerCase(java.util.Locale.ROOT));
				return changed > 0;
			}
			case CLEAR -> {
				return plan.clears().addAll(valid);
			}
			case ROAD -> {
				BlockChoice style = plan.styles().get(QcClientState.roadStyle);
				if (style != null && !style.kind().equals(Roles.ROAD)) {
					QcClientState.say("QC: '" + QcClientState.roadStyle + "' is a building style; pick a road style (B -> Blocks)");
					return false;
				}
				plan.styles().putIfAbsent(QcClientState.roadStyle, new BlockChoice(Roles.ROAD));
				boolean changed = false;
				for (Cell cell : valid) {
					if (plan.hasUnit(cell)) {
						plan.removeUnit(cell);
					}
					changed |= !QcClientState.roadStyle.equals(plan.roads().put(cell, QcClientState.roadStyle));
				}
				// as erase and assign do: a building whose last unit became road is gone, not an empty entry
				plan.pruneBuildings();
				return changed;
			}
			case TEMPLATE -> {
				return false;
			}
		}
		return false;
	}

	/** Removes units, roads and cleared cells from the cells. */
	private static boolean erase(Set<Cell> cells) {
		QcPlan plan = QcClientState.plan;
		boolean changed = false;
		for (Cell cell : cells) {
			if (plan.hasUnit(cell)) {
				plan.removeUnit(cell);
				changed = true;
			}
			changed |= plan.roads().remove(cell) != null;
			changed |= plan.clears().remove(cell);
		}
		plan.pruneBuildings();
		return changed;
	}

	/**
	 * The key of the building the units tool adds to: the active one, or a new building with the
	 * current design (its style created empty when the plan has none of that name).
	 */
	static String ensureBuilding() {
		QcPlan plan = QcClientState.plan;
		if (QcClientState.activeBuilding() != null) {
			return QcClientState.building;
		}
		String key = plan.nextBuildingKey();
		BuildingSpec spec = QcClientState.design.copy();
		plan.buildings().put(key, spec);
		if (!Templates.LEGACY.equals(spec.template)) {
			plan.styles().putIfAbsent(spec.style, new BlockChoice(Roles.BUILDING));
		}
		QcClientState.building = key;
		return key;
	}

	/** Every placement of the template at {@code at}, with the modifiers applied. */
	private static boolean stampTemplate(Cell at) {
		QcPlan template = QcClientState.template;
		if (template == null) {
			QcClientState.say("QC: choose a template first (B -> Templates)");
			return false;
		}
		List<QcPlan> placements = templatePlacements(at);
		Set<Cell> fronts = standingDoorFronts();
		if (!fronts.isEmpty()) {
			int blocked = 0;
			for (QcPlan placed : placements) {
				for (Cell cell : placed.cells()) {
					if (placed.hasUnit(cell) && fronts.contains(cell)) {
						blocked++;
					}
				}
			}
			if (blocked > 0) {
				QcClientState.say("QC: the template would wall up " + blocked + " standing door(s); move or turn it");
				return false;
			}
		}
		for (QcPlan placed : placements) {
			QcClientState.plan.merge(placed.withFreshBuildings(QcClientState.plan));
		}
		int unset = 0;
		for (BlockChoice style : QcClientState.plan.styles().values()) {
			unset += style.isEmpty() ? 1 : 0;
		}
		QcClientState.say("QC: stamped template " + QcClientState.templateName
				+ (unset == 0 ? "" : "; " + unset + " style(s) need blocks (B -> Blocks)"));
		return true;
	}

	/** The template turned by the current rotation and moved so its minimum cell is {@code at}, once per modifier placement. */
	static List<QcPlan> templatePlacements(Cell at) {
		QcPlan template = QcClientState.template;
		if (template == null) {
			return List.of();
		}
		QcPlan turned = template.transformed(QcClientState.templateRotation, false, false, 0, 0, 0).normalized();
		Modifiers modifiers = QcClientState.modifiers;
		List<QcPlan> out = new java.util.ArrayList<>();
		for (Modifiers.Placement placement : modifiers.placements()) {
			int dx = at.x() + placement.dx();
			int dLayer = at.layer() - 1 + placement.dLayer();
			int dz = at.z() + placement.dz();
			QcPlan moved = turned.transformed(0, false, false, dx, dLayer, dz);
			if (placement.flipX() || placement.flipZ()) {
				moved = moved.transformed(0, placement.flipX(), placement.flipZ(),
						placement.flipX() ? 2 * modifiers.mirrorCellX : 0, 0,
						placement.flipZ() ? 2 * modifiers.mirrorCellZ : 0);
			}
			out.add(moved);
		}
		return out;
	}

	private static Set<Cell> templateCells(Cell at) {
		Set<Cell> cells = new LinkedHashSet<>();
		for (QcPlan placed : templatePlacements(at)) {
			cells.addAll(placed.cells());
		}
		if (cells.isEmpty()) {
			cells.add(at);
		}
		return cells;
	}

	/** The layer the crosshair ray passes closest to on the vertical line through a cell's centre. */
	private static int heightLayer(Minecraft client, Cell base) {
		Entity camera = client.getCameraEntity();
		BlockPos anchor = QcClientState.anchor;
		if (camera == null || anchor == null) {
			return base.layer();
		}
		Vec3 eye = camera.getEyePosition(1.0F);
		Vec3 look = camera.getViewVector(1.0F);
		double cx = anchor.getX() + base.x() * 8 + 4.5;
		double cz = anchor.getZ() + base.z() * 8 + 4.5;
		double horizontal = look.x * look.x + look.z * look.z;
		double t = horizontal < 1.0E-6 ? 0.0 : ((cx - eye.x) * look.x + (cz - eye.z) * look.z) / horizontal;
		double y = eye.y + Math.max(0.0, t) * look.y;
		return Math.floorDiv(Mth.floor(y) - anchor.getY(), 8);
	}

	/** The keybind pump: world guard, keybinds and the HUD message lifetime. */
	public static void onClientTick(Minecraft client) {
		syncDimension(client);
		USE_GATE.endTick(client.options.keyUse.isDown());
		if (QcClientState.messageTicks > 0 && --QcClientState.messageTicks == 0) {
			QcClientState.message = null;
		}
		while (QcKeys.editor.consumeClick()) {
			if (QcClientState.active) {
				QcClientState.active = false;
				QcClientState.points.clear();
			} else {
				enterEditorMode(client);
			}
		}
		while (QcKeys.reanchor.consumeClick()) {
			if (QcClientState.active) {
				reanchor(client);
			}
		}
		while (QcKeys.duplicateLayer.consumeClick()) {
			edit(() -> duplicateLayer(client));
		}
		while (QcKeys.save.consumeClick()) {
			client.setScreen(new QcSaveScreen());
		}
		while (QcKeys.build.consumeClick()) {
			apply();
		}
		while (QcKeys.config.consumeClick()) {
			client.setScreen(new QcConfigScreen());
		}
		while (QcKeys.unitAlign.consumeClick()) {
			align(client);
		}
		while (QcKeys.overlay.consumeClick()) {
			QcClientState.overlayVisible = !QcClientState.overlayVisible;
		}
		while (QcKeys.tools.consumeClick()) {
			client.setScreen(new QcToolsScreen());
		}
		while (QcKeys.shape.consumeClick()) {
			QcClientState.shape = QcClientState.shape.next();
			QcClientState.points.clear();
			QcClientState.say("QC: shape " + QcClientState.shape.label());
		}
		while (QcKeys.tool.consumeClick()) {
			QcClientState.tool = QcClientState.tool.next();
			QcClientState.points.clear();
			QcClientState.say("QC: tool " + QcClientState.tool.label());
		}
		while (QcKeys.undo.consumeClick()) {
			if (modifierDown(client, GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT)) {
				redo();
			} else {
				undo();
			}
		}
		while (QcKeys.rotate.consumeClick()) {
			QcClientState.templateRotation = (QcClientState.templateRotation + 1) % 4;
			QcClientState.say("QC: template rotation " + QcClientState.templateRotation * 90 + " deg");
		}
		while (QcKeys.preview.consumeClick()) {
			QcClientState.blockPreview = !QcClientState.blockPreview;
			QcClientState.say("QC: block preview " + (QcClientState.blockPreview ? "on" : "off"));
		}
	}

	/**
	 * Drops the session when the client's level is no longer the one the session belongs to. Runs
	 * every tick and before a unit index is stored, so an index of the new dimension that arrives
	 * before the next tick cannot keep the old dimension's plan alive.
	 */
	static void syncDimension(Minecraft client) {
		ResourceLocation current = client.level == null ? null : client.level.dimension().location();
		if (!Objects.equals(current, QcClientState.dimension)) {
			boolean hadSession = QcClientState.dimension != null;
			QcClientState.reset();
			QcClientState.dimension = current;
			if (hadSession) {
				QcClientState.say(WORLD_MESSAGE);
			}
		}
	}

	/** Undoes the last plan edit. */
	public static void undo() {
		QcPlan previous = QcClientState.history.undo(QcClientState.plan);
		if (previous == null) {
			QcClientState.say("QC: nothing to undo in the plan");
			return;
		}
		QcClientState.plan = previous;
		QcClientState.points.clear();
		QcClientState.changed();
		QcClientState.say("QC: undo (" + QcClientState.history.undoDepth() + " more)");
	}

	/** Redoes the last undone plan edit. */
	public static void redo() {
		QcPlan next = QcClientState.history.redo(QcClientState.plan);
		if (next == null) {
			QcClientState.say("QC: nothing to redo");
			return;
		}
		QcClientState.plan = next;
		QcClientState.points.clear();
		QcClientState.changed();
		QcClientState.say("QC: redo (" + QcClientState.history.redoDepth() + " more)");
	}

	/** Replaces the plan with an empty one, as one undoable step. */
	public static void clearPlan() {
		edit(() -> {
			if (QcClientState.plan.isEmpty()) {
				return false;
			}
			QcClientState.plan = QcClientState.plan.emptied();
			return true;
		});
	}

	/**
	 * Called when the server applied the plan that was sent at history revision {@code revision}.
	 * If the plan is still that one, it is emptied as one undoable step (Z brings it back), since
	 * its units and roads now stand in the world and a second K would build them again. A plan
	 * edited while the apply was on its way is kept, since it is no longer what was applied.
	 * Returns a note for the result message.
	 */
	static String planApplied(int revision) {
		if (QcClientState.history.revision() != revision) {
			return "; the plan changed meanwhile and was kept (Clear plan empties it)";
		}
		if (QcClientState.plan.isEmpty()) {
			return "";
		}
		QcClientState.history.push(QcClientState.plan);
		QcClientState.plan = QcClientState.plan.emptied();
		QcClientState.points.clear();
		QcClientState.changed();
		return "; plan cleared (Z brings it back)";
	}

	/** Runs an arbitrary plan edit from a screen, as one undoable step. */
	public static void editFromScreen(java.util.function.BooleanSupplier action) {
		edit(action);
	}

	/** Copies the cursor layer (units, masks, roads, clears) into the layer above it. */
	private static boolean duplicateLayer(Minecraft client) {
		Cursor cursor = cursor(client);
		if (cursor == null) {
			return false;
		}
		int layer = cursor.layer();
		QcPlan plan = QcClientState.plan;
		QcPlan slice = new QcPlan();
		for (Cell cell : plan.cells()) {
			if (cell.layer() != layer) {
				continue;
			}
			Map<UnitPos, QcUnit> units = plan.units().peekLayer(layer);
			if (units != null && units.containsKey(cell.pos())) {
				slice.units().layer(layer).put(cell.pos(), new QcUnit(units.get(cell.pos())));
				slice.setMask(cell, plan.mask(cell));
				if (plan.building(cell) != null) {
					slice.unitBuildings().put(cell, plan.building(cell));
				}
			}
			if (plan.roads().containsKey(cell)) {
				slice.roads().put(cell, plan.roads().get(cell));
			}
			if (plan.clears().contains(cell)) {
				slice.clears().add(cell);
			}
		}
		if (slice.isEmpty()) {
			return false;
		}
		plan.merge(slice.transformed(0, false, false, 0, 1, 0));
		return true;
	}

	/** Sends the whole plan to the server to be built into the world. */
	public static void apply() {
		if (!QcClientState.active || QcClientState.anchor == null) {
			QcClientState.say("QC: enter the editor (G) before applying a plan");
			return;
		}
		if (QcClientState.plan.isEmpty()) {
			QcClientState.say("QC: the plan is empty");
			return;
		}
		if (QcClientState.requests.waiting(QcRequests.Kind.APPLY, QcClientState.history.revision())) {
			// the same plan is on its way already; an edit (a new revision) may be applied again
			QcClientState.say("QC: this plan is already being applied");
			return;
		}
		QcPlanTransfer.sendApply(QcClientState.anchor, QcClientState.plan, QcClientState.palette);
		QcClientState.say("QC: applying plan (" + QcClientState.plan.summary() + ")...");
	}

	private static boolean toggleWall(Cursor cursor, boolean wallState, boolean gate) {
		Direction face = cursor.face();
		UnitSide side = face == null ? null : UnitSide.of(face);
		if (side == null) {
			QcClientState.say(FACE_MESSAGE);
			return false;
		}
		Map<UnitPos, QcUnit> layer = QcClientState.plan.units().peekLayer(cursor.layer());
		QcUnit center = layer == null ? null : layer.get(cursor.cell());
		QcUnit neighbour = layer == null ? null : layer.get(neighbour(cursor.cell(), side));
		if (center == null || neighbour == null) {
			QcClientState.say(PAIR_MESSAGE);
			return false;
		}
		if (gate) {
			if (wallState && !(hasWallFlag(center, side) && hasWallFlag(neighbour, opposite(side)))) {
				// a gate is a doorway in a wall: without one there is nothing to open, so say so instead of nothing
				QcClientState.say(GATE_MESSAGE);
				return false;
			}
			if (hasWallFlag(center, side) && hasWallFlag(neighbour, opposite(side))
					&& (isGateFlag(center, side) != wallState || isGateFlag(neighbour, opposite(side)) != wallState)) {
				setGateFlag(center, side, wallState);
				setGateFlag(neighbour, opposite(side), wallState);
				return true;
			}
			return false;
		}
		if (hasWallFlag(center, side) == wallState && hasWallFlag(neighbour, opposite(side)) == wallState) {
			return false;
		}
		setWallFlag(center, side, wallState);
		setWallFlag(neighbour, opposite(side), wallState);
		return true;
	}

	/** The reported unit the crosshair points at, if it is inside the cursor cell of its layer. */
	@Nullable
	private static QcUnitRecord crosshairUnit(Minecraft client) {
		Cursor cursor = cursor(client);
		BlockPos anchor = QcClientState.anchor;
		if (cursor == null || anchor == null) {
			return null;
		}
		// the unit actually hit, not the layer a placing tool would build on
		BlockPos origin = anchor.offset(cursor.cell().x() * 8, cursor.hitLayer() * 8, cursor.cell().z() * 8);
		for (QcUnitRecord record : QcClientState.nearbyUnits.values()) {
			if (record.isBuilding() && record.origin().equals(origin)) {
				return record;
			}
		}
		return null;
	}

	private static void align(Minecraft client) {
		QcUnitRecord target = crosshairUnit(client);
		if (target == null) {
			QcClientState.say(NO_UNIT_MESSAGE);
			return;
		}
		client.setScreen(new QcAlignScreen(target));
	}

	private static boolean isPlacingTool(QcTool tool) {
		return tool == QcTool.UNITS || tool == QcTool.TEMPLATE;
	}

	private static boolean modifierDown(Minecraft client, int left, int right) {
		long handle = client.getWindow().getWindow();
		return InputConstants.isKeyDown(handle, left) || InputConstants.isKeyDown(handle, right);
	}

	static UnitPos neighbour(UnitPos cell, UnitSide side) {
		return Flags.neighbour(cell, side);
	}

	static UnitSide opposite(UnitSide side) {
		return Flags.opposite(side);
	}

	static boolean hasWallFlag(QcUnit unit, UnitSide side) {
		return Flags.hasWall(unit, side);
	}

	static boolean isGateFlag(QcUnit unit, UnitSide side) {
		return Flags.isGate(unit, side);
	}

	static void setWallFlag(QcUnit unit, UnitSide side, boolean value) {
		Flags.setWall(unit, side, value);
	}

	static void setGateFlag(QcUnit unit, UnitSide side, boolean value) {
		Flags.setGate(unit, side, value);
	}

	/**
	 * The session cells right in front of a door the server reported ({@code /qc scan}); the units and
	 * template tools never plan a unit there, so a new plan cannot wall up a standing building.
	 */
	static Set<Cell> standingDoorFronts() {
		return QcSurroundings.of(QcClientState.nearbyUnits.values(), QcClientState.anchor).doorFronts();
	}

	/** The world-space origin of a session cell: {@code anchor + 8 * (dx, dy, dz)}. */
	static BlockPos cellOrigin(UnitPos cell, int layer) {
		BlockPos anchor = QcClientState.anchor;
		return anchor.offset(cell.x() * 8, layer * 8, cell.z() * 8);
	}
}
