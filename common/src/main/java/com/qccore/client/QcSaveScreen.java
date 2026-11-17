package com.qccore.client;

import com.qccore.core.QcResult;
import com.qccore.core.plan.PlanCompiler;
import com.qccore.world.QcPlanService;
import com.qccore.core.nbt.StructureNbt;
import java.nio.file.Path;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * Exports the whole plan as a vanilla structure {@code .nbt} into {@link QcSchematics#dir()}: the
 * units with their component masks, the roads and the ground fill, compiled exactly as an apply
 * would place them. By default the file is sparse (no air, what Create's schematicannon wants);
 * "with clearing" also writes the cleared terrain and the road headroom as air, so pasting the
 * structure carves the site like an apply does.
 */
public class QcSaveScreen extends Screen {
	private static final int CLOSE_DELAY_TICKS = 40;
	private static final int NAME_X_OFFSET = -100;
	private static final int NAME_WIDTH = 200;
	private static final int NAME_Y = 60;
	private static final int DIRECTORY_Y = 34;
	private static final int FILE_Y = 44;
	private static final int FOOTER_Y = 112;
	private static final int BUTTON_Y_OFFSET = -32;
	private static final int BUTTON_WIDTH = 100;
	private static final int MUTED_COLOR = 0x808080;
	private static final int FOOTER_COLOR = 0xFFFFFF;
	private static final int FOOTER_ERROR_COLOR = 0xFF5555;

	private EditBox nameField;
	private String name = "qc_layout";
	private String directory = "";
	private String footer = "";
	private boolean footerError;
	private int closeTicks = -1;
	private boolean includeAir;

	public QcSaveScreen() {
		super(Component.translatable("qccore.screen.save"));
	}

	@Override
	protected void init() {
		this.directory = QcSchematics.dir().toAbsolutePath().toString();
		this.nameField = new EditBox(this.font, this.width / 2 + NAME_X_OFFSET, NAME_Y,
				NAME_WIDTH, 20, Component.literal("name"));
		this.nameField.setMaxLength(128);
		this.nameField.setResponder(text -> this.name = text);
		this.nameField.setValue(this.name);
		this.addRenderableWidget(this.nameField);
		this.addRenderableWidget(Button.builder(airLabel(), button -> {
			this.includeAir = !this.includeAir;
			button.setMessage(airLabel());
		}).bounds(this.width / 2 + NAME_X_OFFSET, NAME_Y + 24, NAME_WIDTH, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Save"), button -> save())
				.bounds(this.width / 2 - 105, this.height + BUTTON_Y_OFFSET, BUTTON_WIDTH, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose())
				.bounds(this.width / 2 + 5, this.height + BUTTON_Y_OFFSET, BUTTON_WIDTH, 20).build());
		this.setInitialFocus(this.nameField);
	}

	@Override
	public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context);
		context.drawCenteredString(this.font, this.title, this.width / 2, 6, 0xFFFFFF);
		context.drawString(this.font, this.directory,
				this.width / 2 + NAME_X_OFFSET, DIRECTORY_Y, MUTED_COLOR);
		context.drawString(this.font, QcSchematics.normaliseName(this.name) + ".nbt",
				this.width / 2 + NAME_X_OFFSET, FILE_Y, MUTED_COLOR);
		if (!this.footer.isEmpty()) {
			context.drawCenteredString(this.font, this.footer, this.width / 2, FOOTER_Y,
					this.footerError ? FOOTER_ERROR_COLOR : FOOTER_COLOR);
		}
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public void tick() {
		if (this.closeTicks > 0 && --this.closeTicks == 0) {
			onClose();
		}
	}

	private Component airLabel() {
		return Component.literal(this.includeAir ? "clearing: written as air" : "clearing: left out (sparse file)");
	}

	private void save() {
		QcResult problem = QcPlanService.validate(QcClientState.plan, QcClientState.palette);
		if (problem != null) {
			this.footer = problem.message();
			this.footerError = true;
			return;
		}
		PlanCompiler.Compiled compiled = PlanCompiler.compile(QcClientState.plan, QcClientState.palette, BlockPos.ZERO);
		if (!compiled.problems().isEmpty()) {
			this.footer = compiled.problems().get(0);
			this.footerError = true;
			return;
		}
		CompoundTag tag = StructureNbt.write(compiled.forExport(this.includeAir));
		if (tag == null) {
			this.footer = "the plan produced no blocks";
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
