package com.qccore.client;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.qccore.QCCore;
import com.qccore.core.nbt.StructureNbt;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.NbtCompound;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * The schematic folder layouts are saved into and picked from: {@code <gameDir>/schematics} by
 * default, which is also Litematica's default, overridable through {@code config/qccore.json}.
 */
public final class QcSchematics {
	private static final String DEFAULT_DIRECTORY = "schematics";
	private static final String CONFIG_DIRECTORY = "config";
	private static final String CONFIG_FILE = "qccore.json";
	private static final String CONFIG_KEY = "schematicsDir";
	private static final String DEFAULT_NAME = "qc_layout";
	private static final String SUFFIX = ".nbt";

	private QcSchematics() {
	}

	/** The folder layouts are read from and written to. */
	public static Path dir() {
		Path gameDir = FabricLoader.getInstance().getGameDir();
		Path defaultDir = gameDir.resolve(DEFAULT_DIRECTORY);
		Path config = gameDir.resolve(CONFIG_DIRECTORY).resolve(CONFIG_FILE);
		if (!Files.isRegularFile(config)) {
			return defaultDir;
		}
		try (Reader reader = Files.newBufferedReader(config, StandardCharsets.UTF_8)) {
			JsonObject root = new Gson().fromJson(reader, JsonObject.class);
			JsonElement element = root == null ? null : root.get(CONFIG_KEY);
			if (element == null || !element.isJsonPrimitive()) {
				return defaultDir;
			}
			String raw = element.getAsString().trim();
			return raw.isEmpty() ? defaultDir : gameDir.resolve(raw).normalize();
		} catch (IOException | RuntimeException e) {
			QCCore.LOGGER.warn("qccore: could not read config/qccore.json: {}", e.getMessage());
			return defaultDir;
		}
	}

	/** Every {@code .nbt} file of {@link #dir()}, by file name; a missing folder is simply empty. */
	public static List<Path> list() {
		Path dir = dir();
		if (!Files.isDirectory(dir)) {
			return List.of();
		}
		try (Stream<Path> files = Files.list(dir)) {
			return files.filter(Files::isRegularFile)
					.filter(file -> file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(SUFFIX))
					.sorted(Comparator.comparing(file -> file.getFileName().toString()))
					.toList();
		} catch (IOException e) {
			QCCore.LOGGER.warn("qccore: could not list {}: {}", dir, e.getMessage());
			return List.of();
		}
	}

	/** The file name a layout is saved under: lower case, safe characters, without the suffix. */
	public static String normaliseName(String raw) {
		String name = (raw == null ? "" : raw).trim().toLowerCase(Locale.ROOT);
		StringBuilder safe = new StringBuilder(name.length());
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			safe.append(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '.' || c == '_' || c == '-' ? c : '_');
		}
		String normalised = safe.toString();
		if (normalised.endsWith(SUFFIX)) {
			normalised = normalised.substring(0, normalised.length() - SUFFIX.length());
		}
		return normalised.isEmpty() ? DEFAULT_NAME : normalised;
	}

	/** Writes {@code tag} into {@link #dir()} and returns the file it landed in. */
	public static Path save(String rawName, NbtCompound tag) {
		Path dir = dir();
		try {
			Files.createDirectories(dir);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		Path file = dir.resolve(normaliseName(rawName) + SUFFIX);
		StructureNbt.writeFile(tag, file);
		return file;
	}

	/** Reads a layout or component file back. */
	public static NbtCompound load(Path file) {
		return StructureNbt.readFile(file);
	}
}
