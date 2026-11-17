package com.qccore.client;

import com.qccore.QCCore;
import com.qccore.core.nbt.PaletteCodec;
import com.qccore.core.nbt.PlanCodec;
import com.qccore.core.palette.BlockPalette;
import com.qccore.core.plan.QcPlan;
import com.qccore.platform.QcPlatform;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * QC unit templates: saved plans in {@code <gameDir>/qccore/templates/<name>.qct.nbt}, gzip NBT
 * {@code {"qcTemplate":1,"plan":PlanCodec,"palette":PaletteCodec}}. A template is stored in its
 * normal form (minimum cell {@code (0, 1, 0)}), so it can be stamped anywhere and in any rotation;
 * the palette it was designed with travels along and can be adopted on demand.
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

	/** Saves the plan (normalised) with the palette and returns the file. */
	public static Path save(String rawName, QcPlan plan, BlockPalette palette) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("qcTemplate", 1);
		tag.put("plan", PlanCodec.toNbt(plan.normalized()));
		tag.put("palette", PaletteCodec.toNbt(palette));
		Path file = dir().resolve(QcSchematics.normaliseName(rawName) + SUFFIX);
		try {
			Files.createDirectories(file.getParent());
			NbtIo.writeCompressed(tag, file.toFile());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return file;
	}

	/** A loaded template: its normalised plan and the palette it was saved with. */
	public record Template(String name, QcPlan plan, @Nullable CompoundTag palette) {
	}

	public static Template load(String name) {
		Path file = dir().resolve(name + SUFFIX);
		try {
			CompoundTag tag = NbtIo.readCompressed(file.toFile());
			QcPlan plan = PlanCodec.fromNbt(tag.getCompound("plan")).normalized();
			return new Template(name, plan, tag.contains("palette") ? tag.getCompound("palette") : null);
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

	/** Copies a saved palette into the session palette. */
	public static void adoptPalette(CompoundTag paletteTag) {
		BlockPalette loaded = PaletteCodec.fromNbt(paletteTag);
		copyPalette(loaded, QcClientState.palette);
	}

	static void copyPalette(BlockPalette from, BlockPalette to) {
		to.frameworkBlock().setId(from.frameworkBlock().getRawId());
		to.rowBlock().setId(from.rowBlock().getRawId());
		to.columnBlock().setId(from.columnBlock().getRawId());
		to.wallBlock().setId(from.wallBlock().getRawId());
		to.floorBlock().setId(from.floorBlock().getRawId());
		copyStructure(from.innerWall(), to.innerWall());
		copyStructure(from.gate(), to.gate());
		copyStructure(from.outerWall(), to.outerWall());
		copyStructure(from.innerColumn(), to.innerColumn());
	}

	private static void copyStructure(com.qccore.core.palette.QcComponent from, com.qccore.core.palette.QcComponent to) {
		if (from.isEmpty()) {
			to.setExtracted(null);
		} else {
			to.setStructure(from.getStructure(), from.getPath());
		}
	}
}
