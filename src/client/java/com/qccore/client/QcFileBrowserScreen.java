package com.qccore.client;

import com.qccore.core.QcStructure;
import com.qccore.core.nbt.StructureNbt;
import com.qccore.core.palette.Gate;
import com.qccore.core.palette.InnerColumn;
import com.qccore.core.palette.InnerWall;
import com.qccore.core.palette.OuterWall;
import com.qccore.core.palette.QcComponent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Picks a {@code .nbt} file from {@link QcSchematics#dir()} for a component or as a whole layout. */
public class QcFileBrowserScreen extends Screen {
	/** What the picked file is used for. */
	public enum Mode {
		/** Installs the structure into the component the screen was opened for. */
		PICK_COMPONENT,
		/** Replaces the session layout with the file. */
		LOAD_LAYOUT
	}

	private static final int LIST_TOP = 32;
	private static final int LIST_BOTTOM_OFFSET = -56;
	private static final int ITEM_HEIGHT = 20;
	private static final int TEXT_Y_OFFSET = 5;
	private static final int SIZE_X_OFFSET = -60;
	private static final int DIRECTORY_Y_OFFSET = -50;
	private static final int ERROR_Y_OFFSET = -38;
	private static final int BUTTON_Y_OFFSET = -24;
	private static final int BUTTON_WIDTH = 100;
	private static final int TEXT_COLOR = 0xFFFFFF;
	private static final int MUTED_COLOR = 0x808080;
	private static final int ERROR_COLOR = 0xFF5555;

	private final Screen parent;
	private final Mode mode;
	private final QcComponent component;
	private FileList list;
	private String directory = "";
	private String error = "";

	public QcFileBrowserScreen(Screen parent, Mode mode, @Nullable QcComponent component) {
		super(Text.translatable("qccore.screen.files"));
		this.parent = parent;
		this.mode = mode;
		this.component = component;
	}

	@Override
	protected void init() {
		this.directory = QcSchematics.dir().toAbsolutePath().toString();
		this.list = new FileList(this.client, this.width, this.height, LIST_TOP,
				this.height + LIST_BOTTOM_OFFSET, ITEM_HEIGHT);
		this.list.populate(QcSchematics.list());
		this.addDrawableChild(this.list);
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Select"), button -> select())
				.dimensions(this.width / 2 - 105, this.height + BUTTON_Y_OFFSET, BUTTON_WIDTH, 20).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> this.client.setScreen(this.parent))
				.dimensions(this.width / 2 + 5, this.height + BUTTON_Y_OFFSET, BUTTON_WIDTH, 20).build());
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context);
		context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 6, 0xFFFFFF);
		context.drawTextWithShadow(this.textRenderer, this.directory, 8, this.height + DIRECTORY_Y_OFFSET, MUTED_COLOR);
		if (!this.error.isEmpty()) {
			context.drawTextWithShadow(this.textRenderer, this.error, 8, this.height + ERROR_Y_OFFSET, ERROR_COLOR);
		}
		super.render(context, mouseX, mouseY, delta);
	}

	private void select() {
		FileEntry selected = this.list.getSelectedOrNull();
		Path file = selected == null ? null : selected.file;
		if (file == null) {
			return;
		}
		if (this.mode == Mode.LOAD_LAYOUT) {
			if (this.parent instanceof QcConfigScreen config) {
				config.loadLayout(file);
			}
			this.client.setScreen(this.parent);
			return;
		}
		pickComponent(file);
	}

	private void pickComponent(Path file) {
		NbtCompound tag;
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
		this.client.setScreen(this.parent);
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

	private class FileList extends ElementListWidget<FileEntry> {
		FileList(MinecraftClient client, int width, int height, int top, int bottom, int itemHeight) {
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
		protected boolean isSelectedEntry(int index) {
			FileEntry selected = this.getSelectedOrNull();
			return selected != null && this.children().indexOf(selected) == index;
		}
	}

	private class FileEntry extends ElementListWidget.Entry<FileEntry> {
		private final @Nullable Path file;
		private final long size;

		FileEntry(@Nullable Path file) {
			this.file = file;
			this.size = size(file);
		}

		@Override
		public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight,
				int mouseX, int mouseY, boolean hovered, float tickDelta) {
			if (this.file == null) {
				context.drawTextWithShadow(QcFileBrowserScreen.this.textRenderer,
						"no .nbt files in " + QcFileBrowserScreen.this.directory,
						x + 2, y + TEXT_Y_OFFSET, MUTED_COLOR);
				return;
			}
			context.drawTextWithShadow(QcFileBrowserScreen.this.textRenderer,
					this.file.getFileName().toString(), x + 2, y + TEXT_Y_OFFSET, TEXT_COLOR);
			context.drawTextWithShadow(QcFileBrowserScreen.this.textRenderer,
					this.size + " bytes", x + entryWidth + SIZE_X_OFFSET, y + TEXT_Y_OFFSET, MUTED_COLOR);
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			if (this.file == null) {
				return false;
			}
			FileList owner = QcFileBrowserScreen.this.list;
			owner.setSelected(this);
			return true;
		}

		@Override
		public List<? extends Selectable> selectableChildren() {
			return List.of();
		}

		@Override
		public List<? extends Element> children() {
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
