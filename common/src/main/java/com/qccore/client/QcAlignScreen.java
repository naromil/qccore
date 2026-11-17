package com.qccore.client;

import com.qccore.core.Alignment;
import com.qccore.core.nbt.PaletteCodec;
import com.qccore.net.QcPackets.QcAlignC2S;
import com.qccore.world.QcUnitRecord;
import com.qccore.platform.QcPlatform;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** Re-aligns one placed QC Unit, and with it the whole building, to {@code (8x + a, 8y + b, 8z + c)}. */
public class QcAlignScreen extends Screen {
	private static final int FIELD_Y = 60;
	private static final int FIELD_WIDTH = 18;
	private static final int FIELD_HEIGHT = 18;
	private static final int FIELD_A_X = -60;
	private static final int FIELD_B_X = -20;
	private static final int FIELD_C_X = 20;
	private static final int LABEL_Y = FIELD_Y + 5;
	private static final int TARGET_Y = 34;
	private static final int RESIDUE_Y = 44;
	private static final int BUTTON_Y_OFFSET = -32;
	private static final int BUTTON_WIDTH = 100;
	private static final int MUTED_COLOR = 0x808080;

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
		this.fieldA = residueField(this.width / 2 + FIELD_A_X, "a");
		this.fieldB = residueField(this.width / 2 + FIELD_B_X, "b");
		this.fieldC = residueField(this.width / 2 + FIELD_C_X, "c");
		this.addRenderableWidget(Button.builder(Component.literal("Apply"), button -> apply())
				.bounds(this.width / 2 - 105, this.height + BUTTON_Y_OFFSET, BUTTON_WIDTH, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose())
				.bounds(this.width / 2 + 5, this.height + BUTTON_Y_OFFSET, BUTTON_WIDTH, 20).build());
		this.setInitialFocus(this.fieldA);
	}

	@Override
	public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context);
		context.drawCenteredString(this.font, this.title, this.width / 2, 6, 0xFFFFFF);
		BlockPos origin = this.target.origin();
		context.drawCenteredString(this.font,
				"unit at " + origin.getX() + " " + origin.getY() + " " + origin.getZ(), this.width / 2,
				TARGET_Y, MUTED_COLOR);
		BlockPos residues = Alignment.residues(origin);
		context.drawCenteredString(this.font,
				"currently 8x+" + residues.getX() + ", 8y+" + residues.getY() + ", 8z+" + residues.getZ(),
				this.width / 2, RESIDUE_Y, MUTED_COLOR);
		int left = this.width / 2 + FIELD_A_X - 12;
		context.drawString(this.font, "a", left, LABEL_Y, MUTED_COLOR);
		context.drawString(this.font, "b", left + 40, LABEL_Y, MUTED_COLOR);
		context.drawString(this.font, "c", left + 80, LABEL_Y, MUTED_COLOR);
		super.render(context, mouseX, mouseY, delta);
	}

	private EditBox residueField(int x, String label) {
		EditBox field = new EditBox(this.font, x, FIELD_Y, FIELD_WIDTH, FIELD_HEIGHT,
				Component.literal(label));
		field.setMaxLength(1);
		field.setFilter(text -> text.matches("[0-7]?"));
		this.addRenderableWidget(field);
		return field;
	}

	private void apply() {
		QcPlatform.sendToServer(new QcAlignC2S(this.target.origin(), residue(this.fieldA), residue(this.fieldB),
				residue(this.fieldC), PaletteCodec.toNbt(QcClientState.palette)));
		onClose();
	}

	private static int residue(EditBox field) {
		String text = field.getValue();
		return text.isEmpty() ? 0 : Integer.parseInt(text);
	}
}
