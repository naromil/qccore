package com.qccore.client;

import com.qccore.core.UnitBlockConverter;
import com.qccore.core.UnitLayers;
import com.qccore.core.nbt.StructureNbt;
import com.qccore.core.palette.BlockComponent;
import com.qccore.core.palette.BlockPalette;
import com.qccore.core.palette.Defaults;
import com.qccore.core.palette.QcComponent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The nine components a QC Unit is assembled from: five block ids, the four uploaded structures and
 * the lattice setting of the session.
 */
public class QcConfigScreen extends Screen {
	private static final int LABEL_X_OFFSET = -154;
	private static final int FIELD_X_OFFSET = -40;
	private static final int FIELD_WIDTH = 120;
	private static final int ROW_HEIGHT = 18;
	private static final int ID_FIRST_Y = 20;
	private static final int ID_STEP = 18;
	private static final int LATTICE_Y = 113;
	private static final int COMPONENT_FIRST_Y = 134;
	private static final int COMPONENT_STEP = 16;
	private static final int COMPONENT_HEIGHT = 16;
	private static final int RESIDUE_WIDTH = 18;
	private static final int RESIDUE_A_X = -40;
	private static final int RESIDUE_B_X = -18;
	private static final int RESIDUE_C_X = 4;
	private static final int LATTICE_BUTTON_X = 30;
	private static final int LATTICE_BUTTON_WIDTH = 150;
	private static final int CHOOSE_BUTTON_X = 95;
	private static final int CHOOSE_BUTTON_WIDTH = 60;
	private static final int FOOTER_Y_OFFSET = -36;
	private static final int BUTTON_Y_OFFSET = -24;
	private static final int BUTTON_WIDTH = 100;
	private static final int FIELD_COLOR_VALID = 0x55FF55;
	private static final int FIELD_COLOR_INVALID = 0xFF5555;
	private static final int LABEL_COLOR = 0xA0A0A0;
	private static final int FOOTER_COLOR = 0xFFFFFF;
	private static final int FOOTER_ERROR_COLOR = 0xFF5555;

	private final List<IdRow> idRows = new ArrayList<>();
	private final List<ComponentRow> componentRows = new ArrayList<>();
	private EditBox residueA;
	private EditBox residueB;
	private EditBox residueC;
	private Button latticeButton;
	private boolean latticeSet;
	private String footer = "";
	private boolean footerError;

	public QcConfigScreen() {
		super(Component.translatable("qccore.screen.components"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		this.idRows.clear();
		this.componentRows.clear();
		BlockPalette palette = QcClientState.palette;
		addIdRow("framework", palette.frameworkBlock(), 0);
		addIdRow("row", palette.rowBlock(), 1);
		addIdRow("column", palette.columnBlock(), 2);
		addIdRow("wall", palette.wallBlock(), 3);
		addIdRow("floor", palette.floorBlock(), 4);

		this.latticeSet = QcClientState.alignA != null || QcClientState.alignB != null || QcClientState.alignC != null;
		this.residueA = residueField(this.width / 2 + RESIDUE_A_X, QcClientState.alignA);
		this.residueB = residueField(this.width / 2 + RESIDUE_B_X, QcClientState.alignB);
		this.residueC = residueField(this.width / 2 + RESIDUE_C_X, QcClientState.alignC);
		this.latticeButton = Button.builder(latticeLabel(), button -> toggleLattice())
				.bounds(this.width / 2 + LATTICE_BUTTON_X, LATTICE_Y, LATTICE_BUTTON_WIDTH, 20)
				.build();
		this.addRenderableWidget(this.latticeButton);

		addComponentRow("inner wall", palette.innerWall(), 0);
		addComponentRow("gate", palette.gate(), 1);
		addComponentRow("outer wall", palette.outerWall(), 2);
		addComponentRow("inner column", palette.innerColumn(), 3);

		this.addRenderableWidget(Button.builder(Component.literal("Apply defaults"), button -> applyDefaults())
				.bounds(this.width / 2 - 155, this.height + BUTTON_Y_OFFSET, BUTTON_WIDTH, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Load layout…"),
						button -> this.minecraft.setScreen(new QcFileBrowserScreen(this, QcFileBrowserScreen.Mode.LOAD_LAYOUT, null)))
				.bounds(this.width / 2 - 50, this.height + BUTTON_Y_OFFSET, BUTTON_WIDTH, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> done())
				.bounds(this.width / 2 + 55, this.height + BUTTON_Y_OFFSET, BUTTON_WIDTH, 20).build());
		if (!this.idRows.isEmpty()) {
			this.setInitialFocus(this.idRows.get(0).field());
		}
	}

	@Override
	public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context);
		context.drawCenteredString(this.font, this.title, this.width / 2, 6, 0xFFFFFF);
		for (IdRow row : this.idRows) {
			context.drawString(this.font, row.label(), this.width / 2 + LABEL_X_OFFSET,
					row.field().getY() + 5, LABEL_COLOR);
			row.field().setTextColor(isBlockId(row.field().getValue()) ? FIELD_COLOR_VALID : FIELD_COLOR_INVALID);
		}
		context.drawString(this.font, "lattice", this.width / 2 + LABEL_X_OFFSET, LATTICE_Y + 6, LABEL_COLOR);
		for (ComponentRow row : this.componentRows) {
			context.drawString(this.font, componentText(row), this.width / 2 + LABEL_X_OFFSET,
					row.button().getY() + 4, LABEL_COLOR);
		}
		if (!this.footer.isEmpty()) {
			context.drawCenteredString(this.font, this.footer, this.width / 2,
					this.height + FOOTER_Y_OFFSET, this.footerError ? FOOTER_ERROR_COLOR : FOOTER_COLOR);
		}
		super.render(context, mouseX, mouseY, delta);
	}

	private void addIdRow(String label, BlockComponent component, int index) {
		EditBox field = new EditBox(this.font, this.width / 2 + FIELD_X_OFFSET,
				ID_FIRST_Y + index * ID_STEP, FIELD_WIDTH, ROW_HEIGHT, Component.literal(label));
		field.setMaxLength(128);
		field.setValue(component.getRawId());
		this.addRenderableWidget(field);
		this.idRows.add(new IdRow(label, component, field));
	}

	private void addComponentRow(String label, QcComponent component, int index) {
		Button button = Button.builder(Component.literal("Choose…"), pressed -> this.minecraft
						.setScreen(new QcFileBrowserScreen(this, QcFileBrowserScreen.Mode.PICK_COMPONENT, component)))
				.bounds(this.width / 2 + CHOOSE_BUTTON_X, COMPONENT_FIRST_Y + index * COMPONENT_STEP,
						CHOOSE_BUTTON_WIDTH, COMPONENT_HEIGHT)
				.build();
		this.addRenderableWidget(button);
		this.componentRows.add(new ComponentRow(label, component, button));
	}

	private EditBox residueField(int x, Integer value) {
		EditBox field = new EditBox(this.font, x, LATTICE_Y + 1,
				RESIDUE_WIDTH, ROW_HEIGHT, Component.literal("residue"));
		field.setMaxLength(1);
		field.setFilter(text -> text.matches("[0-7]?"));
		field.setValue(value == null ? "" : value.toString());
		this.addRenderableWidget(field);
		return field;
	}

	private static String componentText(ComponentRow row) {
		QcComponent component = row.component();
		if (component.isEmpty()) {
			return row.label() + ": [Not Configured]";
		}
		return row.label() + ": " + component.getPath() + "  "
				+ component.getSizeX() + "x" + component.getSizeY() + "x" + component.getSizeZ();
	}

	private void toggleLattice() {
		this.latticeSet = !this.latticeSet;
		applyLatticeSetting();
	}

	/** Stores the typed residues while the lattice is on, and clears them while it is free. */
	private void applyLatticeSetting() {
		if (this.latticeSet) {
			QcClientState.alignA = residue(this.residueA);
			QcClientState.alignB = residue(this.residueB);
			QcClientState.alignC = residue(this.residueC);
		} else {
			QcClientState.alignA = null;
			QcClientState.alignB = null;
			QcClientState.alignC = null;
		}
		this.latticeButton.setMessage(latticeLabel());
	}

	private Component latticeLabel() {
		return Component.literal(this.latticeSet ? "lattice: 8x+a, 8y+b, 8z+c" : "lattice: free");
	}

	private void applyDefaults() {
		Defaults.applyTo(QcClientState.palette);
		for (IdRow row : this.idRows) {
			row.field().setValue(row.component().getRawId());
		}
		this.footer = "";
		this.footerError = false;
	}

	private void done() {
		for (IdRow row : this.idRows) {
			if (!isBlockId(row.field().getValue())) {
				this.footer = "fix the highlighted block ids";
				this.footerError = true;
				return;
			}
		}
		for (IdRow row : this.idRows) {
			row.component().setId(row.field().getValue());
		}
		applyLatticeSetting();
		QcClientState.changed();
		onClose();
	}

	/** Replaces the plan's units with {@code file} (an undoable step) and adopts the palette the import configured. */
	public void loadLayout(Path file) {
		String name = file.getFileName().toString();
		try {
			CompoundTag tag = QcSchematics.load(file);
			UnitLayers imported = UnitBlockConverter.convertBlockMapToLayers(StructureNbt.read(tag), QcClientState.palette);
			QcClientState.checkpoint();
			QcClientState.plan.setUnits(imported);
			QcClientState.changed();
			if (!QcClientState.active) {
				QcInteraction.enterEditorMode(this.minecraft);
			}
			for (IdRow row : this.idRows) {
				row.field().setValue(row.component().getRawId());
			}
			this.footer = "loaded " + name + ": " + imported.unitCount() + " unit(s) on "
					+ imported.layerKeys().size() + " layer(s)";
			this.footerError = false;
		} catch (IllegalStateException e) {
			this.footer = name + " contains no QC framework shell";
			this.footerError = true;
		} catch (RuntimeException e) {
			this.footer = e.getMessage() == null ? "could not load " + name : e.getMessage();
			this.footerError = true;
		}
	}

	/** Whether {@code raw} names a block, accepting ids without a namespace. */
	private static boolean isBlockId(String raw) {
		String id = BlockComponent.normalise(raw);
		if (id == null) {
			return false;
		}
		ResourceLocation identifier = ResourceLocation.tryParse(id);
		return identifier != null && BuiltInRegistries.BLOCK.getOptional(identifier).isPresent();
	}

	private static int residue(EditBox field) {
		String text = field.getValue();
		return text.isEmpty() ? 0 : Integer.parseInt(text);
	}

	private record IdRow(String label, BlockComponent component, EditBox field) {
	}

	private record ComponentRow(String label, QcComponent component, Button button) {
	}
}
