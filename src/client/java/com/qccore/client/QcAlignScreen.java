package com.qccore.client;

import com.qccore.core.Alignment;
import com.qccore.core.nbt.PaletteCodec;
import com.qccore.net.QcPackets.QcAlignC2S;
import com.qccore.world.QcUnitRecord;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

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
	private TextFieldWidget fieldA;
	private TextFieldWidget fieldB;
	private TextFieldWidget fieldC;

	public QcAlignScreen(QcUnitRecord target) {
		super(Text.translatable("qccore.screen.align"));
		this.target = target;
	}

	@Override
	protected void init() {
		this.fieldA = residueField(this.width / 2 + FIELD_A_X, "a");
		this.fieldB = residueField(this.width / 2 + FIELD_B_X, "b");
		this.fieldC = residueField(this.width / 2 + FIELD_C_X, "c");
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Apply"), button -> apply())
				.dimensions(this.width / 2 - 105, this.height + BUTTON_Y_OFFSET, BUTTON_WIDTH, 20).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> close())
				.dimensions(this.width / 2 + 5, this.height + BUTTON_Y_OFFSET, BUTTON_WIDTH, 20).build());
		this.setInitialFocus(this.fieldA);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context);
		context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 6, 0xFFFFFF);
		BlockPos origin = this.target.origin();
		context.drawCenteredTextWithShadow(this.textRenderer,
				"unit at " + origin.getX() + " " + origin.getY() + " " + origin.getZ(), this.width / 2,
				TARGET_Y, MUTED_COLOR);
		BlockPos residues = Alignment.residues(origin);
		context.drawCenteredTextWithShadow(this.textRenderer,
				"currently 8x+" + residues.getX() + ", 8y+" + residues.getY() + ", 8z+" + residues.getZ(),
				this.width / 2, RESIDUE_Y, MUTED_COLOR);
		int left = this.width / 2 + FIELD_A_X - 12;
		context.drawTextWithShadow(this.textRenderer, "a", left, LABEL_Y, MUTED_COLOR);
		context.drawTextWithShadow(this.textRenderer, "b", left + 40, LABEL_Y, MUTED_COLOR);
		context.drawTextWithShadow(this.textRenderer, "c", left + 80, LABEL_Y, MUTED_COLOR);
		super.render(context, mouseX, mouseY, delta);
	}

	private TextFieldWidget residueField(int x, String label) {
		TextFieldWidget field = new TextFieldWidget(this.textRenderer, x, FIELD_Y, FIELD_WIDTH, FIELD_HEIGHT,
				Text.literal(label));
		field.setMaxLength(1);
		field.setTextPredicate(text -> text.matches("[0-7]?"));
		this.addDrawableChild(field);
		return field;
	}

	private void apply() {
		ClientPlayNetworking.send(new QcAlignC2S(this.target.origin(), residue(this.fieldA), residue(this.fieldB),
				residue(this.fieldC), PaletteCodec.toNbt(QcClientState.palette)));
		close();
	}

	private static int residue(TextFieldWidget field) {
		String text = field.getText();
		return text.isEmpty() ? 0 : Integer.parseInt(text);
	}
}
