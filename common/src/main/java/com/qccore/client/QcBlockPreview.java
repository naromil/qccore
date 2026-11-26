package com.qccore.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.qccore.core.QcResult;
import com.qccore.core.plan.Cell;
import com.qccore.core.plan.Join;
import com.qccore.core.plan.PlanCompiler;
import com.qccore.core.plan.QcPlan;
import com.qccore.core.plan.RoadJoin;
import com.qccore.core.plan.Surroundings;
import com.qccore.world.QcPlanService;
import com.qccore.world.QcSurroundings;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Ghost blocks of the plan: the compiled blocks (units with their masks, roads, ground fill) drawn
 * translucent in place, the way Effortless Building previews a placement. Only positions whose world block
 * differs are drawn, within {@link #RANGE} blocks of the camera and at most {@link #LIMIT} per
 * frame. The compilation is cached until the plan, the palette or the anchor changes.
 *
 * <p>When the plan rebuilds standing parts (a new part of the same building, {@link Join}; a road it
 * meets, {@link RoadJoin}), the ghost shows how they change, as the apply will (T28): a ghost that
 * replaces a standing block is drawn a hair larger, so it shows over that block instead of hiding inside
 * it, and the standing blocks the apply takes down (cleared from a rebuilt cell, left-over eaves or roof
 * ends outside it, old road furniture in a rebuilt road's headroom) are drawn red.
 */
public final class QcBlockPreview {
	private static final double RANGE = 64.0;
	/** Opacity of a ghost block, 0..255. */
	private static final int ALPHA = 110;
	/** Opacity of the red tint over a standing block the apply takes down. */
	private static final int REMOVED_ALPHA = 150;
	/** Scale of a ghost drawn over a standing block, so it isn't hidden inside it. */
	private static final float OVER = 1.004F;
	private static final int LIMIT = 20000;
	/** Plans touching more cells than this are not compiled for the preview (a 1024-unit plan is ~700k blocks). */
	private static final int CELL_LIMIT = 1024;

	private static int version = -1;
	private static BlockPos anchor;
	private static Map<BlockPos, BlockState> blocks = Map.of();
	/** Standing blocks the apply takes down from the parts it rebuilds, with their state when compiled. */
	private static Map<BlockPos, BlockState> removed = Map.of();
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

	/** How many standing blocks the apply takes down from the parts it rebuilds, as last compiled. */
	public static int removedCount() {
		return removed.size();
	}

	static void refresh() {
		if (version == QcClientState.planVersion && QcClientState.anchor != null && QcClientState.anchor.equals(anchor)) {
			return;
		}
		version = QcClientState.planVersion;
		anchor = QcClientState.anchor;
		removed = Map.of();
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
			Surroundings around = QcSurroundings.of(QcClientState.nearbyUnits.values(), anchor);
			// the same join the server's apply makes, so the ghost shows the standing part it rebuilds
			QcPlan joined = Join.join(QcClientState.plan, around);
			PlanCompiler.Compiled compiled = PlanCompiler.compile(joined, QcClientState.palette, anchor, around);
			blocks = compiled.forExport(false);
			problem = QcResult.first(compiled.problems());
			BlockGetter level = Minecraft.getInstance().level;
			if (level != null) {
				removed = takenDown(level, QcClientState.plan, joined, around, compiled);
			}
		} catch (RuntimeException e) {
			blocks = Map.of();
			removed = Map.of();
			problem = "preview failed: " + e.getMessage();
		}
	}

	/**
	 * The standing blocks the apply takes down from the parts it rebuilds: in a rebuilt building cell
	 * whatever the joined build doesn't place there (the footprint clear, which keeps the shared planes of
	 * a standing neighbour), outside it the old part's left-overs ({@link QcPlanService#stale}), and in the
	 * headroom of a rebuilt road cell its old furniture. Empty when the plan rebuilds nothing.
	 */
	private static Map<BlockPos, BlockState> takenDown(BlockGetter level, QcPlan planned, QcPlan joined,
			Surroundings around, PlanCompiler.Compiled compiled) {
		Set<Cell> rebuilt = Join.joined(planned, joined);
		Set<Cell> roads = new HashSet<>(RoadJoin.join(joined, around).roads().keySet());
		roads.removeAll(joined.roads().keySet());
		if (rebuilt.isEmpty() && roads.isEmpty()) {
			return Map.of();
		}
		Map<BlockPos, BlockState> out = new LinkedHashMap<>();
		if (!rebuilt.isEmpty()) {
			for (BlockPos pos : QcPlanService.stale(level, joined, rebuilt, QcClientState.palette, anchor, around, compiled)) {
				out.put(pos, level.getBlockState(pos));
			}
			// the cells whose planes the footprint clear keeps: every standing record (buildings and roads)
			// the plan doesn't clear itself, as QcPlanService.apply passes them to QcWorldOps.clearFootprint
			Set<BlockPos> neighbours = new HashSet<>(QcClientState.nearbyUnits.keySet());
			neighbours.removeAll(QcPlanService.fullOrigins(joined, anchor));
			for (Cell cell : rebuilt) {
				BlockPos origin = PlanCompiler.origin(anchor, cell);
				for (BlockPos p : BlockPos.betweenClosed(origin, origin.offset(8, 8, 8))) {
					if (compiled.blocks().containsKey(p) || QcPlanService.insideCells(neighbours, p, anchor)) {
						continue;
					}
					BlockState state = level.getBlockState(p);
					if (!state.isAir()) {
						out.put(p.immutable(), state);
					}
				}
			}
		}
		for (BlockPos p : compiled.air()) {
			Cell cell = new Cell(Math.floorDiv(p.getX() - anchor.getX(), 8), Math.floorDiv(p.getY() - anchor.getY(), 8),
					Math.floorDiv(p.getZ() - anchor.getZ(), 8));
			if (roads.contains(cell)) {
				BlockState state = level.getBlockState(p);
				if (!state.isAir()) {
					out.put(p, state);
				}
			}
		}
		return out;
	}

	/** Draws the ghost blocks; {@code matrices} is already translated by minus the camera position. */
	static void render(Minecraft client, PoseStack matrices, Vec3 camera) {
		refresh();
		if (!QcClientState.blockPreview || blocks.isEmpty() && removed.isEmpty() || client.level == null) {
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
			BlockState standing = client.level.getBlockState(pos);
			if (standing.equals(entry.getValue())) {
				continue;
			}
			matrices.pushPose();
			matrices.translate(pos.getX(), pos.getY(), pos.getZ());
			if (!standing.isAir()) {
				// replaces a standing block: a hair larger, or the ghost hides inside it
				grow(matrices, OVER);
			}
			BlockState state = entry.getValue();
			if (state.getRenderShape() == RenderShape.MODEL) {
				VertexConsumer ghost = new AlphaConsumer(consumers.getBuffer(Sheets.translucentCullBlockSheet()), ALPHA, 1F, 1F, 1F);
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
		// what the apply takes down from the standing parts it rebuilds: the standing block, tinted red
		for (Map.Entry<BlockPos, BlockState> entry : removed.entrySet()) {
			if (drawn >= LIMIT) {
				break;
			}
			BlockPos pos = entry.getKey();
			if (camera.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > RANGE * RANGE) {
				continue;
			}
			BlockState state = client.level.getBlockState(pos);
			if (state.isAir() || state.getRenderShape() != RenderShape.MODEL) {
				continue;
			}
			matrices.pushPose();
			matrices.translate(pos.getX(), pos.getY(), pos.getZ());
			grow(matrices, OVER * OVER);
			VertexConsumer red = new AlphaConsumer(consumers.getBuffer(Sheets.translucentCullBlockSheet()), REMOVED_ALPHA,
					1F, 0.25F, 0.25F);
			int color = client.getBlockColors().getColor(state, client.level, pos, 0);
			renderer.getModelRenderer().renderModel(matrices.last(), red, state, renderer.getBlockModel(state),
					(color >> 16 & 255) / 255.0F, (color >> 8 & 255) / 255.0F, (color & 255) / 255.0F,
					LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
			matrices.popPose();
			drawn++;
		}
		consumers.endBatch();
	}

	/** Scales the unit block at the pose's origin by {@code scale} round its centre. */
	private static void grow(PoseStack matrices, float scale) {
		matrices.translate(0.5, 0.5, 0.5);
		matrices.scale(scale, scale, scale);
		matrices.translate(-0.5, -0.5, -0.5);
	}

	/**
	 * Passes every vertex through, with a fixed alpha and the colour multiplied by a tint: the ghost look
	 * (white tint) and the red of a block the apply takes down, on the translucent block-atlas layer.
	 */
	private static final class AlphaConsumer implements VertexConsumer {
		private final VertexConsumer delegate;
		private final int alpha;
		private final float red;
		private final float green;
		private final float blue;

		AlphaConsumer(VertexConsumer delegate, int alpha, float red, float green, float blue) {
			this.delegate = delegate;
			this.alpha = alpha;
			this.red = red;
			this.green = green;
			this.blue = blue;
		}

		@Override
		public VertexConsumer vertex(double x, double y, double z) {
			this.delegate.vertex(x, y, z);
			return this;
		}

		@Override
		public VertexConsumer color(int red, int green, int blue, int ignored) {
			this.delegate.color((int) (red * this.red), (int) (green * this.green), (int) (blue * this.blue), this.alpha);
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
			this.delegate.defaultColor((int) (red * this.red), (int) (green * this.green), (int) (blue * this.blue), this.alpha);
		}

		@Override
		public void unsetDefaultColor() {
			this.delegate.unsetDefaultColor();
		}
	}
}
