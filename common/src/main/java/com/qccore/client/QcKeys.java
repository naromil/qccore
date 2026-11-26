package com.qccore.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** The fourteen editor keybindings; {@link QcInteraction#onClientTick} pumps all of them. */
public final class QcKeys {
	public static final String CATEGORY = "key.categories.qccore";

	/** Toggle the in-world editor (entering captures the anchor, leaving keeps the layout). */
	public static KeyMapping editor;
	/** Move the session anchor to the crosshair. */
	public static KeyMapping reanchor;
	/** Copy the cursor layer into layer + 1. */
	public static KeyMapping duplicateLayer;
	/** Open {@link QcSaveScreen} (export the plan as a structure {@code .nbt}). */
	public static KeyMapping save;
	/** Send the whole plan to the server to be applied to the world. */
	public static KeyMapping build;
	/** Open {@link QcConfigScreen}. */
	public static KeyMapping config;
	/** Toggle {@link QcClientState#overlayVisible}. */
	public static KeyMapping overlay;
	/** Open {@link QcToolsScreen}: tools, shapes, components, modifiers, plan. */
	public static KeyMapping tools;
	/** Cycle the build shape. */
	public static KeyMapping shape;
	/** Cycle the tool. */
	public static KeyMapping tool;
	/** Undo the last plan edit; with Shift, redo. */
	public static KeyMapping undo;
	/** Turn the template a quarter clockwise. */
	public static KeyMapping rotate;
	/** Toggle the ghost-block preview of the plan. */
	public static KeyMapping preview;

	private QcKeys() {
	}

	/**
	 * Creates the fourteen keybindings once and returns them; the loader glue registers the list with
	 * its key-mapping API (Fabric {@code KeyBindingHelper}, Forge {@code RegisterKeyMappingsEvent}).
	 */
	public static List<KeyMapping> create() {
		if (editor != null) {
			return ALL;
		}
		editor = register("key.qccore.editor", GLFW.GLFW_KEY_G);
		reanchor = register("key.qccore.reanchor", GLFW.GLFW_KEY_Y);
		duplicateLayer = register("key.qccore.duplicate_layer", GLFW.GLFW_KEY_H);
		save = register("key.qccore.save", GLFW.GLFW_KEY_J);
		build = register("key.qccore.build", GLFW.GLFW_KEY_K);
		config = register("key.qccore.config", GLFW.GLFW_KEY_I);
		overlay = register("key.qccore.overlay", GLFW.GLFW_KEY_O);
		tools = register("key.qccore.tools", GLFW.GLFW_KEY_B);
		shape = register("key.qccore.shape", GLFW.GLFW_KEY_V);
		tool = register("key.qccore.tool", GLFW.GLFW_KEY_N);
		undo = register("key.qccore.undo", GLFW.GLFW_KEY_Z);
		rotate = register("key.qccore.rotate", GLFW.GLFW_KEY_R);
		preview = register("key.qccore.preview", GLFW.GLFW_KEY_M);
		return ALL;
	}

	private static final List<KeyMapping> ALL = new ArrayList<>();

	private static KeyMapping register(String translationKey, int key) {
		KeyMapping mapping = new KeyMapping(translationKey, InputConstants.Type.KEYSYM, key, CATEGORY);
		ALL.add(mapping);
		return mapping;
	}
}
