package com.qccore.client;

import com.qccore.core.UnitExporter;
import com.qccore.core.nbt.StructureNbt;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.nio.file.Path;

/** Saves the current layout as a vanilla structure {@code .nbt} into {@link QcSchematics#dir()}. */
public class QcSaveScreen extends Screen {
	private static final int CLOSE_DELAY_TICKS = 40;
	private static final int NAME_X_OFFSET = -100;
	private static final int NAME_WIDTH = 200;
	private static final int NAME_Y = 60;
	private static final int DIRECTORY_Y = 34;
	private static final int FILE_Y = 44;
	private static final int FOOTER_Y = 88;
	private static final int BUTTON_Y_OFFSET = -32;
	private static final int BUTTON_WIDTH = 100;
	private static final int MUTED_COLOR = 0x808080;
	private static final int FOOTER_COLOR = 0xFFFFFF;
	private static final int FOOTER_ERROR_COLOR = 0xFF5555;

	private TextFieldWidget nameField;
	private String name = "qc_layout";
	private String directory = "";
	private String footer = "";
	private boolean footerError;
	private int closeTicks = -1;

	public QcSaveScreen() {
		super(Text.translatable("qccore.screen.save"));
	}

	@Override
	protected void init() {
		this.directory = QcSchematics.dir().toAbsolutePath().toString();
		this.nameField = new TextFieldWidget(this.textRenderer, this.width / 2 + NAME_X_OFFSET, NAME_Y,
				NAME_WIDTH, 20, Text.literal("name"));
		this.nameField.setMaxLength(128);
		this.nameField.setChangedListener(text -> this.name = text);
		this.nameField.setText(this.name);
		this.addDrawableChild(this.nameField);
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Save"), button -> save())
				.dimensions(this.width / 2 - 105, this.height + BUTTON_Y_OFFSET, BUTTON_WIDTH, 20).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> close())
				.dimensions(this.width / 2 + 5, this.height + BUTTON_Y_OFFSET, BUTTON_WIDTH, 20).build());
		this.setInitialFocus(this.nameField);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context);
		context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 6, 0xFFFFFF);
		context.drawTextWithShadow(this.textRenderer, this.directory,
				this.width / 2 + NAME_X_OFFSET, DIRECTORY_Y, MUTED_COLOR);
		context.drawTextWithShadow(this.textRenderer, QcSchematics.normaliseName(this.name) + ".nbt",
				this.width / 2 + NAME_X_OFFSET, FILE_Y, MUTED_COLOR);
		if (!this.footer.isEmpty()) {
			context.drawCenteredTextWithShadow(this.textRenderer, this.footer, this.width / 2, FOOTER_Y,
					this.footerError ? FOOTER_ERROR_COLOR : FOOTER_COLOR);
		}
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public void tick() {
		if (this.closeTicks > 0 && --this.closeTicks == 0) {
			close();
		}
	}

	private void save() {
		NbtCompound tag = StructureNbt.write(
				UnitExporter.export(QcClientState.layers, QcClientState.palette, BlockPos.ORIGIN));
		if (tag == null) {
			this.footer = "the layout is empty";
			this.footerError = true;
			return;
		}
		try {
			Path file = QcSchematics.save(this.name, tag);
			this.footer = "saved " + file.toAbsolutePath();
			this.footerError = false;
			this.closeTicks = CLOSE_DELAY_TICKS;
		} catch (RuntimeException e) {
			this.footer = e.getMessage() == null ? "could not save " + this.name : e.getMessage();
			this.footerError = true;
		}
	}
}
