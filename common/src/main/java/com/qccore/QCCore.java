package com.qccore;

import com.qccore.net.QcCommands;
import com.qccore.net.QcServerNetworking;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mod-wide constants shared by both loaders. The loader entry points ({@code com.qccore.fabric.QCCoreFabric},
 * {@code com.qccore.forge.QCCoreForge}) install {@link com.qccore.platform.QcPlatform}, register the packets,
 * {@link QcCommands} and the {@link QcServerNetworking} hooks with their own APIs, then call {@link #initialized()}.
 */
public final class QCCore {
	public static final String MOD_ID = "qccore";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private QCCore() {
	}

	public static void initialized() {
		LOGGER.info("QCCore initialized");
	}

	public static ResourceLocation id(String path) {
		return new ResourceLocation(MOD_ID, path);
	}
}
