package com.qccore.fabric;

import com.qccore.client.QCCoreClient;
import com.qccore.client.QcHud;
import com.qccore.client.QcInteraction;
import com.qccore.client.QcKeys;
import com.qccore.client.QcOverlayRenderer;
import com.qccore.net.QcPackets;
import com.qccore.net.QcPackets.QcPacket;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.KeyMapping;

/**
 * The Fabric client entry point. Mouse input reaches {@link QcInteraction#click} through
 * {@link com.qccore.fabric.mixin.MinecraftClientMixin}.
 */
public class QCCoreFabricClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		QCCoreFabric.clientSender = packet -> ClientPlayNetworking.send(packet.type().id(), QCCoreFabric.encode(packet));
		for (KeyMapping mapping : QcKeys.create()) {
			KeyBindingHelper.registerKeyBinding(mapping);
		}
		ClientTickEvents.END_CLIENT_TICK.register(QcInteraction::onClientTick);
		WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> QcOverlayRenderer.render(context.matrixStack(), context.camera()));
		HudRenderCallback.EVENT.register(QcHud::render);
		for (QcPackets.Type<?> type : QcPackets.S2C) {
			ClientPlayNetworking.registerGlobalReceiver(type.id(), (client, handler, buf, sender) -> {
				QcPacket packet = type.read(buf);
				client.execute(() -> QCCoreClient.handle(packet));
			});
		}
		QCCoreClient.initialized();
	}
}
