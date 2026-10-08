package dev.s1skate.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.s1skate.client.S1SkateClient;
import dev.s1skate.client.SkateController;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	/**
	 * F2 is also Minecraft's screenshot key, and global keys are handled before key mappings ever see them.
	 * In game with no screen open, the skateboard toggle wins; screenshots still work from menus, or rebind
	 * either key in Controls.
	 */
	@Inject(method = "handleGlobalKeyPress", at = @At("HEAD"), cancellable = true)
	private void s1skate$toggleKey(InputConstants.Key key, boolean controlDown, CallbackInfoReturnable<Boolean> cir) {
		Minecraft mc = (Minecraft) (Object) this;
		if (mc.player != null && mc.level != null && mc.gui.screen() == null) {
			if (S1SkateClient.TOGGLE.matches(key)) {
				SkateController.toggle();
				cir.setReturnValue(true);
			} else if (S1SkateClient.CYCLE.matches(key)) {
				SkateController.cycleBoard();
				cir.setReturnValue(true);
			}
		}
	}

	/** The use button is Schedule I's "secondary click" camera orbit while riding. */
	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void s1skate$noUseWhileRiding(CallbackInfo ci) {
		if (SkateController.isRiding()) ci.cancel();
	}
}
