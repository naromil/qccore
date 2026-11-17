package com.qccore.client;

import com.qccore.core.QcStructure;
import com.qccore.core.nbt.StructureNbt;
import com.qccore.core.palette.Gate;
import com.qccore.core.palette.InnerColumn;
import com.qccore.core.palette.InnerWall;
import com.qccore.core.palette.OuterWall;
import com.qccore.core.palette.QcComponent;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/** Picks a {@code .nbt} file from {@link QcSchematics#dir()} for a component or as a whole layout. */
public class QcFileBrowserScreen extends Screen {
	/** What the picked file is used for. */
	public enum Mode {
		/** Installs the structure into the component the screen was opened for. */
		PICK_COMPONENT,
		/** Replaces the session layout with the file. */
		LOAD_LAYOUT
	}

	private static final int LIST_TOP = 34;
	private static final int LIST_HALF_WIDTH = 140;
	private static final int LIST_BOTTOM_OFFSET = -56;
	private static final int ITEM_HEIGHT = 20;
	private static final int TEXT_Y_OFFSET = 5;
	private static final int DIRECTORY_Y_OFFSET = -50;
	private static final int ERROR_Y_OFFSET = -40;
	private static final int BUTTON_Y_OFFSET = -24;
	private static final int BUTTON_WIDTH = 100;

	private final Screen parent;
	private final Mode mode;
	private final QcComponent component;
	private FileList list;
	private String directory = "";
	private String error = "";

	public QcFileBrowserScreen(Screen parent, Mode mode, @Nullable QcComponent component) {
		super(Component.translatable("qccore.screen.files"));
		this.parent = parent;
		this.mode = mode;
		this.component = component;
	}

	@Override
	protected void init() {
		this.directory = QcSchematics.dir().toAbsolutePath().toString();
		this.list = new FileList(this.minecraft, this.width, this.height, LIST_TOP,
				this.height + LIST_BOTTOM_OFFSET, ITEM_HEIGHT);
		this.list.setRenderBackground(false);
		this.list.setRenderTopAndBottom(false);
		this.list.populate(QcSchematics.list());
		this.addRenderableWidget(this.list);
		this.addRenderableWidget(new QcUi.Chip(this.width / 2 - 105, this.height + BUTTON_Y_OFFSET, BUTTON_WIDTH, 18,
				"Cancel", () -> this.minecraft.setScreen(this.parent)).tip("Esc"));
		this.addRenderableWidget(new QcUi.Chip(this.width / 2 + 5, this.height + BUTTON_Y_OFFSET, BUTTON_WIDTH, 18,
				"Select", this::select).accent(QcUi.APPLY).primary().tip("Use the selected file (Enter, or double-click a row)"));
	}

	@Override
	public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context);
		int x = this.width / 2 - LIST_HALF_WIDTH - 6;
		int bottom = this.height + LIST_BOTTOM_OFFSET;
		QcUi.panel(context, this.font, x, LIST_TOP - QcUi.HEADER_HEIGHT - 2, 2 * LIST_HALF_WIDTH + 12,
				bottom - LIST_TOP + QcUi.HEADER_HEIGHT + 4, purpose(), this.mode == Mode.LOAD_LAYOUT ? QcUi.ACCENT : QcUi.WARN);
		super.render(context, mouseX, mouseY, delta);
		QcUi.titleBar(context, this.font, this, null);
		context.drawCenteredString(this.font, this.font.plainSubstrByWidth(this.directory, this.width - 16),
				this.width / 2, this.height + DIRECTORY_Y_OFFSET, 0xFF4A5260);
		if (!this.error.isEmpty()) {
			QcUi.status(context, this.font, this.error, QcUi.ERROR, this.width / 2, this.height + ERROR_Y_OFFSET);
		}
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
			select();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	private String purpose() {
		if (this.mode == Mode.LOAD_LAYOUT) {
			return "Load a QC layout";
		}
		ComponentText text = describe(this.component);
		return "Structure for the " + text.label() + " · " + text.rule();
	}

	private void select() {
		FileEntry selected = this.list.getSelected();
		Path file = selected == null ? null : selected.file;
		if (file == null) {
			return;
		}
		if (this.mode == Mode.LOAD_LAYOUT) {
			if (this.parent instanceof QcConfigScreen config) {
				config.loadLayout(file);
			}
			this.minecraft.setScreen(this.parent);
			return;
		}
		pickComponent(file);
	}

	private void pickComponent(Path file) {
		CompoundTag tag;
		try {
			tag = QcSchematics.load(file);
		} catch (RuntimeException e) {
			this.error = e.getMessage() == null ? "could not read " + file.getFileName() : e.getMessage();
			return;
		}
		String problem = StructureNbt.validateComponentFile(tag);
		if (problem != null) {
			this.error = problem;
			return;
		}
		QcStructure structure;
		try {
			structure = StructureNbt.parseStructure(tag);
		} catch (RuntimeException e) {
			this.error = e.getMessage() == null ? "could not read " + file.getFileName() : e.getMessage();
			return;
		}
		if (!this.component.isValidSize(structure.sizeX(), structure.sizeY(), structure.sizeZ())) {
			ComponentText text = describe(this.component);
			this.error = file.getFileName() + " is " + structure.sizeX() + "x" + structure.sizeY() + "x"
					+ structure.sizeZ() + "; " + text.label() + " needs " + text.rule();
			return;
		}
		this.component.setStructure(structure, file.getFileName().toString());
		this.minecraft.setScreen(this.parent);
	}

	/** Display name and size rule of a structure component, mirrored from its {@code isValidSize}. */
	private static ComponentText describe(QcComponent component) {
		if (component instanceof InnerWall) {
			return new ComponentText("inner wall", "7x7x1 or 7x7x3");
		}
		if (component instanceof Gate) {
			return new ComponentText("gate", "7x7x1 or 7x7x3");
		}
		if (component instanceof OuterWall) {
			return new ComponentText("outer wall", "7x7x0..3");
		}
		if (component instanceof InnerColumn) {
			return new ComponentText("inner column", "3x7x3");
		}
		throw new IllegalArgumentException("unknown component " + component.getClass().getName());
	}

	private class FileList extends ContainerObjectSelectionList<FileEntry> {
		FileList(Minecraft client, int width, int height, int top, int bottom, int itemHeight) {
			super(client, width, height, top, bottom, itemHeight);
		}

		/** One row per file, or the single grey placeholder row of an empty folder. */
		void populate(List<Path> files) {
			if (files.isEmpty()) {
				this.addEntry(new FileEntry(null));
				return;
			}
			for (Path file : files) {
				this.addEntry(new FileEntry(file));
			}
		}

		@Override
		public int getRowWidth() {
			return 2 * LIST_HALF_WIDTH;
		}

		@Override
		protected int getScrollbarPosition() {
			return QcFileBrowserScreen.this.width / 2 + LIST_HALF_WIDTH + 2;
		}

		@Override
		protected boolean isSelectedItem(int index) {
			FileEntry selected = this.getSelected();
			return selected != null && this.children().indexOf(selected) == index;
		}
	}

	private class FileEntry extends ContainerObjectSelectionList.Entry<FileEntry> {
		private final @Nullable Path file;
		private final long size;
		private long lastClick;

		FileEntry(@Nullable Path file) {
			this.file = file;
			this.size = size(file);
		}

		@Override
		public void render(GuiGraphics context, int index, int y, int x, int entryWidth, int entryHeight,
				int mouseX, int mouseY, boolean hovered, float tickDelta) {
			net.minecraft.client.gui.Font font = QcFileBrowserScreen.this.font;
			if (this.file == null) {
				context.drawString(font, "no .nbt files yet - put them in the folder below", x + 4, y + TEXT_Y_OFFSET, QcUi.DIM, false);
				return;
			}
			if (hovered) {
				context.fill(x - 2, y - 2, x + entryWidth - 2, y + entryHeight + 1, 0x302C3644);
			}
			context.renderItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STRUCTURE_BLOCK), x, y);
			context.drawString(font, this.file.getFileName().toString(), x + 20, y + TEXT_Y_OFFSET, QcUi.TEXT, false);
			String size = this.size < 1024 ? this.size + " B" : String.format(java.util.Locale.ROOT, "%.1f KB", this.size / 1024.0);
			context.drawString(font, size, x + entryWidth - 6 - font.width(size), y + TEXT_Y_OFFSET, QcUi.DIM, false);
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			if (this.file == null) {
				return false;
			}
			FileList owner = QcFileBrowserScreen.this.list;
			long now = System.currentTimeMillis();
			boolean again = owner.getSelected() == this && now - this.lastClick < 350;
			this.lastClick = now;
			owner.setSelected(this);
			if (again) {
				QcFileBrowserScreen.this.select();
			}
			return true;
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return List.of();
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return List.of();
		}

		private static long size(@Nullable Path file) {
			if (file == null) {
				return 0L;
			}
			try {
				return Files.size(file);
			} catch (IOException e) {
				return 0L;
			}
		}
	}

	private record ComponentText(String label, String rule) {
	}
}
