package com.qccore.client;

import com.qccore.core.Alignment;
import com.qccore.core.nbt.PaletteCodec;
import com.qccore.net.QcPackets.QcAlignC2S;
import com.qccore.world.QcUnitRecord;
import com.qccore.platform.QcPlatform;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** Re-aligns one placed QC Unit, and with it the whole building, to {@code (8x + a, 8y + b, 8z + c)}. */
public class QcAlignScreen extends Screen {
	private static final int FIELD_WIDTH = 18;
	private static final int FIELD_HEIGHT = 16;
	private static final int PANEL_WIDTH = 240;
	private static final int PANEL_TOP = 26;
	private static final int PANEL_HEIGHT = 92;

	private final QcUnitRecord target;
	private EditBox fieldA;
	private EditBox fieldB;
	private EditBox fieldC;

	public QcAlignScreen(QcUnitRecord target) {
		super(Component.translatable("qccore.screen.align"));
		this.target = target;
	}

	@Override
	protected void init() {
		int x = this.width / 2 - PANEL_WIDTH / 2;
		int y = PANEL_TOP + QcUi.HEADER_HEIGHT + 40;
		BlockPos residues = Alignment.residues(this.target.origin());
		this.fieldA = residueField(x + 40, y, "a", residues.getX(), "x = 8·n + a");
		this.fieldB = residueField(x + 110, y, "b", residues.getY(), "y = 8·n + b");
		this.fieldC = residueField(x + 180, y, "c", residues.getZ(), "z = 8·n + c");
		int bottom = PANEL_TOP + PANEL_HEIGHT + 6;
		this.addRenderableWidget(new QcUi.Chip(x, bottom, PANEL_WIDTH / 2 - 2, 18, "Cancel", this::onClose).tip("Esc"));
		this.addRenderableWidget(new QcUi.Chip(x + PANEL_WIDTH / 2 + 2, bottom, PANEL_WIDTH / 2 - 2, 18, "Align building", this::apply)
				.accent(QcUi.APPLY).primary().tip("Move the whole connected building onto the new lattice (Enter); creative only"));
		this.setInitialFocus(this.fieldA);
	}

	@Override
	public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context);
		QcUi.titleBar(context, this.font, this, null);
		int x = this.width / 2 - PANEL_WIDTH / 2;
		QcUi.panel(context, this.font, x, PANEL_TOP, PANEL_WIDTH, PANEL_HEIGHT, "Target unit", QcUi.ACCENT);
		BlockPos origin = this.target.origin();
		BlockPos residues = Alignment.residues(origin);
		context.drawString(this.font, "origin  " + origin.getX() + " " + origin.getY() + " " + origin.getZ(),
				x + 6, PANEL_TOP + QcUi.HEADER_HEIGHT + 5, QcUi.TEXT, false);
		context.drawString(this.font, "now     8x+" + residues.getX() + ", 8y+" + residues.getY() + ", 8z+" + residues.getZ(),
				x + 6, PANEL_TOP + QcUi.HEADER_HEIGHT + 17, QcUi.MUTED, false);
		int y = PANEL_TOP + QcUi.HEADER_HEIGHT + 45;
		context.drawString(this.font, "8x +", x + 14, y, QcUi.MUTED, false);
		context.drawString(this.font, "8y +", x + 84, y, QcUi.MUTED, false);
		context.drawString(this.font, "8z +", x + 154, y, QcUi.MUTED, false);
		context.drawString(this.font, "0-7 each; the nearest shift per axis is used", x + 6, y + 20, QcUi.DIM, false);
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
			apply();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	private EditBox residueField(int x, int y, String label, int current, String tip) {
		EditBox field = new EditBox(this.font, x, y, FIELD_WIDTH, FIELD_HEIGHT, Component.literal(label));
		field.setMaxLength(1);
		field.setFilter(text -> text.matches("[0-7]?"));
		field.setValue(Integer.toString(current));
		field.setTooltip(QcUi.tip(tip + "\nstarts at the current residue; empty = 0"));
		this.addRenderableWidget(field);
		return field;
	}

	private void apply() {
		QcClientState.requests.expect(QcRequests.Kind.ALIGN, QcClientState.history.revision());
		QcPlatform.sendToServer(new QcAlignC2S(this.target.origin(), residue(this.fieldA), residue(this.fieldB),
				residue(this.fieldC), PaletteCodec.toNbt(QcClientState.palette)));
		onClose();
	}

	private static int residue(EditBox field) {
		String text = field.getValue();
		return text.isEmpty() ? 0 : Integer.parseInt(text);
	}
}
