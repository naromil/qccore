package com.qccore.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.qccore.client.QcInteraction.Cursor;
import com.qccore.core.QcUnit;
import com.qccore.core.UnitLayers;
import com.qccore.core.UnitPos;
import com.qccore.core.UnitSide;
import com.qccore.core.plan.Cell;
import com.qccore.core.plan.ComponentMask;
import com.qccore.core.plan.Modifiers;
import com.qccore.world.QcUnitRecord;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import java.util.Map;

/**
 * Draws the plan in the world: ghost blocks of what it would place ({@link QcBlockPreview}), the
 * unit cells of every layer as a wireframe (partial-component units in purple), the wall and gate
 * plates, cleared cells, road plates, the shape in progress in the tool's colour, the mirror planes,
 * the cursor cell and the markers for reported units and the anchor.
 */
public final class QcOverlayRenderer {

	private static final float CURSOR_LAYER_RED = 0.98F;
	private static final float CURSOR_LAYER_GREEN = 0.86F;
	private static final float CURSOR_LAYER_BLUE = 0.30F;
	private static final float LAYER_RED = 0.35F;
	private static final float LAYER_GREEN = 0.65F;
	private static final float LAYER_BLUE = 0.95F;
	private static final float LAYER_ALPHA = 0.30F;
	private static final float LAYER_OPAQUE_ALPHA = 0.9F;
	private static final float WALL_RED = 0.29F;
	private static final float WALL_GREEN = 0.66F;
	private static final float WALL_BLUE = 1.0F;
	private static final float GATE_RED = 0.27F;
	private static final float GATE_GREEN = 0.88F;
	private static final float GATE_BLUE = 0.48F;
	private static final float MARKER_RED = 0.95F;
	private static final float MARKER_GREEN = 0.75F;
	private static final float MARKER_BLUE = 0.20F;
	private static final float ANCHOR_RED = 0.20F;
	private static final float ANCHOR_GREEN = 0.95F;
	private static final float ANCHOR_BLUE = 0.55F;
	private static final float PARTIAL_RED = 0.75F;
	private static final float PARTIAL_GREEN = 0.45F;
	private static final float PARTIAL_BLUE = 0.95F;
	private static final float MIRROR_RED = 0.95F;
	private static final float MIRROR_GREEN = 0.3F;
	private static final float MIRROR_BLUE = 0.85F;
	/** Half the length of a drawn mirror plane, in blocks. */
	private static final double MIRROR_REACH = 48.0;

	/** Cells further than this from the camera are not drawn. */
	private static final double VIEW_RANGE = 128.0;
	/** Hard cap on the cell boxes of one frame. */
	private static final int CELL_LIMIT = 2048;
	/** Outward offset of a wall plate from the shared unit face. */
	private static final double PLATE_OFFSET = 0.02;
	/** Edge length of the anchor and reported-unit markers. */
	private static final double MARKER_SIZE = 0.15;

	private QcOverlayRenderer() {
	}

	/**
	 * Draws one frame. The loader glue calls it right after the translucent terrain pass (Fabric
	 * {@code WorldRenderEvents.AFTER_TRANSLUCENT}, Forge {@code RenderLevelStageEvent} at
	 * {@code AFTER_TRANSLUCENT_BLOCKS}) with the world pose stack and the camera; the lines are drawn
	 * into the shared buffer source and flushed before returning.
	 */
	public static void render(PoseStack matrices, Camera camera) {
		Minecraft client = Minecraft.getInstance();
		if (!QcClientState.active || !QcClientState.overlayVisible || client.level == null
				|| QcClientState.anchor == null) {
			return;
		}
		Vec3 cameraPos = camera.getPosition();
		Cursor cursor = QcInteraction.cursor(client);
		matrices.pushPose();
		try {
			matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
			QcBlockPreview.render(client, matrices, cameraPos);
			MultiBufferSource.BufferSource consumers = client.renderBuffers().bufferSource();
			VertexConsumer vertices = consumers.getBuffer(RenderType.lines());
			drawCells(matrices, vertices, cameraPos, cursor);
			drawWalls(matrices, vertices);
			drawPlanExtras(matrices, vertices, cameraPos);
			drawShape(client, matrices, vertices);
			drawCursor(matrices, vertices, cursor);
			drawMarkers(matrices, vertices);
			consumers.endBatch(RenderType.lines());
		} finally {
			matrices.popPose();
		}
	}

	/** Every unit cell of every layer; the cursor layer is highlighted. */
	private static void drawCells(PoseStack matrices, VertexConsumer vertices, Vec3 camera, Cursor cursor) {
		UnitLayers layers = QcClientState.plan.units();
		int drawn = 0;
		for (int layer : layers.layerKeys()) {
			Map<UnitPos, QcUnit> units = layers.peekLayer(layer);
			if (units == null) {
				continue;
			}
			for (UnitPos cell : units.keySet()) {
				BlockPos origin = QcInteraction.cellOrigin(cell, layer);
				if (camera.distanceToSqr(origin.getX() + 4.0, origin.getY() + 4.0, origin.getZ() + 4.0)
						> VIEW_RANGE * VIEW_RANGE) {
					continue;
				}
				if (drawn >= CELL_LIMIT) {
					return;
				}
				boolean current = cursor != null && cursor.layer() == layer;
				boolean partial = QcClientState.plan.mask(Cell.of(cell, layer)) != ComponentMask.ALL;
				drawBox(matrices, vertices, origin, 8.0,
						current ? CURSOR_LAYER_RED : partial ? PARTIAL_RED : LAYER_RED,
						current ? CURSOR_LAYER_GREEN : partial ? PARTIAL_GREEN : LAYER_GREEN,
						current ? CURSOR_LAYER_BLUE : partial ? PARTIAL_BLUE : LAYER_BLUE,
						current ? LAYER_OPAQUE_ALPHA : LAYER_ALPHA);
				drawn++;
			}
		}
	}

	/**
	 * One plate per unit and horizontal side whose wall flag is set; the gate flag only changes its
	 * colour, exactly as QCSimple draws a dashed gate line instead of a solid wall line.
	 */
	private static void drawWalls(PoseStack matrices, VertexConsumer vertices) {
		UnitLayers layers = QcClientState.plan.units();
		for (int layer : layers.layerKeys()) {
			Map<UnitPos, QcUnit> units = layers.peekLayer(layer);
			if (units == null) {
				continue;
			}
			for (Map.Entry<UnitPos, QcUnit> entry : units.entrySet()) {
				UnitPos cell = entry.getKey();
				QcUnit unit = entry.getValue();
				BlockPos origin = QcInteraction.cellOrigin(cell, layer);
				for (UnitSide side : UnitSide.values()) {
					if (!units.containsKey(QcInteraction.neighbour(cell, side))) {
						continue;
					}
					if (QcInteraction.hasWallFlag(unit, side)) {
						drawPlate(matrices, vertices, origin, side, QcInteraction.isGateFlag(unit, side));
					}
				}
			}
		}
	}

	/** The shared face plane between two units, pushed outwards so it does not z-fight. */
	private static void drawPlate(PoseStack matrices, VertexConsumer vertices, BlockPos origin, UnitSide side, boolean gate) {
		float red = gate ? GATE_RED : WALL_RED;
		float green = gate ? GATE_GREEN : WALL_GREEN;
		float blue = gate ? GATE_BLUE : WALL_BLUE;
		double lowX = origin.getX() + 1.0;
		double lowY = origin.getY() + 1.0;
		double lowZ = origin.getZ() + 1.0;
		double highX = origin.getX() + 7.0;
		double highY = origin.getY() + 7.0;
		double highZ = origin.getZ() + 7.0;
		switch (side) {
			case NORTH -> LevelRenderer.renderLineBox(matrices, vertices, lowX, lowY, origin.getZ() - PLATE_OFFSET,
					highX, highY, origin.getZ() - PLATE_OFFSET, red, green, blue, LAYER_OPAQUE_ALPHA);
			case SOUTH -> LevelRenderer.renderLineBox(matrices, vertices, lowX, lowY, origin.getZ() + 8.0 + PLATE_OFFSET,
					highX, highY, origin.getZ() + 8.0 + PLATE_OFFSET, red, green, blue, LAYER_OPAQUE_ALPHA);
			case WEST -> LevelRenderer.renderLineBox(matrices, vertices, origin.getX() - PLATE_OFFSET, lowY, lowZ,
					origin.getX() - PLATE_OFFSET, highY, highZ, red, green, blue, LAYER_OPAQUE_ALPHA);
			case EAST -> LevelRenderer.renderLineBox(matrices, vertices, origin.getX() + 8.0 + PLATE_OFFSET, lowY, lowZ,
					origin.getX() + 8.0 + PLATE_OFFSET, highY, highZ, red, green, blue, LAYER_OPAQUE_ALPHA);
		}
	}

	/** Cleared cells (an orange inner box) and road cells (a grey plate on the floor plane, 7 wide). */
	private static void drawPlanExtras(PoseStack matrices, VertexConsumer vertices, Vec3 camera) {
		int drawn = 0;
		for (Cell cell : QcClientState.plan.clears()) {
			BlockPos origin = QcInteraction.cellOrigin(cell.pos(), cell.layer());
			if (far(camera, origin) || drawn++ >= CELL_LIMIT) {
				continue;
			}
			LevelRenderer.renderLineBox(matrices, vertices, origin.getX() + 0.3, origin.getY() + 1.0, origin.getZ() + 0.3,
					origin.getX() + 8.7, origin.getY() + 8.7, origin.getZ() + 8.7,
					QcTool.CLEAR.red, QcTool.CLEAR.green, QcTool.CLEAR.blue, LAYER_OPAQUE_ALPHA);
		}
		for (Cell cell : QcClientState.plan.roads().keySet()) {
			BlockPos origin = QcInteraction.cellOrigin(cell.pos(), cell.layer());
			if (far(camera, origin) || drawn++ >= CELL_LIMIT) {
				continue;
			}
			LevelRenderer.renderLineBox(matrices, vertices, origin.getX() + 1.0, origin.getY() + 1.02, origin.getZ() + 1.0,
					origin.getX() + 8.0, origin.getY() + 1.02, origin.getZ() + 8.0,
					QcTool.ROAD.red, QcTool.ROAD.green, QcTool.ROAD.blue, LAYER_OPAQUE_ALPHA);
		}
	}

	/** The cells the shape in progress (or the template under the crosshair) covers, plus the mirror planes. */
	private static void drawShape(Minecraft client, PoseStack matrices, VertexConsumer vertices) {
		QcTool tool = QcClientState.tool;
		int drawn = 0;
		for (Cell cell : QcInteraction.previewCells(client)) {
			if (drawn++ >= CELL_LIMIT) {
				break;
			}
			BlockPos origin = QcInteraction.cellOrigin(cell.pos(), cell.layer());
			LevelRenderer.renderLineBox(matrices, vertices, origin.getX() + 0.1, origin.getY() + 0.1, origin.getZ() + 0.1,
					origin.getX() + 8.9, origin.getY() + 8.9, origin.getZ() + 8.9,
					tool.red, tool.green, tool.blue, LAYER_OPAQUE_ALPHA);
		}
		Modifiers modifiers = QcClientState.modifiers;
		BlockPos anchor = QcClientState.anchor;
		if (anchor == null || !(modifiers.mirrorX || modifiers.mirrorZ)) {
			return;
		}
		Cursor cursor = QcInteraction.cursor(client);
		int layer = cursor == null ? 1 : cursor.layer();
		double y0 = anchor.getY() + layer * 8.0;
		double y1 = y0 + 9.0;
		double centreX = anchor.getX() + modifiers.mirrorCellX * 8 + 4.5;
		double centreZ = anchor.getZ() + modifiers.mirrorCellZ * 8 + 4.5;
		if (modifiers.mirrorX) {
			LevelRenderer.renderLineBox(matrices, vertices, centreX, y0, centreZ - MIRROR_REACH, centreX, y1, centreZ + MIRROR_REACH,
					MIRROR_RED, MIRROR_GREEN, MIRROR_BLUE, LAYER_OPAQUE_ALPHA);
		}
		if (modifiers.mirrorZ) {
			LevelRenderer.renderLineBox(matrices, vertices, centreX - MIRROR_REACH, y0, centreZ, centreX + MIRROR_REACH, y1, centreZ,
					MIRROR_RED, MIRROR_GREEN, MIRROR_BLUE, LAYER_OPAQUE_ALPHA);
		}
	}

	private static boolean far(Vec3 camera, BlockPos origin) {
		return camera.distanceToSqr(origin.getX() + 4.0, origin.getY() + 4.0, origin.getZ() + 4.0) > VIEW_RANGE * VIEW_RANGE;
	}

	private static void drawCursor(PoseStack matrices, VertexConsumer vertices, Cursor cursor) {
		if (cursor == null || cursor.layer() < 1) {
			return;
		}
		drawBox(matrices, vertices, QcInteraction.cellOrigin(cursor.cell(), cursor.layer()), 8.0,
				1.0F, 1.0F, 1.0F, LAYER_OPAQUE_ALPHA);
	}

	/** A small cube at the anchor and at every unit the server reported. */
	private static void drawMarkers(PoseStack matrices, VertexConsumer vertices) {
		for (QcUnitRecord record : QcClientState.nearbyUnits.values()) {
			drawBox(matrices, vertices, record.origin(), MARKER_SIZE,
					MARKER_RED, MARKER_GREEN, MARKER_BLUE, LAYER_OPAQUE_ALPHA);
		}
		drawBox(matrices, vertices, QcClientState.anchor, MARKER_SIZE,
				ANCHOR_RED, ANCHOR_GREEN, ANCHOR_BLUE, LAYER_OPAQUE_ALPHA);
	}

	private static void drawBox(PoseStack matrices, VertexConsumer vertices, BlockPos origin, double size,
			float red, float green, float blue, float alpha) {
		LevelRenderer.renderLineBox(matrices, vertices,
				origin.getX(), origin.getY(), origin.getZ(),
				origin.getX() + size, origin.getY() + size, origin.getZ() + size,
				red, green, blue, alpha);
	}
}
