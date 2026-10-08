package dev.s1skate.client.mixin;

import dev.s1skate.client.SkateCamera;
import dev.s1skate.client.SkateController;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mouse look drives the skateboard camera instead of the player while riding. */
@Mixin(Entity.class)
public abstract class EntityMixin {
	@Inject(method = "turn", at = @At("HEAD"), cancellable = true)
	private void s1skate$turn(double xo, double yo, CallbackInfo ci) {
		if (SkateController.isRiding() && (Object) this == Minecraft.getInstance().player) {
			SkateCamera.mouse(xo, yo);
			ci.cancel();
		}
	}
}
