package dev.s1skate.client;

import dev.s1skate.client.sim.SkateSim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dev-only scripted ride (enabled with -Ds1skate.selftest): builds a smooth-stone pad with a slab step and
 * a grass strip on the integrated server, mounts, pushes, carves, ollies and steps off, logging the sim
 * state and taking screenshots. Run with `gradlew runSelftest`; never active in a normal game.
 */
public final class SelfTest {
	private static final Logger LOG = LoggerFactory.getLogger("s1skate-selftest");
	public static final boolean ENABLED = System.getProperty("s1skate.selftest") != null;

	private static int tick = -1;
	private static int px, pz;
	private static final int Y = 150;
	private static double maxY, groundY;

	private SelfTest() {
	}

	public static void tick(Minecraft mc) {
		if (!ENABLED || mc.player == null || mc.level == null) return;
		tick++;
		var o = mc.options;
		SkateSim sim = SkateController.sim();
		switch (tick) {
			case 60 -> {
				px = mc.player.getBlockX();
				pz = mc.player.getBlockZ();
				net.minecraft.client.KeyMapping.click(net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.getBoundKeyOf(o.keyToggleGui));
				cmd(mc, "time set noon");
				cmd(mc, "weather clear");
				cmd(mc, String.format("fill %d %d %d %d %d %d air", px - 30, Y + 1, pz - 8, px + 30, Y + 8, pz + 70));
				cmd(mc, String.format("fill %d %d %d %d %d %d smooth_stone", px - 30, Y, pz - 8, px + 30, Y, pz + 70));
				cmd(mc, String.format("fill %d %d %d %d %d %d smooth_stone_slab", px + 10, Y + 1, pz + 40, px + 30, Y + 1, pz + 40));
				cmd(mc, String.format("fill %d %d %d %d %d %d grass_block", px + 10, Y, pz + 50, px + 30, Y, pz + 70));
				cmd(mc, String.format("tp @p %d.5 %d %d.5 0 20", px - 15, Y + 1, pz));
			}
			case 100 -> {
				LOG.info("phase ride: mounting at {}", mc.player.position());
				SkateController.toggle();
				LOG.info("riding={} board={} fromPack={}", SkateController.isRiding(),
					SkateController.isRiding() ? SkateController.sim().def.key : "-", BoardRegistry.isFromPack());
			}
			case 105 -> Screenshot.grab(mc, false);
			case 110 -> o.keyUp.setDown(true);
			case 170 -> Screenshot.grab(mc, false);
			case 200 -> {
				o.keyUp.setDown(false);
				o.keyJump.setDown(true);
				LOG.info("phase ollie: charging");
			}
			case 210 -> {
				o.keyJump.setDown(false);
				groundY = sim != null ? sim.position.y : 0;
				maxY = groundY;
			}
			case 215 -> Screenshot.grab(mc, false);
			case 250 -> {
				LOG.info(String.format("ollie: rise %.3f m", maxY - groundY));
				o.keyRight.setDown(true);
				LOG.info("phase carve right");
			}
			case 262 -> Screenshot.grab(mc, false);
			case 275 -> o.keyRight.setDown(false);
			case 290 -> {
				SkateController.toggle();
				cmd(mc, String.format("tp @p %d.5 %d %d.5 0 0", px + 20, Y + 1, pz + 20));
			}
			case 310 -> {
				LOG.info("phase step+grass: slab row at z={}, grass from z={}", pz + 40, pz + 50);
				SkateController.toggle();
				o.keyUp.setDown(true);
			}
			case 372 -> Screenshot.grab(mc, false);
			case 400 -> o.keyUp.setDown(false);
			case 430 -> {
				LOG.info("phase orbit");
				o.keyUse.setDown(true);
			}
			case 431, 432, 433, 434, 435, 436, 437, 438, 439, 440 -> SkateCamera.mouse(60, -10);
			case 445 -> Screenshot.grab(mc, false);
			case 446 -> o.keyUse.setDown(false);
			case 500 -> Screenshot.grab(mc, false);
			case 520 -> {
				SkateController.toggle();
				LOG.info("dismounted: riding={} pos={} vel={} camera={}", SkateController.isRiding(), mc.player.position(),
					mc.player.getDeltaMovement(), mc.options.getCameraType());
			}
			case 560 -> {
				LOG.info("SELFTEST DONE");
				mc.stop();
			}
			default -> {
			}
		}
		if (sim != null) {
			maxY = Math.max(maxY, sim.position.y);
			if (tick % 10 == 0) {
				LOG.info(String.format("t=%d pos=(%.2f, %.3f, %.2f) speed=%.2f km/h grounded=%s tilt=%.1f steer=%.2f pushing=%s fov=%.2f",
					tick, mc.player.getX(), mc.player.getY(), mc.player.getZ(), sim.currentSpeedKmh, sim.isGrounded(), sim.tiltDegrees(),
					sim.currentSteerInput, sim.isPushing(), SkateCamera.fovMultiplier()));
			}
		}
	}

	private static void cmd(Minecraft mc, String command) {
		MinecraftServer server = mc.getSingleplayerServer();
		if (server == null) {
			LOG.warn("no integrated server for: {}", command);
			return;
		}
		server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
	}
}
