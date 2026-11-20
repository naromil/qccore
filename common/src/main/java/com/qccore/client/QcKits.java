package com.qccore.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.qccore.QCCore;
import com.qccore.core.qc.BlockChoice;
import com.qccore.core.qc.Roles;
import com.qccore.platform.QcPlatform;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Block kits: one style's chosen blocks, saved as editable JSON in
 * {@code <gameDir>/qccore/kits/<name>.json} so the same choice can fill any building or road later
 * (qckit's {@code blocks={...}} dicts, kept as files).
 *
 * <pre>
 * {"kind": "building" | "road", "blocks": {"frame": "minecraft:spruce_log", "floor": ..., ...}}
 * </pre>
 * A kit is whatever the player chose and saved; the read-only batch presets the chooser lists beside
 * them are {@link com.qccore.core.qc.Presets}.
 */
public final class QcKits {
	private static final String SUFFIX = ".json";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private QcKits() {
	}

	public static Path dir() {
		return QcPlatform.gameDir().resolve("qccore").resolve("kits");
	}

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

	/** Writes one style as a kit and returns the file. */
	public static Path save(String rawName, BlockChoice style) {
		JsonObject root = new JsonObject();
		root.addProperty("kind", style.kind());
		JsonObject blocks = new JsonObject();
		style.blocks().forEach(blocks::addProperty);
		root.add("blocks", blocks);
		Path file = dir().resolve(QcSchematics.normaliseName(rawName) + SUFFIX);
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(root, writer);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return file;
	}

	/** Reads a kit; throws {@link UncheckedIOException} or {@link IllegalArgumentException} when it cannot. */
	public static BlockChoice load(String name) {
		JsonObject root;
		try (Reader reader = Files.newBufferedReader(dir().resolve(name + SUFFIX), StandardCharsets.UTF_8)) {
			root = GSON.fromJson(reader, JsonObject.class);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		if (root == null || !root.has("blocks") || !root.get("blocks").isJsonObject()) {
			throw new IllegalArgumentException("kit " + name + " has no \"blocks\" (kits from before the rework are not styles)");
		}
		String kind = root.has("kind") && root.get("kind").isJsonPrimitive() ? root.get("kind").getAsString() : Roles.BUILDING;
		BlockChoice style = new BlockChoice(kind);
		for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("blocks").entrySet()) {
			if (entry.getValue().isJsonPrimitive()) {
				style.set(entry.getKey(), entry.getValue().getAsString());
			}
		}
		return style;
	}
}
