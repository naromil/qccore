package com.qccore.client;

import com.qccore.client.QcUi.Chip;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * QC unit templates: pick one to stamp with the template tool, save the current plan as a template,
 * adopt the palette a template was designed with, or delete one. The templates are a clickable
 * list (scroll with the wheel, double-click to use at once); the actions act on the selected one.
 */
public class QcTemplateScreen extends Screen {
	private static final int COLUMN = 204;
	private static final int TOP = 22;
	private static final int ROW = 16;

	private final Screen parent;
	private final List<Chip> rows = new ArrayList<>();
	private List<String> names = List.of();
	private int selected;
	private int scroll;
	private int visibleRows;
	private EditBox nameField;
	private String typedName;
	private String footer = "";
	private boolean footerError;
	private long lastClick;
	private int lastClicked = -1;
	private int left;
	private int right;

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
		if (this.typedName == null) {
			int index = this.names.indexOf(QcClientState.templateName);
			this.selected = Math.max(0, index);
		}
		this.selected = Math.max(0, Math.min(this.selected, this.names.size() - 1));
		this.left = this.width / 2 - COLUMN - 3;
		this.right = this.width / 2 + 3;
		int listTop = TOP + QcUi.HEADER_HEIGHT + 4;
		this.visibleRows = Math.max(1, (this.height - 30 - listTop - 4) / ROW);
		this.scroll = Math.max(0, Math.min(this.scroll, this.names.size() - this.visibleRows));
		if (this.selected < this.scroll) {
			this.scroll = this.selected;
		} else if (this.selected >= this.scroll + this.visibleRows) {
			this.scroll = this.selected - this.visibleRows + 1;
		}
		this.rows.clear();
		for (int i = 0; i < Math.min(this.visibleRows, this.names.size() - this.scroll); i++) {
			int index = this.scroll + i;
			String name = this.names.get(index);
			Chip row = new Chip(this.left + 4, listTop + i * ROW, COLUMN - 14, ROW - 2, name, () -> click(index))
					.selected(() -> this.selected == index).accent(QcUi.color(QcTool.TEMPLATE))
					.label(() -> name + (name.equals(QcClientState.templateName) ? "  ✔" : ""));
			this.rows.add(this.addRenderableWidget(row));
		}

		int y = TOP + QcUi.HEADER_HEIGHT + 18;
		int inner = COLUMN - 8;
		int half = (inner - 2) / 2;
		this.addRenderableWidget(new Chip(this.right + 4, y, inner, 18, "Use for the template tool", () -> use(false))
				.icon(() -> new ItemStack(Items.STRUCTURE_BLOCK), false).accent(QcUi.color(QcTool.TEMPLATE))
				.tip("Switch to the template tool with this template (double-click a row does the same)"));
		y += 20;
		this.addRenderableWidget(new Chip(this.right + 4, y, half, ROW - 2, "Use + palette", () -> use(true))
				.tip("Also adopt the nine components the template was saved with"));
		this.addRenderableWidget(new Chip(this.right + 6 + half, y, half, ROW - 2, "Delete", this::delete)
				.accent(QcUi.ERROR).tip("Delete the template file"));

		int saveTop = TOP + 70;
		y = saveTop + QcUi.HEADER_HEIGHT + 4;
		this.nameField = new EditBox(this.font, this.right + 4, y, inner, ROW, Component.literal("name"));
		this.nameField.setMaxLength(64);
		this.nameField.setValue(this.typedName != null ? this.typedName
				: QcClientState.templateName.isEmpty() ? "qc_template" : QcClientState.templateName);
		this.nameField.setResponder(text -> this.typedName = text);
		this.addRenderableWidget(this.nameField);
		y += ROW + 2;
		this.addRenderableWidget(new Chip(this.right + 4, y, inner, ROW, "Save plan as template", this::save)
				.accent(QcUi.APPLY).tip("Saves units, flags, masks, roads, clears and the palette"));

		this.addRenderableWidget(new Chip(this.right + COLUMN - 100, this.height - 22, 100, 18, "Done",
				() -> this.minecraft.setScreen(this.parent)).tip("Back to the tools (Esc)"));
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		this.renderBackground(g);
		QcUi.titleBar(g, this.font, this, null);
		int listHeight = QcUi.HEADER_HEIGHT + 8 + this.visibleRows * ROW;
		QcUi.panel(g, this.font, this.left, TOP, COLUMN, listHeight, "Templates · " + this.names.size(),
				QcUi.color(QcTool.TEMPLATE));
		if (this.names.isEmpty()) {
			g.drawString(this.font, "no templates yet", this.left + 8, TOP + QcUi.HEADER_HEIGHT + 6, QcUi.DIM, false);
			g.drawString(this.font, "plan something, then save it →", this.left + 8, TOP + QcUi.HEADER_HEIGHT + 18, QcUi.DIM, false);
		} else if (this.names.size() > this.visibleRows) {
			int trackTop = TOP + QcUi.HEADER_HEIGHT + 4;
			int trackHeight = this.visibleRows * ROW - 2;
			int thumb = Math.max(8, trackHeight * this.visibleRows / this.names.size());
			int thumbTop = trackTop + (trackHeight - thumb) * this.scroll / Math.max(1, this.names.size() - this.visibleRows);
			g.fill(this.left + COLUMN - 7, trackTop, this.left + COLUMN - 4, trackTop + trackHeight, 0xFF1E252F);
			g.fill(this.left + COLUMN - 7, thumbTop, this.left + COLUMN - 4, thumbTop + thumb, QcUi.MUTED);
		}

		QcUi.panel(g, this.font, this.right, TOP, COLUMN, 66, "Selected", QcUi.ACCENT);
		String current = this.names.isEmpty() ? "—" : this.names.get(this.selected);
		g.drawString(this.font, this.font.plainSubstrByWidth(current, COLUMN - 12), this.right + 6,
				TOP + QcUi.HEADER_HEIGHT + 4, QcUi.TEXT, false);
		int saveTop = TOP + 70;
		QcUi.panel(g, this.font, this.right, saveTop, COLUMN, 58, "Save the current plan", QcUi.APPLY);
		int hintY = saveTop + 62;
		g.drawString(this.font, QcClientState.plan.isEmpty() ? "the plan is empty" : "plan: " + QcUi.planSummary(),
				this.right + 2, hintY, QcUi.DIM, false);
		g.drawString(this.font, "R turns a template a quarter;", this.right + 2, hintY + 12, QcUi.DIM, false);
		g.drawString(this.font, "array and mirror repeat it", this.right + 2, hintY + 22, QcUi.DIM, false);
		g.drawString(this.font, this.font.plainSubstrByWidth(QcTemplates.dir().toAbsolutePath().toString(), COLUMN),
				this.right + 2, hintY + 36, 0xFF4A5260, false);
		if (!this.footer.isEmpty()) {
			QcUi.status(g, this.font, this.footer, this.footerError ? QcUi.ERROR : QcUi.OK, this.width / 2, this.height - 40);
		}
		super.render(g, mouseX, mouseY, delta);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
		if (mouseX < this.width / 2 && this.names.size() > this.visibleRows) {
			int next = Math.max(0, Math.min(this.names.size() - this.visibleRows, this.scroll - (int) Math.signum(delta)));
			if (next != this.scroll) {
				this.scroll = next;
				rebuild();
			}
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, delta);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (!this.nameField.isFocused() && !this.names.isEmpty()) {
			if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_UP) {
				int step = keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN ? 1 : -1;
				this.selected = Math.floorMod(this.selected + step, this.names.size());
				rebuild();
				return true;
			}
			if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
				use(false);
				return true;
			}
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	private void click(int index) {
		long now = System.currentTimeMillis();
		boolean doubleClick = index == this.lastClicked && now - this.lastClick < 350;
		this.lastClick = now;
		this.lastClicked = index;
		this.selected = index;
		if (doubleClick) {
			use(false);
		}
	}

	/** Re-lays the widgets (the list rows depend on the scroll position), keeping the typed name. */
	private void rebuild() {
		if (this.nameField != null) {
			this.typedName = this.nameField.getValue();
		}
		this.rebuildWidgets();
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
		rebuild();
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
			rebuild();
		} catch (RuntimeException e) {
			this.footer = "could not save: " + e.getMessage();
			this.footerError = true;
		}
	}
}
