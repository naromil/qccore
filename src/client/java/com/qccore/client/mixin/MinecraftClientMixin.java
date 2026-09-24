package com.qccore.client.mixin;

import com.qccore.client.QcClientState;
import com.qccore.client.QcInteraction;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While the editor is active the mouse buttons edit the virtual layout instead of the world, so the
 * vanilla attack and use paths are cancelled at their head.
 */
@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
	@Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
	private void qccore$attack(CallbackInfoReturnable<Boolean> cir) {
		MinecraftClient client = (MinecraftClient) (Object) this;
		if (QcClientState.active && client.currentScreen == null) {
			QcInteraction.click(false);
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
	private void qccore$use(CallbackInfo ci) {
		MinecraftClient client = (MinecraftClient) (Object) this;
		if (QcClientState.active && client.currentScreen == null) {
			QcInteraction.click(true);
			ci.cancel();
		}
	}
}
