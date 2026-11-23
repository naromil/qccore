package com.qccore.client;

import com.qccore.client.QcUi.Chip;
import com.qccore.client.QcUi.Stepper;
import com.qccore.core.plan.BuildingSpec;
import com.qccore.core.qc.BlockChoice;
import com.qccore.core.qc.Design;
import com.qccore.core.qc.Facades;
import com.qccore.core.qc.Preview;
import com.qccore.core.qc.Roles;
import com.qccore.core.qc.Roofs;
import com.qccore.core.qc.Templates;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The design of the active building (or of the next new one): qckit's template and the geometry
 * overrides a {@code Plan.building(...)} call takes - roof, facades (masks or pieces, {@code a|b} for a
 * rhythm), piers, partition, doors, lights and the on/off parts - plus the name of the style that
 * holds its blocks. No blocks are chosen here; that is the block chooser's job.
 *
 * <p>When the screen has room, a 3D preview shows the design as a small two-storey building: the
 * blocks of its style where chosen, default blocks ({@link Preview}) elsewhere, only in the picture.
 * Drag it to turn it.
 *
 * <p>Every change to a planned building is an undoable plan edit; the next new building starts from
 * whatever was edited last.
 */
public class QcDesignScreen extends Screen {
	private static final int COLUMN = 206;
	private static final int ROW = 15;
	private static final int CHIP_H = 13;
	private static final String[] FACADE_KEYS = {"facade_ground", "facade", "facade_top", "facade_end", "facade_blind"};
	private static final String[] FACADE_LABELS = {"ground", "upper", "top", "run ends", "blind"};

	private final Screen parent;
	private final Map<String, EditBox> facadeFields = new LinkedHashMap<>();
	private EditBox styleField;
	private int left;
	private int right;
	private int top;
	private final QcSceneView view = new QcSceneView();
	private Object viewKey;
	/** Where the preview goes (x, y, w, h), or {@code null} when the screen has no room for it. */
	private int[] viewRect;

	public QcDesignScreen(Screen parent) {
		super(Component.literal("QC building design"));
		this.parent = parent;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private static BuildingSpec spec() {
		return QcClientState.editedDesign();
	}

	private static Design design() {
		return spec().design();
	}

	@Override
	protected void init() {
		commitFields();
		this.facadeFields.clear();
		int spare = this.width - 8 - 2 * COLUMN - 6;
		int previewW = spare >= 124 ? Math.min(220, spare - 4) : 0;
		this.left = (this.width - 2 * COLUMN - 6 - (previewW > 0 ? previewW + 4 : 0)) / 2;
		this.right = this.left + COLUMN + 6;
		this.top = 17 + Math.max(0, (this.height - 22 - 17 - 205) / 2);
		int below = this.height - 26 - (this.top + 2 * 92 + 6);
		this.viewRect = previewW > 0 ? new int[] {this.right + COLUMN + 4, this.top, previewW, 205}
				: below >= 60 ? new int[] {this.right, this.top + 2 * 92 + 6, COLUMN, below} : null;
		int inner = COLUMN - 8;
		int body = QcUi.HEADER_HEIGHT + 3;

		// 1. Templates
		int y = this.top + body;
		List<String> templates = Templates.names();
		int quarter = (inner - 6) / 4;
		for (int i = 0; i < templates.size(); i++) {
			String name = templates.get(i);
			this.addRenderableWidget(new Chip(this.left + 4 + (i % 4) * (quarter + 2), y + (i / 4) * ROW, quarter, CHIP_H, name,
					() -> change(() -> {
						if (name.equals(spec().template)) {
							return false;
						}
						spec().template = name;
						spec().options.clear();
						return true;
					})).selected(() -> name.equals(spec().template))
					.accent(name.equals(Templates.LEGACY) ? QcUi.WARN : QcUi.color(QcTool.UNITS))
					.tip(name.equals(Templates.LEGACY) ? "Legacy: QCSimple's nine components and the lattice-screen palette"
							: "qckit template " + name + ": " + Templates.options(name)));
		}

		// 2. Roof
		int roofTop = this.top + 92 + 3;
		y = roofTop + body;
		List<String> roofs = Roofs.names();
		for (int i = 0; i < roofs.size(); i++) {
			String roof = roofs.get(i);
			this.addRenderableWidget(optionChip(this.left + 4 + (i % 4) * (quarter + 2), y + (i / 4) * ROW, quarter, "roof", roof));
		}
		y += 2 * ROW;
		this.addRenderableWidget(new Stepper(this.left + 4 + inner - 48, y, 48, CHIP_H, "rise", 0, 16,
				() -> design().integer("roof_rise", 6), value -> set("roof_rise", String.valueOf(value)))
				.tip("Roof rise: the pitched roof stops rising after this many blocks"));
		this.addRenderableWidget(optionChip(this.left + 4, y, 30, "roof_axis", "x").tip("Gable ridge east-west"));
		this.addRenderableWidget(optionChip(this.left + 36, y, 30, "roof_axis", "z").tip("Gable ridge north-south"));
		this.addRenderableWidget(optionChip(this.left + 68, y, 34, "roof_axis", "auto").tip("Gable ridge along the longer side"));

		// 3. Style
		int styleTop = roofTop + 61 + 3;
		y = styleTop + body;
		this.styleField = new EditBox(this.font, this.left + 40, y, inner - 36, CHIP_H, Component.literal("style"));
		this.styleField.setMaxLength(48);
		this.styleField.setValue(spec().style == null ? "" : spec().style);
		this.styleField.setTooltip(QcUi.tip("The style holding this building's blocks; buildings sharing a style share their blocks"));
		this.addRenderableWidget(this.styleField);
		y += ROW;
		this.addRenderableWidget(new Chip(this.left + 4, y, (inner - 2) / 2, CHIP_H, "Reset to template", () -> change(() -> {
			if (spec().options.isEmpty()) {
				return false;
			}
			spec().options.clear();
			return true;
		})).tip("Drop this building's overrides"));
		this.addRenderableWidget(new Chip(this.left + 6 + (inner - 2) / 2, y, (inner - 2) / 2, CHIP_H, "Blocks…", () -> {
			commitFields();
			this.minecraft.setScreen(new QcBlockChooserScreen(this, spec().style));
		}).accent(QcUi.OK).tip("Choose the blocks of this style"));

		// 4. Facades
		y = this.top + body;
		for (int i = 0; i < FACADE_KEYS.length; i++) {
			EditBox field = new EditBox(this.font, this.right + 52, y + i * ROW, inner - 48, CHIP_H, Component.literal(FACADE_LABELS[i]));
			field.setMaxLength(96);
			String key = FACADE_KEYS[i];
			String value = design().get(key);
			field.setValue(value == null ? "" : value);
			field.setTooltip(QcUi.tip(key + ": a facade or piece, a|b for a rhythm\n" + String.join(", ", Facades.names())));
			this.facadeFields.put(key, field);
			this.addRenderableWidget(field);
		}

		// 5. Layout
		int layoutTop = this.top + 92 + 3;
		y = layoutTop + body;
		String[] piers = {"normal", "dense", "2x2", "thick"};
		for (int i = 0; i < piers.length; i++) {
			this.addRenderableWidget(optionChip(this.right + 52 + i * ((inner - 48) / 4), y, (inner - 48) / 4 - 2, "piers", piers[i]));
		}
		y += ROW;
		String[] partitions = {"open", "rooms", "walls"};
		for (int i = 0; i < partitions.length; i++) {
			this.addRenderableWidget(optionChip(this.right + 52 + i * ((inner - 48) / 3), y, (inner - 48) / 3 - 2, "partition", partitions[i]));
		}
		y += ROW;
		this.addRenderableWidget(optionChip(this.right + 52, y, 34, "doors", "one").tip("One door, mid-run on the first road face"));
		this.addRenderableWidget(optionChip(this.right + 88, y, 38, "doors", "every").tip("A door on every ground unit facing a road"));
		this.addRenderableWidget(optionChip(this.right + 130, y, 30, "door_width", "1").tip("A real door (the door role; unset = archway)"));
		this.addRenderableWidget(optionChip(this.right + 162, y, 38, "door_width", "3").tip("A 3-wide archway"));
		y += ROW;
		String[] lights = {"wall", "centre", "none"};
		for (int i = 0; i < lights.length; i++) {
			this.addRenderableWidget(optionChip(this.right + 52 + i * ((inner - 48) / 3), y, (inner - 48) / 3 - 2, "lights", lights[i]));
		}
		y += ROW;
		String[] toggles = {"row", "sills", "plinth", "stairs", "portal"};
		int fifth = (inner - 8) / 5;
		for (int i = 0; i < toggles.length; i++) {
			String key = toggles[i];
			this.addRenderableWidget(new Chip(this.right + 4 + i * (fifth + 2), y, fifth, CHIP_H, key,
					() -> set(key, design().flag(key) ? "false" : "true"))
					.selected(() -> design().flag(key)).accent(QcUi.OK).tip("Toggle " + key));
		}

		int bottom = this.height - 20;
		this.addRenderableWidget(new Chip(this.left, bottom, 70, 17, "Done", this::onClose).tip("Back (Esc)"));
	}

	private Chip optionChip(int x, int y, int w, String key, String value) {
		return new Chip(x, y, w, CHIP_H, value, () -> set(key, value))
				.selected(() -> value.equals(design().str(key, key.equals("roof_axis") ? "auto" : "")))
				.tip(key + " = " + value);
	}

	/** Sets one design option on the edited spec (an override of its template). */
	private void set(String key, String value) {
		change(() -> {
			if (value.equals(spec().options.get(key))) {
				return false;
			}
			spec().options.put(key, value);
			return true;
		});
	}

	/**
	 * One undoable edit of a planned building, or a plain change of the design new buildings start
	 * from; the text fields then show the result, so closing the screen does not write stale text back.
	 * Text typed but not yet committed is committed first, so a click elsewhere does not throw it away.
	 */
	private void change(BooleanSupplier action) {
		commitFields();
		if (QcClientState.activeBuilding() != null) {
			QcInteraction.editFromScreen(action);
			QcClientState.design = QcClientState.activeBuilding().copy();
		} else {
			action.getAsBoolean();
		}
		reloadFields();
	}

	private void reloadFields() {
		Design design = design();
		for (Map.Entry<String, EditBox> entry : this.facadeFields.entrySet()) {
			String value = design.get(entry.getKey());
			if (!entry.getValue().getValue().trim().equals(value == null ? "" : value)) {
				entry.getValue().setValue(value == null ? "" : value);
			}
		}
		if (this.styleField != null && spec().style != null && !this.styleField.getValue().trim().equals(spec().style)) {
			this.styleField.setValue(spec().style);
		}
	}

	/** Copies the facade fields and the style name into the spec. */
	private void commitFields() {
		if (this.styleField == null) {
			return;
		}
		Map<String, String> values = new LinkedHashMap<>();
		for (Map.Entry<String, EditBox> entry : this.facadeFields.entrySet()) {
			values.put(entry.getKey(), entry.getValue().getValue().trim());
		}
		String style = this.styleField.getValue().trim();
		BooleanSupplier action = () -> {
			boolean changed = false;
			BuildingSpec spec = spec();
			Design current = spec.design();
			for (Map.Entry<String, String> entry : values.entrySet()) {
				String now = current.get(entry.getKey());
				String typed = entry.getValue();
				if (!typed.equals(now == null ? "" : now)) {
					if (typed.isEmpty()) {
						spec.options.put(entry.getKey(), "none");
					} else {
						spec.options.put(entry.getKey(), typed);
					}
					changed = true;
				}
			}
			if (!style.isEmpty() && !style.equals(spec.style)) {
				spec.style = style;
				changed = true;
			}
			if (!Templates.LEGACY.equals(spec.template) && spec.style != null && !spec.style.isEmpty()
					&& QcClientState.activeBuilding() != null) {
				changed |= QcClientState.plan.styles().putIfAbsent(spec.style, new BlockChoice(Roles.BUILDING)) == null;
			}
			return changed;
		};
		if (QcClientState.activeBuilding() != null) {
			QcInteraction.editFromScreen(action);
			QcClientState.design = QcClientState.activeBuilding().copy();
		} else {
			action.getAsBoolean();
		}
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		this.renderBackground(g);
		BuildingSpec active = QcClientState.activeBuilding();
		QcUi.titleBar(g, this.font, this, active == null ? "the next new building" : "building " + QcClientState.building);
		int body = QcUi.HEADER_HEIGHT + 3;
		QcUi.panel(g, this.font, this.left, this.top, COLUMN, 92, "Template · " + spec().template, QcUi.color(QcTool.UNITS));
		int roofTop = this.top + 92 + 3;
		QcUi.panel(g, this.font, this.left, roofTop, COLUMN, 61, "Roof", QcUi.WARN);
		g.drawString(this.font, "ridge", this.left + 106, roofTop + body + 2 * ROW + 3, QcUi.DIM, false);
		int styleTop = roofTop + 61 + 3;
		QcUi.panel(g, this.font, this.left, styleTop, COLUMN, 46, "Style", QcUi.OK);
		g.drawString(this.font, "blocks", this.left + 6, styleTop + body + 3, QcUi.MUTED, false);
		QcUi.panel(g, this.font, this.right, this.top, COLUMN, 92, "Facades", QcUi.ACCENT);
		int y = this.top + body;
		for (int i = 0; i < FACADE_LABELS.length; i++) {
			g.drawString(this.font, FACADE_LABELS[i], this.right + 6, y + i * ROW + 3, QcUi.MUTED, false);
			EditBox field = this.facadeFields.get(FACADE_KEYS[i]);
			field.setTextColor(validFacades(field.getValue()) ? QcUi.TEXT : QcUi.ERROR);
		}
		int layoutTop = this.top + 92 + 3;
		QcUi.panel(g, this.font, this.right, layoutTop, COLUMN, 92, "Layout", QcUi.ACCENT);
		String[] labels = {"piers", "partition", "doors", "lights"};
		for (int i = 0; i < labels.length; i++) {
			g.drawString(this.font, labels[i], this.right + 6, layoutTop + body + i * ROW + 3, QcUi.MUTED, false);
		}
		renderPreview(g);
		List<String> problems = design().problems();
		if (!problems.isEmpty()) {
			QcUi.status(g, this.font, "⚠ " + problems.get(0), QcUi.ERROR, this.width / 2, this.height - 36);
		}
		super.render(g, mouseX, mouseY, delta);
	}

	/** The design as a small building: its style's blocks where chosen, default blocks elsewhere. */
	private void renderPreview(GuiGraphics g) {
		int[] r = this.viewRect;
		if (r == null) {
			return;
		}
		QcUi.panel(g, this.font, r[0], r[1], r[2], r[3], "Preview", QcUi.color(QcTool.UNITS));
		BuildingSpec spec = spec();
		BlockChoice style = QcClientState.plan.styles().get(spec.style);
		BlockChoice blocks = style != null && style.kind().equals(Roles.BUILDING) ? style : new BlockChoice(Roles.BUILDING);
		Object key = List.of(spec.template == null ? "" : spec.template, new HashMap<>(spec.options), new HashMap<>(blocks.blocks()));
		if (!key.equals(this.viewKey)) {
			this.viewKey = key;
			this.view.show(Templates.LEGACY.equals(spec.template) || !design().problems().isEmpty() ? null
					: Preview.withDefaults(Preview.sample(blocks, spec.template, spec.options)));
		}
		int innerTop = r[1] + QcUi.HEADER_HEIGHT + 3;
		int innerH = r[3] - QcUi.HEADER_HEIGHT - 16;
		if (this.view.isEmpty()) {
			String why = Templates.LEGACY.equals(spec.template) ? "no preview for the legacy template"
					: !design().problems().isEmpty() ? "fix the design to see it" : this.view.status();
			g.drawString(this.font, this.font.plainSubstrByWidth(why, r[2] - 10), r[0] + 5, innerTop + 2, QcUi.DIM, false);
			return;
		}
		this.view.render(g, r[0] + 3, innerTop, r[2] - 6, innerH);
		g.drawString(this.font, this.font.plainSubstrByWidth("default blocks where unset · drag to turn", r[2] - 8),
				r[0] + 4, r[1] + r[3] - 11, QcUi.MUTED, false);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
		int[] r = this.viewRect;
		if (r != null && mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3]) {
			this.view.drag(dx);
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, dx, dy);
	}

	private static boolean validFacades(String text) {
		if (text.isBlank() || text.trim().equals("none")) {
			return true;
		}
		for (String name : text.split("\\|")) {
			if (!Facades.exists(name.trim())) {
				return false;
			}
		}
		return true;
	}

	@Override
	public void onClose() {
		commitFields();
		this.minecraft.setScreen(this.parent);
	}

	@Override
	public void removed() {
		commitFields();
	}
}
