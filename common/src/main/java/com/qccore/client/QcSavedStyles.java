package com.qccore.client;

import com.qccore.QCCore;
import com.qccore.core.qc.BlockChoice;
import com.qccore.platform.QcPlatform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

/**
 * The plan's styles (the blocks chosen role by role) kept on disk in
 * {@code <gameDir>/qccore/styles.nbt}, so a restart or a crash does not lose them. A world change
 * already keeps them ({@link QcClientState#reset()}); this carries them over a restart too.
 *
 * <p>The block chooser writes the file when it closes; the editor reads it once, the first time it
 * opens after a launch, and only adds styles the plan does not have yet. Styles with no block chosen
 * are not written. Kits ({@link QcKits}) stay the way to keep a choice under a name of its own.
 */
public final class QcSavedStyles {
	private static final String FILE = "styles.nbt";

	private static boolean restored;

	private QcSavedStyles() {
	}

	public static Path file() {
		return QcPlatform.gameDir().resolve("qccore").resolve(FILE);
	}

	/** The styles that have at least one block chosen, by name. */
	static CompoundTag toNbt(Map<String, BlockChoice> styles) {
		CompoundTag tag = new CompoundTag();
		styles.forEach((name, style) -> {
			if (style != null && !style.isEmpty()) {
				tag.put(name, style.toNbt());
			}
		});
		return tag;
	}

	static Map<String, BlockChoice> fromNbt(CompoundTag tag) {
		Map<String, BlockChoice> styles = new LinkedHashMap<>();
		for (String name : tag.getAllKeys()) {
			if (tag.get(name) instanceof CompoundTag style) {
				BlockChoice choice = BlockChoice.fromNbt(style);
				if (!choice.isEmpty()) {
					styles.put(name, choice);
				}
			}
		}
		return styles;
	}

	/** Adds the saved styles the plan does not have yet; returns how many were added. */
	static int restoreInto(Map<String, BlockChoice> target, Map<String, BlockChoice> saved) {
		int added = 0;
		for (Map.Entry<String, BlockChoice> entry : saved.entrySet()) {
			BlockChoice present = target.get(entry.getKey());
			if (present == null || present.isEmpty()) {
				target.put(entry.getKey(), entry.getValue().copy());
				added++;
			}
		}
		return added;
	}

	/**
	 * Writes the plan's styles; a failure is logged and never interrupts the player. The saved file is
	 * read in first if this launch has not read it yet: the chooser can be opened from B before the
	 * first G, and writing then would replace the styles kept from last time with an empty set.
	 */
	public static void save(Map<String, BlockChoice> styles) {
		restoreOnce(styles);
		write(file(), styles);
	}

	static void write(Path file, Map<String, BlockChoice> styles) {
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling(FILE + ".tmp");
			NbtIo.writeCompressed(toNbt(styles), tmp.toFile());
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException | RuntimeException e) {
			QCCore.LOGGER.warn("qccore: could not save the styles to {}: {}", file, e.getMessage());
		}
	}

	/**
	 * {@link #restoreOnce} with a word for the player when anything came back. Called by every way
	 * into the plan's styles (G, and the B screen that leads to the block chooser).
	 */
	public static void restoreOnceAndSay(Map<String, BlockChoice> target) {
		int restored = restoreOnce(target);
		if (restored > 0) {
			QcClientState.say("QC: " + restored + " style(s) restored with the blocks you chose last time (Blocks…)");
		}
	}

	/**
	 * Once per launch: adds the saved styles to the plan. Returns how many were added (0 when the
	 * file is missing or unreadable, or on every later call).
	 */
	public static int restoreOnce(Map<String, BlockChoice> target) {
		if (restored) {
			return 0;
		}
		restored = true;
		return read(file(), target);
	}

	/** Adds the styles saved in {@code file} that {@code target} lacks; 0 when missing or unreadable. */
	static int read(Path file, Map<String, BlockChoice> target) {
		if (!Files.isRegularFile(file)) {
			return 0;
		}
		try {
			return restoreInto(target, fromNbt(NbtIo.readCompressed(file.toFile())));
		} catch (IOException | RuntimeException e) {
			QCCore.LOGGER.warn("qccore: could not read the saved styles in {}: {}", file, e.getMessage());
			return 0;
		}
	}
}
