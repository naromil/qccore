package com.qccore.client;

import com.qccore.core.QcUnit;
import com.qccore.core.UnitPos;
import com.qccore.core.UnitSide;
import com.qccore.core.plan.BuildingSpec;
import com.qccore.core.plan.Cell;
import com.qccore.core.plan.Flags;
import com.qccore.core.plan.QcPlan;
import com.qccore.client.QcUi.Chip;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The tweak tool's screen for one planned unit (T26, step 1): per side, the partition it shares with
 * a unit of the same building (wall, gate, open, or the design's) and, on an outside ground-storey
 * face, the door (here, never, or auto: the design places the doors). Below, the bulk partition the
 * tweak tool sets with a shape of two or more units. Every change is one undoable plan edit.
 */
public class QcTweakScreen extends Screen {
	private static final int WIDTH = 304;
	private static final int ROW = 16;
	private static final int CHIP_H = 13;
	private static final int SIDE_W = 34;
	private static final int PART_X = 44;
	private static final int PART_W = 34;
	private static final int DOOR_X = 196;
	private static final int DOOR_W = 32;
	private static final UnitSide[] SIDES = {UnitSide.NORTH, UnitSide.EAST, UnitSide.SOUTH, UnitSide.WEST};
	private static final QcPlan.Partition[] PARTITIONS = {QcPlan.Partition.WALL, QcPlan.Partition.GATE,
			QcPlan.Partition.OPEN, QcPlan.Partition.DESIGN};

	private final Cell cell;
	private int left;
	private int top;
	private int bulkTop;

	public QcTweakScreen(Cell cell) {
		super(Component.literal("Tweak unit"));
		this.cell = cell;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		this.left = this.width / 2 - WIDTH / 2;
		int content = 28 + 4 * ROW + 14 + 6 + 34;
		this.top = 18 + Math.max(0, (this.height - 18 - 24 - content) / 2);
		this.bulkTop = this.top + 28 + 4 * ROW + 14 + 6;
		int y = this.top + 28;
		for (UnitSide side : SIDES) {
			Cell other = neighbour(side);
			if (sameBuilding(other)) {
				for (int i = 0; i < PARTITIONS.length; i++) {
					QcPlan.Partition partition = PARTITIONS[i];
					this.addRenderableWidget(new Chip(this.left + PART_X + i * (PART_W + 2), y, PART_W, CHIP_H,
							label(partition), () -> QcInteraction.editFromScreen(() ->
									QcClientState.plan.partition(Set.of(this.cell, other), partition) > 0))
							.selected(() -> partition == partitionOf(side)).accent(QcUi.color(QcTool.TWEAK))
							.tip(partitionTip(partition)));
				}
			} else if (ground()) {
				String[] modes = {QcPlan.DOOR_HERE, QcPlan.DOOR_NEVER, null};
				for (int i = 0; i < modes.length; i++) {
					String mode = modes[i];
					this.addRenderableWidget(new Chip(this.left + DOOR_X + i * (DOOR_W + 2), y, DOOR_W, CHIP_H,
							mode == null ? "auto" : mode, () -> QcInteraction.editFromScreen(() ->
									QcClientState.plan.setDoor(this.cell, side, mode)))
							.selected(() -> java.util.Objects.equals(mode, QcClientState.plan.door(this.cell, side)))
							.accent(QcUi.OK).tip(doorTip(mode)));
				}
			}
			y += ROW;
		}

		int inner = WIDTH - 8;
		int quarter = (inner - 6) / 4;
		y = this.bulkTop + QcUi.HEADER_HEIGHT + 4;
		for (int i = 0; i < PARTITIONS.length; i++) {
			QcPlan.Partition partition = PARTITIONS[i];
			this.addRenderableWidget(new Chip(this.left + 4 + i * (quarter + 2), y, quarter, CHIP_H,
					QcUi.partitionName(partition), () -> QcClientState.tweakPartition = partition)
					.selected(() -> QcClientState.tweakPartition == partition).accent(QcUi.color(QcTool.TWEAK))
					.tip("With a floor, line or box shape, the tweak tool sets every shared side inside it to: "
							+ partitionTip(partition).toLowerCase(java.util.Locale.ROOT)));
		}

		this.addRenderableWidget(new Chip(this.width / 2 - 35, this.height - 20, 70, 17, "Done", this::onClose)
				.tip("Close (Esc)"));
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		this.renderBackground(g);
		QcUi.titleBar(g, this.font, this, "each change is one undo step");
		QcPlan plan = QcClientState.plan;
		if (!plan.hasUnit(this.cell)) {
			// erased behind the screen (an undo): nothing left to tweak
			this.onClose();
			return;
		}
		QcUi.panel(g, this.font, this.left, this.top, WIDTH, 28 + 4 * ROW + 14,
				this.font.plainSubstrByWidth(heading(), WIDTH - 12), QcUi.color(QcTool.TWEAK));
		int head = this.top + QcUi.HEADER_HEIGHT + 3;
		g.drawString(this.font, "side", this.left + 6, head, QcUi.DIM, false);
		g.drawString(this.font, "partition (shared with this building)", this.left + PART_X, head, QcUi.DIM, false);
		g.drawString(this.font, "door", this.left + DOOR_X, head, QcUi.DIM, false);
		int y = this.top + 28;
		for (UnitSide side : SIDES) {
			g.drawString(this.font, side.name().toLowerCase(java.util.Locale.ROOT), this.left + 6, y + 3, QcUi.TEXT, false);
			Cell other = neighbour(side);
			if (!sameBuilding(other)) {
				g.drawString(this.font, plan.hasUnit(other) ? "another building" : plan.roads().containsKey(other)
						? "outside · road" : "outside", this.left + PART_X + 2, y + 3, QcUi.MUTED, false);
				if (!ground()) {
					g.drawString(this.font, "upper storey", this.left + DOOR_X + 2, y + 3, QcUi.MUTED, false);
				}
			} else {
				g.drawString(this.font, "inside", this.left + DOOR_X + 2, y + 3, QcUi.MUTED, false);
			}
			y += ROW;
		}
		g.drawString(this.font, this.font.plainSubstrByWidth("Doors go on the ground storey, on a road or open face; "
				+ "\"here\" replaces the design's door.", WIDTH - 12), this.left + 6, y + 2, QcUi.DIM, false);

		QcUi.panel(g, this.font, this.left, this.bulkTop, WIDTH, 34, "Bulk partition: floor, line or box shape",
				QcUi.color(QcTool.TWEAK));
		super.render(g, mouseX, mouseY, delta);
	}

	private String heading() {
		QcPlan plan = QcClientState.plan;
		String key = plan.building(this.cell);
		BuildingSpec spec = key == null ? null : plan.buildings().get(key);
		String where = "unit " + this.cell.x() + ", " + this.cell.layer() + ", " + this.cell.z();
		return spec == null ? where : where + " · " + spec.template + " " + key + " (" + QcToolsScreen.units(plan, key) + ")";
	}

	private Cell neighbour(UnitSide side) {
		UnitPos pos = Flags.neighbour(this.cell.pos(), side);
		return Cell.of(pos, this.cell.layer());
	}

	/** A planned unit of this unit's building in {@code other}: the side between them is a partition. */
	private boolean sameBuilding(Cell other) {
		QcPlan plan = QcClientState.plan;
		String key = plan.building(this.cell);
		return plan.hasUnit(other) && key != null && key.equals(plan.building(other));
	}

	/** This unit is on its building's ground storey (no unit of the building right below it). */
	private boolean ground() {
		QcPlan plan = QcClientState.plan;
		Cell below = new Cell(this.cell.x(), this.cell.layer() - 1, this.cell.z());
		String key = plan.building(this.cell);
		return !(plan.hasUnit(below) && key != null && key.equals(plan.building(below)));
	}

	private QcPlan.Partition partitionOf(UnitSide side) {
		Map<UnitPos, QcUnit> layer = QcClientState.plan.units().peekLayer(this.cell.layer());
		QcUnit unit = layer == null ? null : layer.get(this.cell.pos());
		String override = unit == null ? null : QcPlan.partyOverride(unit, side);
		if (override == null) {
			return QcPlan.Partition.DESIGN;
		}
		return switch (override) {
			case "wall" -> QcPlan.Partition.WALL;
			case "gate" -> QcPlan.Partition.GATE;
			default -> QcPlan.Partition.OPEN;
		};
	}

	private static String label(QcPlan.Partition partition) {
		return switch (partition) {
			case WALL -> "wall";
			case GATE -> "gate";
			case OPEN -> "open";
			case DESIGN -> "design";
		};
	}

	private static String partitionTip(QcPlan.Partition partition) {
		return switch (partition) {
			case WALL -> "A party wall";
			case GATE -> "A wall with a gate (rooms)";
			case OPEN -> "No wall (a hall)";
			case DESIGN -> "The building design's partition";
		};
	}

	private static String doorTip(String mode) {
		if (QcPlan.DOOR_HERE.equals(mode)) {
			return "A door on this face; it replaces the door the design would place";
		}
		if (QcPlan.DOOR_NEVER.equals(mode)) {
			return "Never a door on this face";
		}
		return "The design places the doors (facing the roads)";
	}
}
