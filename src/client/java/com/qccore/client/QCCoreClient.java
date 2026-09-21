package com.qccore.client;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QCCoreClient implements ClientModInitializer {
	private static final Logger LOGGER = LoggerFactory.getLogger("qccore/client");

	@Override
	public void onInitializeClient() {
		LOGGER.info("QCCore client initialized");
	}
}
