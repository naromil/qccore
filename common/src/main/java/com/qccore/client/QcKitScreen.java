package com.qccore.client;

import com.qccore.core.plan.BlockIds;
import com.qccore.core.plan.RoadStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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
	private static final int TEXT_COLOR = 0xFFFFFF;
	private static final int LABEL_COLOR = 0xA0A0A0;
	private static final int MUTED_COLOR = 0x808080;
	private static final int ERROR_COLOR = 0xFF5555;
	private static final int VALID_COLOR = 0x55FF55;
	private static final int LANE_WIDTH = 82;
	private static final int LABEL_WIDTH = 66;
	private static final int FIELDS_TOP = 80;

	private final Screen parent;
	private List<String> kits = List.of();
	private int kit;
	private Button kitButton;
	private Button styleButton;
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
		int y = 18;
		this.addRenderableWidget(Button.builder(Component.literal("<"), b -> stepKit(-1)).bounds(x, y, 16, 20).build());
		this.kitButton = this.addRenderableWidget(Button.builder(Component.empty(), b -> stepKit(1)).bounds(x + 18, y, 118, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal(">"), b -> stepKit(1)).bounds(x + 138, y, 16, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Load kit"), b -> loadKit()).bounds(x + 158, y, 74, 20).build());
		this.kitName = new EditBox(this.font, x + 240, y, 92, 20, Component.literal("kit"));
		this.kitName.setMaxLength(64);
		this.kitName.setValue(this.kits.isEmpty() ? "my_kit" : this.kits.get(0));
		this.addRenderableWidget(this.kitName);
		this.addRenderableWidget(Button.builder(Component.literal("Save kit"), b -> saveKit()).bounds(x + 336, y, 74, 20).build());

		y += 24;
		this.addRenderableWidget(Button.builder(Component.literal("<"), b -> stepStyle(-1)).bounds(x, y, 16, 20).build());
		this.styleButton = this.addRenderableWidget(Button.builder(Component.empty(), b -> stepStyle(1)).bounds(x + 18, y, 118, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal(">"), b -> stepStyle(1)).bounds(x + 138, y, 16, 20).build());
		this.styleName = new EditBox(this.font, x + 240, y, 92, 20, Component.literal("style"));
		this.styleName.setMaxLength(32);
		this.styleName.setValue("street");
		this.addRenderableWidget(this.styleName);
		this.addRenderableWidget(Button.builder(Component.literal("New style"), b -> newStyle()).bounds(x + 336, y, 74, 20).build());
		y = FIELDS_TOP;
		int lanes = x + LABEL_WIDTH;
		for (int i = 0; i < RoadStyle.LANES; i++) {
			int laneX = lanes + i * (LANE_WIDTH + 4);
			this.floor[i] = lane(laneX, y);
			this.joint[i] = lane(laneX, y + 22);
			this.over[i] = lane(laneX, y + 44);
			this.post[i] = lane(laneX, y + 66);
		}
		this.medallion = lane(lanes, y + 88);
		this.medallion.setWidth(2 * LANE_WIDTH + 4);
		this.centre = lane(lanes + 2 * (LANE_WIDTH + 4), y + 88);
		this.centre.setWidth(2 * LANE_WIDTH + 4);

		int bottom = this.height - 24;
		this.addRenderableWidget(Button.builder(Component.literal("Lay this style with the road tool"), b -> useStyle())
				.bounds(x, bottom, 300, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> {
			storeStyle();
			this.minecraft.setScreen(this.parent);
		}).bounds(x + 310, bottom, 100, 20).build());
		refreshKit();
		loadStyleFields();
	}

	@Override
	public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context);
		context.drawCenteredString(this.font, this.title, this.width / 2, 6, TEXT_COLOR);
		int x = this.width / 2 - 205;
		context.drawString(this.font, "lane 1 = at a building face ... lane 4 = on the axis;  joint = every 8 blocks",
				x, FIELDS_TOP - 11, MUTED_COLOR);
		String[] labels = {"floor *", "joint", "over (y1)", "post a,b,..", "medal./ctr."};
		for (int i = 0; i < labels.length; i++) {
			context.drawString(this.font, labels[i], x, FIELDS_TOP + 6 + i * 22, LABEL_COLOR);
		}
		for (EditBox[] row : new EditBox[][]{this.floor, this.joint, this.over}) {
			for (EditBox field : row) {
				colour(field);
			}
		}
		colour(this.medallion);
		colour(this.centre);
		if (!this.footer.isEmpty()) {
			context.drawCenteredString(this.font, this.footer, this.width / 2, this.height - 36,
					this.footerError ? ERROR_COLOR : TEXT_COLOR);
		}
		super.render(context, mouseX, mouseY, delta);
	}

	private EditBox lane(int x, int y) {
		EditBox field = new EditBox(this.font, x, y, LANE_WIDTH, 20, Component.empty());
		field.setMaxLength(160);
		return this.addRenderableWidget(field);
	}

	private static void colour(EditBox field) {
		String text = field.getValue();
		field.setTextColor(text.isBlank() ? TEXT_COLOR : BlockIds.parse(text) != null ? VALID_COLOR : ERROR_COLOR);
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
