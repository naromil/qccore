package com.qccore.fabric.mixin;

import com.qccore.client.QcClientState;
import com.qccore.client.QcInteraction;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While the editor is active the mouse buttons edit the virtual layout instead of the world, so the
 * vanilla attack and use paths are cancelled at their head.
 */
@Mixin(Minecraft.class)
public class MinecraftClientMixin {
	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void qccore$attack(CallbackInfoReturnable<Boolean> cir) {
		Minecraft client = (Minecraft) (Object) this;
		if (QcClientState.active && client.screen == null) {
			QcInteraction.click(false);
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void qccore$use(CallbackInfo ci) {
		Minecraft client = (Minecraft) (Object) this;
		if (QcClientState.active && client.screen == null) {
			QcInteraction.click(true);
			ci.cancel();
		}
	}
}
