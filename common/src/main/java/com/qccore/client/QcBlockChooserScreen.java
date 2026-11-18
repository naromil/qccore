package com.qccore.client;

import com.qccore.client.QcUi.Chip;
import com.qccore.core.plan.BlockIds;
import com.qccore.core.qc.BlockChoice;
import com.qccore.core.qc.Role;
import com.qccore.core.qc.Roles;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

/**
 * The block chooser: the plan's styles on the left, the roles of the selected style on the right,
 * one block per role. A role can be typed (an id, states allowed: {@code oak_log[axis=x]}; a road
 * post takes ids bottom-up, comma separated), picked from a searchable grid, or taken from the held
 * item. By default only the roles the plan's buildings and roads actually reach are listed, the
 * ones still needing a block first; unset optional roles leave their part out, unset roles with a
 * fallback repeat the role they fall back to.
 *
 * <p>Kits save the selected style as a file and load a file into it. Every change is one undoable
 * plan edit.
 */
public class QcBlockChooserScreen extends Screen {
	private static final int ROW = 15;
	private static final int CHIP_H = 13;
	private static final int LEFT_W = 132;

	private final Screen parent;
	private String style;
	private BlockChoice work;
	private boolean allRoles;
	private int page;
	private int stylePage;
	private int kit;
	private final Map<String, EditBox> fields = new LinkedHashMap<>();
	private EditBox newStyle;
	private EditBox kitName;
	private String footer = "";
	private boolean footerError;
	private int left;
	private int right;
	private int rightW;
	private int top;
	private int rowsPerPage;

	public QcBlockChooserScreen(Screen parent) {
		super(Component.literal("QC block chooser"));
		this.parent = parent;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		storeFields();
		this.fields.clear();
		Map<String, BlockChoice> styles = QcClientState.plan.styles();
		if (this.style == null || !styles.containsKey(this.style)) {
			String wanted = QcClientState.chooserStyle != null ? QcClientState.chooserStyle : QcClientState.editedDesign().style;
			this.style = styles.containsKey(wanted) ? wanted : styles.isEmpty() ? null : firstNeedingBlocks();
			this.work = null;
		}
		if (this.work == null && this.style != null) {
			this.work = styles.get(this.style).copy();
		}
		QcClientState.chooserStyle = this.style;
		int total = Math.min(this.width - 8, 420);
		this.left = (this.width - total) / 2;
		this.right = this.left + LEFT_W + 4;
		this.rightW = total - LEFT_W - 4;
		this.top = 19;
		int bottom = this.height - 24;
		int body = QcUi.HEADER_HEIGHT + 3;

		// 1. Styles
		List<String> names = new ArrayList<>(styles.keySet());
		int styleRows = Math.max(1, (bottom - 92 - this.top - body - ROW) / ROW);
		this.stylePage = Math.max(0, Math.min(this.stylePage, (names.size() - 1) / styleRows));
		int y = this.top + body;
		for (int i = this.stylePage * styleRows; i < names.size() && i < (this.stylePage + 1) * styleRows; i++) {
			String name = names.get(i);
			this.addRenderableWidget(new Chip(this.left + 4, y, LEFT_W - 8, CHIP_H, "", () -> select(name))
					.label(() -> styleLabel(name)).selected(() -> name.equals(this.style))
					.accent(styles.get(name).kind().equals(Roles.ROAD) ? QcUi.color(QcTool.ROAD)
							: styles.get(name).kind().equals(Roles.DECOR) ? QcUi.WARN : QcUi.color(QcTool.UNITS))
					.tip(styles.get(name).kind() + " style " + name));
			y += ROW;
		}
		if (names.size() > styleRows) {
			this.addRenderableWidget(new Chip(this.left + 4, y, 30, CHIP_H, "‹", () -> {
				this.stylePage = Math.max(0, this.stylePage - 1);
				rebuild();
			}));
			this.addRenderableWidget(new Chip(this.left + 36, y, 30, CHIP_H, "›", () -> {
				this.stylePage++;
				rebuild();
			}));
		}
		int newTop = bottom - 92;
		y = newTop + body;
		this.newStyle = new EditBox(this.font, this.left + 4, y, LEFT_W - 8, CHIP_H, Component.literal("new style"));
		this.newStyle.setMaxLength(48);
		this.newStyle.setHint(Component.literal("new style name").withStyle(st -> st.withColor(0x667080)));
		this.addRenderableWidget(this.newStyle);
		y += ROW;
		int third = (LEFT_W - 12) / 3;
		this.addRenderableWidget(new Chip(this.left + 4, y, third, CHIP_H, "+ bldg", () -> create(Roles.BUILDING))
				.tip("Add an empty building style"));
		this.addRenderableWidget(new Chip(this.left + 6 + third, y, third, CHIP_H, "+ road", () -> create(Roles.ROAD))
				.accent(QcUi.color(QcTool.ROAD)).tip("Add an empty road style and make it the road tool's"));
		this.addRenderableWidget(new Chip(this.left + 8 + 2 * third, y, third, CHIP_H, "+ decor", () -> create(Roles.DECOR))
				.accent(QcUi.WARN).tip("Add an empty decor style and dress the plan's streets with it"));

		// 2. Kits
		int kitTop = newTop + 46;
		y = kitTop + body;
		List<String> kits = QcKits.list();
		this.kit = kits.isEmpty() ? 0 : Math.floorMod(this.kit, kits.size());
		this.addRenderableWidget(new Chip(this.left + 4, y, LEFT_W - 8 - 40, CHIP_H, "", () -> {
			this.kit++;
			rebuild();
		}).label(() -> kits.isEmpty() ? "no kits saved" : "kit: " + kits.get(Math.floorMod(this.kit, kits.size())) + " ›")
				.tip("Next kit in qccore/kits"));
		this.addRenderableWidget(new Chip(this.left + LEFT_W - 42, y, 38, CHIP_H, "Load", () -> loadKit(kits))
				.tip("Load the kit's blocks into the selected style"));
		y += ROW;
		this.kitName = new EditBox(this.font, this.left + 4, y, LEFT_W - 8 - 40, CHIP_H, Component.literal("kit name"));
		this.kitName.setMaxLength(48);
		this.kitName.setValue(this.style == null ? "" : this.style);
		this.addRenderableWidget(this.kitName);
		this.addRenderableWidget(new Chip(this.left + LEFT_W - 42, y, 38, CHIP_H, "Save", this::saveKit).accent(QcUi.APPLY)
				.tip("Save the selected style's blocks as a kit"));

		// 3. Roles of the selected style
		this.rowsPerPage = Math.max(1, (bottom - this.top - body - ROW - 4) / ROW);
		if (this.work != null) {
			List<Role> roles = visibleRoles();
			this.page = Math.max(0, Math.min(this.page, (roles.size() - 1) / this.rowsPerPage));
			y = this.top + body;
			int fieldX = this.right + 84;
			int fieldW = this.rightW - 84 - 4 - 2 * 18;
			for (int i = this.page * this.rowsPerPage; i < roles.size() && i < (this.page + 1) * this.rowsPerPage; i++) {
				Role role = roles.get(i);
				EditBox field = new EditBox(this.font, fieldX + 18, y, fieldW - 18, CHIP_H, Component.literal(role.name()));
				field.setMaxLength(160);
				String own = this.work.get(role.name());
				field.setValue(own == null ? "" : own);
				String fallback = role.fallback() == null ? null : this.work.resolve(role.name());
				if (fallback != null && own == null) {
					field.setHint(Component.literal("= " + fallback).withStyle(st -> st.withColor(0x667080)));
				} else if (role.optional()) {
					field.setHint(Component.literal("off").withStyle(st -> st.withColor(0x667080)));
				}
				field.setTooltip(QcUi.tip(role.name() + ": " + role.about()
						+ (role.optional() ? "\n(optional: empty leaves it out)" : "")
						+ (role.fallback() != null ? "\n(empty repeats " + role.fallback().replace("|", " or ") + ")" : "")));
				field.setResponder(text -> this.work.set(role.name(), text));
				this.fields.put(role.name(), field);
				this.addRenderableWidget(field);
				this.addRenderableWidget(new Chip(fieldX + fieldW + 2, y, 16, CHIP_H, "⌕", () -> {
					storeFields();
					this.minecraft.setScreen(new QcBlockPickerScreen(this, role.name(), role.hint(), id -> choose(role.name(), id)));
				}).tip("Pick from all blocks"));
				this.addRenderableWidget(new Chip(fieldX + fieldW + 20, y, 16, CHIP_H, "✋", () -> {
					String held = heldBlock();
					if (held == null) {
						this.footer = "hold a block item in the main hand";
						this.footerError = true;
						return;
					}
					choose(role.name(), held);
				}).tip("Use the block in your main hand"));
				y += ROW;
			}
			int pages = Math.max(1, (roles.size() + this.rowsPerPage - 1) / this.rowsPerPage);
			int navY = bottom - ROW + 2;
			if (this.work.kind().equals(Roles.DECOR)) {
				decorControls(navY);
			} else {
				this.addRenderableWidget(new Chip(this.right + 4, navY, 72, CHIP_H, "", () -> {
					storeFields();
					this.allRoles = !this.allRoles;
					this.page = 0;
					rebuild();
				}).label(() -> this.allRoles ? "all roles" : "used roles").selected(() -> this.allRoles)
						.tip("Show every role of this kind, or only those the plan's buildings and roads reach"));
			}
			if (pages > 1) {
				this.addRenderableWidget(new Chip(this.right + this.rightW - 66, navY, 30, CHIP_H, "‹", () -> {
					storeFields();
					this.page = Math.max(0, this.page - 1);
					rebuild();
				}));
				this.addRenderableWidget(new Chip(this.right + this.rightW - 34, navY, 30, CHIP_H, "›", () -> {
					storeFields();
					this.page++;
					rebuild();
				}));
			}
		}

		this.addRenderableWidget(new Chip(this.left, this.height - 20, 70, 17, "Done", this::onClose).tip("Back (Esc)"));
	}

	/** Sets one role (its field too, so committing the fields cannot undo the choice) as one plan edit. */
	private void choose(String role, String id) {
		EditBox field = this.fields.get(role);
		if (field != null) {
			field.setValue(id);
		}
		this.work.set(role, id);
		commit();
		rebuild();
	}

	private void rebuild() {
		this.rebuildWidgets();
	}

	/** For a decor style: whether it dresses the plan's streets, how densely, and the seed. */
	private void decorControls(int navY) {
		String name = this.style;
		this.addRenderableWidget(new Chip(this.right + 4, navY, 64, CHIP_H, "", () -> {
			storeFields();
			commit();
			QcInteraction.editFromScreen(() -> {
				QcClientState.plan.decorStyle = name.equals(QcClientState.plan.decorStyle) ? "" : name;
				return true;
			});
		}).label(() -> name.equals(QcClientState.plan.decorStyle) ? "decor on" : "decor off")
				.selected(() -> name.equals(QcClientState.plan.decorStyle)).accent(QcUi.WARN)
				.tip("Dress the plan's streets with this style (one decor style at a time)"));
		this.addRenderableWidget(new QcUi.Stepper(this.right + 70, navY, 58, CHIP_H, "density", 0, 100,
				() -> QcClientState.plan.decorDensity, value -> QcInteraction.editFromScreen(() -> {
					QcClientState.plan.decorDensity = value;
					return true;
				})).tip("How much decor, 0-100"));
		this.addRenderableWidget(new QcUi.Stepper(this.right + 130, navY, 58, CHIP_H, "seed", -999, 999,
				() -> QcClientState.plan.decorSeed, value -> QcInteraction.editFromScreen(() -> {
					QcClientState.plan.decorSeed = value;
					return true;
				})).tip("Another seed, another arrangement of the same props"));
	}

	/** The roles to list: reached ones (unset required first) or every role of the kind. */
	private List<Role> visibleRoles() {
		Set<String> reached = QcBlockPreview.reached().get(this.style);
		Set<String> missing = QcBlockPreview.missing().get(this.style);
		List<Role> first = new ArrayList<>();
		List<Role> rest = new ArrayList<>();
		for (Role role : Roles.of(this.work.kind())) {
			boolean used = reached != null && reached.contains(role.name());
			if (!this.allRoles && reached != null && !used) {
				continue;
			}
			if (missing != null && missing.contains(role.name())) {
				first.add(role);
			} else {
				rest.add(role);
			}
		}
		first.addAll(rest);
		return first;
	}

	private String firstNeedingBlocks() {
		for (String name : QcClientState.plan.styles().keySet()) {
			Set<String> missing = QcBlockPreview.missing().get(name);
			if (missing != null && !missing.isEmpty()) {
				return name;
			}
		}
		return QcClientState.plan.styles().keySet().iterator().next();
	}

	private String styleLabel(String name) {
		Set<String> missing = QcBlockPreview.missing().get(name);
		int count = missing == null ? 0 : missing.size();
		return (count == 0 ? "✔ " : "✖" + count + " ") + name;
	}

	private void select(String name) {
		storeFields();
		commit();
		this.style = name;
		this.work = QcClientState.plan.styles().get(name).copy();
		this.page = 0;
		this.footer = "";
		rebuild();
	}

	private void create(String kind) {
		String name = this.newStyle.getValue().trim();
		if (name.isEmpty()) {
			this.footer = "type a name for the new style";
			this.footerError = true;
			return;
		}
		storeFields();
		commit();
		QcInteraction.editFromScreen(() -> QcClientState.plan.styles().putIfAbsent(name, new BlockChoice(kind)) == null);
		if (kind.equals(Roles.ROAD)) {
			QcClientState.roadStyle = name;
		}
		if (kind.equals(Roles.DECOR)) {
			QcInteraction.editFromScreen(() -> {
				QcClientState.plan.decorStyle = name;
				return true;
			});
		}
		this.style = name;
		this.work = QcClientState.plan.styles().get(name).copy();
		this.allRoles = true;
		rebuild();
	}

	private void loadKit(List<String> kits) {
		if (kits.isEmpty() || this.work == null) {
			return;
		}
		String name = kits.get(Math.floorMod(this.kit, kits.size()));
		try {
			BlockChoice loaded = QcKits.load(name);
			if (!loaded.kind().equals(this.work.kind())) {
				this.footer = "kit " + name + " is a " + loaded.kind() + " kit; the style is a " + this.work.kind() + " style";
				this.footerError = true;
				return;
			}
			this.work = loaded;
			this.fields.clear();
			commit();
			this.footer = "loaded kit " + name + " into " + this.style;
			this.footerError = false;
			rebuild();
		} catch (RuntimeException e) {
			this.footer = "could not load " + name + ": " + e.getMessage();
			this.footerError = true;
		}
	}

	private void saveKit() {
		if (this.work == null) {
			return;
		}
		storeFields();
		commit();
		try {
			this.footer = "saved " + QcKits.save(this.kitName.getValue(), this.work).getFileName();
			this.footerError = false;
			rebuild();
		} catch (RuntimeException e) {
			this.footer = "could not save the kit: " + e.getMessage();
			this.footerError = true;
		}
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		this.renderBackground(g);
		QcUi.titleBar(g, this.font, this, "type an id, ⌕ search, ✋ held block");
		int bottom = this.height - 24;
		int body = QcUi.HEADER_HEIGHT + 3;
		QcUi.panel(g, this.font, this.left, this.top, LEFT_W, bottom - 92 - this.top - 3, "Styles", QcUi.ACCENT);
		QcUi.panel(g, this.font, this.left, bottom - 92, LEFT_W, 43, "New style", QcUi.ACCENT);
		QcUi.panel(g, this.font, this.left, bottom - 46, LEFT_W, 46, "Kits", QcUi.APPLY);
		if (this.work == null) {
			QcUi.panel(g, this.font, this.right, this.top, this.rightW, bottom - this.top, "Roles", QcUi.OK);
			g.drawString(this.font, "The plan has no styles yet: place a building or a road,", this.right + 6,
					this.top + body + 4, QcUi.MUTED, false);
			g.drawString(this.font, "or add a style on the left.", this.right + 6, this.top + body + 16, QcUi.MUTED, false);
		} else {
			QcUi.panel(g, this.font, this.right, this.top, this.rightW, bottom - this.top,
					this.work.kind() + " style · " + this.style, QcUi.OK);
			Set<String> missing = QcBlockPreview.missing().get(this.style);
			for (Map.Entry<String, EditBox> entry : this.fields.entrySet()) {
				EditBox field = entry.getValue();
				String role = entry.getKey();
				boolean need = missing != null && missing.contains(role) && this.work.resolve(role) == null;
				g.drawString(this.font, this.font.plainSubstrByWidth(role, 80), this.right + 6, field.getY() + 3,
						need ? QcUi.ERROR : this.work.get(role) != null ? QcUi.TEXT : QcUi.MUTED, false);
				String text = field.getValue().trim();
				field.setTextColor(text.isEmpty() || valid(role, text) ? QcUi.TEXT : QcUi.ERROR);
				String shown = text.isEmpty() ? this.work.resolve(role) : text;
				ItemStack icon = shown == null ? ItemStack.EMPTY : QcUi.blockIcon(firstId(shown));
				if (!icon.isEmpty()) {
					g.renderItem(icon, field.getX() - 18, field.getY() - 2);
				}
			}
		}
		if (!this.footer.isEmpty()) {
			QcUi.status(g, this.font, this.footer, this.footerError ? QcUi.ERROR : QcUi.OK, this.width / 2, this.height - 36);
		}
		super.render(g, mouseX, mouseY, delta);
	}

	private static String firstId(String text) {
		int comma = text.indexOf(',');
		int eq = text.indexOf('=');
		String id = comma >= 0 ? text.substring(0, comma) : text;
		return eq >= 0 && (id.indexOf('[') < 0 || eq < id.indexOf('[')) ? id.substring(0, eq) : id;
	}

	/** Whether a typed value names blocks: a post is a comma list, a mix {@code id=share,...}. */
	private static boolean valid(String role, String text) {
		if (role.startsWith("post")) {
			for (String id : text.split(",")) {
				if (!id.isBlank() && BlockIds.parse(id.trim()) == null) {
					return false;
				}
			}
			return true;
		}
		if (role.endsWith("_mix")) {
			for (String part : text.split(",")) {
				int eq = part.lastIndexOf('=');
				if (eq <= 0 || BlockIds.parse(part.substring(0, eq).trim()) == null) {
					return false;
				}
			}
			return true;
		}
		return BlockIds.parse(text) != null;
	}

	private static String heldBlock() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || !(client.player.getMainHandItem().getItem() instanceof BlockItem item)) {
			return null;
		}
		var id = BuiltInRegistries.BLOCK.getKey(item.getBlock());
		return id.getNamespace().equals("minecraft") ? id.getPath() : id.toString();
	}

	/** Copies the visible fields into the working copy. */
	private void storeFields() {
		if (this.work == null) {
			return;
		}
		for (Map.Entry<String, EditBox> entry : this.fields.entrySet()) {
			this.work.set(entry.getKey(), entry.getValue().getValue());
		}
	}

	/**
	 * Writes the working copy into the plan as one undoable edit, when it changed. It does not read
	 * the fields: callers whose source is the fields call {@link #storeFields()} first, callers that
	 * set the working copy themselves (a kit, a pick) must not have it overwritten by stale text.
	 */
	private void commit() {
		if (this.work == null || this.style == null) {
			return;
		}
		BlockChoice now = QcClientState.plan.styles().get(this.style);
		if (now != null && now.equals(this.work)) {
			return;
		}
		BlockChoice copy = this.work.copy();
		String name = this.style;
		QcInteraction.editFromScreen(() -> {
			QcClientState.plan.styles().put(name, copy);
			return true;
		});
	}

	@Override
	public void onClose() {
		storeFields();
		commit();
		this.minecraft.setScreen(this.parent);
	}

	@Override
	public void removed() {
		storeFields();
		commit();
	}
}
