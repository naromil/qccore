package com.qccore.client;

import com.qccore.client.QcUi.Chip;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/**
 * A searchable grid of every block that has an item, modded ones included: type to filter by id,
 * click a block to choose it. Opened from the block chooser for one role; the role's hint
 * ({@code stairs}, {@code slab}, ...) pre-fills the search and can be cleared.
 */
public class QcBlockPickerScreen extends Screen {
	private static final int CELL = 20;
	private static final List<String> ALL = new ArrayList<>();

	private final Screen parent;
	private final Consumer<String> onPick;
	private final String role;
	private EditBox search;
	private String query;
	private List<String> matches = List.of();
	private int page;
	private int columns;
	private int rows;
	private int gridLeft;
	private int gridTop;

	public QcBlockPickerScreen(Screen parent, String role, String hint, Consumer<String> onPick) {
		super(Component.literal("Choose a block"));
		this.parent = parent;
		this.role = role;
		this.onPick = onPick;
		this.query = hint == null ? "" : switch (hint) {
			case "stairs" -> "_stairs";
			case "slab" -> "_slab";
			case "door" -> "_door";
			case "trapdoor" -> "trapdoor";
			case "fence" -> "fence";
			case "wall" -> "_wall";
			case "light" -> "lantern";
			default -> "";
		};
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private static List<String> all() {
		if (ALL.isEmpty()) {
			for (Block block : BuiltInRegistries.BLOCK) {
				if (block.asItem() != Items.AIR) {
					ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
					ALL.add(id.getNamespace().equals("minecraft") ? id.getPath() : id.toString());
				}
			}
		}
		return ALL;
	}

	@Override
	protected void init() {
		this.columns = Math.max(4, (this.width - 40) / CELL);
		this.rows = Math.max(3, (this.height - 17 - 24 - 46) / CELL);
		this.gridLeft = (this.width - this.columns * CELL) / 2;
		this.gridTop = 17 + 26;
		this.search = new EditBox(this.font, this.gridLeft, 19, Math.min(220, this.columns * CELL - 110), 16, Component.literal("search"));
		this.search.setValue(this.query);
		this.search.setHint(Component.literal("search block ids").withStyle(st -> st.withColor(0x667080)));
		this.search.setResponder(text -> {
			this.query = text;
			this.page = 0;
			filter();
		});
		this.addRenderableWidget(this.search);
		this.setInitialFocus(this.search);
		int right = this.gridLeft + this.columns * CELL;
		this.addRenderableWidget(new Chip(right - 104, 19, 50, 16, "‹ page", () -> this.page = Math.max(0, this.page - 1)));
		this.addRenderableWidget(new Chip(right - 52, 19, 52, 16, "page ›", () -> {
			if ((this.page + 1) * this.columns * this.rows < this.matches.size()) {
				this.page++;
			}
		}));
		this.addRenderableWidget(new Chip(this.gridLeft, this.height - 20, 70, 17, "Cancel", this::onClose));
		filter();
		// a smaller window holds fewer blocks per page: stay on a page that still exists
		int perPage = this.columns * this.rows;
		this.page = Math.min(this.page, Math.max(0, (this.matches.size() - 1) / perPage));
	}

	private void filter() {
		String q = this.query.trim().toLowerCase(Locale.ROOT);
		List<String> out = new ArrayList<>();
		for (String id : all()) {
			if (q.isEmpty() || id.contains(q)) {
				out.add(id);
			}
		}
		this.matches = out;
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		this.renderBackground(g);
		QcUi.titleBar(g, this.font, this, "for " + this.role);
		int perPage = this.columns * this.rows;
		int from = this.page * perPage;
		String hovered = null;
		for (int i = 0; i < perPage && from + i < this.matches.size(); i++) {
			String id = this.matches.get(from + i);
			int x = this.gridLeft + (i % this.columns) * CELL;
			int y = this.gridTop + (i / this.columns) * CELL;
			boolean over = mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL;
			g.fill(x, y, x + CELL - 2, y + CELL - 2, over ? 0x60FFFFFF : 0x30000000);
			ItemStack stack = QcUi.blockIcon(id);
			g.renderItem(stack, x + 1, y + 1);
			if (over) {
				hovered = id;
			}
		}
		int pages = Math.max(1, (this.matches.size() + perPage - 1) / perPage);
		g.drawString(this.font, this.matches.size() + " blocks · page " + (this.page + 1) + "/" + pages,
				this.gridLeft + 80, this.height - 15, QcUi.MUTED, false);
		super.render(g, mouseX, mouseY, delta);
		if (hovered != null) {
			g.renderTooltip(this.font, Component.literal(hovered), mouseX, mouseY);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int perPage = this.columns * this.rows;
		int col = (int) Math.floor((mouseX - this.gridLeft) / CELL);
		int row = (int) Math.floor((mouseY - this.gridTop) / CELL);
		if (col >= 0 && col < this.columns && row >= 0 && row < this.rows) {
			int index = this.page * perPage + row * this.columns + col;
			if (index < this.matches.size()) {
				String id = this.matches.get(index);
				this.minecraft.setScreen(this.parent);
				this.onPick.accept(id);
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
		int perPage = this.columns * this.rows;
		if (delta < 0 && (this.page + 1) * perPage < this.matches.size()) {
			this.page++;
		} else if (delta > 0 && this.page > 0) {
			this.page--;
		}
		return true;
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(this.parent);
	}
}
