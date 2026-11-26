package com.qccore.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.qccore.client.QcInteraction.Cursor;
import com.qccore.core.plan.BuildShape;
import com.qccore.core.plan.Modifiers;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.lwjgl.glfw.GLFW;

/**
 * The editor HUD: a compact status panel in the top-left corner (tool with its icon and colour,
 * shape progress, components or style, modifiers, plan, cursor, lattice, plan problems), the
 * pending message as a fading toast above the hotbar (also outside a session, so "enter the editor
 * first" is seen), and the full list of controls - with the player's actual key bindings - while
 * Left Alt is held.
 */
public final class QcHud {
	private static final int PAD = 4;
	private static final int LINE_HEIGHT = 10;
	private static final int LABEL_WIDTH = 46;
	private static final int MAX_WIDTH = 260;

	private QcHud() {
	}

	/** Called by the loader glue for every HUD frame (Fabric {@code HudRenderCallback}, Forge a GUI overlay); draws nothing outside a session. */
	public static void render(GuiGraphics context, float tickDelta) {
		Minecraft client = Minecraft.getInstance();
		Font font = client.font;
		renderToast(context, font, client, tickDelta);
		if (!QcClientState.active || QcClientState.anchor == null) {
			return;
		}
		QcTool tool = QcClientState.tool;
		int toolColor = QcUi.color(tool);
		boolean help = altDown(client);

		List<Row> rows = help ? controls(client) : status(client);
		int labelWidth = LABEL_WIDTH;
		if (help) {
			labelWidth = 0;
			for (Row row : rows) {
				labelWidth = Math.max(labelWidth, font.width(row.label()));
			}
			labelWidth += 2 * PAD + 4;
		}
		String problem = help ? null : QcBlockPreview.problem();
		int width = 24 + font.width(tool.label()) + 2 * PAD;
		for (Row row : rows) {
			width = Math.max(width, labelWidth + font.width(row.value()) + 2 * PAD);
		}
		if (problem != null) {
			width = Math.max(width, font.width("⚠ " + problem) + 2 * PAD);
		}
		width = Math.min(width, MAX_WIDTH);
		int x = PAD;
		int y = PAD;
		int height = 22 + rows.size() * LINE_HEIGHT + (problem != null ? LINE_HEIGHT : 0) + 12;
		context.fill(x, y, x + width, y + height, 0xC0101318);
		context.fill(x, y, x + 2, y + height, toolColor);

		// header: tool icon, title, tool name
		context.renderItem(QcUi.icon(tool), x + 4, y + 2);
		context.drawString(font, help ? "QC Editor · controls" : "QC Editor", x + 23, y + 3, QcUi.MUTED, false);
		context.drawString(font, tool.label(), x + 23, y + 12, toolColor, true);
		int lineY = y + 22;
		for (Row row : rows) {
			int labelX = help ? x + labelWidth - PAD - 4 - font.width(row.label()) : x + PAD + 2;
			context.drawString(font, row.label(), labelX, lineY, help ? QcUi.WARN : 0xFF8A94A3, false);
			context.drawString(font, font.plainSubstrByWidth(row.value(), width - labelWidth - PAD),
					x + labelWidth, lineY, row.color(), false);
			lineY += LINE_HEIGHT;
		}
		if (problem != null) {
			context.drawString(font, font.plainSubstrByWidth("⚠ " + problem, width - 2 * PAD), x + PAD + 2, lineY, QcUi.ERROR, false);
			lineY += LINE_HEIGHT;
		}
		context.drawString(font, help ? "release Alt to go back" : key(QcKeys.tools) + " tools · hold Alt: controls",
				x + PAD + 2, lineY + 1, QcUi.DIM, false);
	}

	private static List<Row> status(Minecraft client) {
		QcTool tool = QcClientState.tool;
		List<Row> rows = new ArrayList<>();
		rows.add(new Row("shape", shapeText(), QcUi.TEXT));
		if (tool == QcTool.UNITS || tool == QcTool.ASSIGN) {
			rows.add(new Row("building", buildingText(), QcUi.TEXT));
		}
		if (tool == QcTool.ROAD) {
			rows.add(new Row("style", QcClientState.roadStyle, QcUi.TEXT));
		}
		String unset = unsetText();
		if (unset != null) {
			rows.add(new Row("unset", unset, QcUi.WARN));
		}
		String modifiers = modifiersText();
		if (modifiers != null) {
			rows.add(new Row("mods", modifiers, QcUi.WARN));
		}
		rows.add(new Row("plan", QcUi.planSummary(), QcUi.TEXT));
		rows.add(new Row("blocks", QcBlockPreview.size() + "  ·  ghosts " + (QcClientState.blockPreview ? "on" : "off"), QcUi.MUTED));
		rows.add(new Row("cursor", cursorText(client), QcUi.MUTED));
		rows.add(new Row("lattice", latticeText(), QcUi.MUTED));
		return rows;
	}

	/** The fading message above the hotbar. */
	private static void renderToast(GuiGraphics context, Font font, Minecraft client, float tickDelta) {
		if (QcClientState.messageTicks <= 0 || QcClientState.message == null) {
			return;
		}
		String text = QcClientState.message.startsWith("QC: ") ? QcClientState.message.substring(4) : QcClientState.message;
		float alpha = Math.min(1.0F, (QcClientState.messageTicks - tickDelta) / 10.0F);
		if (alpha <= 0.05F) {
			return;
		}
		int w = font.width(text) + 16;
		int screenW = client.getWindow().getGuiScaledWidth();
		int screenH = client.getWindow().getGuiScaledHeight();
		int x = (screenW - w) / 2;
		int y = screenH - 74;
		int accent = QcUi.color(QcClientState.tool);
		context.fill(x, y, x + w, y + 14, QcUi.withAlpha(0x101318, 0.8F * alpha));
		context.fill(x, y + 13, x + w, y + 14, QcUi.withAlpha(accent, alpha));
		context.drawString(font, text, x + 8, y + 3, QcUi.withAlpha(0xFFE36E, Math.max(alpha, 0.1F)), false);
	}

	/** {@code key -> action}, read from the live bindings so a rebinding shows up here. */
	private static List<Row> controls(Minecraft client) {
		String use = key(client.options.keyUse);
		String attack = key(client.options.keyAttack);
		String[][] controls = {
				{use, "use tool / next point"},
				{attack, "erase / cancel shape"},
				{"Shift+" + use, "wall on a shared face"},
				{"Ctrl+" + use, "gate on a shared face"},
				{"Shift/Ctrl+" + attack, "clear wall / gate"},
				{key(QcKeys.tools), "planning tools"},
				{key(QcKeys.tool) + " / " + key(QcKeys.shape), "cycle tool / shape"},
				{key(QcKeys.undo), "undo edit (Shift: redo)"},
				{key(QcKeys.rotate), "turn template"},
				{key(QcKeys.preview) + " / " + key(QcKeys.overlay), "ghost blocks / overlay"},
				{key(QcKeys.build), "apply plan to world"},
				{key(QcKeys.save), "export plan .nbt"},
				{key(QcKeys.config), "components & lattice"},
				{key(QcKeys.reanchor), "re-anchor here"},
				{key(QcKeys.duplicateLayer), "duplicate layer up"},
				{key(QcKeys.editor), "leave the editor"},
		};
		List<Row> rows = new ArrayList<>();
		for (String[] control : controls) {
			rows.add(new Row(control[0], control[1], QcUi.TEXT));
		}
		return rows;
	}

	private static String key(KeyMapping mapping) {
		if (mapping == null) {
			return "?";
		}
		return mapping.getTranslatedKeyMessage().getString()
				.replace("Left Button", "LMB").replace("Right Button", "RMB").replace("Middle Button", "MMB");
	}

	private static boolean altDown(Minecraft client) {
		return client.screen == null && InputConstants.isKeyDown(client.getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_ALT);
	}

	private static String shapeText() {
		QcTool tool = QcClientState.tool;
		if (tool == QcTool.TEMPLATE) {
			return (QcClientState.templateName.isEmpty() ? "no template chosen" : QcClientState.templateName)
					+ "  ·  " + QcClientState.templateRotation * 90 + "°";
		}
		BuildShape shape = QcInteraction.activeShape();
		StringBuilder line = new StringBuilder(shape.label());
		if (shape.points() > 1) {
			int done = QcClientState.points.size();
			line.append("  ");
			for (int i = 0; i < shape.points(); i++) {
				line.append(i < done ? "●" : i == done ? "◉" : "○");
			}
			line.append(done == 0 ? "  click to start" : "  point " + (done + 1) + "/" + shape.points());
		}
		return line.toString();
	}

	/** The active building (or the design a new one starts from) and its style. */
	private static String buildingText() {
		com.qccore.core.plan.BuildingSpec active = QcClientState.activeBuilding();
		if (active == null) {
			return "new · " + QcClientState.design.template + " · " + QcClientState.design.style;
		}
		return QcClientState.building + " · " + active.template + " · " + active.style;
	}

	/** How many roles the plan's styles still need, or {@code null} when every reached role has a block. */
	private static String unsetText() {
		int count = 0;
		for (java.util.Set<String> roles : QcBlockPreview.missing().values()) {
			count += roles.size();
		}
		return count == 0 ? null : count + " role(s) without a block · B → Blocks";
	}

	private static String modifiersText() {
		Modifiers modifiers = QcClientState.modifiers;
		if (!modifiers.isActive()) {
			return null;
		}
		List<String> parts = new ArrayList<>();
		if (modifiers.mirrorX) {
			parts.add("mirror E-W");
		}
		if (modifiers.mirrorZ) {
			parts.add("mirror N-S");
		}
		if (modifiers.arrayCount > 1) {
			parts.add("array ×" + modifiers.arrayCount);
		}
		return String.join(" · ", parts);
	}

	private static String latticeText() {
		BlockPos anchor = QcClientState.anchor;
		String lattice = QcClientState.alignA == null && QcClientState.alignB == null && QcClientState.alignC == null
				? "free" : "8x+" + residue(QcClientState.alignA) + " 8y+" + residue(QcClientState.alignB)
				+ " 8z+" + residue(QcClientState.alignC);
		return lattice + "  ·  anchor " + anchor.getX() + " " + anchor.getY() + " " + anchor.getZ();
	}

	private static String cursorText(Minecraft client) {
		Cursor cursor = QcInteraction.cursor(client);
		if (cursor == null) {
			return "—";
		}
		Direction face = cursor.face();
		return "cell " + cursor.cell().x() + ", " + cursor.cell().z() + "  ·  layer " + cursor.layer()
				+ (face == null ? "" : "  ·  " + face.getName());
	}

	private static int residue(Integer value) {
		return value == null ? 0 : value;
	}

	private record Row(String label, String value, int color) {
	}
}
