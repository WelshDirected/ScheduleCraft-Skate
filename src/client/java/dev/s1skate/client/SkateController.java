package dev.s1skate.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.s1skate.client.sim.BoardDef;
import dev.s1skate.client.sim.SkateSim;
import dev.s1skate.client.sim.SkateWorld;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;

/**
 * Owns the local player's ride. Minecraft is the host: its world, collision and player stay real; while
 * riding, the player's movement is replaced by Schedule I's skateboard sim, stepped at Unity's 50 Hz.
 */
public final class SkateController {
	/** Rider's feet sit on the deck: the default deck's top is 5.7 cm above the board origin. */
	static final double FEET_ABOVE_BOARD = 0.057;
	private static final double TICK = 0.05;

	private static SkateSim sim;
	private static double accumulator;
	private static final Vector3d prevPos = new Vector3d(), currPos = new Vector3d();
	private static final Quaterniond prevRot = new Quaterniond(), currRot = new Quaterniond();
	private static float visualLean;
	private static long lastFrameNanos;
	private static CameraType savedCameraType;

	private SkateController() {
	}

	public static boolean isRiding() {
		return sim != null;
	}

	public static SkateSim sim() {
		return sim;
	}

	// ------------------------------------------------------------------ mount / dismount

	public static void toggle() {
		if (isRiding()) dismount(true);
		else mount();
	}

	public static void cycleBoard() {
		boolean riding = isRiding();
		if (riding) dismount(false);
		BoardRegistry.reload();
		BoardDef def = BoardRegistry.cycle();
		message(Component.translatable("s1skate.selected", def.displayName));
		if (riding) mount();
	}

	private static void mount() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) return;
		if (!canRide(player)) {
			message(Component.translatable("s1skate.cant_ride"));
			return;
		}
		BoardRegistry.reload();
		BoardDef def = BoardRegistry.current();
		McWorld world = new McWorld(mc.level, player);

		// Skateboard_Equippable.GetSkateboardSpawnPose
		Vector3d base = Space.toUnity(player.position());
		Vector3d fwd = Space.unityForwardFromMcYaw(player.getYRot());
		Vector3d front = new Vector3d(base).fma(SkateSim.SURFACE_SAMPLE_DISTANCE, fwd).add(0, 0.4, 0);
		Vector3d rear = new Vector3d(base).fma(-SkateSim.SURFACE_SAMPLE_DISTANCE, fwd).add(0, 0.4, 0);
		Vector3d down = new Vector3d(0, -1, 0);
		Vector3d frontHit = hitPoint(world, front, down, SkateSim.SURFACE_SAMPLE_RAY_LENGTH);
		Vector3d rearHit = hitPoint(world, rear, down, SkateSim.SURFACE_SAMPLE_RAY_LENGTH);
		Vector3d pos = new Vector3d(frontHit).add(rearHit).mul(0.5)
			.add(0, SkateSim.BOARD_SPAWN_UPWARDS_SHIFT + def.settings.HoverHeight, 0);
		Vector3d dir = new Vector3d(frontHit).sub(rearHit).normalize();
		Vector3d right = new Vector3d(0, 1, 0).cross(dir).normalize();
		Vector3d up = new Vector3d(dir).cross(right).normalize();
		Quaterniond rot = Space.lookRotation(dir, up);
		if (Math.toDegrees(up.angle(new Vector3d(0, 1, 0))) > SkateSim.BOARD_SPAWN_ANGLE_LIMIT) {
			message(Component.translatable("s1skate.too_steep"));
			return;
		}

		sim = new SkateSim(def);
		Vec3 dm = player.getDeltaMovement();
		Vector3d playerVel = Space.toUnity(dm.x * 20, Math.max(0, dm.y) * 20, dm.z * 20);
		sim.place(pos, rot, playerVel);
		accumulator = 0;
		prevPos.set(pos); currPos.set(pos);
		prevRot.set(rot); currRot.set(rot);
		visualLean = 0;
		player.getAbilities().flying = false;
		player.setSprinting(false);
		Vec3 feet = feetFor(pos);
		player.setPos(feet.x, feet.y, feet.z);
		SkateCamera.onMount(sim);
		// third person while riding: the rider is drawn and the first-person hand is not
		savedCameraType = mc.options.getCameraType();
		mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		mc.level.playLocalSound(player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(),
			SoundSource.PLAYERS, 0.6f, 1.4f, false);
		message(Component.translatable(BoardRegistry.isFromPack() ? "s1skate.mounted" : "s1skate.mounted_placeholder",
			def.displayName));
	}

	public static void dismount(boolean keepMomentum) {
		if (sim == null) return;
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null && keepMomentum) {
			// Skateboard_Equippable.OnDismount hands the board's horizontal momentum back to the player
			Vector3d v = new Vector3d(sim.velocity.x, 0, sim.velocity.z);
			Vec3 mc = Space.toMc(v);
			player.setDeltaMovement(mc.x / 20.0, player.getDeltaMovement().y, mc.z / 20.0);
		}
		if (player != null) {
			// keep looking where the skateboard camera was looking
			float[] look = SkateCamera.lastLook();
			if (look != null) {
				player.setYRot(look[0]);
				player.setYHeadRot(look[0]);
				player.setYBodyRot(look[0]);
				player.setXRot(Math.max(-90f, Math.min(90f, look[1])));
			}
		}
		sim = null;
		SkateCamera.onDismount();
		if (savedCameraType != null) {
			Minecraft.getInstance().options.setCameraType(savedCameraType);
			savedCameraType = null;
		}
	}

	private static boolean canRide(LocalPlayer p) {
		return p.isAlive() && !p.isSpectator() && !p.isPassenger() && !p.isInWater() && !p.isInLava() && !p.isFallFlying()
			&& !p.isSleeping();
	}

	// ------------------------------------------------------------------ physics (replaces LivingEntity.travel)

	public static void travel(LocalPlayer player) {
		Level level = player.level();
		if (!canRide(player) && sim != null) {
			dismount(true);
			return;
		}
		prevPos.set(currPos);
		prevRot.set(currRot);

		boolean rainy = level.isRainingAt(BlockPos.containing(player.getEyePosition()));
		sim.setRain(rainy ? level.getRainLevel(1f) : 0f);

		SkateSim.Input in = readInput();
		McWorld world = new McWorld(level, player);
		accumulator += TICK;
		while (accumulator >= SkateSim.FIXED_DT - 1e-9) {
			accumulator -= SkateSim.FIXED_DT;
			Vector3d before = new Vector3d(sim.position);
			Vector3d want = sim.step(in, world);
			Vec3 delta = Space.toMc(new Vector3d(want).sub(before));
			player.move(MoverType.SELF, delta);
			sim.resolve(boardFor(player.position()));
			effects(player);
			if (sim.tiltDegrees() > SkateSim.DISMOUNT_ANGLE) { // Skateboard_Equippable: bail out past 80 degrees
				dismount(true);
				return;
			}
		}

		currPos.set(sim.position);
		currRot.set(sim.rotation);
		Vec3 v = Space.toMc(sim.velocity);
		player.setDeltaMovement(v.x / 20.0, v.y / 20.0, v.z / 20.0);
		// the board carries the rider: no fall damage, and the server sees a grounded player
		player.resetFallDistance();
		player.setOnGround(true);

		// stand sideways on the deck, facing the board's right, head turned towards the nose
		float boardYaw = Space.mcYaw(sim.forward());
		player.setYRot(boardYaw + 90f);
		player.setYBodyRot(boardYaw + 90f);
		player.setYHeadRot(boardYaw + 30f);
		player.setXRot(15f);
	}

	private static SkateSim.Input readInput() {
		Minecraft mc = Minecraft.getInstance();
		var o = mc.options;
		SkateSim.Input in = new SkateSim.Input();
		if (mc.gui.screen() != null) return in; // GetInput: not accepting input
		float lr = (o.keyRight.isDown() ? 1f : 0f) - (o.keyLeft.isDown() ? 1f : 0f);
		float fb = (o.keyUp.isDown() ? 1f : 0f) - (o.keyDown.isDown() ? 1f : 0f);
		in.horizontal = lr;
		in.vertical = fb;
		in.drive = fb;
		in.jump = o.keyJump.isDown();
		return in;
	}

	private static void effects(LocalPlayer player) {
		if (sim.firedJump) {
			player.level().playLocalSound(player.getX(), player.getY(), player.getZ(), SoundEvents.WOOD_HIT, SoundSource.PLAYERS,
				0.7f, 1.3f, false);
		}
		if (sim.firedLand) {
			player.level().playLocalSound(player.getX(), player.getY(), player.getZ(), SoundEvents.WOOD_PLACE, SoundSource.PLAYERS,
				0.8f, 0.8f, false);
		}
	}

	static Vec3 feetFor(Vector3d boardPos) {
		Vec3 m = Space.toMc(boardPos);
		return new Vec3(m.x, m.y + FEET_ABOVE_BOARD, m.z);
	}

	static Vector3d boardFor(Vec3 feet) {
		return Space.toUnity(feet.x, feet.y - FEET_ABOVE_BOARD, feet.z);
	}

	private static Vector3d hitPoint(McWorld world, Vector3d origin, Vector3d dir, double len) {
		SkateWorld.Hit h = world.raycast(origin, dir, len);
		return new Vector3d(origin).fma(h != null ? h.distance() : len, dir);
	}

	// ------------------------------------------------------------------ render-side interpolation

	public static Vector3d renderPos(float partialTick) {
		return Space.lerp(prevPos, currPos, partialTick);
	}

	public static Quaterniond renderRot(float partialTick) {
		return new Quaterniond(prevRot).slerp(currRot, partialTick);
	}

	/** Per-frame delta time, shared by the camera and the visuals (Unity's Time.deltaTime in LateUpdate). */
	static float frameDelta() {
		long now = System.nanoTime();
		float dt = lastFrameNanos == 0 ? 0f : (now - lastFrameNanos) / 1e9f;
		lastFrameNanos = now;
		return Math.min(dt, 0.1f);
	}

	/** Draws the board. The pose stack is camera-relative Minecraft space. */
	public static void render(PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector collector, Vec3 cameraPos,
		float partialTick, float frameDt) {
		if (sim == null) return;
		Minecraft mc = Minecraft.getInstance();
		BoardDef def = sim.def;
		// SkateboardVisuals.LateUpdate: lean into the turn
		float targetLean = sim.currentSteerInput * -def.maxBoardLean;
		visualLean += (targetLean - visualLean) * Math.min(1f, frameDt * def.boardLeanRate);

		Vector3d pos = renderPos(partialTick);
		Quaterniond rot = renderRot(partialTick).mul(Space.euler(0, 0, visualLean));
		Vec3 m = Space.toMc(pos);
		int light = LightCoordsUtil.getLightCoords(mc.level, BlockPos.containing(m.x, m.y + 0.25, m.z));

		poseStack.pushPose();
		poseStack.translate(m.x - cameraPos.x, m.y - cameraPos.y, m.z - cameraPos.z);
		poseStack.scale(1f, 1f, -1f); // Unity -> Minecraft handedness
		poseStack.rotate(new Quaternionf((float) rot.x, (float) rot.y, (float) rot.z, (float) rot.w));
		BoardRegistry.model(def).submit(poseStack, collector, light);
		poseStack.popPose();
	}

	private static void message(Component c) {
		LocalPlayer p = Minecraft.getInstance().player;
		if (p != null) p.sendOverlayMessage(c);
	}

	// ------------------------------------------------------------------ world adapter

	/** Physics.Raycast against Minecraft's block collision shapes; "Terrain" = natural ground blocks. */
	static final class McWorld implements SkateWorld {
		private final Level level;
		private final LocalPlayer player;

		McWorld(Level level, LocalPlayer player) {
			this.level = level;
			this.player = player;
		}

		@Override
		public Hit raycast(Vector3d origin, Vector3d direction, double maxDistance) {
			Vec3 from = Space.toMc(origin);
			Vec3 to = Space.toMc(new Vector3d(origin).fma(maxDistance, direction));
			BlockHitResult r = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
			if (r.getType() != HitResult.Type.BLOCK) return null;
			double d = r.getLocation().distanceTo(from);
			return new Hit(d, isTerrain(level.getBlockState(r.getBlockPos())));
		}

		private static boolean isTerrain(BlockState s) {
			return s.is(BlockTags.SUBSTRATE_OVERWORLD) || s.is(BlockTags.SAND) || s.is(BlockTags.SNOW) || s.is(Blocks.GRAVEL)
				|| s.is(Blocks.FARMLAND) || s.is(BlockTags.LEAVES) || s.is(Blocks.SOUL_SAND)
				|| s.is(Blocks.SOUL_SOIL);
		}
	}
}
