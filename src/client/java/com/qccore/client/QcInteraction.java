package com.qccore.client;

import com.qccore.core.Alignment;
import com.qccore.core.QcUnit;
import com.qccore.core.UnitPos;
import com.qccore.core.UnitSide;
import com.qccore.core.nbt.LayersCodec;
import com.qccore.core.nbt.PaletteCodec;
import com.qccore.net.QcPackets.QcBuildC2S;
import com.qccore.world.QcUnitRecord;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Everything the in-world editor does with the crosshair: entering the mode, deriving the cursor
 * cell from the crosshair, and the mouse actions the input mixin forwards here. Rendering lives in
 * {@link QcOverlayRenderer}.
 */
public final class QcInteraction {
	private static final String LAYER_MESSAGE = "QC: layers start at 1";
	private static final String FACE_MESSAGE = "QC: look at a side face to place walls";
	private static final String PAIR_MESSAGE = "QC: both QC units must exist to attach a wall";
	private static final String NO_UNIT_MESSAGE = "QC: no placed QC Unit in the crosshair";
	private static final String WORLD_MESSAGE = "QC: editor reset (changed dimension)";

	/** How close a reported unit has to be before the anchor adopts its lattice. */
	private static final double LATTICE_RANGE = 48.0;

	/** Where the crosshair points, in session unit coordinates. */
	public record Cursor(UnitPos cell, int layer, @Nullable Direction face, boolean blockHit) {
	}

	private QcInteraction() {
	}

	/** Enters the editor with the anchor at the player, so layer 1 is the player's own cell. */
	public static void enterEditorMode(MinecraftClient client) {
		if (client.player == null || client.world == null) {
			return;
		}
		int x = MathHelper.floor(client.player.getX());
		int y = MathHelper.floor(client.player.getY());
		int z = MathHelper.floor(client.player.getZ());
		QcClientState.anchor = new BlockPos(x, Math.floorDiv(y, 8) * 8 - 8, z);
		QcClientState.active = true;
		QcClientState.dimension = client.world.getRegistryKey().getValue();
		adoptNearbyLattice();
	}

	/** Moves the session anchor to the crosshair; works for block hits and misses alike. */
	public static void reanchor(MinecraftClient client) {
		HitResult target = client.crosshairTarget;
		if (target == null) {
			return;
		}
		Vec3d reference = target.getPos();
		QcClientState.anchor = new BlockPos(MathHelper.floor(reference.x),
				MathHelper.floor(reference.y) - 8, MathHelper.floor(reference.z));
		adoptNearbyLattice();
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
			double distance = record.origin().getSquaredDistance(anchor);
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
	public static Cursor cursor(MinecraftClient client) {
		if (!QcClientState.active || client.crosshairTarget == null || QcClientState.anchor == null) {
			return null;
		}
		HitResult target = client.crosshairTarget;
		Direction face = null;
		BlockPos reference;
		boolean blockHit = target instanceof BlockHitResult;
		if (target instanceof BlockHitResult hit) {
			reference = hit.getBlockPos();
			face = hit.getSide();
		} else {
			reference = BlockPos.ofFloored(target.getPos());
		}
		BlockPos anchor = QcClientState.anchor;
		UnitPos cell = new UnitPos(Math.floorDiv(reference.getX() - anchor.getX(), 8),
				Math.floorDiv(reference.getZ() - anchor.getZ(), 8));
		return new Cursor(cell, Math.floorDiv(reference.getY() - anchor.getY(), 8), face, blockHit);
	}

	/**
	 * Runs one mouse button press. Holding a button repeats this on purpose and every action just
	 * sets a state, which is what makes drag-painting behave like QCSimple's canvas drag.
	 */
	public static void click(boolean useKey) {
		MinecraftClient client = MinecraftClient.getInstance();
		Cursor cursor = cursor(client);
		if (cursor == null) {
			return;
		}
		boolean control = modifierDown(client, GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL);
		boolean shift = modifierDown(client, GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT);
		if (useKey) {
			if (control) {
				toggleWall(cursor, true, true);
			} else if (shift) {
				toggleWall(cursor, true, false);
			} else {
				place(client, cursor);
			}
		} else {
			if (control) {
				toggleWall(cursor, false, true);
			} else if (shift) {
				toggleWall(cursor, false, false);
			} else {
				erase(cursor);
			}
		}
	}

	/** The keybind pump: world guard, keybinds and the HUD message lifetime. */
	public static void onClientTick(MinecraftClient client) {
		Identifier current = client.world == null ? null : client.world.getRegistryKey().getValue();
		if (!Objects.equals(current, QcClientState.dimension)) {
			boolean hadSession = QcClientState.dimension != null;
			QcClientState.reset();
			QcClientState.dimension = current;
			if (hadSession) {
				QcClientState.say(WORLD_MESSAGE);
			}
		}
		if (QcClientState.messageTicks > 0 && --QcClientState.messageTicks == 0) {
			QcClientState.message = null;
		}
		while (QcKeys.editor.wasPressed()) {
			if (QcClientState.active) {
				QcClientState.active = false;
			} else {
				enterEditorMode(client);
			}
		}
		while (QcKeys.reanchor.wasPressed()) {
			if (QcClientState.active) {
				reanchor(client);
			}
		}
		while (QcKeys.duplicateLayer.wasPressed()) {
			duplicateLayer(client);
		}
		while (QcKeys.save.wasPressed()) {
			client.setScreen(new QcSaveScreen());
		}
		while (QcKeys.build.wasPressed()) {
			build();
		}
		while (QcKeys.config.wasPressed()) {
			client.setScreen(new QcConfigScreen());
		}
		while (QcKeys.unitAlign.wasPressed()) {
			align(client);
		}
		while (QcKeys.overlay.wasPressed()) {
			QcClientState.overlayVisible = !QcClientState.overlayVisible;
		}
	}

	/** Copies the cursor layer into the layer above it, like {@code EditorState.duplicateLayerData}. */
	private static void duplicateLayer(MinecraftClient client) {
		Cursor cursor = cursor(client);
		if (cursor == null || cursor.layer() < 1) {
			return;
		}
		Map<UnitPos, QcUnit> source = QcClientState.layers.layer(cursor.layer());
		Map<UnitPos, QcUnit> duplicate = new LinkedHashMap<>();
		for (Map.Entry<UnitPos, QcUnit> entry : source.entrySet()) {
			duplicate.put(entry.getKey(), new QcUnit(entry.getValue()));
		}
		QcClientState.layers.setLayer(cursor.layer() + 1, duplicate);
	}

	private static void place(MinecraftClient client, Cursor cursor) {
		if (cursor.layer() < 1) {
			QcClientState.say(LAYER_MESSAGE);
			return;
		}
		QcClientState.layers.layer(cursor.layer()).putIfAbsent(placementCell(client, cursor), new QcUnit());
	}

	private static void erase(Cursor cursor) {
		Map<UnitPos, QcUnit> layer = QcClientState.layers.peekLayer(cursor.layer());
		if (layer == null) {
			return;
		}
		for (UnitSide side : UnitSide.values()) {
			QcUnit neighbour = layer.get(neighbour(cursor.cell(), side));
			if (neighbour == null) {
				continue;
			}
			setWallFlag(neighbour, opposite(side), false);
			setGateFlag(neighbour, opposite(side), false);
		}
		layer.remove(cursor.cell());
	}

	private static void toggleWall(Cursor cursor, boolean wallState, boolean gate) {
		Direction face = cursor.face();
		UnitSide side = face == null ? null : UnitSide.of(face);
		if (side == null) {
			QcClientState.say(FACE_MESSAGE);
			return;
		}
		Map<UnitPos, QcUnit> layer = QcClientState.layers.peekLayer(cursor.layer());
		QcUnit center = layer == null ? null : layer.get(cursor.cell());
		QcUnit neighbour = layer == null ? null : layer.get(neighbour(cursor.cell(), side));
		if (center == null || neighbour == null) {
			QcClientState.say(PAIR_MESSAGE);
			return;
		}
		if (gate) {
			if (hasWallFlag(center, side) && hasWallFlag(neighbour, opposite(side))) {
				setGateFlag(center, side, wallState);
				setGateFlag(neighbour, opposite(side), wallState);
			}
			return;
		}
		setWallFlag(center, side, wallState);
		setWallFlag(neighbour, opposite(side), wallState);
	}

	/** The reported unit the crosshair points at, if it is inside the cursor cell of its layer. */
	@Nullable
	private static QcUnitRecord crosshairUnit(MinecraftClient client) {
		Cursor cursor = cursor(client);
		BlockPos anchor = QcClientState.anchor;
		if (cursor == null || anchor == null || cursor.layer() < 1) {
			return null;
		}
		BlockPos origin = anchor.add(cursor.cell().x() * 8, cursor.layer() * 8, cursor.cell().z() * 8);
		for (QcUnitRecord record : QcClientState.nearbyUnits.values()) {
			if (record.origin().equals(origin)) {
				return record;
			}
		}
		return null;
	}

	private static void build() {
		if (!QcClientState.active || QcClientState.anchor == null) {
			QcClientState.say(NO_UNIT_MESSAGE);
			return;
		}
		ClientPlayNetworking.send(new QcBuildC2S(QcClientState.anchor,
				LayersCodec.toNbt(QcClientState.layers), PaletteCodec.toNbt(QcClientState.palette)));
	}

	private static void align(MinecraftClient client) {
		QcUnitRecord target = crosshairUnit(client);
		if (target == null) {
			QcClientState.say(NO_UNIT_MESSAGE);
			return;
		}
		client.setScreen(new QcAlignScreen(target));
	}

	/** The cell a placement lands in: the hit block offset by the face, or the air block aimed at. */
	private static UnitPos placementCell(MinecraftClient client, Cursor cursor) {
		BlockPos anchor = QcClientState.anchor;
		BlockPos reference = cursor.blockHit() && client.crosshairTarget instanceof BlockHitResult hit
				? hit.getBlockPos().offset(cursor.face())
				: BlockPos.ofFloored(client.crosshairTarget.getPos());
		return new UnitPos(Math.floorDiv(reference.getX() - anchor.getX(), 8),
				Math.floorDiv(reference.getZ() - anchor.getZ(), 8));
	}

	private static boolean modifierDown(MinecraftClient client, int left, int right) {
		long handle = client.getWindow().getHandle();
		return InputUtil.isKeyPressed(handle, left) || InputUtil.isKeyPressed(handle, right);
	}

	static UnitPos neighbour(UnitPos cell, UnitSide side) {
		Direction direction = side.toDirection();
		return new UnitPos(cell.x() + direction.getOffsetX(), cell.z() + direction.getOffsetZ());
	}

	static UnitSide opposite(UnitSide side) {
		return UnitSide.of(side.toDirection().getOpposite());
	}

	static boolean hasWallFlag(QcUnit unit, UnitSide side) {
		return switch (side) {
			case NORTH -> unit.hasWallN();
			case EAST -> unit.hasWallE();
			case SOUTH -> unit.hasWallS();
			case WEST -> unit.hasWallW();
		};
	}

	static boolean isGateFlag(QcUnit unit, UnitSide side) {
		return switch (side) {
			case NORTH -> unit.isGateN();
			case EAST -> unit.isGateE();
			case SOUTH -> unit.isGateS();
			case WEST -> unit.isGateW();
		};
	}

	static void setWallFlag(QcUnit unit, UnitSide side, boolean value) {
		switch (side) {
			case NORTH -> unit.setWallN(value);
			case EAST -> unit.setWallE(value);
			case SOUTH -> unit.setWallS(value);
			case WEST -> unit.setWallW(value);
		}
	}

	static void setGateFlag(QcUnit unit, UnitSide side, boolean value) {
		switch (side) {
			case NORTH -> unit.setGateN(value);
			case EAST -> unit.setGateE(value);
			case SOUTH -> unit.setGateS(value);
			case WEST -> unit.setGateW(value);
		}
	}

	/** The world-space origin of a session cell: {@code anchor + 8 * (dx, dy, dz)}. */
	static BlockPos cellOrigin(UnitPos cell, int layer) {
		BlockPos anchor = QcClientState.anchor;
		return anchor.add(cell.x() * 8, layer * 8, cell.z() * 8);
	}
}
