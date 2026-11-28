package com.qccore.client;

import com.qccore.core.plan.BuildShape;
import com.qccore.core.plan.Cell;
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
 * tool and the build shape, the settings card of the current tool (top right, titled after the tool, T33:
 * Building for the building, assign and erase tools, Road, Clear terrain, Tweak, Template), the mirror and
 * array modifiers, the plan's undo/redo, and the two ways to finish a plan: apply it to the world or export
 * it as a structure {@code .nbt}.
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

	private static final int CARD_HEIGHT = 65;

	private EditBox ground;
	private QcToolCards.Card card;
	private int margin;
	private Cell cursorCell;
	/** The planned unit under the crosshair when the screen opened, ghosts included (for Pick aimed). */
	private Cell aimedUnit;
	/** The planned road cell under the crosshair when the screen opened (for the Road card's Pick aimed). */
	private Cell aimedRoad;
	/** A word the Road card shows on its status line after a click (the HUD is hidden behind the screen). */
	private String roadNote;
	private int left;
	private int right;
	private int top;
	private int shapeTop;
	private int planTop;
	private int modTop;
	private int libTop;

	public QcToolsScreen() {
		super(Component.translatable("qccore.screen.tools"));
		// B can come before the first G after a launch: bring back the saved styles here too, so the
		// block chooser shows them and its save on close doesn't start from an empty set
		QcSavedStyles.restoreOnceAndSay(QcClientState.plan.styles());
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		// a re-init (window resize) rebuilds the ground field: keep what was typed into it
		commit();
		this.ground = null;
		this.card = QcToolCards.Card.of(QcClientState.tool);
		this.cursorCell = this.minecraft == null ? null : QcInteraction.target(this.minecraft);
		this.aimedUnit = this.minecraft == null ? null : QcInteraction.aimedPlanUnit(this.minecraft);
		this.aimedRoad = this.minecraft == null ? null : QcInteraction.aimedPlanRoad(this.minecraft);
		this.margin = QcClientState.plan.clearMargin;
		this.left = this.width / 2 - COLUMN - GAP / 2;
		this.right = this.width / 2 + GAP / 2;
		this.top = 17 + Math.max(0, (this.height - 22 - 17 - CONTENT_HEIGHT) / 2);
		this.shapeTop = this.top + 37 + 3;
		this.planTop = this.shapeTop + 48 + 3;
		this.modTop = this.top + CARD_HEIGHT + 3;
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
			this.addRenderableWidget(new Chip(this.left + 4 + i * tileStep, y, TILE, TILE, QcUi.shortName(tool), () -> setTool(tool)).selected(() -> QcClientState.tool == tool).accent(QcUi.color(tool))
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
			QcClientState.requests.expect(QcRequests.Kind.UNDO, QcClientState.history.revision());
			QcPlatform.sendToServer(new QcUndoC2S());
			onClose();
		}).tip("Undo the last apply in the world (/qc undo)"));
		this.addRenderableWidget(new Chip(this.left + 8 + 2 * third, y, third, CHIP_H, "Redo apply", () -> {
			QcClientState.requests.expect(QcRequests.Kind.REDO, QcClientState.history.revision());
			QcPlatform.sendToServer(new QcRedoC2S());
			onClose();
		}).tip("Re-apply what the last world undo removed (/qc redo)"));

		// 4. The current tool's settings card (T33)
		switch (this.card) {
			case BUILDING -> initBuildingCard(inner, third);
			case ROAD -> initRoadCard(inner, third);
			case CLEAR -> initClearCard(inner);
			case TWEAK -> initTweakCard(inner);
			case TEMPLATE -> initTemplateCard(inner);
		}

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
		this.addRenderableWidget(new Chip(this.right + 6 + third, y, third, CHIP_H, "Blocks & kits", () -> {
			commit();
			this.minecraft.setScreen(new QcBlockChooserScreen(this));
		}).tip("Block choices per style, saved and loaded as kits"));
		this.addRenderableWidget(new Chip(this.right + 8 + 2 * third, y, third, CHIP_H, "Lattice", () -> {
			commit();
			this.minecraft.setScreen(new QcConfigScreen(this));
		}).tip("The lattice, layouts and the legacy QCSimple palette (key I)"));

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

	/** Building card (building, assign and erase tools): the building the building tool adds to, its design and blocks. */
	private void initBuildingCard(int inner, int third) {
		int y = this.top + QcUi.HEADER_HEIGHT + 3;
		int arrow = 11;
		int newW = 26;
		int labelW = inner - 2 * arrow - newW - 6;
		this.addRenderableWidget(new Chip(this.right + 4, y, arrow, CHIP_H, "‹", () -> stepBuilding(-1))
				.tip("Previous building of the plan"));
		this.addRenderableWidget(new Chip(this.right + 6 + arrow, y, labelW, CHIP_H, "", () -> stepBuilding(1))
				.label(QcToolsScreen::buildingLabel).accent(QcUi.color(QcTool.UNITS))
				.tip("The building the building tool adds to; click or ‹ › to step through the plan's buildings"));
		this.addRenderableWidget(new Chip(this.right + 8 + arrow + labelW, y, arrow, CHIP_H, "›", () -> stepBuilding(1))
				.tip("Next building of the plan"));
		this.addRenderableWidget(new Chip(this.right + 4 + inner - newW, y, newW, CHIP_H, "New", () -> {
			QcClientState.building = null;
			QcClientState.tool = QcTool.UNITS;
		}).selected(() -> QcClientState.activeBuilding() == null)
				.tip("The next unit starts a new building with the current design"));
		y += ROW;
		this.addRenderableWidget(new Chip(this.right + 4, y, third, CHIP_H, "Design…", () -> {
			commit();
			this.minecraft.setScreen(new QcDesignScreen(this));
		}).tip("Template, roof, facades, piers and partitions of the active building"));
		this.addRenderableWidget(new Chip(this.right + 6 + third, y, third, CHIP_H, "Blocks…", () -> {
			commit();
			this.minecraft.setScreen(new QcBlockChooserScreen(this));
		}).accent(QcUi.OK).tip("Choose the block of every role of every style, and save or load kits"));
		this.addRenderableWidget(new Chip(this.right + 8 + 2 * third, y, third, CHIP_H, "Pick aimed", () -> {
			String key = this.aimedUnit == null ? null : QcClientState.plan.building(this.aimedUnit);
			if (key != null) {
				QcClientState.building = key;
				QcClientState.design = QcClientState.plan.buildings().get(key).copy();
			}
		}).selected(() -> this.aimedUnit != null
				&& QcClientState.activeBuilding() != null
				&& java.util.Objects.equals(QcClientState.building, QcClientState.plan.building(this.aimedUnit)))
				.tip(this.aimedUnit == null ? "Aim at a planned unit before opening this screen"
						: "Make building " + QcClientState.plan.building(this.aimedUnit)
								+ " (the aimed unit's) the active one"));
	}

	/**
	 * Road card: the road style the road tool lays (‹ name · N cells › New), its blocks, the style of the
	 * aimed road, and on the status line what the style still lacks.
	 */
	private void initRoadCard(int inner, int third) {
		int y = this.top + QcUi.HEADER_HEIGHT + 3;
		int arrow = 11;
		int newW = 26;
		int labelW = inner - 2 * arrow - newW - 6;
		this.addRenderableWidget(new Chip(this.right + 4, y, arrow, CHIP_H, "‹", () -> stepRoadStyle(-1))
				.tip("Previous road style of the plan"));
		this.addRenderableWidget(new Chip(this.right + 6 + arrow, y, labelW, CHIP_H, "", () -> stepRoadStyle(1))
				.label(QcToolsScreen::roadLabel).accent(QcUi.color(QcTool.ROAD))
				.tip("The style the road tool lays; click or ‹ › to step through the plan's road styles"));
		this.addRenderableWidget(new Chip(this.right + 8 + arrow + labelW, y, arrow, CHIP_H, "›", () -> stepRoadStyle(1))
				.tip("Next road style of the plan"));
		this.addRenderableWidget(new Chip(this.right + 4 + inner - newW, y, newW, CHIP_H, "New", this::newRoadStyle)
				.tip("Start a new road style (no blocks yet) for the next roads"));
		y += ROW;
		int half = (inner - 2) / 2;
		this.addRenderableWidget(new Chip(this.right + 4, y, half, CHIP_H, "Blocks…", () -> {
			commit();
			if (ensureRoadStyle()) {
				this.minecraft.setScreen(new QcBlockChooserScreen(this, QcClientState.roadStyle));
			}
		}).accent(QcUi.OK).tip("Choose the blocks of this road style's lanes"));
		this.addRenderableWidget(new Chip(this.right + 6 + half, y, half, CHIP_H, "Pick aimed", () -> {
			String style = this.aimedRoad == null ? null : QcClientState.plan.roads().get(this.aimedRoad);
			if (style != null) {
				QcClientState.roadStyle = style;
				this.roadNote = null;
			}
		}).selected(() -> this.aimedRoad != null
				&& QcClientState.roadStyle.equals(QcClientState.plan.roads().get(this.aimedRoad)))
				.tip(this.aimedRoad == null ? "Aim at a planned road before opening this screen"
						: "Lay the style of the aimed road (" + QcClientState.plan.roads().get(this.aimedRoad) + ")"));
	}

	/** Clear terrain card: the clear margin and the ground block (moved here from the old Terrain panel). */
	private void initClearCard(int inner) {
		int y = this.top + QcUi.HEADER_HEIGHT + 3;
		this.addRenderableWidget(new Stepper(this.right + 4 + inner - 48, y, 48, CHIP_H, "clear margin",
				0, QcPlan.MAX_CLEAR_MARGIN, () -> this.margin, value -> this.margin = value)
				.tip("Blocks cleared around every planned cell (0-" + QcPlan.MAX_CLEAR_MARGIN + ")"));
		y += ROW;
		this.ground = new EditBox(this.font, this.right + 45, y, inner - 41, CHIP_H, Component.literal("ground"));
		this.ground.setMaxLength(128);
		this.ground.setValue(QcClientState.plan.ground);
		this.ground.setHint(Component.literal("block id, e.g. grass_block").withStyle(st -> st.withColor(0x667080)));
		this.ground.setTooltip(QcUi.tip("Fills the floor plane of cleared cells; leave empty for none"));
		this.addRenderableWidget(this.ground);
	}

	/** Tweak card: the partition a floor, line or box shape sets on every shared side inside it. */
	private void initTweakCard(int inner) {
		int y = this.top + QcUi.HEADER_HEIGHT + 3;
		int quarter = (inner - 6) / 4;
		QcPlan.Partition[] partitions = QcTweakScreen.PARTITIONS;
		for (int i = 0; i < partitions.length; i++) {
			QcPlan.Partition partition = partitions[i];
			this.addRenderableWidget(new Chip(this.right + 4 + i * (quarter + 2), y, quarter, CHIP_H,
					QcUi.partitionName(partition), () -> QcClientState.tweakPartition = partition)
					.selected(() -> QcClientState.tweakPartition == partition).accent(QcUi.color(QcTool.TWEAK))
					.tip("With a floor, line or box shape, the tweak tool sets every shared side inside it to: "
							+ QcTweakScreen.partitionTip(partition).toLowerCase(java.util.Locale.ROOT)));
		}
	}

	/** Template card: the template the tool stamps and its turn; the template screen picks another. */
	private void initTemplateCard(int inner) {
		int y = this.top + QcUi.HEADER_HEIGHT + 3;
		int half = (inner - 2) / 2;
		this.addRenderableWidget(new Chip(this.right + 4, y, half, CHIP_H, "Templates…", () -> {
			commit();
			this.minecraft.setScreen(new QcTemplateScreen(this));
		}).accent(QcUi.color(QcTool.TEMPLATE)).tip("Pick, save or delete plan templates"));
		this.addRenderableWidget(new Chip(this.right + 6 + half, y, half, CHIP_H, "", () ->
				QcClientState.templateRotation = (QcClientState.templateRotation + 1) % 4)
				.label(() -> "Turn ↻ " + QcClientState.templateRotation * 90 + "°")
				.tip("Turn the template a quarter (R in the world)"));
	}

	/** Switches the tool; a tool with another settings card rebuilds the screen so the card follows it. */
	private void setTool(QcTool tool) {
		QcClientState.tool = tool;
		QcClientState.points.clear();
		if (QcToolCards.Card.of(tool) != this.card) {
			this.roadNote = null;
			this.rebuildWidgets();
		}
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
		int body = QcUi.HEADER_HEIGHT + 3;
		renderCard(g, body);
		QcUi.panel(g, this.font, this.right, this.modTop, COLUMN, 80, "Modifiers"
				+ (QcClientState.modifiers.isActive() ? " · on" : ""), QcUi.WARN);
		int y = this.modTop + body + 2 * ROW + 2;
		g.drawString(this.font, "array copies", this.right + 6, y + 3, QcUi.MUTED, false);
		g.drawString(this.font, "step x·y·z", this.right + 6, y + ROW + 3, QcUi.MUTED, false);
		QcUi.panel(g, this.font, this.right, this.libTop, COLUMN, 33, "Library & setup", QcUi.ACCENT);

		String problem = QcBlockPreview.problem();
		if (problem != null) {
			QcUi.status(g, this.font, this.font.plainSubstrByWidth("⚠ " + problem, this.width - 24), QcUi.ERROR,
					this.width / 2, this.height - 36);
		}
		super.render(g, mouseX, mouseY, delta);
	}

	/** The settings card's panel, labels and status line (its widgets come from init). */
	private void renderCard(GuiGraphics g, int body) {
		QcTool tool = this.card == QcToolCards.Card.BUILDING ? QcTool.UNITS : QcClientState.tool;
		QcUi.panel(g, this.font, this.right, this.top, COLUMN, CARD_HEIGHT, this.card.title, QcUi.color(tool));
		int x = this.right + 6;
		int width = COLUMN - 12;
		String status;
		int colour = QcUi.MUTED;
		int statusY = this.top + body + 2 * ROW + 3;
		switch (this.card) {
			case BUILDING -> {
				status = styleStatus();
				colour = missingRoles() > 0 ? QcUi.WARN : QcUi.MUTED;
			}
			case ROAD -> {
				status = this.roadNote != null ? this.roadNote : roadStatus();
				colour = this.roadNote != null || !roadMissing().isEmpty() ? QcUi.WARN : QcUi.MUTED;
			}
			case CLEAR -> {
				g.drawString(this.font, "clear margin", x, this.top + body + 3, QcUi.MUTED, false);
				g.drawString(this.font, "ground", x, this.top + body + ROW + 3, QcUi.MUTED, false);
				status = "the clear tool carves cells; ground fills their floor";
			}
			case TWEAK -> {
				g.drawString(this.font, this.font.plainSubstrByWidth("one unit: a click opens its sides and doors", width),
						x, this.top + body + ROW + 3, QcUi.MUTED, false);
				status = "2+ units: the shape sets " + QcUi.partitionName(QcClientState.tweakPartition) + " inside it";
			}
			case TEMPLATE -> {
				String name = QcClientState.template == null || QcClientState.templateName.isEmpty()
						? "no template picked → Templates…" : "stamps: " + QcClientState.templateName;
				g.drawString(this.font, this.font.plainSubstrByWidth(name, width), x, this.top + body + ROW + 3,
						QcClientState.template == null ? QcUi.WARN : QcUi.TEXT, false);
				status = "array and mirror repeat it; R turns it in the world";
			}
			default -> status = "";
		}
		g.drawString(this.font, this.font.plainSubstrByWidth(status, width), x, statusY, colour, false);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (!(this.getFocused() instanceof EditBox box && box.isFocused())) {
			if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
				int index = keyCode - GLFW.GLFW_KEY_1;
				if (index < QcTool.values().length) {
					setTool(QcTool.values()[index]);
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

	/** The building the building tool adds to, in words: "adding to: house B · 6 units" or "next unit: a new house". */
	private static String buildingLabel() {
		com.qccore.core.plan.BuildingSpec active = QcClientState.activeBuilding();
		if (active == null) {
			return "next unit: a new " + QcClientState.design.template;
		}
		return "adding to: " + active.template + " " + QcClientState.building + " · "
				+ units(QcClientState.plan, QcClientState.building);
	}

	/** "1 unit" / "N units" of one building of the plan. */
	static String units(QcPlan plan, String key) {
		long count = plan.unitBuildings().values().stream().filter(key::equals).count();
		return count + (count == 1 ? " unit" : " units");
	}

	/** How many reached roles of the edited design's style still need a block. */
	private static int missingRoles() {
		java.util.Set<String> missing = QcBlockPreview.missing().get(QcClientState.editedDesign().style);
		return missing == null ? 0 : missing.size();
	}

	/** What the edited design's style still lacks, in plain words. */
	private static String styleStatus() {
		int missing = missingRoles();
		if (missing == 0) {
			return "blocks complete · style " + QcClientState.editedDesign().style;
		}
		return missing + (missing == 1 ? " role" : " roles") + " without a block → Blocks…";
	}

	/** Cycles the active building through "new" and the plan's buildings. */
	private void stepBuilding(int delta) {
		java.util.List<String> keys = new java.util.ArrayList<>();
		keys.add(null);
		keys.addAll(QcClientState.plan.buildings().keySet());
		int index = keys.indexOf(QcClientState.activeBuilding() == null ? null : QcClientState.building);
		String next = keys.get(Math.floorMod(index + delta, keys.size()));
		QcClientState.building = next;
		if (next != null) {
			QcClientState.design = QcClientState.plan.buildings().get(next).copy();
		}
	}

	/** The plan's road styles, in order. */
	private static java.util.List<String> roadStyles() {
		java.util.List<String> names = new java.util.ArrayList<>();
		QcClientState.plan.styles().forEach((name, style) -> {
			if (style.kind().equals(com.qccore.core.qc.Roles.ROAD)) {
				names.add(name);
			}
		});
		return names;
	}

	/** "street · 12 cells", or "street · new" for a style no road uses (or that doesn't exist yet). */
	static String roadLabel() {
		String name = QcClientState.roadStyle;
		long cells = QcClientState.plan.roads().values().stream().filter(name::equals).count();
		return name + " · " + (cells == 0 ? "new" : cells + (cells == 1 ? " cell" : " cells"));
	}

	/** The required road roles of the active road style without a block (all of them when it doesn't exist yet). */
	private static java.util.Set<String> roadMissing() {
		com.qccore.core.qc.BlockChoice style = QcClientState.plan.styles().get(QcClientState.roadStyle);
		if (style == null) {
			return new com.qccore.core.qc.BlockChoice(com.qccore.core.qc.Roles.ROAD).missing();
		}
		if (!style.kind().equals(com.qccore.core.qc.Roles.ROAD)) {
			return java.util.Set.of();
		}
		java.util.Set<String> missing = new java.util.LinkedHashSet<>(style.missing());
		// roles only some plans need (the flight of a level change) come from the compiled plan
		java.util.Set<String> needed = QcBlockPreview.missing().get(QcClientState.roadStyle);
		if (needed != null) {
			missing.addAll(needed);
		}
		return missing;
	}

	/** What the road style still lacks, in plain words: "blocks complete" or "lanes 2, 3 without a block → Blocks…". */
	static String roadStatus() {
		com.qccore.core.qc.BlockChoice style = QcClientState.plan.styles().get(QcClientState.roadStyle);
		if (style != null && !style.kind().equals(com.qccore.core.qc.Roles.ROAD)) {
			return "'" + QcClientState.roadStyle + "' is a " + style.kind() + " style → New";
		}
		return QcToolCards.roadStatus(roadMissing());
	}

	/** Steps the road tool's style through the plan's road styles; with one or none it says so on the card. */
	private void stepRoadStyle(int delta) {
		java.util.List<String> names = roadStyles();
		if (names.isEmpty() || names.size() == 1 && names.get(0).equals(QcClientState.roadStyle)) {
			// in-screen word: the HUD that say() writes to is hidden behind this screen (T33)
			this.roadNote = names.isEmpty() ? "no road style yet: the first road makes '" + QcClientState.roadStyle + "'"
					: "the plan has one road style; New starts another";
			return;
		}
		this.roadNote = null;
		int index = names.indexOf(QcClientState.roadStyle);
		QcClientState.roadStyle = index < 0 ? names.get(delta > 0 ? 0 : names.size() - 1)
				: names.get(Math.floorMod(index + delta, names.size()));
	}

	/** A new, empty road style named after the active one ("street 2", "street 3", ...), as one undoable edit. */
	private void newRoadStyle() {
		String name = QcToolCards.nextRoadName(QcClientState.roadStyle, QcClientState.plan.styles().keySet());
		QcInteraction.editFromScreen(() -> QcClientState.plan.styles()
				.putIfAbsent(name, new com.qccore.core.qc.BlockChoice(com.qccore.core.qc.Roles.ROAD)) == null);
		QcClientState.roadStyle = name;
		this.roadNote = null;
	}

	/** Makes sure the active road style exists (as the road tool would on its first road); false if a building style holds the name. */
	private boolean ensureRoadStyle() {
		com.qccore.core.qc.BlockChoice style = QcClientState.plan.styles().get(QcClientState.roadStyle);
		if (style != null) {
			if (!style.kind().equals(com.qccore.core.qc.Roles.ROAD)) {
				this.roadNote = "'" + QcClientState.roadStyle + "' is a " + style.kind() + " style → New";
				return false;
			}
			return true;
		}
		String name = QcClientState.roadStyle;
		QcInteraction.editFromScreen(() -> QcClientState.plan.styles()
				.putIfAbsent(name, new com.qccore.core.qc.BlockChoice(com.qccore.core.qc.Roles.ROAD)) == null);
		return true;
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
