package com.qccore;

import com.qccore.net.QcCommands;
import com.qccore.net.QcServerNetworking;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QCCore implements ModInitializer {
	public static final String MOD_ID = "qccore";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		QcServerNetworking.register();
		QcCommands.register();
		LOGGER.info("QCCore initialized");
	}

	public static Identifier id(String path) {
		return new Identifier(MOD_ID, path);
	}
}
