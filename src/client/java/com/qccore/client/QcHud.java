package com.qccore.client;

import com.qccore.client.QcInteraction.Cursor;
import com.qccore.core.plan.BuildShape;
import com.qccore.core.plan.ComponentMask;
import com.qccore.core.plan.Modifiers;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** The editor HUD: lattice, cursor, tool and shape, the plan, the controls, and the pending message. */
public final class QcHud {
	private static final String CONTROLS =
			"RMB use tool   LMB erase/cancel shape   Shift+RMB wall   Ctrl+RMB gate   Shift/Ctrl+LMB clear";
	private static final String KEYBINDS =
			"B tools  N tool  V shape  Z undo (Shift redo)  R turn template  M ghosts  K apply  J export  I components";
	private static final String KEYBINDS_2 =
			"G exit   Y re-anchor   H duplicate layer   U align   O overlay   /qc undo | redo the last apply";
	private static final int TOOL_COLOR = 0x9FE0FF;
	private static final int WARN_COLOR = 0xFF8080;
	private static final int LINE_COLOR = 0xFFFFFF;
	private static final int MESSAGE_COLOR = 0xFFE36E;
	private static final int LINE_OFFSET = 4;
	private static final int LINE_HEIGHT = 10;

	private QcHud() {
	}

	/** Registered on {@link HudRenderCallback#EVENT}; draws nothing outside a session. */
	public static void render(DrawContext context, float tickDelta) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (!QcClientState.active || QcClientState.anchor == null) {
			return;
		}
		int y = LINE_OFFSET;
		context.drawTextWithShadow(client.textRenderer, latticeLine(), LINE_OFFSET, y, LINE_COLOR);
		y += LINE_HEIGHT;
		context.drawTextWithShadow(client.textRenderer, cursorLine(client), LINE_OFFSET, y, LINE_COLOR);
		y += LINE_HEIGHT;
		context.drawTextWithShadow(client.textRenderer, toolLine(), LINE_OFFSET, y, TOOL_COLOR);
		y += LINE_HEIGHT;
		context.drawTextWithShadow(client.textRenderer, planLine(), LINE_OFFSET, y, TOOL_COLOR);
		y += LINE_HEIGHT;
		String problem = QcBlockPreview.problem();
		if (problem != null) {
			context.drawTextWithShadow(client.textRenderer, "plan problem: " + problem, LINE_OFFSET, y, WARN_COLOR);
			y += LINE_HEIGHT;
		}
		context.drawTextWithShadow(client.textRenderer, CONTROLS, LINE_OFFSET, y, LINE_COLOR);
		y += LINE_HEIGHT;
		context.drawTextWithShadow(client.textRenderer, KEYBINDS, LINE_OFFSET, y, LINE_COLOR);
		y += LINE_HEIGHT;
		context.drawTextWithShadow(client.textRenderer, KEYBINDS_2, LINE_OFFSET, y, LINE_COLOR);
		if (QcClientState.messageTicks > 0 && QcClientState.message != null) {
			y += LINE_HEIGHT;
			context.drawTextWithShadow(client.textRenderer, QcClientState.message, LINE_OFFSET, y, MESSAGE_COLOR);
		}
	}

	private static String toolLine() {
		QcTool tool = QcClientState.tool;
		StringBuilder line = new StringBuilder("tool: ").append(tool.label());
		if (tool == QcTool.TEMPLATE) {
			line.append(" ").append(QcClientState.templateName.isEmpty() ? "<none>" : QcClientState.templateName)
					.append(" ").append(QcClientState.templateRotation * 90).append(" deg");
		} else {
			BuildShape shape = QcInteraction.activeShape();
			line.append("  shape: ").append(shape.label());
			if (!QcClientState.points.isEmpty()) {
				line.append(" (point ").append(QcClientState.points.size() + 1).append("/").append(shape.points()).append(")");
			}
		}
		if (tool == QcTool.UNITS || tool == QcTool.PAINT) {
			line.append("  components: ").append(ComponentMask.describe(QcClientState.componentMask));
		}
		if (tool == QcTool.ROAD) {
			line.append("  style: ").append(QcClientState.roadStyle);
		}
		Modifiers modifiers = QcClientState.modifiers;
		if (modifiers.isActive()) {
			line.append("  modifiers:");
			if (modifiers.mirrorX) {
				line.append(" mirror E-W");
			}
			if (modifiers.mirrorZ) {
				line.append(" mirror N-S");
			}
			if (modifiers.arrayCount > 1) {
				line.append(" array x").append(modifiers.arrayCount);
			}
		}
		return line.toString();
	}

	private static String planLine() {
		return "plan: " + QcClientState.plan.summary() + ", " + QcBlockPreview.size() + " block(s)   ghosts "
				+ (QcClientState.blockPreview ? "on" : "off");
	}

	private static String latticeLine() {
		BlockPos anchor = QcClientState.anchor;
		return "QC editor  lattice: " + latticeLabel() + "  anchor: "
				+ anchor.getX() + " " + anchor.getY() + " " + anchor.getZ();
	}

	private static String latticeLabel() {
		if (QcClientState.alignA == null && QcClientState.alignB == null && QcClientState.alignC == null) {
			return "free";
		}
		return residue(QcClientState.alignA) + "," + residue(QcClientState.alignB) + "," + residue(QcClientState.alignC);
	}

	private static String cursorLine(MinecraftClient client) {
		Cursor cursor = QcInteraction.cursor(client);
		if (cursor == null) {
			return "cursor: none";
		}
		Direction face = cursor.face();
		return "cursor: cell " + cursor.cell().x() + "," + cursor.cell().z()
				+ "  layer " + cursor.layer()
				+ "  face: " + (face == null ? "none" : face.getName());
	}

	private static int residue(Integer value) {
		return value == null ? 0 : value;
	}
}
