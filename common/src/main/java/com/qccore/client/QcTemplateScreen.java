package com.qccore.client;

import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * QC unit templates: pick one to stamp with the template tool, save the current plan as a template,
 * adopt the palette a template was designed with, or delete one.
 */
public class QcTemplateScreen extends Screen {
	private static final int TEXT_COLOR = 0xFFFFFF;
	private static final int MUTED_COLOR = 0x808080;
	private static final int ERROR_COLOR = 0xFF5555;

	private final Screen parent;
	private List<String> names = List.of();
	private int selected;
	private Button pick;
	private EditBox nameField;
	private String footer = "";
	private boolean footerError;

	public QcTemplateScreen(Screen parent) {
		super(Component.translatable("qccore.screen.templates"));
		this.parent = parent;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		this.names = QcTemplates.list();
		int index = this.names.indexOf(QcClientState.templateName);
		this.selected = Math.max(0, index);
		int x = this.width / 2 - 150;
		int y = 40;
		this.addRenderableWidget(Button.builder(Component.literal("<"), b -> step(-1)).bounds(x, y, 20, 20).build());
		this.pick = this.addRenderableWidget(Button.builder(Component.empty(), b -> step(1)).bounds(x + 24, y, 252, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal(">"), b -> step(1)).bounds(x + 280, y, 20, 20).build());
		y += 24;
		this.addRenderableWidget(Button.builder(Component.literal("Use for template tool"), b -> use(false))
				.bounds(x, y, 148, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Use + adopt its palette"), b -> use(true))
				.bounds(x + 152, y, 148, 20).build());
		y += 24;
		this.addRenderableWidget(Button.builder(Component.literal("Delete selected"), b -> delete())
				.bounds(x, y, 148, 20).build());
		y += 44;
		this.nameField = new EditBox(this.font, x, y, 176, 20, Component.literal("name"));
		this.nameField.setMaxLength(64);
		this.nameField.setValue(QcClientState.templateName.isEmpty() ? "qc_template" : QcClientState.templateName);
		this.addRenderableWidget(this.nameField);
		this.addRenderableWidget(Button.builder(Component.literal("Save plan as template"), b -> save())
				.bounds(x + 180, y, 120, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.minecraft.setScreen(this.parent))
				.bounds(this.width / 2 - 50, this.height - 28, 100, 20).build());
		refresh();
	}

	@Override
	public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context);
		context.drawCenteredString(this.font, this.title, this.width / 2, 8, TEXT_COLOR);
		context.drawCenteredString(this.font, QcTemplates.dir().toAbsolutePath().toString(),
				this.width / 2, 24, MUTED_COLOR);
		context.drawString(this.font, "saves units, flags, masks, roads, clears + the palette",
				this.width / 2 - 150, 124, MUTED_COLOR);
		context.drawString(this.font, "R turns it a quarter; array and mirror repeat it",
				this.width / 2 - 150, 176, MUTED_COLOR);
		if (!this.footer.isEmpty()) {
			context.drawCenteredString(this.font, this.footer, this.width / 2, this.height - 44,
					this.footerError ? ERROR_COLOR : TEXT_COLOR);
		}
		super.render(context, mouseX, mouseY, delta);
	}

	private void step(int delta) {
		if (!this.names.isEmpty()) {
			this.selected = Math.floorMod(this.selected + delta, this.names.size());
		}
		refresh();
	}

	private void refresh() {
		this.pick.setMessage(Component.literal(this.names.isEmpty() ? "no templates yet"
				: this.names.get(this.selected) + "  (" + (this.selected + 1) + "/" + this.names.size() + ")"));
	}

	private void use(boolean adoptPalette) {
		if (this.names.isEmpty()) {
			return;
		}
		String name = this.names.get(this.selected);
		try {
			QcTemplates.Template template = QcTemplates.load(name);
			QcClientState.template = template.plan();
			QcClientState.templateName = name;
			QcClientState.templateRotation = 0;
			QcClientState.tool = QcTool.TEMPLATE;
			QcClientState.points.clear();
			if (adoptPalette && template.palette() != null) {
				QcTemplates.adoptPalette(template.palette());
				QcClientState.changed();
			}
			this.footer = "template tool: " + name + " (" + template.plan().summary() + ")";
			this.footerError = false;
		} catch (RuntimeException e) {
			this.footer = "could not load " + name + ": " + e.getMessage();
			this.footerError = true;
		}
	}

	private void delete() {
		if (this.names.isEmpty()) {
			return;
		}
		String name = this.names.get(this.selected);
		try {
			QcTemplates.delete(name);
			if (name.equals(QcClientState.templateName)) {
				QcClientState.template = null;
				QcClientState.templateName = "";
			}
			this.footer = "deleted " + name;
			this.footerError = false;
		} catch (RuntimeException e) {
			this.footer = "could not delete " + name + ": " + e.getMessage();
			this.footerError = true;
		}
		this.names = QcTemplates.list();
		this.selected = Math.min(this.selected, Math.max(0, this.names.size() - 1));
		refresh();
	}

	private void save() {
		if (QcClientState.plan.isEmpty()) {
			this.footer = "the plan is empty";
			this.footerError = true;
			return;
		}
		try {
			Path file = QcTemplates.save(this.nameField.getValue(), QcClientState.plan, QcClientState.palette);
			this.footer = "saved " + file.getFileName();
			this.footerError = false;
			this.names = QcTemplates.list();
			String saved = QcSchematics.normaliseName(this.nameField.getValue());
			this.selected = Math.max(0, this.names.indexOf(saved));
			refresh();
		} catch (RuntimeException e) {
			this.footer = "could not save: " + e.getMessage();
			this.footerError = true;
		}
	}
}
