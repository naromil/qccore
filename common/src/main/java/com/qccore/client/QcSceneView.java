package com.qccore.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.qccore.core.plan.BlockIds;
import com.qccore.core.plan.QcPlan;
import com.qccore.core.qc.District;
import com.qccore.core.qc.Preview;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A small turnable 3D picture of a plan's blocks inside a screen: the block chooser's live sample
 * and the template screen's preview. {@link #show} generates the plan with {@link District} (no
 * world, no anchor) and keeps the result until the plan is shown again; {@link #render} draws it
 * isometrically, fitted into a rectangle and clipped to it. Blocks hidden on all six sides are
 * skipped, and at most {@link #LIMIT} blocks are kept. Drag inside the rectangle to turn it.
 *
 * <p>{@link #highlight} picks out one role of one style (the block chooser's hovered role row): the
 * rest of the picture is dimmed and that role's blocks are drawn again on top, so they show even
 * where other blocks hide them.
 */
public final class QcSceneView {
	private static final int LIMIT = 12000;

	private record Placed(BlockPos pos, BlockState state) {
	}

	private List<Placed> placed = List.of();
	private QcPlan plan;
	private District.Result result;
	private Map<BlockPos, BlockState> states = Map.of();
	/** The highlighted style and role ({@code null} for none) and the blocks drawn for it. */
	private List<String> lit;
	private List<Placed> litPlaced = List.of();
	private int litCount;
	private int minX;
	private int minY;
	private int minZ;
	private int maxX;
	private int maxY;
	private int maxZ;
	private float yaw = 225.0F;
	private String status = "";
	private int unset;

	/** Generates {@code plan} (or clears the view for {@code null}) and keeps its visible blocks. */
	public void show(QcPlan plan) {
		this.placed = List.of();
		this.status = "";
		this.unset = 0;
		this.plan = null;
		this.result = null;
		this.states = Map.of();
		this.lit = null;
		this.litPlaced = List.of();
		this.litCount = 0;
		if (plan == null || plan.isEmpty()) {
			return;
		}
		District.Result result;
		try {
			result = District.build(plan);
		} catch (RuntimeException e) {
			this.status = "preview failed: " + e.getMessage();
			return;
		}
		this.plan = plan;
		this.result = result;
		for (java.util.Set<String> roles : result.missing().values()) {
			this.unset += roles.size();
		}
		Map<BlockPos, BlockState> states = new HashMap<>();
		for (Map.Entry<BlockPos, String> entry : result.blocks().entrySet()) {
			BlockState state = BlockIds.parseLenient(entry.getValue());
			if (state != null && !state.isAir()) {
				states.put(entry.getKey(), state);
			}
		}
		List<Placed> kept = new ArrayList<>();
		this.minX = this.minY = this.minZ = Integer.MAX_VALUE;
		this.maxX = this.maxY = this.maxZ = Integer.MIN_VALUE;
		for (Map.Entry<BlockPos, BlockState> entry : states.entrySet()) {
			BlockPos pos = entry.getKey();
			this.minX = Math.min(this.minX, pos.getX());
			this.minY = Math.min(this.minY, pos.getY());
			this.minZ = Math.min(this.minZ, pos.getZ());
			this.maxX = Math.max(this.maxX, pos.getX());
			this.maxY = Math.max(this.maxY, pos.getY());
			this.maxZ = Math.max(this.maxZ, pos.getZ());
			if (!hidden(states, pos) && kept.size() < LIMIT) {
				kept.add(new Placed(pos, entry.getValue()));
			}
		}
		this.placed = kept;
		this.states = states;
		if (kept.isEmpty()) {
			this.status = "nothing to draw yet";
		}
	}

	/**
	 * Highlights the blocks of {@code role} of the shown plan's style {@code style}, or nothing for a
	 * {@code null} role. Cheap to call every frame: the blocks are found again only when the role or
	 * the plan changes.
	 */
	public void highlight(String style, String role) {
		List<String> key = role == null || this.plan == null ? null : List.of(style, role);
		if (Objects.equals(key, this.lit)) {
			return;
		}
		this.lit = key;
		this.litPlaced = List.of();
		this.litCount = 0;
		if (key == null) {
			return;
		}
		Set<BlockPos> blocks;
		try {
			blocks = Preview.roleBlocks(this.plan, this.result, style, role);
		} catch (RuntimeException e) {
			return;
		}
		Map<BlockPos, BlockState> own = new HashMap<>();
		for (BlockPos pos : blocks) {
			BlockState state = this.states.get(pos);
			if (state != null) {
				own.put(pos, state);
			}
		}
		List<Placed> kept = new ArrayList<>();
		for (Map.Entry<BlockPos, BlockState> entry : own.entrySet()) {
			if (!hidden(own, entry.getKey()) && kept.size() < LIMIT) {
				kept.add(new Placed(entry.getKey(), entry.getValue()));
			}
		}
		this.litPlaced = kept;
		this.litCount = own.size();
	}

	/** How many blocks the highlighted role has in the picture ({@code -1} when none is highlighted). */
	public int highlighted() {
		return this.lit == null ? -1 : this.litCount;
	}

	/** A line about the last {@link #show} when it drew nothing ("" otherwise). */
	public String status() {
		return this.status;
	}

	/** How many required roles the last plan shown reached without a block (they are not drawn). */
	public int unset() {
		return this.unset;
	}

	public boolean isEmpty() {
		return this.placed.isEmpty();
	}

	/** Turns the view by a mouse drag of {@code dx} GUI pixels. */
	public void drag(double dx) {
		this.yaw = (float) ((this.yaw + dx * 1.5) % 360.0);
	}

	private static boolean hidden(Map<BlockPos, BlockState> states, BlockPos pos) {
		for (Direction direction : Direction.values()) {
			BlockState next = states.get(pos.relative(direction));
			if (next == null || !next.canOcclude()) {
				return false;
			}
		}
		return true;
	}

	/** Pushes the pose that puts the scene, turned and scaled by {@code scale}, in the middle of the rectangle. */
	private void place(PoseStack pose, int x, int y, int w, int h, float scale) {
		pose.pushPose();
		pose.translate(x + w / 2.0F, y + h / 2.0F, 400.0F);
		pose.scale(scale, -scale, scale);
		pose.mulPose(Axis.XP.rotationDegrees(30.0F));
		pose.mulPose(Axis.YP.rotationDegrees(this.yaw));
		pose.translate(-(this.minX + (this.maxX - this.minX + 1) / 2.0F), -(this.minY + (this.maxY - this.minY + 1) / 2.0F),
				-(this.minZ + (this.maxZ - this.minZ + 1) / 2.0F));
	}

	private static void drawBlocks(GuiGraphics g, Minecraft client, List<Placed> blocks) {
		PoseStack pose = g.pose();
		BlockRenderDispatcher renderer = client.getBlockRenderer();
		MultiBufferSource.BufferSource buffers = g.bufferSource();
		for (Placed block : blocks) {
			if (block.state().getRenderShape() == RenderShape.INVISIBLE) {
				continue;
			}
			pose.pushPose();
			pose.translate(block.pos().getX(), block.pos().getY(), block.pos().getZ());
			renderer.renderSingleBlock(block.state(), pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
			pose.popPose();
		}
		g.flush();
	}

	/** Draws the blocks fitted into {@code (x, y, w, h)}. */
	public void render(GuiGraphics g, int x, int y, int w, int h) {
		if (this.placed.isEmpty() || w < 8 || h < 8) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		float sizeX = this.maxX - this.minX + 1;
		float sizeY = this.maxY - this.minY + 1;
		float sizeZ = this.maxZ - this.minZ + 1;
		float across = (float) Math.sqrt(sizeX * sizeX + sizeZ * sizeZ);
		// pitch 30°: a height shows at cos 30, the footprint's depth at sin 30
		float scale = 0.92F * Math.min(w / across, h / (sizeY * 0.866F + across * 0.5F));
		g.enableScissor(x, y, x + w, y + h);
		PoseStack pose = g.pose();
		place(pose, x, y, w, h, scale);
		Lighting.setupFor3DItems();
		drawBlocks(g, client, this.placed);
		pose.popPose();
		if (this.lit != null) {
			// dim the picture, then draw the role's blocks over it on a cleared depth buffer
			RenderSystem.clear(256, Minecraft.ON_OSX);
			g.fill(x, y, x + w, y + h, 0xB0101418);
			g.flush();
			RenderSystem.clear(256, Minecraft.ON_OSX);
			place(pose, x, y, w, h, scale);
			drawBlocks(g, client, this.litPlaced);
			pose.popPose();
		}
		g.disableScissor();
		// the scene stands in front of the screen's later widgets and tooltips: clear its depth
		RenderSystem.clear(256, Minecraft.ON_OSX);
	}
}
