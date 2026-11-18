package com.qccore.client;

import com.qccore.core.plan.BuildShape;
import com.qccore.core.plan.Cell;
import com.qccore.core.plan.ComponentMask;
import com.qccore.core.plan.Modifiers;
import com.qccore.core.plan.QcPlan;
import com.qccore.client.QcUi.Chip;
import com.qccore.client.QcUi.Stepper;
import com.qccore.net.QcPackets.QcRedoC2S;
import com.qccore.net.QcPackets.QcUndoC2S;
import com.qccore.platform.QcPlatform;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * The hub of the planning tools (key B), in the spirit of Effortless Building's radial menu: the
 * tool and the build shape, which of the nine components new units build, the mirror and array
 * modifiers, terrain clearing settings, the plan's undo/redo, and the two ways to finish a plan:
 * apply it to the world or export it as a structure {@code .nbt}.
 *
 * <p>Two columns of panels, every choice visible at once as a chip that lights up when selected,
 * numbers as {@link Stepper}s, a tooltip on every control. Keys while no text field is focused:
 * 1-9 pick a tool, Ctrl+Z / Ctrl+Y undo and redo plan edits, Enter applies the plan.
 */
public class QcToolsScreen extends Screen {
	private static final int COLUMN = 206;
	private static final int GAP = 6;
	private static final int ROW = 15;
	private static final int CHIP_H = 13;
	private static final int TILE = 18;
	private static final int CONTENT_HEIGHT = 199;
	private static final String[] SHORT_NAMES = {"framework", "row", "column", "wall", "floor",
			"inner col.", "inner wall", "gate", "outer wall"};

	private EditBox ground;
	private int margin;
	private Cell cursorCell;
	private int left;
	private int right;
	private int top;
	private int shapeTop;
	private int planTop;
	private int terrainTop;
	private int modTop;
	private int libTop;

	public QcToolsScreen() {
		super(Component.translatable("qccore.screen.tools"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		// a re-init (window resize) rebuilds the ground field: keep what was typed into it
		commit();
		this.cursorCell = this.minecraft == null ? null : QcInteraction.target(this.minecraft);
		this.margin = QcClientState.plan.clearMargin;
		this.left = this.width / 2 - COLUMN - GAP / 2;
		this.right = this.width / 2 + GAP / 2;
		this.top = 17 + Math.max(0, (this.height - 22 - 17 - CONTENT_HEIGHT) / 2);
		this.shapeTop = this.top + 37 + 3;
		this.planTop = this.shapeTop + 48 + 3;
		this.terrainTop = this.planTop + 48 + 3;
		this.modTop = this.top + 80 + 3;
		this.libTop = this.modTop + 80 + 3;
		int inner = COLUMN - 8;
		int half = (inner - 2) / 2;
		int third = (inner - 4) / 3;
		int body = QcUi.HEADER_HEIGHT + 3;

		// 1. Tools: one icon tile each, in key order 1..9
		int y = this.top + body;
		QcTool[] tools = QcTool.values();
		int tileStep = (inner - TILE) / (tools.length - 1);
		for (int i = 0; i < tools.length; i++) {
			QcTool tool = tools[i];
			this.addRenderableWidget(new Chip(this.left + 4 + i * tileStep, y, TILE, TILE, QcUi.shortName(tool), () -> {
				QcClientState.tool = tool;
				QcClientState.points.clear();
			}).selected(() -> QcClientState.tool == tool).accent(QcUi.color(tool))
					.icon(() -> QcUi.icon(tool), true).tip(QcUi.describe(tool) + "\nkey " + (i + 1)));
		}

		// 2. Shapes
		y = this.shapeTop + body;
		BuildShape[] shapes = BuildShape.values();
		for (int i = 0; i < shapes.length; i++) {
			BuildShape shape = shapes[i];
			this.addRenderableWidget(new Chip(this.left + 4 + (i % 3) * (third + 2), y + (i / 3) * ROW, third, CHIP_H,
					shape.label(), () -> {
				QcClientState.shape = shape;
				QcClientState.points.clear();
			}).selected(() -> QcClientState.shape == shape)
					.tip(shape.label() + ": " + shape.points() + " click" + (shape.points() == 1 ? "" : "s")
							+ (shape.points() == 3 ? " (the third sets the height)" : "")));
		}

		// 3. Plan history and the world undo
		y = this.planTop + body;
		this.addRenderableWidget(new Chip(this.left + 4, y, half, CHIP_H, "", this::undo)
				.label(() -> "↶ Undo edit (" + QcClientState.history.undoDepth() + ")")
				.tip("Undo the last plan edit\nCtrl+Z here, Z in the world"));
		this.addRenderableWidget(new Chip(this.left + 6 + half, y, half, CHIP_H, "", this::redo)
				.label(() -> "↷ Redo edit (" + QcClientState.history.redoDepth() + ")")
				.tip("Redo a plan edit\nCtrl+Y here, Shift+Z in the world"));
		y += ROW;
		this.addRenderableWidget(new Chip(this.left + 4, y, third, CHIP_H, "Clear plan", QcInteraction::clearPlan)
				.accent(QcUi.ERROR).tip("Empty the plan (undoable)"));
		this.addRenderableWidget(new Chip(this.left + 6 + third, y, third, CHIP_H, "Undo apply", () -> {
			QcPlatform.sendToServer(new QcUndoC2S());
			onClose();
		}).tip("Undo the last apply in the world (/qc undo)"));
		this.addRenderableWidget(new Chip(this.left + 8 + 2 * third, y, third, CHIP_H, "Redo apply", () -> {
			QcPlatform.sendToServer(new QcRedoC2S());
			onClose();
		}).tip("Re-apply what the last world undo removed (/qc redo)"));

		// 4. Terrain
		y = this.terrainTop + body;
		this.addRenderableWidget(new Stepper(this.left + 4 + inner - 48, y, 48, CHIP_H, "clear margin",
				0, QcPlan.MAX_CLEAR_MARGIN, () -> this.margin, value -> this.margin = value)
				.tip("Blocks cleared around every planned cell (0-" + QcPlan.MAX_CLEAR_MARGIN + ")"));
		y += ROW;
		this.ground = new EditBox(this.font, this.left + 45, y, inner - 41, CHIP_H, Component.literal("ground"));
		this.ground.setMaxLength(128);
		this.ground.setValue(QcClientState.plan.ground);
		this.ground.setHint(Component.literal("block id, e.g. grass_block").withStyle(st -> st.withColor(0x667080)));
		this.ground.setTooltip(QcUi.tip("Fills the floor plane of cleared cells; leave empty for none"));
		this.addRenderableWidget(this.ground);

		// 5. Components
		y = this.top + body;
		for (int i = 0; i < ComponentMask.BITS.length; i++) {
			int bit = ComponentMask.BITS[i];
			this.addRenderableWidget(new Chip(this.right + 4 + (i % 3) * (third + 2), y + (i / 3) * ROW, third, CHIP_H,
					SHORT_NAMES[i], () -> QcClientState.componentMask ^= bit)
					.selected(() -> ComponentMask.has(QcClientState.componentMask, bit)).accent(QcUi.OK)
					.tip("Toggle " + ComponentMask.NAMES[i] + " for new units and the paint tool"));
		}
		y += 3 * ROW + 2;
		this.addRenderableWidget(new Chip(this.right + 4, y, third, CHIP_H, "All",
				() -> QcClientState.componentMask = ComponentMask.ALL)
				.selected(() -> QcClientState.componentMask == ComponentMask.ALL).tip("All nine components"));
		int shell = ComponentMask.FRAMEWORK | ComponentMask.FLOOR;
		this.addRenderableWidget(new Chip(this.right + 6 + third, y, third, CHIP_H, "Shell only",
				() -> QcClientState.componentMask = shell)
				.selected(() -> QcClientState.componentMask == shell).tip("Framework + floor"));
		int skin = ComponentMask.ROW | ComponentMask.COLUMN | ComponentMask.WALL | ComponentMask.OUTER_WALL;
		this.addRenderableWidget(new Chip(this.right + 8 + 2 * third, y, third, CHIP_H, "Skin only",
				() -> QcClientState.componentMask = skin)
				.selected(() -> QcClientState.componentMask == skin).tip("Row, column, wall and outer wall"));

		// 6. Modifiers
		Modifiers modifiers = QcClientState.modifiers;
		y = this.modTop + body;
		this.addRenderableWidget(new Chip(this.right + 4, y, half, CHIP_H, "Mirror E-W",
				() -> modifiers.mirrorX = !modifiers.mirrorX).selected(() -> modifiers.mirrorX).accent(QcUi.WARN)
				.tip("Mirror every shape across the east-west mirror cell"));
		this.addRenderableWidget(new Chip(this.right + 6 + half, y, half, CHIP_H, "Mirror N-S",
				() -> modifiers.mirrorZ = !modifiers.mirrorZ).selected(() -> modifiers.mirrorZ).accent(QcUi.WARN)
				.tip("Mirror every shape across the north-south mirror cell"));
		y += ROW;
		this.addRenderableWidget(new Chip(this.right + 4, y, inner, CHIP_H, "", () -> {
			if (this.cursorCell != null) {
				modifiers.mirrorCellX = this.cursorCell.x();
				modifiers.mirrorCellZ = this.cursorCell.z();
			}
		}).label(() -> "Mirror cell " + modifiers.mirrorCellX + ", " + modifiers.mirrorCellZ
				+ (this.cursorCell == null ? "" : "  → set to cursor"))
				.tip(this.cursorCell == null ? "Aim at a cell before opening this screen to move the mirror there"
						: "Move the mirror to the cell under the crosshair (" + this.cursorCell.x() + ", " + this.cursorCell.z() + ")"));
		y += ROW + 2;
		this.addRenderableWidget(new Stepper(this.right + 4 + inner - 48, y, 48, CHIP_H, "copies", 1, 64,
				() -> modifiers.arrayCount, value -> modifiers.arrayCount = value)
				.tip("Array: how many copies of every shape (1 = off)"));
		y += ROW;
		int stepW = 46;
		int stepX = this.right + 4 + inner - 3 * stepW - 4;
		this.addRenderableWidget(new Stepper(stepX, y, stepW, CHIP_H, "step x", -64, 64,
				() -> modifiers.arrayStepX, value -> modifiers.arrayStepX = value).tip("Array step east (+) / west (-) in cells"));
		this.addRenderableWidget(new Stepper(stepX + stepW + 2, y, stepW, CHIP_H, "step layer", -64, 64,
				() -> modifiers.arrayStepLayer, value -> modifiers.arrayStepLayer = value).tip("Array step up (+) / down (-) in layers"));
		this.addRenderableWidget(new Stepper(stepX + 2 * stepW + 4, y, stepW, CHIP_H, "step z", -64, 64,
				() -> modifiers.arrayStepZ, value -> modifiers.arrayStepZ = value).tip("Array step south (+) / north (-) in cells"));

		// 7. Library
		y = this.libTop + body;
		this.addRenderableWidget(new Chip(this.right + 4, y, third, CHIP_H, "Templates", () -> {
			commit();
			this.minecraft.setScreen(new QcTemplateScreen(this));
		}).tip("Pick, save or delete plan templates"));
		this.addRenderableWidget(new Chip(this.right + 6 + third, y, third, CHIP_H, "Kits/roads", () -> {
			commit();
			this.minecraft.setScreen(new QcKitScreen(this));
		}).tip("Block kits and road styles"));
		this.addRenderableWidget(new Chip(this.right + 8 + 2 * third, y, third, CHIP_H, "Palette", () -> {
			commit();
			this.minecraft.setScreen(new QcConfigScreen());
		}).tip("The nine components and the lattice (key I)"));

		// 8. Footer
		int bottom = this.height - 20;
		this.addRenderableWidget(new Chip(this.left, bottom, 70, 17, "Done", this::onClose).tip("Close (Esc or B)"));
		this.addRenderableWidget(new Chip(this.right + COLUMN - 196, bottom, 94, 17, "Export .nbt…", () -> {
			commit();
			this.minecraft.setScreen(new QcSaveScreen());
		}).tip("Save the plan as a structure file (J)"));
		this.addRenderableWidget(new Chip(this.right + COLUMN - 98, bottom, 98, 17, "Apply plan ▶", () -> {
			commit();
			onClose();
			QcInteraction.apply();
		}).accent(QcUi.APPLY).primary().tip("Build the plan in the world (K or Enter); creative only, undoable"));
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		this.renderBackground(g);
		QcUi.titleBar(g, this.font, this, "1-9 tool · Ctrl+Z/Y · Enter apply");
		QcTool tool = QcClientState.tool;
		QcUi.panel(g, this.font, this.left, this.top, COLUMN, 37, "Tool · " + tool.label(), QcUi.color(tool));
		QcUi.panel(g, this.font, this.left, this.shapeTop, COLUMN, 48, "Shape", QcUi.ACCENT);
		QcUi.panel(g, this.font, this.left, this.planTop, COLUMN, 48,
				this.font.plainSubstrByWidth("Plan · " + QcUi.planSummary(), COLUMN - 10), QcUi.ACCENT);
		QcUi.panel(g, this.font, this.left, this.terrainTop, COLUMN, 48, "Terrain", QcUi.color(QcTool.CLEAR));
		int body = QcUi.HEADER_HEIGHT + 3;
		g.drawString(this.font, "clear margin", this.left + 6, this.terrainTop + body + 3, QcUi.MUTED, false);
		g.drawString(this.font, "ground", this.left + 6, this.terrainTop + body + ROW + 3, QcUi.MUTED, false);

		QcUi.panel(g, this.font, this.right, this.top, COLUMN, 80, "Components · "
				+ Integer.bitCount(QcClientState.componentMask) + "/9", QcUi.OK);
		QcUi.panel(g, this.font, this.right, this.modTop, COLUMN, 80, "Modifiers"
				+ (QcClientState.modifiers.isActive() ? " · on" : ""), QcUi.WARN);
		int y = this.modTop + body + 2 * ROW + 2;
		g.drawString(this.font, "array copies", this.right + 6, y + 3, QcUi.MUTED, false);
		g.drawString(this.font, "step x·y·z", this.right + 6, y + ROW + 3, QcUi.MUTED, false);
		QcUi.panel(g, this.font, this.right, this.libTop, COLUMN, 33, "Library & setup", QcUi.ACCENT);

		String problem = QcBlockPreview.problem();
		if (problem != null) {
			QcUi.status(g, this.font, "⚠ " + problem, QcUi.ERROR, this.width / 2, this.height - 36);
		}
		super.render(g, mouseX, mouseY, delta);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (!(this.getFocused() instanceof EditBox box && box.isFocused())) {
			if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
				int index = keyCode - GLFW.GLFW_KEY_1;
				if (index < QcTool.values().length) {
					QcClientState.tool = QcTool.values()[index];
					QcClientState.points.clear();
					return true;
				}
			}
			if (hasControlDown() && keyCode == GLFW.GLFW_KEY_Z) {
				if (hasShiftDown()) {
					redo();
				} else {
					undo();
				}
				return true;
			}
			if (hasControlDown() && keyCode == GLFW.GLFW_KEY_Y) {
				redo();
				return true;
			}
			if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
				commit();
				onClose();
				QcInteraction.apply();
				return true;
			}
			if (QcKeys.tools != null && QcKeys.tools.matches(keyCode, scanCode)) {
				onClose();
				return true;
			}
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void onClose() {
		commit();
		super.onClose();
	}

	@Override
	public void removed() {
		commit();
	}

	/**
	 * Plan undo from the screen. Pending margin/ground changes are committed first (so the undo
	 * reverts them), and the fields are reloaded afterwards: otherwise closing the screen would
	 * write the stale field values back over the restored plan and drop the redo stack.
	 */
	private void undo() {
		commit();
		QcInteraction.undo();
		reload();
	}

	private void redo() {
		commit();
		QcInteraction.redo();
		reload();
	}

	/** Shows the plan's clear margin and ground block in the fields. */
	private void reload() {
		this.margin = QcClientState.plan.clearMargin;
		if (this.ground != null) {
			this.ground.setValue(QcClientState.plan.ground);
		}
	}

	/** Copies the clear margin and the ground block into the plan, as one undoable edit. */
	private void commit() {
		if (this.ground == null) {
			return;
		}
		int newMargin = this.margin;
		String newGround = this.ground.getValue().trim();
		if (newMargin != QcClientState.plan.clearMargin || !newGround.equals(QcClientState.plan.ground)) {
			QcInteraction.editFromScreen(() -> {
				QcClientState.plan.clearMargin = newMargin;
				QcClientState.plan.ground = newGround;
				return true;
			});
		}
	}
}
