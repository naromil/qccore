package com.qccore.client;

import com.qccore.QCCore;
import com.qccore.core.nbt.PlanCodec;
import com.qccore.core.plan.QcPlan;
import com.qccore.platform.QcPlatform;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * QC templates: saved plans in {@code <gameDir>/qccore/templates/<name>.qct.nbt}, gzip NBT
 * {@code {"qcTemplate":2,"plan":PlanCodec}}. A template is geometry only: its buildings keep their
 * template, design overrides and style <em>names</em>, its roads their style names, but every
 * style's blocks are stripped ({@link QcPlan#stripped()}). Stamping it adds empty styles the player
 * fills in the block chooser; a style of the same name already chosen in the plan is used as it is.
 * It is stored in its normal form (minimum cell {@code (0, 1, 0)}), so it can be stamped anywhere
 * and in any rotation. Version-1 templates (with a QCSimple palette) still load; the palette is ignored.
 */
public final class QcTemplates {
	private static final String SUFFIX = ".qct.nbt";

	private QcTemplates() {
	}

	public static Path dir() {
		return QcPlatform.gameDir().resolve("qccore").resolve("templates");
	}

	/** The template names, sorted; a missing folder is empty. */
	public static List<String> list() {
		Path dir = dir();
		if (!Files.isDirectory(dir)) {
			return List.of();
		}
		try (Stream<Path> files = Files.list(dir)) {
			return files.filter(Files::isRegularFile)
					.map(file -> file.getFileName().toString())
					.filter(name -> name.toLowerCase(Locale.ROOT).endsWith(SUFFIX))
					.map(name -> name.substring(0, name.length() - SUFFIX.length()))
					.sorted()
					.toList();
		} catch (IOException e) {
			QCCore.LOGGER.warn("qccore: could not list {}: {}", dir, e.getMessage());
			return List.of();
		}
	}

	/** Saves the plan, normalised and with its blocks stripped, and returns the file. */
	public static Path save(String rawName, QcPlan plan) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("qcTemplate", 2);
		tag.put("plan", PlanCodec.toNbt(plan.stripped().normalized()));
		Path file = dir().resolve(QcSchematics.normaliseName(rawName) + SUFFIX);
		try {
			Files.createDirectories(file.getParent());
			NbtIo.writeCompressed(tag, file.toFile());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return file;
	}

	/** A loaded template: its normalised plan. */
	public record Template(String name, QcPlan plan) {
	}

	public static Template load(String name) {
		Path file = dir().resolve(name + SUFFIX);
		try {
			CompoundTag tag = NbtIo.readCompressed(file.toFile());
			QcPlan plan = PlanCodec.fromNbt(tag.getCompound("plan")).stripped().normalized();
			return new Template(name, plan);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public static void delete(String name) {
		try {
			Files.deleteIfExists(dir().resolve(name + SUFFIX));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
