package com.qccore.client;

import com.qccore.core.plan.BlockIds;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The shared look of the QC screens and the HUD: one flat dark theme with an accent colour per
 * tool, framed panels with a title strip, {@link Chip} buttons that show their selected state, the
 * {@link Stepper} number widget (click the arrows, scroll, Shift for steps of 8) and a status pill.
 */
public final class QcUi {
	public static final int PANEL = 0xD0101318;
	public static final int PANEL_BORDER = 0xFF2B323D;
	public static final int HEADER = 0xF01A202A;
	public static final int ACCENT = 0xFF5FB4FF;
	public static final int TEXT = 0xFFF2F4F7;
	public static final int MUTED = 0xFF9AA4B2;
	public static final int DIM = 0xFF667080;
	public static final int OK = 0xFF6BE38A;
	public static final int ERROR = 0xFFFF6B6B;
	public static final int WARN = 0xFFFFD166;
	public static final int APPLY = 0xFF3FBF6A;
	private static final int CHIP = 0xFF1E252F;
	private static final int CHIP_HOVER = 0xFF2C3644;
	private static final int CHIP_BORDER = 0xFF323B48;
	private static final int CHIP_DISABLED = 0xFF161A20;
	/** Height of the title strip of a {@link #panel}; the content starts below it. */
	public static final int HEADER_HEIGHT = 13;

	private QcUi() {
	}

	/** A framed panel with an optional title strip whose left edge carries {@code accent}. */
	public static void panel(GuiGraphics g, Font font, int x, int y, int w, int h, String title, int accent) {
		g.fill(x, y, x + w, y + h, PANEL);
		g.renderOutline(x, y, w, h, PANEL_BORDER);
		if (title != null && !title.isEmpty()) {
			g.fill(x + 1, y + 1, x + w - 1, y + HEADER_HEIGHT, HEADER);
			g.fill(x + 1, y + 1, x + 3, y + HEADER_HEIGHT, accent);
			g.drawString(font, title, x + 7, y + 3, TEXT, false);
		}
	}

	/** The screen title as a bar across the top. */
	public static void titleBar(GuiGraphics g, Font font, Screen screen, String hint) {
		g.fillGradient(0, 0, screen.width, 14, 0xE0141A22, 0xC0141A22);
		g.fill(0, 14, screen.width, 15, ACCENT);
		g.drawCenteredString(font, screen.getTitle(), screen.width / 2, 3, TEXT);
		if (hint != null && screen.width - font.width(hint) - 6 > (screen.width + font.width(screen.getTitle())) / 2 + 8) {
			g.drawString(font, hint, screen.width - font.width(hint) - 6, 3, DIM, false);
		}
	}

	/** A one-line message in a rounded-looking pill centred on {@code centerX}. */
	public static void status(GuiGraphics g, Font font, String text, int color, int centerX, int y) {
		if (text == null || text.isEmpty()) {
			return;
		}
		int w = font.width(text) + 12;
		int x = centerX - w / 2;
		g.fill(x + 1, y, x + w - 1, y + 13, 0xE0101318);
		g.fill(x, y + 1, x + w, y + 12, 0xE0101318);
		g.fill(x + 1, y + 12, x + w - 1, y + 13, color);
		g.drawString(font, text, x + 6, y + 3, color, false);
	}

	/** {@link com.qccore.core.plan.QcPlan#summary()} shortened for narrow panels: "3 units · 2 cleared · 0 road". */
	public static String planSummary() {
		return QcClientState.plan.summary().replaceAll("^1 unit\\(s\\)", "1 unit").replace(" unit(s)", " units")
				.replace(" cleared cell(s)", " cleared").replace(" road cell(s)", " road").replace(" building(s)", " bldg").replace(", ", " · ");
	}

	public static Tooltip tip(String text) {
		return Tooltip.create(Component.literal(text));
	}

	/** Opaque ARGB colour of a tool (the colour its wireframes are drawn in). */
	public static int color(QcTool tool) {
		return 0xFF000000 | (int) (tool.red * 255) << 16 | (int) (tool.green * 255) << 8 | (int) (tool.blue * 255);
	}

	/** A short label for the tool tiles. */
	public static String shortName(QcTool tool) {
		return switch (tool) {
			case UNITS -> "building";
			case ERASE -> "erase";
			case ASSIGN -> "assign";
			case WALLS -> "walls";
			case GATES -> "gates";
			case OPEN -> "open";
			case CLEAR -> "clear";
			case ROAD -> "road";
			case TEMPLATE -> "template";
		};
	}

	/** What a tool does, for its tooltip. */
	public static String describe(QcTool tool) {
		return switch (tool) {
			case UNITS -> "Building: add QC units to the active building (a new one if none is active)";
			case ERASE -> "Erase: remove planned units, roads and cleared cells";
			case ASSIGN -> "Assign: move planned units into the active building";
			case WALLS -> "Partition walls: inner walls on every shared side in the shape";
			case GATES -> "Partition gates: inner walls with a gate (rooms)";
			case OPEN -> "Partition open: no partition inside the shape (a hall)";
			case CLEAR -> "Clear terrain: carve the cells, level the floor if a ground block is set";
			case ROAD -> "Road: lay a QC road in the active road style (blocks in the block chooser)";
			case TEMPLATE -> "Template: stamp the chosen template; R turns it";
		};
	}

	/** The item drawn on a tool's tile and in the HUD. */
	public static ItemStack icon(QcTool tool) {
		return switch (tool) {
			case UNITS -> blockIcon(QcClientState.activeStyleBlock("frame"), Items.BRICKS);
			case ERASE -> new ItemStack(Items.BARRIER);
			case ASSIGN -> new ItemStack(Items.BRUSH);
			case WALLS -> new ItemStack(Items.DEEPSLATE_BRICK_WALL);
			case GATES -> new ItemStack(Items.SPRUCE_FENCE_GATE);
			case OPEN -> new ItemStack(Items.GLASS);
			case CLEAR -> new ItemStack(Items.IRON_SHOVEL);
			case ROAD -> new ItemStack(Items.DIRT_PATH);
			case TEMPLATE -> new ItemStack(Items.STRUCTURE_BLOCK);
		};
	}

	/** The item of the block {@code id} names (states allowed), or {@code fallback} when it names none. */
	public static ItemStack blockIcon(String id, net.minecraft.world.item.Item fallback) {
		ItemStack stack = blockIcon(id);
		return stack.isEmpty() && fallback != null ? new ItemStack(fallback) : stack;
	}

	/** The item of the block {@code id} names, or {@link ItemStack#EMPTY}. */
	public static ItemStack blockIcon(String id) {
		if (id == null || id.isBlank()) {
			return ItemStack.EMPTY;
		}
		try {
			BlockState state = BlockIds.parse(id);
			return state == null ? ItemStack.EMPTY : new ItemStack(state.getBlock().asItem());
		} catch (RuntimeException e) {
			return ItemStack.EMPTY;
		}
	}

	/** A small block-id validity mark: a green dot for a block, red for anything else, nothing when blank. */
	public static void idMark(GuiGraphics g, int x, int y, String id) {
		if (id == null || id.isBlank()) {
			return;
		}
		boolean valid;
		try {
			valid = BlockIds.parse(id) != null;
		} catch (RuntimeException e) {
			valid = false;
		}
		g.fill(x, y, x + 4, y + 4, valid ? OK : ERROR);
	}

	/**
	 * A flat button with a hover state, an optional icon and a "selected" state (accent bar plus a
	 * tinted face) read from a supplier every frame, so a group of chips works as a radio group.
	 */
	public static class Chip extends AbstractButton {
		private final Runnable action;
		private BooleanSupplier selected = () -> false;
		private Supplier<ItemStack> icon;
		private Supplier<String> label;
		private int accent = ACCENT;
		private boolean iconOnly;
		private boolean filled;

		public Chip(int x, int y, int w, int h, String label, Runnable action) {
			super(x, y, w, h, Component.literal(label));
			this.action = action;
		}

		public Chip selected(BooleanSupplier selected) {
			this.selected = selected;
			return this;
		}

		public Chip accent(int accent) {
			this.accent = accent;
			return this;
		}

		/** A solid accent-coloured face: the primary action of a screen. */
		public Chip primary() {
			this.filled = true;
			return this;
		}

		public Chip icon(Supplier<ItemStack> icon, boolean iconOnly) {
			this.icon = icon;
			this.iconOnly = iconOnly;
			return this;
		}

		public Chip label(Supplier<String> label) {
			this.label = label;
			return this;
		}

		public Chip tip(String text) {
			this.setTooltip(QcUi.tip(text));
			return this;
		}

		@Override
		public void onPress() {
			this.action.run();
		}

		@Override
		protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
			int x = this.getX();
			int y = this.getY();
			int w = this.getWidth();
			int h = this.getHeight();
			boolean on = this.selected.getAsBoolean();
			boolean hover = this.active && this.isHoveredOrFocused();
			int face;
			if (!this.active) {
				face = CHIP_DISABLED;
			} else if (this.filled) {
				face = hover ? brighten(this.accent) : darken(this.accent);
			} else if (on) {
				face = blend(CHIP, this.accent, hover ? 0.42F : 0.30F);
			} else {
				face = hover ? CHIP_HOVER : CHIP;
			}
			g.fill(x, y, x + w, y + h, face);
			g.renderOutline(x, y, w, h, on || (hover && !this.filled) ? this.accent : CHIP_BORDER);
			if (on) {
				g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, this.accent);
			}
			Font font = net.minecraft.client.Minecraft.getInstance().font;
			int textX = x + 4;
			if (this.icon != null) {
				ItemStack stack = this.icon.get();
				if (!stack.isEmpty()) {
					int iconX = this.iconOnly ? x + (w - 16) / 2 : x + 2;
					g.renderItem(stack, iconX, y + (h - 16) / 2);
					textX = x + 20;
				}
			}
			if (this.iconOnly) {
				return;
			}
			String text;
			if (this.label != null) {
				text = this.label.get();
				this.setMessage(Component.literal(text));
			} else {
				text = this.getMessage().getString();
			}
			int color = !this.active ? DIM : this.filled ? 0xFFFFFFFF : on ? TEXT : hover ? TEXT : MUTED;
			int available = x + w - 3 - textX;
			if (font.width(text) > available) {
				text = font.plainSubstrByWidth(text, available - font.width("…")) + "…";
			}
			int textWidth = font.width(text);
			int drawX = this.icon == null ? x + (w - textWidth) / 2 : textX;
			g.drawString(font, text, drawX, y + (h - 8) / 2, color, this.filled);
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			this.defaultButtonNarrationText(output);
		}
	}

	/**
	 * An integer field without typing: {@code ‹ value ›}. The arrows step by one (eight with Shift),
	 * the mouse wheel steps while hovered, and the value is written through at once.
	 */
	public static class Stepper extends AbstractWidget {
		private static final int ARROW = 11;
		private final IntSupplier getter;
		private final IntConsumer setter;
		private final int min;
		private final int max;

		public Stepper(int x, int y, int w, int h, String name, int min, int max, IntSupplier getter, IntConsumer setter) {
			super(x, y, w, h, Component.literal(name));
			this.min = min;
			this.max = max;
			this.getter = getter;
			this.setter = setter;
		}

		public Stepper tip(String text) {
			this.setTooltip(QcUi.tip(text + "\nclick the arrows or scroll; Shift steps by 8"));
			return this;
		}

		private void step(int direction) {
			int amount = Screen.hasShiftDown() ? 8 : 1;
			int value = Math.max(this.min, Math.min(this.max, this.getter.getAsInt() + direction * amount));
			this.setter.accept(value);
		}

		@Override
		public void onClick(double mouseX, double mouseY) {
			if (mouseX < this.getX() + ARROW) {
				step(-1);
			} else if (mouseX >= this.getX() + this.getWidth() - ARROW) {
				step(1);
			}
		}

		@Override
		public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
			if (!this.active || !this.visible || !this.isMouseOver(mouseX, mouseY) || delta == 0) {
				return false;
			}
			step(delta > 0 ? 1 : -1);
			return true;
		}

		@Override
		public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
			if (!this.isFocused()) {
				return false;
			}
			if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN) {
				step(-1);
				return true;
			}
			if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_UP) {
				step(1);
				return true;
			}
			return false;
		}

		@Override
		protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
			int x = this.getX();
			int y = this.getY();
			int w = this.getWidth();
			int h = this.getHeight();
			boolean hover = this.isHoveredOrFocused();
			g.fill(x, y, x + w, y + h, 0xFF0C0F13);
			g.renderOutline(x, y, w, h, hover ? ACCENT : CHIP_BORDER);
			boolean overLeft = this.isHovered() && mouseX < x + ARROW;
			boolean overRight = this.isHovered() && mouseX >= x + w - ARROW;
			g.fill(x + 1, y + 1, x + ARROW, y + h - 1, overLeft ? CHIP_HOVER : CHIP);
			g.fill(x + w - ARROW, y + 1, x + w - 1, y + h - 1, overRight ? CHIP_HOVER : CHIP);
			Font font = net.minecraft.client.Minecraft.getInstance().font;
			int textY = y + (h - 8) / 2;
			int value = this.getter.getAsInt();
			g.drawString(font, "‹", x + 4, textY, value > this.min ? TEXT : DIM, false);
			g.drawString(font, "›", x + w - ARROW + 4, textY, value < this.max ? TEXT : DIM, false);
			String text = Integer.toString(value);
			g.drawString(font, text, x + (w - font.width(text)) / 2, textY, TEXT, false);
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			output.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE,
					Component.literal(this.getMessage().getString() + " " + this.getter.getAsInt()));
		}
	}

	static int blend(int a, int b, float t) {
		int ar = a >> 16 & 0xFF;
		int ag = a >> 8 & 0xFF;
		int ab = a & 0xFF;
		int br = b >> 16 & 0xFF;
		int bg = b >> 8 & 0xFF;
		int bb = b & 0xFF;
		int r = (int) (ar + (br - ar) * t);
		int gr = (int) (ag + (bg - ag) * t);
		int bl = (int) (ab + (bb - ab) * t);
		return 0xFF000000 | r << 16 | gr << 8 | bl;
	}

	static int darken(int color) {
		return blend(color, 0xFF000000, 0.25F);
	}

	static int brighten(int color) {
		return blend(color, 0xFFFFFFFF, 0.15F);
	}

	/** {@code color} with its alpha replaced by {@code alpha} (0..1). */
	public static int withAlpha(int color, float alpha) {
		int a = Math.max(0, Math.min(255, (int) (alpha * 255)));
		return a << 24 | color & 0xFFFFFF;
	}
}
