package com.qccore.client;

import com.qccore.client.QcInteraction.Cursor;
import com.qccore.core.QcUnit;
import com.qccore.core.UnitLayers;
import com.qccore.core.UnitPos;
import com.qccore.core.UnitSide;
import com.qccore.world.QcUnitRecord;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Map;

/**
 * Draws the layout as a translucent wireframe in the world: the cells of every layer, the wall and
 * gate plates, the cursor cell and the markers for reported units and the anchor.
 */
public final class QcOverlayRenderer implements WorldRenderEvents.AfterTranslucent {
	public static final QcOverlayRenderer INSTANCE = new QcOverlayRenderer();

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

	@Override
	public void afterTranslucent(WorldRenderContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (!QcClientState.active || !QcClientState.overlayVisible || client.world == null
				|| QcClientState.anchor == null) {
			return;
		}
		VertexConsumerProvider consumers = context.consumers();
		VertexConsumer vertices = consumers.getBuffer(RenderLayer.getLines());
		MatrixStack matrices = context.matrixStack();
		Vec3d camera = context.camera().getPos();
		Cursor cursor = QcInteraction.cursor(client);
		matrices.push();
		try {
			matrices.translate(-camera.x, -camera.y, -camera.z);
			drawCells(matrices, vertices, camera, cursor);
			drawWalls(matrices, vertices);
			drawCursor(matrices, vertices, cursor);
			drawMarkers(matrices, vertices);
		} finally {
			matrices.pop();
		}
	}

	/** Every unit cell of every layer; the cursor layer is highlighted. */
	private void drawCells(MatrixStack matrices, VertexConsumer vertices, Vec3d camera, Cursor cursor) {
		UnitLayers layers = QcClientState.layers;
		int drawn = 0;
		for (int layer : layers.layerKeys()) {
			Map<UnitPos, QcUnit> units = layers.peekLayer(layer);
			if (units == null) {
				continue;
			}
			for (UnitPos cell : units.keySet()) {
				BlockPos origin = QcInteraction.cellOrigin(cell, layer);
				if (camera.squaredDistanceTo(origin.getX() + 4.0, origin.getY() + 4.0, origin.getZ() + 4.0)
						> VIEW_RANGE * VIEW_RANGE) {
					continue;
				}
				if (drawn >= CELL_LIMIT) {
					return;
				}
				boolean current = cursor != null && cursor.layer() == layer;
				drawBox(matrices, vertices, origin, 8.0,
						current ? CURSOR_LAYER_RED : LAYER_RED,
						current ? CURSOR_LAYER_GREEN : LAYER_GREEN,
						current ? CURSOR_LAYER_BLUE : LAYER_BLUE,
						current ? LAYER_OPAQUE_ALPHA : LAYER_ALPHA);
				drawn++;
			}
		}
	}

	/**
	 * One plate per unit and horizontal side whose wall flag is set; the gate flag only changes its
	 * colour, exactly as QCSimple draws a dashed gate line instead of a solid wall line.
	 */
	private void drawWalls(MatrixStack matrices, VertexConsumer vertices) {
		UnitLayers layers = QcClientState.layers;
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
	private void drawPlate(MatrixStack matrices, VertexConsumer vertices, BlockPos origin, UnitSide side, boolean gate) {
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
			case NORTH -> WorldRenderer.drawBox(matrices, vertices, lowX, lowY, origin.getZ() - PLATE_OFFSET,
					highX, highY, origin.getZ() - PLATE_OFFSET, red, green, blue, LAYER_OPAQUE_ALPHA);
			case SOUTH -> WorldRenderer.drawBox(matrices, vertices, lowX, lowY, origin.getZ() + 8.0 + PLATE_OFFSET,
					highX, highY, origin.getZ() + 8.0 + PLATE_OFFSET, red, green, blue, LAYER_OPAQUE_ALPHA);
			case WEST -> WorldRenderer.drawBox(matrices, vertices, origin.getX() - PLATE_OFFSET, lowY, lowZ,
					origin.getX() - PLATE_OFFSET, highY, highZ, red, green, blue, LAYER_OPAQUE_ALPHA);
			case EAST -> WorldRenderer.drawBox(matrices, vertices, origin.getX() + 8.0 + PLATE_OFFSET, lowY, lowZ,
					origin.getX() + 8.0 + PLATE_OFFSET, highY, highZ, red, green, blue, LAYER_OPAQUE_ALPHA);
		}
	}

	private void drawCursor(MatrixStack matrices, VertexConsumer vertices, Cursor cursor) {
		if (cursor == null || cursor.layer() < 1) {
			return;
		}
		drawBox(matrices, vertices, QcInteraction.cellOrigin(cursor.cell(), cursor.layer()), 8.0,
				1.0F, 1.0F, 1.0F, LAYER_OPAQUE_ALPHA);
	}

	/** A small cube at the anchor and at every unit the server reported. */
	private void drawMarkers(MatrixStack matrices, VertexConsumer vertices) {
		for (QcUnitRecord record : QcClientState.nearbyUnits.values()) {
			drawBox(matrices, vertices, record.origin(), MARKER_SIZE,
					MARKER_RED, MARKER_GREEN, MARKER_BLUE, LAYER_OPAQUE_ALPHA);
		}
		drawBox(matrices, vertices, QcClientState.anchor, MARKER_SIZE,
				ANCHOR_RED, ANCHOR_GREEN, ANCHOR_BLUE, LAYER_OPAQUE_ALPHA);
	}

	private void drawBox(MatrixStack matrices, VertexConsumer vertices, BlockPos origin, double size,
			float red, float green, float blue, float alpha) {
		WorldRenderer.drawBox(matrices, vertices,
				origin.getX(), origin.getY(), origin.getZ(),
				origin.getX() + size, origin.getY() + size, origin.getZ() + size,
				red, green, blue, alpha);
	}
}
