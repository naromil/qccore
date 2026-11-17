package com.qccore.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.qccore.QCCore;
import com.qccore.core.QcStructure;
import com.qccore.core.nbt.StructureNbt;
import com.qccore.core.palette.BlockPalette;
import com.qccore.core.palette.QcComponent;
import com.qccore.core.plan.RoadStyle;
import com.qccore.platform.QcPlatform;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * QC kits: the in-game counterpart of qckit's per-build block choices, saved as editable JSON in
 * {@code <gameDir>/qccore/kits/<name>.json}.
 *
 * <pre>
 * {
 *   "palette": {"framework": id, "row": id, "column": id, "wall": id, "floor": id,
 *               "innerWall": file, "gate": file, "outerWall": file, "innerColumn": file},
 *   "roads":   {"street": {"floor": [l1, l2, l3, l4], "joint": [..4], "over": [..4],
 *                          "post": [[bottom, ..., top], ..4], "medallion": id, "centre": id}},
 *   "ground":  id
 * }
 * </pre>
 * Structure entries name {@code .nbt} files of the schematics folder; an empty string switches the
 * component off and a missing key leaves it as it is. As in qckit there are no preset kits: a kit is
 * whatever the player chose and saved.
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

	/** Writes the session palette, road styles and ground block as a kit. */
	public static Path save(String rawName) {
		JsonObject root = new JsonObject();
		BlockPalette palette = QcClientState.palette;
		JsonObject paletteJson = new JsonObject();
		paletteJson.addProperty("framework", palette.frameworkBlock().getRawId());
		paletteJson.addProperty("row", palette.rowBlock().getRawId());
		paletteJson.addProperty("column", palette.columnBlock().getRawId());
		paletteJson.addProperty("wall", palette.wallBlock().getRawId());
		paletteJson.addProperty("floor", palette.floorBlock().getRawId());
		putStructure(paletteJson, "innerWall", palette.innerWall());
		putStructure(paletteJson, "gate", palette.gate());
		putStructure(paletteJson, "outerWall", palette.outerWall());
		putStructure(paletteJson, "innerColumn", palette.innerColumn());
		root.add("palette", paletteJson);
		JsonObject roads = new JsonObject();
		for (Map.Entry<String, RoadStyle> entry : QcClientState.roadStyles.entrySet()) {
			roads.add(entry.getKey(), roadJson(entry.getValue()));
		}
		root.add("roads", roads);
		root.addProperty("ground", QcClientState.plan.ground == null ? "" : QcClientState.plan.ground);
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

	/**
	 * Loads a kit into the session: the palette ids, the structures it names, its road styles and
	 * its ground block. Returns the problems found (missing structure files, wrong sizes); the rest of
	 * the kit is applied regardless.
	 */
	public static List<String> load(String name) {
		List<String> problems = new ArrayList<>();
		JsonObject root;
		try (Reader reader = Files.newBufferedReader(dir().resolve(name + SUFFIX), StandardCharsets.UTF_8)) {
			root = GSON.fromJson(reader, JsonObject.class);
		} catch (IOException | RuntimeException e) {
			problems.add("could not read kit " + name + ": " + e.getMessage());
			return problems;
		}
		if (root == null) {
			problems.add("kit " + name + " is empty");
			return problems;
		}
		JsonObject paletteJson = object(root, "palette");
		if (paletteJson != null) {
			BlockPalette palette = QcClientState.palette;
			setId(paletteJson, "framework", palette.frameworkBlock());
			setId(paletteJson, "row", palette.rowBlock());
			setId(paletteJson, "column", palette.columnBlock());
			setId(paletteJson, "wall", palette.wallBlock());
			setId(paletteJson, "floor", palette.floorBlock());
			loadStructure(paletteJson, "innerWall", palette.innerWall(), problems);
			loadStructure(paletteJson, "gate", palette.gate(), problems);
			loadStructure(paletteJson, "outerWall", palette.outerWall(), problems);
			loadStructure(paletteJson, "innerColumn", palette.innerColumn(), problems);
		}
		JsonObject roads = object(root, "roads");
		if (roads != null) {
			for (Map.Entry<String, JsonElement> entry : roads.entrySet()) {
				if (entry.getValue().isJsonObject()) {
					QcClientState.roadStyles.put(entry.getKey(), road(entry.getValue().getAsJsonObject()));
				}
			}
			if (!QcClientState.roadStyles.containsKey(QcClientState.roadStyle) && !QcClientState.roadStyles.isEmpty()) {
				QcClientState.roadStyle = QcClientState.roadStyles.keySet().iterator().next();
			}
		}
		if (root.has("ground") && root.get("ground").isJsonPrimitive()) {
			QcClientState.plan.ground = root.get("ground").getAsString();
		}
		QcClientState.changed();
		return problems;
	}

	static JsonObject roadJson(RoadStyle style) {
		JsonObject json = new JsonObject();
		json.add("floor", array(style.floor));
		json.add("joint", array(style.joint));
		json.add("over", array(style.over));
		JsonArray posts = new JsonArray();
		for (List<String> column : style.post) {
			posts.add(array(column.toArray(new String[0])));
		}
		json.add("post", posts);
		json.addProperty("medallion", style.medallion);
		json.addProperty("centre", style.centre);
		return json;
	}

	static RoadStyle road(JsonObject json) {
		RoadStyle style = new RoadStyle();
		read(json, "floor", style.floor);
		read(json, "joint", style.joint);
		read(json, "over", style.over);
		if (json.has("post") && json.get("post").isJsonArray()) {
			JsonArray posts = json.getAsJsonArray("post");
			for (int i = 0; i < RoadStyle.LANES && i < posts.size(); i++) {
				if (posts.get(i).isJsonArray()) {
					for (JsonElement element : posts.get(i).getAsJsonArray()) {
						style.post[i].add(element.getAsString());
					}
				}
			}
		}
		style.medallion = string(json, "medallion");
		style.centre = string(json, "centre");
		return style;
	}

	private static void putStructure(JsonObject json, String key, QcComponent component) {
		String path = component.isEmpty() ? "" : component.getPath();
		if (path != null && !path.isEmpty() && !Files.isRegularFile(QcSchematics.dir().resolve(path))) {
			return;
		}
		json.addProperty(key, path == null ? "" : path);
	}

	private static void loadStructure(JsonObject json, String key, QcComponent component, List<String> problems) {
		if (!json.has(key) || !json.get(key).isJsonPrimitive()) {
			return;
		}
		String file = json.get(key).getAsString().trim();
		if (file.isEmpty()) {
			component.setExtracted(null);
			return;
		}
		try {
			QcStructure structure = StructureNbt.parseStructure(QcSchematics.load(QcSchematics.dir().resolve(file)));
			if (!component.isValidSize(structure.sizeX(), structure.sizeY(), structure.sizeZ())) {
				problems.add(key + ": " + file + " has the wrong size");
				return;
			}
			component.setStructure(structure, file);
		} catch (RuntimeException e) {
			problems.add(key + ": could not load " + file);
		}
	}

	private static void setId(JsonObject json, String key, com.qccore.core.palette.BlockComponent component) {
		if (json.has(key) && json.get(key).isJsonPrimitive()) {
			component.setId(json.get(key).getAsString());
		}
	}

	private static JsonObject object(JsonObject json, String key) {
		return json.has(key) && json.get(key).isJsonObject() ? json.getAsJsonObject(key) : null;
	}

	private static String string(JsonObject json, String key) {
		return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : "";
	}

	private static JsonArray array(String[] values) {
		JsonArray array = new JsonArray();
		for (String value : values) {
			array.add(value == null ? "" : value);
		}
		return array;
	}

	private static void read(JsonObject json, String key, String[] into) {
		if (!json.has(key) || !json.get(key).isJsonArray()) {
			return;
		}
		JsonArray array = json.getAsJsonArray(key);
		for (int i = 0; i < into.length && i < array.size(); i++) {
			into[i] = array.get(i).getAsString();
		}
	}
}
