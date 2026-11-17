package com.qccore.client;

import com.qccore.core.plan.BlockIds;
import com.qccore.core.plan.RoadStyle;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
	private static final int LANE_WIDTH = 66;

	private final Screen parent;
	private List<String> kits = List.of();
	private int kit;
	private ButtonWidget kitButton;
	private ButtonWidget styleButton;
	private TextFieldWidget kitName;
	private TextFieldWidget styleName;
	private final TextFieldWidget[] floor = new TextFieldWidget[RoadStyle.LANES];
	private final TextFieldWidget[] joint = new TextFieldWidget[RoadStyle.LANES];
	private final TextFieldWidget[] over = new TextFieldWidget[RoadStyle.LANES];
	private final TextFieldWidget[] post = new TextFieldWidget[RoadStyle.LANES];
	private TextFieldWidget medallion;
	private TextFieldWidget centre;
	private String footer = "";
	private boolean footerError;

	public QcKitScreen(Screen parent) {
		super(Text.translatable("qccore.screen.kits"));
		this.parent = parent;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	protected void init() {
		this.kits = QcKits.list();
		int x = this.width / 2 - 170;
		int y = 22;
		this.addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> stepKit(-1)).dimensions(x, y, 20, 20).build());
		this.kitButton = this.addDrawableChild(ButtonWidget.builder(Text.empty(), b -> stepKit(1)).dimensions(x + 22, y, 176, 20).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> stepKit(1)).dimensions(x + 200, y, 20, 20).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Load kit"), b -> loadKit()).dimensions(x + 224, y, 116, 20).build());
		y += 22;
		this.kitName = new TextFieldWidget(this.textRenderer, x, y, 220, 20, Text.literal("kit"));
		this.kitName.setMaxLength(64);
		this.kitName.setText(this.kits.isEmpty() ? "my_kit" : this.kits.get(0));
		this.addDrawableChild(this.kitName);
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Save kit"), b -> saveKit()).dimensions(x + 224, y, 116, 20).build());

		y += 34;
		this.addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> stepStyle(-1)).dimensions(x, y, 20, 20).build());
		this.styleButton = this.addDrawableChild(ButtonWidget.builder(Text.empty(), b -> stepStyle(1)).dimensions(x + 22, y, 176, 20).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> stepStyle(1)).dimensions(x + 200, y, 20, 20).build());
		this.styleName = new TextFieldWidget(this.textRenderer, x + 224, y, 64, 20, Text.literal("style"));
		this.styleName.setMaxLength(32);
		this.styleName.setText("street");
		this.addDrawableChild(this.styleName);
		this.addDrawableChild(ButtonWidget.builder(Text.literal("New"), b -> newStyle()).dimensions(x + 290, y, 50, 20).build());
		y += 24;
		int lanes = x + 60;
		for (int i = 0; i < RoadStyle.LANES; i++) {
			this.floor[i] = lane(lanes + i * (LANE_WIDTH + 4), y);
			this.joint[i] = lane(lanes + i * (LANE_WIDTH + 4), y + 22);
			this.over[i] = lane(lanes + i * (LANE_WIDTH + 4), y + 44);
			this.post[i] = lane(lanes + i * (LANE_WIDTH + 4), y + 66);
		}
		this.medallion = lane(lanes, y + 88);
		this.medallion.setWidth(2 * LANE_WIDTH + 4);
		this.centre = lane(lanes + 2 * (LANE_WIDTH + 4), y + 88);
		this.centre.setWidth(2 * LANE_WIDTH + 4);
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Lay this style with the road tool"), b -> useStyle())
				.dimensions(x, y + 114, 340, 20).build());

		this.addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> {
			storeStyle();
			this.client.setScreen(this.parent);
		}).dimensions(this.width / 2 - 50, this.height - 26, 100, 20).build());
		refreshKit();
		loadStyleFields();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context);
		context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 6, TEXT_COLOR);
		int x = this.width / 2 - 170;
		int y = 102;
		context.drawTextWithShadow(this.textRenderer, "road style (lane 1 = at a building face ... lane 4 = axis)", x, 68, LABEL_COLOR);
		String[] labels = {"floor *", "joint", "over (y1)", "post", "medallion"};
		for (int i = 0; i < labels.length; i++) {
			context.drawTextWithShadow(this.textRenderer, labels[i], x, y + 6 + i * 22, LABEL_COLOR);
		}
		context.drawTextWithShadow(this.textRenderer, "joint = every 8 blocks on the transverse planes; post = ids bottom-up, comma separated;"
				+ " centre = right half of the last row", x, y + 138, MUTED_COLOR);
		for (TextFieldWidget[] row : new TextFieldWidget[][]{this.floor, this.joint, this.over}) {
			for (TextFieldWidget field : row) {
				colour(field);
			}
		}
		colour(this.medallion);
		colour(this.centre);
		if (!this.footer.isEmpty()) {
			context.drawCenteredTextWithShadow(this.textRenderer, this.footer, this.width / 2, this.height - 40,
					this.footerError ? ERROR_COLOR : TEXT_COLOR);
		}
		super.render(context, mouseX, mouseY, delta);
	}

	private TextFieldWidget lane(int x, int y) {
		TextFieldWidget field = new TextFieldWidget(this.textRenderer, x, y, LANE_WIDTH, 20, Text.empty());
		field.setMaxLength(160);
		return this.addDrawableChild(field);
	}

	private static void colour(TextFieldWidget field) {
		String text = field.getText();
		field.setEditableColor(text.isBlank() ? TEXT_COLOR : BlockIds.parse(text) != null ? VALID_COLOR : ERROR_COLOR);
	}

	private void stepKit(int delta) {
		if (!this.kits.isEmpty()) {
			this.kit = Math.floorMod(this.kit + delta, this.kits.size());
			this.kitName.setText(this.kits.get(this.kit));
		}
		refreshKit();
	}

	private void refreshKit() {
		this.kitButton.setMessage(Text.literal(this.kits.isEmpty() ? "no kits in qccore/kits"
				: this.kits.get(this.kit) + "  (" + (this.kit + 1) + "/" + this.kits.size() + ")"));
		this.styleButton.setMessage(Text.literal(QcClientState.roadStyles.isEmpty() ? "no road styles"
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
			this.footer = "saved " + QcKits.save(this.kitName.getText()).getFileName();
			this.footerError = false;
			this.kits = QcKits.list();
			this.kit = Math.max(0, this.kits.indexOf(QcSchematics.normaliseName(this.kitName.getText())));
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
		String name = this.styleName.getText().trim();
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
			boolean any = Arrays.stream(this.floor).anyMatch(field -> !field.getText().isBlank());
			if (!any) {
				return;
			}
			String name = this.styleName.getText().trim().isEmpty() ? "street" : this.styleName.getText().trim();
			style = new RoadStyle();
			QcClientState.roadStyles.put(name, style);
			QcClientState.roadStyle = name;
		}
		for (int i = 0; i < RoadStyle.LANES; i++) {
			style.floor[i] = this.floor[i].getText().trim();
			style.joint[i] = this.joint[i].getText().trim();
			style.over[i] = this.over[i].getText().trim();
			style.post[i].clear();
			for (String id : this.post[i].getText().split(",")) {
				if (!id.isBlank()) {
					style.post[i].add(id.trim());
				}
			}
		}
		style.medallion = this.medallion.getText().trim();
		style.centre = this.centre.getText().trim();
	}

	private void loadStyleFields() {
		RoadStyle style = QcClientState.roadStyles.get(QcClientState.roadStyle);
		for (int i = 0; i < RoadStyle.LANES; i++) {
			this.floor[i].setText(style == null ? "" : style.floor[i]);
			this.joint[i].setText(style == null ? "" : style.joint[i]);
			this.over[i].setText(style == null ? "" : style.over[i]);
			this.post[i].setText(style == null ? "" : String.join(",", style.post[i]));
		}
		this.medallion.setText(style == null ? "" : style.medallion);
		this.centre.setText(style == null ? "" : style.centre);
		if (style != null) {
			this.styleName.setText(QcClientState.roadStyle);
		}
	}
}
