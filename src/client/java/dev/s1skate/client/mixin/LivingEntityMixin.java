package dev.s1skate.client.mixin;

import dev.s1skate.client.SkateController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The local player's own movement is replaced by the skateboard sim while riding. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

	private boolean s1skate$isRidingPlayer() {
		return SkateController.isRiding() && (Object) this == Minecraft.getInstance().player;
	}

	@Inject(method = "travel", at = @At("HEAD"), cancellable = true)
	private void s1skate$travel(Vec3 input, CallbackInfo ci) {
		if (s1skate$isRidingPlayer()) {
			SkateController.travel((LocalPlayer) (Object) this);
			ci.cancel();
		}
	}

	@Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
	private void s1skate$noVanillaJump(CallbackInfo ci) {
		if (s1skate$isRidingPlayer()) ci.cancel(); // space charges the ollie instead
	}

	@Inject(method = "calculateEntityAnimation", at = @At("HEAD"), cancellable = true)
	private void s1skate$standStill(boolean useY, CallbackInfo ci) {
		if (s1skate$isRidingPlayer()) {
			((LivingEntity) (Object) this).walkAnimation.stop(); // no walking legs while rolling
			ci.cancel();
		}
	}
}
