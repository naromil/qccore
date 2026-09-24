package com.qccore.client;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/** The eight editor keybindings; {@link QcInteraction#onClientTick} pumps all of them. */
public final class QcKeys {
	public static final String CATEGORY = "key.categories.qccore";

	/** Toggle the in-world editor (entering captures the anchor, leaving keeps the layout). */
	public static KeyBinding editor;
	/** Move the session anchor to the crosshair. */
	public static KeyBinding reanchor;
	/** Copy the cursor layer into layer + 1. */
	public static KeyBinding duplicateLayer;
	/** Open {@link QcSaveScreen}. */
	public static KeyBinding save;
	/** Send the current layout to the server to be built into the world. */
	public static KeyBinding build;
	/** Open {@link QcConfigScreen}. */
	public static KeyBinding config;
	/** Open {@link QcAlignScreen} for the targeted placed unit. */
	public static KeyBinding unitAlign;
	/** Toggle {@link QcClientState#overlayVisible}. */
	public static KeyBinding overlay;

	private QcKeys() {
	}

	public static void register() {
		editor = register("key.qccore.editor", GLFW.GLFW_KEY_G);
		reanchor = register("key.qccore.reanchor", GLFW.GLFW_KEY_Y);
		duplicateLayer = register("key.qccore.duplicate_layer", GLFW.GLFW_KEY_H);
		save = register("key.qccore.save", GLFW.GLFW_KEY_J);
		build = register("key.qccore.build", GLFW.GLFW_KEY_K);
		config = register("key.qccore.config", GLFW.GLFW_KEY_I);
		unitAlign = register("key.qccore.unit_align", GLFW.GLFW_KEY_U);
		overlay = register("key.qccore.overlay", GLFW.GLFW_KEY_O);
	}

	private static KeyBinding register(String translationKey, int key) {
		return KeyBindingHelper.registerKeyBinding(
				new KeyBinding(translationKey, InputUtil.Type.KEYSYM, key, CATEGORY));
	}
}
