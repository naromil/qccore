package com.qccore.client;

import com.qccore.core.QcResult;
import com.qccore.core.plan.PlanCompiler;
import com.qccore.world.QcPlanService;
import com.qccore.core.nbt.StructureNbt;
import java.nio.file.Path;
import net.minecraft.client.gui.GuiGraphics;
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
	private static final int PANEL_WIDTH = 240;
	private static final int PANEL_TOP = 26;
	private static final int PANEL_HEIGHT = 112;

	private EditBox nameField;
	private String name = "qc_layout";
	private String directory = "";
	private String footer = "";
	private boolean footerError;
	private int closeTicks = -1;
	private boolean includeAir;
	private final QcConfirm confirm = new QcConfirm();

	public QcSaveScreen() {
		super(Component.translatable("qccore.screen.save"));
	}

	@Override
	protected void init() {
		this.directory = QcSchematics.dir().toAbsolutePath().toString();
		int x = this.width / 2 - PANEL_WIDTH / 2;
		this.nameField = new EditBox(this.font, x + 6, PANEL_TOP + QcUi.HEADER_HEIGHT + 16, PANEL_WIDTH - 12, 18,
				Component.literal("name"));
		this.nameField.setMaxLength(128);
		this.nameField.setResponder(text -> this.name = text);
		this.nameField.setValue(this.name);
		this.addRenderableWidget(this.nameField);
		int half = (PANEL_WIDTH - 14) / 2;
		int y = PANEL_TOP + QcUi.HEADER_HEIGHT + 52;
		this.addRenderableWidget(new QcUi.Chip(x + 6, y, half, 16, "Sparse", () -> this.includeAir = false)
				.selected(() -> !this.includeAir)
				.tip("Leave cleared terrain out: only the building's blocks (what Create's schematicannon wants)"));
		this.addRenderableWidget(new QcUi.Chip(x + 8 + half, y, half, 16, "With clearing", () -> this.includeAir = true)
				.selected(() -> this.includeAir)
				.tip("Also write the cleared terrain and road headroom as air, so a paste carves the site"));
		int bottom = PANEL_TOP + PANEL_HEIGHT + 6;
		this.addRenderableWidget(new QcUi.Chip(x, bottom, PANEL_WIDTH / 2 - 2, 18, "Cancel", this::onClose).tip("Esc"));
		this.addRenderableWidget(new QcUi.Chip(x + PANEL_WIDTH / 2 + 2, bottom, PANEL_WIDTH / 2 - 2, 18, "Save .nbt", this::save)
				.accent(QcUi.APPLY).primary().tip("Write the file (Enter)"));
		this.setInitialFocus(this.nameField);
	}

	@Override
	public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context);
		QcUi.titleBar(context, this.font, this, null);
		int x = this.width / 2 - PANEL_WIDTH / 2;
		QcUi.panel(context, this.font, x, PANEL_TOP, PANEL_WIDTH, PANEL_HEIGHT, "File", QcUi.ACCENT);
		context.drawString(this.font, "name", x + 6, PANEL_TOP + QcUi.HEADER_HEIGHT + 5, QcUi.MUTED, false);
		context.drawString(this.font, "→ " + QcSchematics.normaliseName(this.name) + ".nbt",
				x + 6, PANEL_TOP + QcUi.HEADER_HEIGHT + 38, QcUi.DIM, false);
		context.drawString(this.font, "plan: " + QcUi.planSummary(), x + 6,
				PANEL_TOP + QcUi.HEADER_HEIGHT + 74, QcUi.MUTED, false);
		context.drawString(this.font, this.font.plainSubstrByWidth(this.directory, PANEL_WIDTH - 12), x + 6,
				PANEL_TOP + QcUi.HEADER_HEIGHT + 86, 0xFF4A5260, false);
		if (!this.footer.isEmpty()) {
			QcUi.status(context, this.font, this.footer, this.footerError ? QcUi.ERROR : QcUi.OK, this.width / 2,
					PANEL_TOP + PANEL_HEIGHT + 30);
		}
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
			save();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void tick() {
		if (this.closeTicks > 0 && --this.closeTicks == 0) {
			onClose();
		}
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
			this.footer = QcResult.first(compiled.problems());
			this.footerError = true;
			return;
		}
		CompoundTag tag = StructureNbt.write(compiled.forExport(this.includeAir));
		if (tag == null) {
			this.footer = "the plan produced no blocks";
			this.footerError = true;
			return;
		}
		String fileName = QcSchematics.normaliseName(this.name) + ".nbt";
		if (java.nio.file.Files.exists(QcSchematics.dir().resolve(fileName)) && !this.confirm.confirm("export:" + fileName)) {
			this.footer = fileName + " exists: save again to replace it";
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
