package com.qccore.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.nio.file.Path;
import java.util.List;

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
	private ButtonWidget pick;
	private TextFieldWidget nameField;
	private String footer = "";
	private boolean footerError;

	public QcTemplateScreen(Screen parent) {
		super(Text.translatable("qccore.screen.templates"));
		this.parent = parent;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	protected void init() {
		this.names = QcTemplates.list();
		int index = this.names.indexOf(QcClientState.templateName);
		this.selected = Math.max(0, index);
		int x = this.width / 2 - 150;
		int y = 40;
		this.addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> step(-1)).dimensions(x, y, 20, 20).build());
		this.pick = this.addDrawableChild(ButtonWidget.builder(Text.empty(), b -> step(1)).dimensions(x + 24, y, 252, 20).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> step(1)).dimensions(x + 280, y, 20, 20).build());
		y += 24;
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Use for template tool"), b -> use(false))
				.dimensions(x, y, 148, 20).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Use + adopt its palette"), b -> use(true))
				.dimensions(x + 152, y, 148, 20).build());
		y += 24;
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Delete selected"), b -> delete())
				.dimensions(x, y, 148, 20).build());
		y += 44;
		this.nameField = new TextFieldWidget(this.textRenderer, x, y, 196, 20, Text.literal("name"));
		this.nameField.setMaxLength(64);
		this.nameField.setText(QcClientState.templateName.isEmpty() ? "qc_template" : QcClientState.templateName);
		this.addDrawableChild(this.nameField);
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Save plan as template"), b -> save())
				.dimensions(x + 200, y, 100, 20).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> this.client.setScreen(this.parent))
				.dimensions(this.width / 2 - 50, this.height - 28, 100, 20).build());
		refresh();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context);
		context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, TEXT_COLOR);
		context.drawCenteredTextWithShadow(this.textRenderer, QcTemplates.dir().toAbsolutePath().toString(),
				this.width / 2, 24, MUTED_COLOR);
		context.drawTextWithShadow(this.textRenderer, "saves units, flags, component masks, roads and cleared cells, plus the palette",
				this.width / 2 - 150, 124, MUTED_COLOR);
		context.drawTextWithShadow(this.textRenderer, "stamp with the template tool; R turns it a quarter, the modifiers repeat/mirror it",
				this.width / 2 - 150, 176, MUTED_COLOR);
		if (!this.footer.isEmpty()) {
			context.drawCenteredTextWithShadow(this.textRenderer, this.footer, this.width / 2, this.height - 44,
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
		this.pick.setMessage(Text.literal(this.names.isEmpty() ? "no templates yet"
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
			Path file = QcTemplates.save(this.nameField.getText(), QcClientState.plan, QcClientState.palette);
			this.footer = "saved " + file.getFileName();
			this.footerError = false;
			this.names = QcTemplates.list();
			String saved = QcSchematics.normaliseName(this.nameField.getText());
			this.selected = Math.max(0, this.names.indexOf(saved));
			refresh();
		} catch (RuntimeException e) {
			this.footer = "could not save: " + e.getMessage();
			this.footerError = true;
		}
	}
}
