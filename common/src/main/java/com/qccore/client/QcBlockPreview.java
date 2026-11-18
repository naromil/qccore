package com.qccore.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.qccore.core.plan.PlanCompiler;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

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

	private static int rolesVersion = -1;
	private static com.qccore.core.qc.District.Result roles;

	/** Per style, the required roles the plan's buildings and roads reach without a block. */
	public static Map<String, java.util.Set<String>> missing() {
		ensureRoles();
		return roles == null ? Map.of() : roles.missing();
	}

	/** Per style, every role the plan's buildings and roads reach. */
	public static Map<String, java.util.Set<String>> reached() {
		ensureRoles();
		return roles == null ? Map.of() : roles.reached();
	}

	private static void ensureRoles() {
		if (rolesVersion == QcClientState.planVersion) {
			return;
		}
		rolesVersion = QcClientState.planVersion;
		if (QcClientState.plan.cells().size() > CELL_LIMIT) {
			roles = null;
			return;
		}
		try {
			roles = com.qccore.core.qc.District.build(QcClientState.plan);
		} catch (RuntimeException e) {
			roles = null;
		}
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
	static void render(Minecraft client, PoseStack matrices, Vec3 camera) {
		refresh();
		if (!QcClientState.blockPreview || blocks.isEmpty() || client.level == null) {
			return;
		}
		BlockRenderDispatcher renderer = client.getBlockRenderer();
		MultiBufferSource.BufferSource consumers = client.renderBuffers().bufferSource();
		int drawn = 0;
		for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
			BlockPos pos = entry.getKey();
			if (camera.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > RANGE * RANGE) {
				continue;
			}
			if (client.level.getBlockState(pos).equals(entry.getValue())) {
				continue;
			}
			matrices.pushPose();
			matrices.translate(pos.getX(), pos.getY(), pos.getZ());
			BlockState state = entry.getValue();
			if (state.getRenderShape() == RenderShape.MODEL) {
				VertexConsumer ghost = new AlphaConsumer(consumers.getBuffer(Sheets.translucentCullBlockSheet()), ALPHA);
				int color = client.getBlockColors().getColor(state, null, null, 0);
				renderer.getModelRenderer().renderModel(matrices.last(), ghost, state, renderer.getBlockModel(state),
						(color >> 16 & 255) / 255.0F, (color >> 8 & 255) / 255.0F, (color & 255) / 255.0F,
						LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
			} else {
				renderer.renderSingleBlock(state, matrices, consumers,
						LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
			}
			matrices.popPose();
			if (++drawn >= LIMIT) {
				break;
			}
		}
		consumers.endBatch();
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
		public VertexConsumer uv(float u, float v) {
			this.delegate.uv(u, v);
			return this;
		}

		@Override
		public VertexConsumer overlayCoords(int u, int v) {
			this.delegate.overlayCoords(u, v);
			return this;
		}

		@Override
		public VertexConsumer uv2(int u, int v) {
			this.delegate.uv2(u, v);
			return this;
		}

		@Override
		public VertexConsumer normal(float x, float y, float z) {
			this.delegate.normal(x, y, z);
			return this;
		}

		@Override
		public void endVertex() {
			this.delegate.endVertex();
		}

		@Override
		public void defaultColor(int red, int green, int blue, int ignored) {
			this.delegate.defaultColor(red, green, blue, this.alpha);
		}

		@Override
		public void unsetDefaultColor() {
			this.delegate.unsetDefaultColor();
		}
	}
}
