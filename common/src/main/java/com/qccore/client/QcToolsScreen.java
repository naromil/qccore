package com.qccore.client;

import com.qccore.core.plan.Cell;
import com.qccore.core.plan.ComponentMask;
import com.qccore.core.plan.Modifiers;
import com.qccore.core.plan.QcPlan;
import com.qccore.net.QcPackets.QcRedoC2S;
import com.qccore.net.QcPackets.QcUndoC2S;
import com.qccore.platform.QcPlatform;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;

/**
 * The hub of the planning tools (key B), in the spirit of Effortless Building's radial menu: the
 * tool and the build shape, which of the nine components new units build, the mirror and array
 * modifiers, terrain clearing settings, the plan's undo/redo, and the two ways to finish a plan:
 * apply it to the world or export it as a structure {@code .nbt}.
 */
public class QcToolsScreen extends Screen {
	private static final int COLUMN_WIDTH = 200;
	private static final int ROW = 22;
	private static final int TOP = 24;
	private static final int LABEL_COLOR = 0xA0A0A0;
	private static final int TEXT_COLOR = 0xFFFFFF;
	private static final String[] SHORT_NAMES = {"framework", "row", "column", "wall", "floor",
			"inner col.", "inner wall", "gate", "outer wall"};

	private final List<Button> maskButtons = new ArrayList<>();
	private Button toolButton;
	private Button shapeButton;
	private Button mirrorXButton;
	private Button mirrorZButton;
	private Button mirrorCellButton;
	private EditBox arrayCount;
	private EditBox arrayX;
	private EditBox arrayY;
	private EditBox arrayZ;
	private EditBox margin;
	private EditBox ground;
	private Cell cursorCell;

	public QcToolsScreen() {
		super(Component.translatable("qccore.screen.tools"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		this.maskButtons.clear();
		this.cursorCell = this.minecraft == null ? null : QcInteraction.target(this.minecraft);
		int left = this.width / 2 - COLUMN_WIDTH - 5;
		int right = this.width / 2 + 5;
		int y = TOP;

		this.toolButton = this.addRenderableWidget(Button.builder(Component.empty(), b -> {
			QcClientState.tool = hasShiftDown() ? previous(QcClientState.tool) : QcClientState.tool.next();
			QcClientState.points.clear();
			refresh();
		}).bounds(left, y, COLUMN_WIDTH, 20).build());
		this.mirrorXButton = this.addRenderableWidget(Button.builder(Component.empty(), b -> {
			QcClientState.modifiers.mirrorX = !QcClientState.modifiers.mirrorX;
			refresh();
		}).bounds(right, y, 98, 20).build());
		this.mirrorZButton = this.addRenderableWidget(Button.builder(Component.empty(), b -> {
			QcClientState.modifiers.mirrorZ = !QcClientState.modifiers.mirrorZ;
			refresh();
		}).bounds(right + 102, y, 98, 20).build());
		y += ROW;

		this.shapeButton = this.addRenderableWidget(Button.builder(Component.empty(), b -> {
			QcClientState.shape = QcClientState.shape.next();
			QcClientState.points.clear();
			refresh();
		}).bounds(left, y, COLUMN_WIDTH, 20).build());
		this.mirrorCellButton = this.addRenderableWidget(Button.builder(Component.empty(), b -> {
			if (this.cursorCell != null) {
				QcClientState.modifiers.mirrorCellX = this.cursorCell.x();
				QcClientState.modifiers.mirrorCellZ = this.cursorCell.z();
			}
			refresh();
		}).bounds(right, y, COLUMN_WIDTH, 20).build());
		y += ROW + 12;

		int maskTop = y;
		for (int i = 0; i < ComponentMask.BITS.length; i++) {
			int bit = ComponentMask.BITS[i];
			Button button = Button.builder(Component.empty(), b -> {
				QcClientState.componentMask ^= bit;
				refresh();
			}).bounds(left + (i % 3) * 67, maskTop + (i / 3) * ROW, 65, 20).build();
			this.maskButtons.add(this.addRenderableWidget(button));
		}
		y = maskTop + 3 * ROW;
		this.addRenderableWidget(Button.builder(Component.literal("All"), b -> {
			QcClientState.componentMask = ComponentMask.ALL;
			refresh();
		}).bounds(left, y, 65, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Shell only"), b -> {
			QcClientState.componentMask = ComponentMask.FRAMEWORK | ComponentMask.FLOOR;
			refresh();
		}).bounds(left + 67, y, 65, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Skin only"), b -> {
			QcClientState.componentMask = ComponentMask.ROW | ComponentMask.COLUMN | ComponentMask.WALL | ComponentMask.OUTER_WALL;
			refresh();
		}).bounds(left + 134, y, 66, 20).build());

		Modifiers modifiers = QcClientState.modifiers;
		this.arrayCount = field(right + 40, maskTop, 30, Integer.toString(modifiers.arrayCount));
		this.arrayX = field(right + 112, maskTop, 26, Integer.toString(modifiers.arrayStepX));
		this.arrayY = field(right + 142, maskTop, 26, Integer.toString(modifiers.arrayStepLayer));
		this.arrayZ = field(right + 174, maskTop, 26, Integer.toString(modifiers.arrayStepZ));
		this.margin = field(right + 100, maskTop + ROW, 30, Integer.toString(QcClientState.plan.clearMargin));
		this.ground = field(right + 60, maskTop + 2 * ROW, 140, QcClientState.plan.ground);
		this.ground.setMaxLength(128);
		this.addRenderableWidget(Button.builder(Component.literal("Templates…"), b -> {
			commit();
			this.minecraft.setScreen(new QcTemplateScreen(this));
		}).bounds(right, maskTop + 3 * ROW, 98, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Kits & roads…"), b -> {
			commit();
			this.minecraft.setScreen(new QcKitScreen(this));
		}).bounds(right + 102, maskTop + 3 * ROW, 98, 20).build());

		int bottom = this.height - 48;
		this.addRenderableWidget(Button.builder(Component.literal("Undo edit"), b -> {
			QcInteraction.undo();
		}).bounds(left, bottom, 98, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Redo edit"), b -> {
			QcInteraction.redo();
		}).bounds(left + 102, bottom, 98, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Clear plan"), b -> QcInteraction.clearPlan())
				.bounds(right, bottom, 65, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Undo apply"), b -> {
			QcPlatform.sendToServer(new QcUndoC2S());
			onClose();
		}).bounds(right + 67, bottom, 65, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Redo apply"), b -> {
			QcPlatform.sendToServer(new QcRedoC2S());
			onClose();
		}).bounds(right + 134, bottom, 66, 20).build());
		bottom += ROW;
		this.addRenderableWidget(Button.builder(Component.literal("Apply plan to world"), b -> {
			commit();
			onClose();
			QcInteraction.apply();
		}).bounds(left, bottom, 133, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Export plan as .nbt…"), b -> {
			commit();
			this.minecraft.setScreen(new QcSaveScreen());
		}).bounds(left + 137, bottom, 133, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
				.bounds(left + 274, bottom, 136, 20).build());
		refresh();
	}

	@Override
	public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context);
		context.drawCenteredString(this.font, this.title, this.width / 2, 6, TEXT_COLOR);
		int left = this.width / 2 - COLUMN_WIDTH - 5;
		int right = this.width / 2 + 5;
		int maskTop = TOP + 2 * ROW + 12;
		context.drawString(this.font, "components of new units / paint", left, maskTop - 10, LABEL_COLOR);
		context.drawString(this.font, "copies", right, maskTop + 6, LABEL_COLOR);
		context.drawString(this.font, "step", right + 80, maskTop + 6, LABEL_COLOR);
		context.drawString(this.font, "x", right + 122, maskTop - 10, LABEL_COLOR);
		context.drawString(this.font, "layer", right + 142, maskTop - 10, LABEL_COLOR);
		context.drawString(this.font, "z", right + 184, maskTop - 10, LABEL_COLOR);
		context.drawString(this.font, "clear margin (0-4)", right, maskTop + ROW + 6, LABEL_COLOR);
		context.drawString(this.font, "ground", right, maskTop + 2 * ROW + 6, LABEL_COLOR);
		QcPlan plan = QcClientState.plan;
		context.drawCenteredString(this.font, "plan: " + plan.summary() + "   edits: "
						+ QcClientState.history.undoDepth() + " undo / " + QcClientState.history.redoDepth() + " redo",
				this.width / 2, this.height - 62, TEXT_COLOR);
		super.render(context, mouseX, mouseY, delta);
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

	/** Copies the typed numbers into the modifiers and the plan settings. */
	private void commit() {
		if (this.arrayCount == null) {
			return;
		}
		Modifiers modifiers = QcClientState.modifiers;
		modifiers.arrayCount = clamp(parse(this.arrayCount, modifiers.arrayCount), 1, 64);
		modifiers.arrayStepX = clamp(parse(this.arrayX, modifiers.arrayStepX), -64, 64);
		modifiers.arrayStepLayer = clamp(parse(this.arrayY, modifiers.arrayStepLayer), -64, 64);
		modifiers.arrayStepZ = clamp(parse(this.arrayZ, modifiers.arrayStepZ), -64, 64);
		int newMargin = clamp(parse(this.margin, QcClientState.plan.clearMargin), 0, QcPlan.MAX_CLEAR_MARGIN);
		String newGround = this.ground.getValue().trim();
		if (newMargin != QcClientState.plan.clearMargin || !newGround.equals(QcClientState.plan.ground)) {
			QcInteraction.editFromScreen(() -> {
				QcClientState.plan.clearMargin = newMargin;
				QcClientState.plan.ground = newGround;
				return true;
			});
		}
	}

	private void refresh() {
		this.toolButton.setMessage(Component.literal("Tool: " + QcClientState.tool.label() + "  (N)"));
		this.shapeButton.setMessage(Component.literal("Shape: " + QcClientState.shape.label() + "  (V)"));
		this.mirrorXButton.setMessage(Component.literal("Mirror E-W: " + (QcClientState.modifiers.mirrorX ? "on" : "off")));
		this.mirrorZButton.setMessage(Component.literal("Mirror N-S: " + (QcClientState.modifiers.mirrorZ ? "on" : "off")));
		this.mirrorCellButton.setMessage(Component.literal("Mirror cell " + QcClientState.modifiers.mirrorCellX + ", "
				+ QcClientState.modifiers.mirrorCellZ + " (click: cursor cell)"));
		for (int i = 0; i < this.maskButtons.size(); i++) {
			boolean on = ComponentMask.has(QcClientState.componentMask, ComponentMask.BITS[i]);
			this.maskButtons.get(i).setMessage(Component.literal((on ? "§a" : "§8") + SHORT_NAMES[i]));
		}
	}

	private EditBox field(int x, int y, int width, String value) {
		EditBox field = new EditBox(this.font, x, y, width, 20, Component.empty());
		field.setMaxLength(4);
		field.setValue(value);
		return this.addRenderableWidget(field);
	}

	private static int parse(EditBox field, int fallback) {
		try {
			return Integer.parseInt(field.getValue().trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static int clamp(int value, int low, int high) {
		return Math.max(low, Math.min(high, value));
	}

	private static QcTool previous(QcTool tool) {
		QcTool[] values = QcTool.values();
		return values[(tool.ordinal() + values.length - 1) % values.length];
	}
}
