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
	private static final int COLUMN = 204;
	private static final int TOP = 22;
	private static final int ID_STEP = 20;
	private static final int STRUCTURE_STEP = 26;
	private static final int FIELD_X = 84;
	private static final int FIELD_WIDTH = 114;
	private static final int ROW_HEIGHT = 16;
	private static final int RESIDUE_WIDTH = 18;

	private final List<IdRow> idRows = new ArrayList<>();
	private final List<ComponentRow> componentRows = new ArrayList<>();
	private EditBox residueA;
	private EditBox residueB;
	private EditBox residueC;
	private boolean latticeSet;
	private String footer = "";
	private boolean footerError;
	private int left;
	private int right;

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
		this.left = this.width / 2 - COLUMN - 3;
		this.right = this.width / 2 + 3;
		BlockPalette palette = QcClientState.palette;
		addIdRow("framework", palette.frameworkBlock(), 0, "The 9x9x9 shell edges: corners and beams");
		addIdRow("row", palette.rowBlock(), 1, "Horizontal beams between the frame corners");
		addIdRow("column", palette.columnBlock(), 2, "Vertical posts on the unit edges");
		addIdRow("wall", palette.wallBlock(), 3, "Infill of the exposed outer faces");
		addIdRow("floor", palette.floorBlock(), 4, "The floor plane of every unit");

		this.latticeSet = QcClientState.alignA != null || QcClientState.alignB != null || QcClientState.alignC != null;
		int latticeY = latticeY();
		this.addRenderableWidget(new QcUi.Chip(this.left + FIELD_X, latticeY, 44, ROW_HEIGHT, "", this::toggleLattice)
				.label(() -> this.latticeSet ? "fixed" : "free").selected(() -> this.latticeSet)
				.tip("free: units go where you aim\nfixed: unit origins snap to (8x+a, 8y+b, 8z+c)"));
		this.residueA = residueField(this.left + FIELD_X + 50, QcClientState.alignA, "a: x residue (0-7)");
		this.residueB = residueField(this.left + FIELD_X + 74, QcClientState.alignB, "b: y residue (0-7)");
		this.residueC = residueField(this.left + FIELD_X + 98, QcClientState.alignC, "c: z residue (0-7)");
		updateResidueVisibility();

		addComponentRow("inner wall", palette.innerWall(), 0);
		addComponentRow("gate", palette.gate(), 1);
		addComponentRow("outer wall", palette.outerWall(), 2);
		addComponentRow("inner column", palette.innerColumn(), 3);

		int bottom = this.height - 22;
		this.addRenderableWidget(new QcUi.Chip(this.left, bottom, 100, 18, "Apply defaults", this::applyDefaults)
				.tip("Reset the five block ids to the built-in deepslate/spruce set"));
		this.addRenderableWidget(new QcUi.Chip(this.left + 104, bottom, 100, 18, "Load layout…",
				() -> this.minecraft.setScreen(new QcFileBrowserScreen(this, QcFileBrowserScreen.Mode.LOAD_LAYOUT, null)))
				.tip("Replace the plan's units with a QC building from a .nbt (undoable)"));
		this.addRenderableWidget(new QcUi.Chip(this.right + COLUMN - 100, bottom, 100, 18, "Done", this::done)
				.accent(QcUi.APPLY).primary().tip("Keep these components (Enter)"));
		if (!this.idRows.isEmpty()) {
			this.setInitialFocus(this.idRows.get(0).field());
		}
	}

	private int latticeY() {
		return TOP + QcUi.HEADER_HEIGHT + 4 + 5 * ID_STEP + 6;
	}

	@Override
	public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context);
		QcUi.titleBar(context, this.font, this, null);
		int latticeY = latticeY();
		QcUi.panel(context, this.font, this.left, TOP, COLUMN, latticeY + ROW_HEIGHT + 5 - TOP, "Blocks", QcUi.ACCENT);
		context.fill(this.left + 4, latticeY - 4, this.left + COLUMN - 4, latticeY - 3, 0xFF2B323D);
		for (IdRow row : this.idRows) {
			String value = row.field().getValue();
			boolean valid = isBlockId(value);
			row.field().setTextColor(valid ? QcUi.TEXT : QcUi.ERROR);
			int y = row.field().getY();
			context.fill(this.left + 4, y - 1, this.left + 22, y + 17, valid ? 0xFF1E252F : 0x60FF4040);
			if (valid) {
				context.renderItem(QcUi.blockIcon(value), this.left + 5, y);
			} else {
				context.drawString(this.font, "?", this.left + 11, y + 4, QcUi.ERROR, false);
			}
			context.drawString(this.font, row.label(), this.left + 26, y + 4, QcUi.MUTED, false);
		}
		context.drawString(this.font, "lattice", this.left + 26, latticeY + 4, QcUi.MUTED, false);

		int structuresHeight = QcUi.HEADER_HEIGHT + 4 + this.componentRows.size() * STRUCTURE_STEP + 2;
		QcUi.panel(context, this.font, this.right, TOP, COLUMN, structuresHeight, "Structures", QcUi.WARN);
		for (ComponentRow row : this.componentRows) {
			QcComponent component = row.component();
			int y = row.button().getY() - 1;
			context.drawString(this.font, row.label(), this.right + 6, y, QcUi.TEXT, false);
			if (component.isEmpty()) {
				context.drawString(this.font, "not configured", this.right + 6, y + 10, QcUi.ERROR, false);
			} else {
				String size = component.getSizeX() + "×" + component.getSizeY() + "×" + component.getSizeZ();
				context.drawString(this.font, size, this.right + 6 + this.font.width(row.label()) + 6, y, QcUi.OK, false);
				context.drawString(this.font, this.font.plainSubstrByWidth(String.valueOf(component.getPath()), COLUMN - 70),
						this.right + 6, y + 10, QcUi.DIM, false);
			}
		}
		if (!this.footer.isEmpty()) {
			QcUi.status(context, this.font, this.footer, this.footerError ? QcUi.ERROR : QcUi.OK, this.width / 2, this.height - 40);
		}
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
			done();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	private void addIdRow(String label, BlockComponent component, int index, String tip) {
		EditBox field = new EditBox(this.font, this.left + FIELD_X, TOP + QcUi.HEADER_HEIGHT + 4 + index * ID_STEP,
				FIELD_WIDTH, ROW_HEIGHT, Component.literal(label));
		field.setMaxLength(128);
		field.setValue(component.getRawId());
		field.moveCursorToStart();
		field.setHint(Component.literal("block id").withStyle(s -> s.withColor(0x667080)));
		field.setTooltip(QcUi.tip(tip + "\nany block id, namespace optional"));
		this.addRenderableWidget(field);
		this.idRows.add(new IdRow(label, component, field));
	}

	private void addComponentRow(String label, QcComponent component, int index) {
		QcUi.Chip button = new QcUi.Chip(this.right + COLUMN - 58, TOP + QcUi.HEADER_HEIGHT + 6 + index * STRUCTURE_STEP,
				52, ROW_HEIGHT, "Choose…", () -> this.minecraft
				.setScreen(new QcFileBrowserScreen(this, QcFileBrowserScreen.Mode.PICK_COMPONENT, component)))
				.tip("Pick a .nbt structure for the " + label);
		this.addRenderableWidget(button);
		this.componentRows.add(new ComponentRow(label, component, button));
	}

	private EditBox residueField(int x, Integer value, String tip) {
		EditBox field = new EditBox(this.font, x, latticeY(), RESIDUE_WIDTH, ROW_HEIGHT, Component.literal("residue"));
		field.setMaxLength(1);
		field.setFilter(text -> text.matches("[0-7]?"));
		field.setValue(value == null ? "" : value.toString());
		field.setTooltip(QcUi.tip(tip));
		field.setHint(Component.literal(tip.substring(0, 1)).withStyle(s -> s.withColor(0x667080)));
		this.addRenderableWidget(field);
		return field;
	}

	private void updateResidueVisibility() {
		this.residueA.setVisible(this.latticeSet);
		this.residueB.setVisible(this.latticeSet);
		this.residueC.setVisible(this.latticeSet);
	}

	private void toggleLattice() {
		this.latticeSet = !this.latticeSet;
		updateResidueVisibility();
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
	}

	private void applyDefaults() {
		Defaults.applyTo(QcClientState.palette);
		for (IdRow row : this.idRows) {
			row.field().setValue(row.component().getRawId());
			row.field().moveCursorToStart();
		}
		this.footer = "defaults restored";
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

	private record ComponentRow(String label, QcComponent component, QcUi.Chip button) {
	}
}
