package com.qccore.client;

import com.qccore.core.qc.BlockChoice;
import com.qccore.core.qc.Roles;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The styles kept over a restart: round trip, empty styles left out, the plan's own styles win. */
class QcSavedStylesTest {

	private static BlockChoice style(String kind, String role, String id) {
		BlockChoice choice = new BlockChoice(kind);
		choice.set(role, id);
		return choice;
	}

	@Test
	void chosenStylesSurviveARoundTripAndEmptyOnesAreLeftOut() {
		Map<String, BlockChoice> styles = new LinkedHashMap<>();
		styles.put("house", style(Roles.BUILDING, "frame", "minecraft:spruce_log[axis=y]"));
		styles.put("street", style(Roles.ROAD, "floor1", "minecraft:stone_bricks"));
		styles.put("hall", new BlockChoice(Roles.BUILDING));

		Map<String, BlockChoice> back = QcSavedStyles.fromNbt(QcSavedStyles.toNbt(styles));

		assertEquals(2, back.size());
		assertEquals("minecraft:spruce_log[axis=y]", back.get("house").get("frame"));
		assertEquals(Roles.ROAD, back.get("street").kind());
		assertEquals("minecraft:stone_bricks", back.get("street").get("floor1"));
		assertFalse(back.containsKey("hall"));
	}

	@Test
	void restoreFillsOnlyMissingOrEmptyStyles() {
		Map<String, BlockChoice> plan = new LinkedHashMap<>();
		plan.put("house", style(Roles.BUILDING, "frame", "minecraft:oak_log"));
		plan.put("hall", new BlockChoice(Roles.BUILDING));

		Map<String, BlockChoice> saved = new LinkedHashMap<>();
		saved.put("house", style(Roles.BUILDING, "frame", "minecraft:spruce_log"));
		saved.put("hall", style(Roles.BUILDING, "wall", "minecraft:bricks"));
		saved.put("street", style(Roles.ROAD, "floor1", "minecraft:stone_bricks"));

		assertEquals(2, QcSavedStyles.restoreInto(plan, saved));
		assertEquals("minecraft:oak_log", plan.get("house").get("frame"));
		assertEquals("minecraft:bricks", plan.get("hall").get("wall"));
		assertTrue(plan.containsKey("street"));
		assertNotSame(saved.get("street"), plan.get("street"));
	}

	@Test
	void malformedEntriesReadBackEmpty() {
		CompoundTag tag = new CompoundTag();
		tag.putString("house", "not a style");
		assertTrue(QcSavedStyles.fromNbt(tag).isEmpty());
		assertTrue(QcSavedStyles.fromNbt(new CompoundTag()).isEmpty());
	}

	@Test
	void aSaveBeforeTheFirstReadKeepsTheStylesFromLastTime(@TempDir Path dir) {
		Path file = dir.resolve("qccore").resolve("styles.nbt");
		Map<String, BlockChoice> lastTime = new LinkedHashMap<>();
		lastTime.put("house", style(Roles.BUILDING, "frame", "minecraft:spruce_log"));
		QcSavedStyles.write(file, lastTime);

		// a new launch: B -> Blocks… picks a road block before G ever read the file, then the chooser closes
		Map<String, BlockChoice> plan = new LinkedHashMap<>();
		plan.put("street", style(Roles.ROAD, "floor1", "minecraft:stone_bricks"));
		assertEquals(1, QcSavedStyles.read(file, plan));
		QcSavedStyles.write(file, plan);

		Map<String, BlockChoice> back = new LinkedHashMap<>();
		assertEquals(2, QcSavedStyles.read(file, back));
		assertEquals("minecraft:spruce_log", back.get("house").get("frame"));
		assertEquals("minecraft:stone_bricks", back.get("street").get("floor1"));
		assertEquals(0, QcSavedStyles.read(dir.resolve("missing.nbt"), back));
	}
}
