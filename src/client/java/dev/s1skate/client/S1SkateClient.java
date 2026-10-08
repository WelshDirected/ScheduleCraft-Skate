package dev.s1skate.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.s1skate.S1Skate;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public class S1SkateClient implements ClientModInitializer {
	public static final KeyMapping.Category CATEGORY =
		KeyMapping.Category.register(Identifier.fromNamespaceAndPath(S1Skate.MOD_ID, "skateboard"));
	/** F2 by default, as asked; Minecraft's screenshot key is also F2, see MinecraftMixin. */
	public static final KeyMapping TOGGLE = KeyMappingHelper.registerKeyMapping(
		new KeyMapping("key.s1skate.toggle", InputConstants.Type.KEYBOARD, InputConstants.KEY_F2, CATEGORY));
	public static final KeyMapping CYCLE = KeyMappingHelper.registerKeyMapping(
		new KeyMapping("key.s1skate.cycle", InputConstants.Type.KEYBOARD, InputConstants.KEY_F6, CATEGORY));

	private static float lastFrameDt;

	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			// only reached when the toggle is rebound to a key that isn't a global one
			while (TOGGLE.consumeClick()) SkateController.toggle();
			while (CYCLE.consumeClick()) SkateController.cycleBoard();
			if (SkateController.isRiding() && (mc.player == null || mc.level == null)) SkateController.dismount(false);
			SelfTest.tick(mc);
		});
		LevelRenderEvents.COLLECT_SUBMITS.register(ctx -> {
			if (!SkateController.isRiding()) return;
			Minecraft mc = Minecraft.getInstance();
			float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
			SkateController.render(ctx.poseStack(), ctx.submitNodeCollector(), ctx.levelState().cameraRenderState.pos, pt, lastFrameDt);
		});
	}

	/** Frame delta measured once per frame by the camera hook and reused by the board visuals. */
	public static float frame() {
		lastFrameDt = SkateController.frameDelta();
		return lastFrameDt;
	}
}
