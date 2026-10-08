package dev.s1skate.client.mixin;

import dev.s1skate.client.S1SkateClient;
import dev.s1skate.client.SkateCamera;
import dev.s1skate.client.SkateController;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** While riding, Schedule I's skateboard camera replaces Minecraft's first/third-person camera. */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	private boolean detached;

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Shadow
	protected abstract void setPosition(double x, double y, double z);

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void s1skate$skateCamera(float partialTicks, CallbackInfo ci) {
		float dt = S1SkateClient.frame();
		if (!SkateController.isRiding() || !SkateCamera.isActive()) return;
		double[] c = SkateCamera.update(partialTicks, dt);
		if (c == null) return;
		this.setRotation((float) c[3], (float) c[4]);
		this.setPosition(c[0], c[1], c[2]);
		this.detached = true; // render the rider
	}

	@Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
	private void s1skate$speedFov(float partialTicks, CallbackInfoReturnable<Float> cir) {
		if (SkateController.isRiding()) cir.setReturnValue(cir.getReturnValueF() * SkateCamera.fovMultiplier());
	}
}
