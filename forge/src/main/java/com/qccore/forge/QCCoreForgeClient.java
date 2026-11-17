package com.qccore.forge;

import com.qccore.QCCore;
import com.qccore.client.QCCoreClient;
import com.qccore.client.QcClientState;
import com.qccore.client.QcHud;
import com.qccore.client.QcInteraction;
import com.qccore.client.QcKeys;
import com.qccore.client.QcOverlayRenderer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * The Forge client glue: keybindings, the HUD overlay, the world overlay after the translucent
 * terrain, the client tick, and the mouse input that Fabric routes through {@code MinecraftClientMixin}.
 *
 * <p>Input, without a mixin: while a session is active and no screen is open,
 * <ul>
 *   <li>an attack press is consumed from {@code keyAttack} at the start of the client tick, before
 *       vanilla's {@code handleKeybinds} sees it, and becomes {@code QcInteraction.click(false)} - one
 *       click per press, like the Fabric mixin on {@code startAttack};</li>
 *   <li>the use key reaches {@code startUseItem}, whose {@link InputEvent.InteractionKeyMappingTriggered}
 *       (main hand) becomes {@code QcInteraction.click(true)} and is cancelled - holding the key repeats
 *       it, as the multi-point shapes expect;</li>
 *   <li>every other attack event (holding the key on a block, {@code continueAttack}) is cancelled
 *       without a click, so the world is never mined during a session.</li>
 * </ul>
 */
public final class QCCoreForgeClient {

	private QCCoreForgeClient() {
	}

	static void init(IEventBus modBus) {
		modBus.addListener(QCCoreForgeClient::onRegisterKeys);
		modBus.addListener(QCCoreForgeClient::onRegisterOverlays);
		IEventBus forgeBus = MinecraftForge.EVENT_BUS;
		forgeBus.addListener(QCCoreForgeClient::onClientTick);
		forgeBus.addListener(QCCoreForgeClient::onRenderLevel);
		forgeBus.addListener(QCCoreForgeClient::onInteraction);
		QCCoreClient.initialized();
	}

	private static void onRegisterKeys(RegisterKeyMappingsEvent event) {
		for (KeyMapping mapping : QcKeys.create()) {
			event.register(mapping);
		}
	}

	private static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
		event.registerAboveAll(QCCore.MOD_ID + "_hud",
				(gui, graphics, partialTick, width, height) -> QcHud.render(graphics, partialTick));
	}

	private static boolean editing(Minecraft client) {
		return QcClientState.active && client.screen == null && client.player != null;
	}

	private static void onClientTick(TickEvent.ClientTickEvent event) {
		Minecraft client = Minecraft.getInstance();
		if (event.phase == TickEvent.Phase.START) {
			if (editing(client) && client.getOverlay() == null) {
				while (client.options.keyAttack.consumeClick()) {
					QcInteraction.click(false);
				}
			}
			return;
		}
		QcInteraction.onClientTick(client);
	}

	private static void onRenderLevel(RenderLevelStageEvent event) {
		if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
			QcOverlayRenderer.render(event.getPoseStack(), event.getCamera());
		}
	}

	private static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
		Minecraft client = Minecraft.getInstance();
		if (!editing(client) || event.isPickBlock()) {
			return;
		}
		if (event.isUseItem() && event.getHand() == InteractionHand.MAIN_HAND) {
			QcInteraction.click(true);
		}
		event.setSwingHand(false);
		event.setCanceled(true);
	}
}
