package com.qccore.client;

import com.qccore.core.plan.PlanCompiler;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Map;

/**
 * Ghost blocks of the plan: the compiled blocks (units with their masks, roads, ground fill) drawn
 * translucent in place, the way Effortless Building previews a placement. Only positions whose world block
 * differs are drawn, within {@link #RANGE} blocks of the camera and at most {@link #LIMIT} per
 * frame. The compilation is cached until the plan, the palette or the anchor changes.
 */
public final class QcBlockPreview {
	private static final double RANGE = 64.0;
	/** Opacity of a ghost block, 0..255. */
	private static final int ALPHA = 110;
	private static final int LIMIT = 20000;
	/** Plans touching more cells than this are not compiled for the preview (a 1024-unit plan is ~700k blocks). */
	private static final int CELL_LIMIT = 1024;

	private static int version = -1;
	private static BlockPos anchor;
	private static Map<BlockPos, BlockState> blocks = Map.of();
	private static String problem;

	private QcBlockPreview() {
	}

	/** The first compile problem of the current plan (an unknown road block...), or {@code null}. */
	public static String problem() {
		return problem;
	}

	/** How many blocks the plan places, as last compiled. */
	public static int size() {
		return blocks.size();
	}

	static void refresh() {
		if (version == QcClientState.planVersion && QcClientState.anchor != null && QcClientState.anchor.equals(anchor)) {
			return;
		}
		version = QcClientState.planVersion;
		anchor = QcClientState.anchor;
		if (anchor == null || QcClientState.plan.isEmpty()) {
			blocks = Map.of();
			problem = null;
			return;
		}
		if (QcClientState.plan.cells().size() > CELL_LIMIT) {
			blocks = Map.of();
			problem = null;
			return;
		}
		try {
			PlanCompiler.Compiled compiled = PlanCompiler.compile(QcClientState.plan, QcClientState.palette, anchor);
			blocks = compiled.forExport(false);
			problem = compiled.problems().isEmpty() ? null : compiled.problems().get(0);
		} catch (RuntimeException e) {
			blocks = Map.of();
			problem = "preview failed: " + e.getMessage();
		}
	}

	/** Draws the ghost blocks; {@code matrices} is already translated by minus the camera position. */
	static void render(MinecraftClient client, MatrixStack matrices, Vec3d camera) {
		refresh();
		if (!QcClientState.blockPreview || blocks.isEmpty() || client.world == null) {
			return;
		}
		BlockRenderManager renderer = client.getBlockRenderManager();
		VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
		int drawn = 0;
		for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
			BlockPos pos = entry.getKey();
			if (camera.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > RANGE * RANGE) {
				continue;
			}
			if (client.world.getBlockState(pos).equals(entry.getValue())) {
				continue;
			}
			matrices.push();
			matrices.translate(pos.getX(), pos.getY(), pos.getZ());
			BlockState state = entry.getValue();
			if (state.getRenderType() == BlockRenderType.MODEL) {
				VertexConsumer ghost = new AlphaConsumer(consumers.getBuffer(TexturedRenderLayers.getEntityTranslucentCull()), ALPHA);
				int color = client.getBlockColors().getColor(state, null, null, 0);
				renderer.getModelRenderer().render(matrices.peek(), ghost, state, renderer.getModel(state),
						(color >> 16 & 255) / 255.0F, (color >> 8 & 255) / 255.0F, (color & 255) / 255.0F,
						LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
			} else {
				renderer.renderBlockAsEntity(state, matrices, consumers,
						LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
			}
			matrices.pop();
			if (++drawn >= LIMIT) {
				break;
			}
		}
		consumers.draw();
	}

	/** Passes every vertex through, with a fixed alpha: the ghost look on the translucent block-atlas layer. */
	private static final class AlphaConsumer implements VertexConsumer {
		private final VertexConsumer delegate;
		private final int alpha;

		AlphaConsumer(VertexConsumer delegate, int alpha) {
			this.delegate = delegate;
			this.alpha = alpha;
		}

		@Override
		public VertexConsumer vertex(double x, double y, double z) {
			this.delegate.vertex(x, y, z);
			return this;
		}

		@Override
		public VertexConsumer color(int red, int green, int blue, int ignored) {
			this.delegate.color(red, green, blue, this.alpha);
			return this;
		}

		@Override
		public VertexConsumer texture(float u, float v) {
			this.delegate.texture(u, v);
			return this;
		}

		@Override
		public VertexConsumer overlay(int u, int v) {
			this.delegate.overlay(u, v);
			return this;
		}

		@Override
		public VertexConsumer light(int u, int v) {
			this.delegate.light(u, v);
			return this;
		}

		@Override
		public VertexConsumer normal(float x, float y, float z) {
			this.delegate.normal(x, y, z);
			return this;
		}

		@Override
		public void next() {
			this.delegate.next();
		}

		@Override
		public void fixedColor(int red, int green, int blue, int ignored) {
			this.delegate.fixedColor(red, green, blue, this.alpha);
		}

		@Override
		public void unfixColor() {
			this.delegate.unfixColor();
		}
	}
}
