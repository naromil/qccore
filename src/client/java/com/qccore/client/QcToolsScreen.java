package com.qccore.client;

import com.qccore.core.plan.Cell;
import com.qccore.core.plan.ComponentMask;
import com.qccore.core.plan.Modifiers;
import com.qccore.core.plan.QcPlan;
import com.qccore.net.QcPackets.QcUndoC2S;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

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

	private final List<ButtonWidget> maskButtons = new ArrayList<>();
	private ButtonWidget toolButton;
	private ButtonWidget shapeButton;
	private ButtonWidget mirrorXButton;
	private ButtonWidget mirrorZButton;
	private ButtonWidget mirrorCellButton;
	private TextFieldWidget arrayCount;
	private TextFieldWidget arrayX;
	private TextFieldWidget arrayY;
	private TextFieldWidget arrayZ;
	private TextFieldWidget margin;
	private TextFieldWidget ground;
	private Cell cursorCell;

	public QcToolsScreen() {
		super(Text.translatable("qccore.screen.tools"));
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	protected void init() {
		this.maskButtons.clear();
		this.cursorCell = this.client == null ? null : QcInteraction.target(this.client);
		int left = this.width / 2 - COLUMN_WIDTH - 5;
		int right = this.width / 2 + 5;
		int y = TOP;

		this.toolButton = this.addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
			QcClientState.tool = hasShiftDown() ? previous(QcClientState.tool) : QcClientState.tool.next();
			QcClientState.points.clear();
			refresh();
		}).dimensions(left, y, COLUMN_WIDTH, 20).build());
		this.mirrorXButton = this.addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
			QcClientState.modifiers.mirrorX = !QcClientState.modifiers.mirrorX;
			refresh();
		}).dimensions(right, y, 98, 20).build());
		this.mirrorZButton = this.addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
			QcClientState.modifiers.mirrorZ = !QcClientState.modifiers.mirrorZ;
			refresh();
		}).dimensions(right + 102, y, 98, 20).build());
		y += ROW;

		this.shapeButton = this.addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
			QcClientState.shape = QcClientState.shape.next();
			QcClientState.points.clear();
			refresh();
		}).dimensions(left, y, COLUMN_WIDTH, 20).build());
		this.mirrorCellButton = this.addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
			if (this.cursorCell != null) {
				QcClientState.modifiers.mirrorCellX = this.cursorCell.x();
				QcClientState.modifiers.mirrorCellZ = this.cursorCell.z();
			}
			refresh();
		}).dimensions(right, y, COLUMN_WIDTH, 20).build());
		y += ROW + 12;

		int maskTop = y;
		for (int i = 0; i < ComponentMask.BITS.length; i++) {
			int bit = ComponentMask.BITS[i];
			ButtonWidget button = ButtonWidget.builder(Text.empty(), b -> {
				QcClientState.componentMask ^= bit;
				refresh();
			}).dimensions(left + (i % 3) * 67, maskTop + (i / 3) * ROW, 65, 20).build();
			this.maskButtons.add(this.addDrawableChild(button));
		}
		y = maskTop + 3 * ROW;
		this.addDrawableChild(ButtonWidget.builder(Text.literal("All"), b -> {
			QcClientState.componentMask = ComponentMask.ALL;
			refresh();
		}).dimensions(left, y, 65, 20).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Shell only"), b -> {
			QcClientState.componentMask = ComponentMask.FRAMEWORK | ComponentMask.FLOOR;
			refresh();
		}).dimensions(left + 67, y, 65, 20).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Skin only"), b -> {
			QcClientState.componentMask = ComponentMask.ROW | ComponentMask.COLUMN | ComponentMask.WALL | ComponentMask.OUTER_WALL;
			refresh();
		}).dimensions(left + 134, y, 66, 20).build());

		Modifiers modifiers = QcClientState.modifiers;
		this.arrayCount = field(right + 40, maskTop, 30, Integer.toString(modifiers.arrayCount));
		this.arrayX = field(right + 112, maskTop, 26, Integer.toString(modifiers.arrayStepX));
		this.arrayY = field(right + 142, maskTop, 26, Integer.toString(modifiers.arrayStepLayer));
		this.arrayZ = field(right + 174, maskTop, 26, Integer.toString(modifiers.arrayStepZ));
		this.margin = field(right + 100, maskTop + ROW, 30, Integer.toString(QcClientState.plan.clearMargin));
		this.ground = field(right + 60, maskTop + 2 * ROW, 140, QcClientState.plan.ground);
		this.ground.setMaxLength(128);
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Templates…"), b -> {
			commit();
			this.client.setScreen(new QcTemplateScreen(this));
		}).dimensions(right, maskTop + 3 * ROW, 98, 20).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Kits & roads…"), b -> {
			commit();
			this.client.setScreen(new QcKitScreen(this));
		}).dimensions(right + 102, maskTop + 3 * ROW, 98, 20).build());

		int bottom = this.height - 48;
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Undo edit"), b -> {
			QcInteraction.undo();
		}).dimensions(left, bottom, 98, 20).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Redo edit"), b -> {
			QcInteraction.redo();
		}).dimensions(left + 102, bottom, 98, 20).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Clear plan"), b -> QcInteraction.clearPlan())
				.dimensions(right, bottom, 98, 20).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Undo last apply"), b -> {
			ClientPlayNetworking.send(new QcUndoC2S());
			close();
		}).dimensions(right + 102, bottom, 98, 20).build());
		bottom += ROW;
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Apply plan to world"), b -> {
			commit();
			close();
			QcInteraction.apply();
		}).dimensions(left, bottom, 133, 20).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Export plan as .nbt…"), b -> {
			commit();
			this.client.setScreen(new QcSaveScreen());
		}).dimensions(left + 137, bottom, 133, 20).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
				.dimensions(left + 274, bottom, 136, 20).build());
		refresh();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context);
		context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 6, TEXT_COLOR);
		int left = this.width / 2 - COLUMN_WIDTH - 5;
		int right = this.width / 2 + 5;
		int maskTop = TOP + 2 * ROW + 12;
		context.drawTextWithShadow(this.textRenderer, "components of new units / paint", left, maskTop - 10, LABEL_COLOR);
		context.drawTextWithShadow(this.textRenderer, "copies", right, maskTop + 6, LABEL_COLOR);
		context.drawTextWithShadow(this.textRenderer, "step", right + 80, maskTop + 6, LABEL_COLOR);
		context.drawTextWithShadow(this.textRenderer, "x", right + 122, maskTop - 10, LABEL_COLOR);
		context.drawTextWithShadow(this.textRenderer, "layer", right + 142, maskTop - 10, LABEL_COLOR);
		context.drawTextWithShadow(this.textRenderer, "z", right + 184, maskTop - 10, LABEL_COLOR);
		context.drawTextWithShadow(this.textRenderer, "clear margin (0-4)", right, maskTop + ROW + 6, LABEL_COLOR);
		context.drawTextWithShadow(this.textRenderer, "ground", right, maskTop + 2 * ROW + 6, LABEL_COLOR);
		QcPlan plan = QcClientState.plan;
		context.drawCenteredTextWithShadow(this.textRenderer, "plan: " + plan.summary() + "   edits: "
						+ QcClientState.history.undoDepth() + " undo / " + QcClientState.history.redoDepth() + " redo",
				this.width / 2, this.height - 62, TEXT_COLOR);
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public void close() {
		commit();
		super.close();
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
		String newGround = this.ground.getText().trim();
		if (newMargin != QcClientState.plan.clearMargin || !newGround.equals(QcClientState.plan.ground)) {
			QcInteraction.editFromScreen(() -> {
				QcClientState.plan.clearMargin = newMargin;
				QcClientState.plan.ground = newGround;
				return true;
			});
		}
	}

	private void refresh() {
		this.toolButton.setMessage(Text.literal("Tool: " + QcClientState.tool.label() + "  (N)"));
		this.shapeButton.setMessage(Text.literal("Shape: " + QcClientState.shape.label() + "  (V)"));
		this.mirrorXButton.setMessage(Text.literal("Mirror E-W: " + (QcClientState.modifiers.mirrorX ? "on" : "off")));
		this.mirrorZButton.setMessage(Text.literal("Mirror N-S: " + (QcClientState.modifiers.mirrorZ ? "on" : "off")));
		this.mirrorCellButton.setMessage(Text.literal("Mirror cell " + QcClientState.modifiers.mirrorCellX + ", "
				+ QcClientState.modifiers.mirrorCellZ + " (click: cursor cell)"));
		for (int i = 0; i < this.maskButtons.size(); i++) {
			boolean on = ComponentMask.has(QcClientState.componentMask, ComponentMask.BITS[i]);
			this.maskButtons.get(i).setMessage(Text.literal((on ? "§a" : "§8") + ComponentMask.NAMES[i]));
		}
	}

	private TextFieldWidget field(int x, int y, int width, String value) {
		TextFieldWidget field = new TextFieldWidget(this.textRenderer, x, y, width, 20, Text.empty());
		field.setMaxLength(4);
		field.setText(value);
		return this.addDrawableChild(field);
	}

	private static int parse(TextFieldWidget field, int fallback) {
		try {
			return Integer.parseInt(field.getText().trim());
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
