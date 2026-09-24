package com.qccore.client;

import com.qccore.client.QcInteraction.Cursor;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** The editor HUD: lattice, cursor, the controls, and the pending message. */
public final class QcHud {
	private static final String CONTROLS =
			"RMB place   LMB erase   Shift+RMB wall   Ctrl+RMB gate   Shift/Ctrl+LMB clear";
	private static final String KEYBINDS =
			"G exit   Y re-anchor   H duplicate layer   J save   K build   I components   U align   O overlay";
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
		context.drawTextWithShadow(client.textRenderer, CONTROLS, LINE_OFFSET, y, LINE_COLOR);
		y += LINE_HEIGHT;
		context.drawTextWithShadow(client.textRenderer, KEYBINDS, LINE_OFFSET, y, LINE_COLOR);
		if (QcClientState.messageTicks > 0 && QcClientState.message != null) {
			y += LINE_HEIGHT;
			context.drawTextWithShadow(client.textRenderer, QcClientState.message, LINE_OFFSET, y, MESSAGE_COLOR);
		}
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
