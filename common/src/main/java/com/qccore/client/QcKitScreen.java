package com.qccore.client;

import com.qccore.core.plan.BlockIds;
import com.qccore.core.plan.RoadStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Kits and road styles. A kit (qckit's "blocks chosen for this build") is the palette, the road
 * styles and the ground block, saved as JSON under {@code <gameDir>/qccore/kits}; the road style
 * editor below it chooses the blocks of a QC road lane by lane, exactly the roles of qckit's
 * {@code road_style}: lane 1 at the building face, lane 4 on the axis.
 */
public class QcKitScreen extends Screen {
	private static final int LANE_WIDTH = 82;
	private static final int LABEL_WIDTH = 70;
	private static final int FIELDS_TOP = 116;
	private static final int FIELD_STEP = 18;
	private static final int FIELD_HEIGHT = 16;
	private static final String[] ROW_TIPS = {
			"floor: the road surface per lane (required in every lane)",
			"joint: replaces the floor every 8 blocks (optional)",
			"over: one block above the floor, y1 (optional)",
			"post: comma-separated ids stacked a, b, ... at the joints (optional)",
			"medallion / centre: the plaza medallion and the centre block (optional)"};

	private final Screen parent;
	private List<String> kits = List.of();
	private int kit;
	private QcUi.Chip kitButton;
	private QcUi.Chip styleButton;
	private EditBox kitName;
	private EditBox styleName;
	private final EditBox[] floor = new EditBox[RoadStyle.LANES];
	private final EditBox[] joint = new EditBox[RoadStyle.LANES];
	private final EditBox[] over = new EditBox[RoadStyle.LANES];
	private final EditBox[] post = new EditBox[RoadStyle.LANES];
	private EditBox medallion;
	private EditBox centre;
	private String footer = "";
	private boolean footerError;

	public QcKitScreen(Screen parent) {
		super(Component.translatable("qccore.screen.kits"));
		this.parent = parent;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		this.kits = QcKits.list();
		int x = this.width / 2 - 205;
		int y = 22 + QcUi.HEADER_HEIGHT + 4;
		this.addRenderableWidget(new QcUi.Chip(x + 4, y, 16, 18, "‹", () -> stepKit(-1)).tip("Previous kit"));
		this.kitButton = this.addRenderableWidget(new QcUi.Chip(x + 22, y, 130, 18, "", () -> stepKit(1)).tip("Next kit"));
		this.addRenderableWidget(new QcUi.Chip(x + 154, y, 16, 18, "›", () -> stepKit(1)).tip("Next kit"));
		this.addRenderableWidget(new QcUi.Chip(x + 174, y, 64, 18, "Load", this::loadKit)
				.tip("Load the kit: palette ids, structures, road styles and ground"));
		this.kitName = new EditBox(this.font, x + 246, y + 1, 92, FIELD_HEIGHT, Component.literal("kit"));
		this.kitName.setMaxLength(64);
		this.kitName.setValue(this.kits.isEmpty() ? "my_kit" : this.kits.get(0));
		this.kitName.setTooltip(QcUi.tip("Name to save the kit under"));
		this.addRenderableWidget(this.kitName);
		this.addRenderableWidget(new QcUi.Chip(x + 342, y, 64, 18, "Save", this::saveKit).accent(QcUi.APPLY)
				.tip("Save the current palette, road styles and ground as a kit"));

		y = 66 + QcUi.HEADER_HEIGHT + 4;
		this.addRenderableWidget(new QcUi.Chip(x + 4, y, 16, 18, "‹", () -> stepStyle(-1)).tip("Previous style"));
		this.styleButton = this.addRenderableWidget(new QcUi.Chip(x + 22, y, 130, 18, "", () -> stepStyle(1))
				.accent(QcUi.color(QcTool.ROAD)).tip("Next style"));
		this.addRenderableWidget(new QcUi.Chip(x + 154, y, 16, 18, "›", () -> stepStyle(1)).tip("Next style"));
		this.styleName = new EditBox(this.font, x + 246, y + 1, 92, FIELD_HEIGHT, Component.literal("style"));
		this.styleName.setMaxLength(32);
		this.styleName.setValue("street");
		this.styleName.setTooltip(QcUi.tip("Name of a new road style"));
		this.addRenderableWidget(this.styleName);
		this.addRenderableWidget(new QcUi.Chip(x + 342, y, 64, 18, "New", this::newStyle).tip("Create a road style with this name"));
		y = FIELDS_TOP;
		int lanes = x + LABEL_WIDTH;
		for (int i = 0; i < RoadStyle.LANES; i++) {
			int laneX = lanes + i * (LANE_WIDTH + 2);
			this.floor[i] = lane(laneX, y, ROW_TIPS[0]);
			this.joint[i] = lane(laneX, y + FIELD_STEP, ROW_TIPS[1]);
			this.over[i] = lane(laneX, y + 2 * FIELD_STEP, ROW_TIPS[2]);
			this.post[i] = lane(laneX, y + 3 * FIELD_STEP, ROW_TIPS[3]);
		}
		this.medallion = lane(lanes, y + 4 * FIELD_STEP, "medallion: the plaza medallion block (optional)");
		this.medallion.setWidth(2 * LANE_WIDTH + 2);
		this.centre = lane(lanes + 2 * (LANE_WIDTH + 2), y + 4 * FIELD_STEP, "centre: the centre block (optional)");
		this.centre.setWidth(2 * LANE_WIDTH + 2);

		int bottom = this.height - 22;
		this.addRenderableWidget(new QcUi.Chip(x, bottom, 200, 18, "Lay this style with the road tool", this::useStyle)
				.icon(() -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIRT_PATH), false)
				.accent(QcUi.color(QcTool.ROAD)).tip("Switch to the road tool with this style"));
		this.addRenderableWidget(new QcUi.Chip(x + 310, bottom, 100, 18, "Done", () -> {
			storeStyle();
			this.minecraft.setScreen(this.parent);
		}).tip("Back (Esc keeps the edits too)"));
		refreshKit();
		loadStyleFields();
	}

	@Override
	public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context);
		QcUi.titleBar(context, this.font, this, null);
		int x = this.width / 2 - 205;
		QcUi.panel(context, this.font, x, 22, 410, 40, "Kit", QcUi.ACCENT);
		int styleBottom = FIELDS_TOP + 5 * FIELD_STEP + 2;
		QcUi.panel(context, this.font, x, 66, 410, styleBottom - 66, "Road style", QcUi.color(QcTool.ROAD));
		int lanes = x + LABEL_WIDTH;
		String[] laneNames = {"lane 1 · face", "lane 2", "lane 3", "lane 4 · axis"};
		for (int i = 0; i < RoadStyle.LANES; i++) {
			context.drawString(this.font, laneNames[i], lanes + i * (LANE_WIDTH + 2) + 2, FIELDS_TOP - 10, QcUi.DIM, false);
		}
		String[] labels = {"floor *", "joint", "over (y1)", "post a,b,…", "medal. / ctr."};
		for (int i = 0; i < labels.length; i++) {
			context.drawString(this.font, labels[i], x + 6, FIELDS_TOP + 4 + i * FIELD_STEP, i == 0 ? QcUi.TEXT : QcUi.MUTED, false);
		}
		for (EditBox[] row : new EditBox[][]{this.floor, this.joint, this.over}) {
			for (EditBox field : row) {
				colour(field);
			}
		}
		colour(this.medallion);
		colour(this.centre);
		if (!this.footer.isEmpty()) {
			QcUi.status(context, this.font, this.footer, this.footerError ? QcUi.ERROR : QcUi.OK, this.width / 2, this.height - 40);
		}
		super.render(context, mouseX, mouseY, delta);
	}

	private EditBox lane(int x, int y, String tip) {
		EditBox field = new EditBox(this.font, x, y, LANE_WIDTH, FIELD_HEIGHT, Component.empty());
		field.setMaxLength(160);
		field.setTooltip(QcUi.tip(tip));
		return this.addRenderableWidget(field);
	}

	private static void colour(EditBox field) {
		String text = field.getValue();
		field.setTextColor(text.isBlank() ? QcUi.TEXT : BlockIds.parse(text) != null ? QcUi.OK : QcUi.ERROR);
	}

	private void stepKit(int delta) {
		if (!this.kits.isEmpty()) {
			this.kit = Math.floorMod(this.kit + delta, this.kits.size());
			this.kitName.setValue(this.kits.get(this.kit));
		}
		refreshKit();
	}

	private void refreshKit() {
		this.kitButton.setMessage(Component.literal(this.kits.isEmpty() ? "no kits in qccore/kits"
				: this.kits.get(this.kit) + "  (" + (this.kit + 1) + "/" + this.kits.size() + ")"));
		this.styleButton.setMessage(Component.literal(QcClientState.roadStyles.isEmpty() ? "no road styles"
				: "style: " + QcClientState.roadStyle));
	}

	private void loadKit() {
		if (this.kits.isEmpty()) {
			return;
		}
		List<String> problems = QcKits.load(this.kits.get(this.kit));
		this.footer = problems.isEmpty() ? "loaded kit " + this.kits.get(this.kit) : String.join("; ", problems);
		this.footerError = !problems.isEmpty();
		loadStyleFields();
		refreshKit();
	}

	private void saveKit() {
		storeStyle();
		try {
			this.footer = "saved " + QcKits.save(this.kitName.getValue()).getFileName();
			this.footerError = false;
			this.kits = QcKits.list();
			this.kit = Math.max(0, this.kits.indexOf(QcSchematics.normaliseName(this.kitName.getValue())));
			refreshKit();
		} catch (RuntimeException e) {
			this.footer = "could not save the kit: " + e.getMessage();
			this.footerError = true;
		}
	}

	private void stepStyle(int delta) {
		storeStyle();
		List<String> names = new ArrayList<>(QcClientState.roadStyles.keySet());
		if (names.isEmpty()) {
			return;
		}
		int index = Math.floorMod(names.indexOf(QcClientState.roadStyle) + delta, names.size());
		QcClientState.roadStyle = names.get(index);
		loadStyleFields();
		refreshKit();
	}

	private void newStyle() {
		storeStyle();
		String name = this.styleName.getValue().trim();
		if (name.isEmpty()) {
			return;
		}
		QcClientState.roadStyles.putIfAbsent(name, new RoadStyle());
		QcClientState.roadStyle = name;
		loadStyleFields();
		refreshKit();
	}

	private void useStyle() {
		storeStyle();
		RoadStyle style = QcClientState.roadStyles.get(QcClientState.roadStyle);
		if (style == null || !style.isComplete()) {
			this.footer = "a road style needs a block in every floor lane";
			this.footerError = true;
			return;
		}
		QcClientState.tool = QcTool.ROAD;
		QcClientState.points.clear();
		this.footer = "road tool: " + QcClientState.roadStyle;
		this.footerError = false;
	}

	/** Copies the fields into the selected style (creating it under the typed name if none exists). */
	private void storeStyle() {
		RoadStyle style = QcClientState.roadStyles.get(QcClientState.roadStyle);
		if (style == null) {
			boolean any = Arrays.stream(this.floor).anyMatch(field -> !field.getValue().isBlank());
			if (!any) {
				return;
			}
			String name = this.styleName.getValue().trim().isEmpty() ? "street" : this.styleName.getValue().trim();
			style = new RoadStyle();
			QcClientState.roadStyles.put(name, style);
			QcClientState.roadStyle = name;
		}
		for (int i = 0; i < RoadStyle.LANES; i++) {
			style.floor[i] = this.floor[i].getValue().trim();
			style.joint[i] = this.joint[i].getValue().trim();
			style.over[i] = this.over[i].getValue().trim();
			style.post[i].clear();
			for (String id : this.post[i].getValue().split(",")) {
				if (!id.isBlank()) {
					style.post[i].add(id.trim());
				}
			}
		}
		style.medallion = this.medallion.getValue().trim();
		style.centre = this.centre.getValue().trim();
	}

	private void loadStyleFields() {
		RoadStyle style = QcClientState.roadStyles.get(QcClientState.roadStyle);
		for (int i = 0; i < RoadStyle.LANES; i++) {
			this.floor[i].setValue(style == null ? "" : style.floor[i]);
			this.joint[i].setValue(style == null ? "" : style.joint[i]);
			this.over[i].setValue(style == null ? "" : style.over[i]);
			this.post[i].setValue(style == null ? "" : String.join(",", style.post[i]));
		}
		this.medallion.setValue(style == null ? "" : style.medallion);
		this.centre.setValue(style == null ? "" : style.centre);
		if (style != null) {
			this.styleName.setValue(QcClientState.roadStyle);
		}
	}
}
