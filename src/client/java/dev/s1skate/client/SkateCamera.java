package dev.s1skate.client;

import dev.s1skate.client.sim.BoardDef;
import dev.s1skate.client.sim.SkateSim;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * Port of ScheduleOne.Skating.SkateboardCamera, keyboard-and-mouse path (NeedSecondaryClick): the camera
 * trails the board; holding the secondary button (Minecraft's "use" key) orbits it with the mouse, and on
 * release it eases back behind the board. Runs per frame in Unity space; the result drives Minecraft's camera.
 */
public final class SkateCamera {
	private static final double FOLLOW_DELTA = 7.5;
	private static final double Y_MIN = -20, Y_MAX = 89;
	private static final float MANUAL_OVERRIDE_TIME = 0.01f, MANUAL_OVERRIDE_RETURN_TIME = 0.6f;
	private static final double X_SPEED = 60, Y_SPEED = 40;

	private static boolean active;
	private static BoardDef def;
	private static final Vector3d camPos = new Vector3d(), targetPos = new Vector3d(), dollyPos = new Vector3d();
	private static final Quaterniond camRot = new Quaterniond(), targetRot = new Quaterniond(), dollyRot = new Quaterniond();
	private static final Vector3d lastFrameOffset = new Vector3d(), lastManualOffset = new Vector3d();
	private static double x, y, orbitDistance;
	private static float timeSinceAdjusted = Float.MAX_VALUE;
	private static boolean cameraAdjusted, secondaryWasDown;
	private static double mouseX, mouseY;
	private static float fovMultiplier = 1f;
	private static float[] lastLook;

	// board pose this frame
	private static final Vector3d boardPos = new Vector3d(), originPos = new Vector3d();
	private static final Quaterniond boardRot = new Quaterniond();

	private SkateCamera() {
	}

	public static boolean isActive() {
		return active;
	}

	/** Minecraft {yaw, pitch} the camera last looked along, or null before the first frame. */
	public static float[] lastLook() {
		return lastLook;
	}

	public static float fovMultiplier() {
		return fovMultiplier;
	}

	/** Mouse motion while riding goes here instead of turning the player (Entity.turn units). */
	public static void mouse(double dx, double dy) {
		mouseX += dx;
		mouseY += dy;
	}

	static void onMount(SkateSim sim) {
		def = sim.def;
		active = true;
		fovMultiplier = 1f;
		lastLook = null;
		mouseX = mouseY = 0;
		cameraAdjusted = false;
		secondaryWasDown = false;
		setBoardPose(sim.position, sim.rotation);
		// OnPlayerMountedSkateboard
		timeSinceAdjusted = 100f;
		targetPos.set(limitCameraPosition(targetCameraPosition()));
		targetRot.set(lookAt(targetPos));
		camPos.set(targetPos);
		camRot.set(targetRot);
		dollyPos.set(targetPos);
		dollyRot.set(targetRot);
		lastManualOffset.set(inverseTransformPoint(camPos));
		lastFrameOffset.set(inverseTransformPoint(camPos));
	}

	static void onDismount() {
		active = false;
		fovMultiplier = 1f;
	}

	/** Per frame: Update (CheckForClick) + LateUpdate (UpdateCamera, UpdateFOV). Returns {x,y,z,yaw,pitch} in Minecraft space. */
	public static double[] update(float partialTick, float dt) {
		SkateSim sim = SkateController.sim();
		if (sim == null) return null;
		setBoardPose(SkateController.renderPos(partialTick), SkateController.renderRot(partialTick));
		Minecraft mc = Minecraft.getInstance();
		boolean secondary = mc.gui.screen() == null && mc.options.keyUse.isDown();
		double mdx = mouseX, mdy = mouseY;
		mouseX = mouseY = 0;

		// CheckForClick
		timeSinceAdjusted += dt;
		if (secondary) {
			if (!secondaryWasDown && timeSinceAdjusted > MANUAL_OVERRIDE_TIME) {
				cameraAdjusted = true;
				double[] py = Space.pitchYaw(camRot);
				x = py[1];
				y = py[0];
				orbitDistance = restDistance();
			}
			if (cameraAdjusted) timeSinceAdjusted = 0f;
		} else {
			cameraAdjusted = false;
		}
		secondaryWasDown = secondary;

		// UpdateCamera
		targetPos.set(limitCameraPosition(targetCameraPosition()));
		targetRot.set(lookAt(targetPos));
		dollyPos.set(Space.lerp(dollyPos, targetPos, dt * FOLLOW_DELTA));
		dollyRot.set(Space.lerp(dollyRot, targetRot, dt * FOLLOW_DELTA));
		orbitDistance = Math.max(restDistance(), Math.min(100, originPos.distance(dollyPos)));

		// HandleSecondaryClickCameraMovement
		if (timeSinceAdjusted <= MANUAL_OVERRIDE_TIME) {
			if (secondary) {
				// MouseDelta * speed * 0.02 * sensitivity; Minecraft's turn() already applies sensitivity, 0.15 deg per unit
				x += mdx * 0.15 * (X_SPEED / 60.0);
				y += mdy * 0.15 * (Y_SPEED / 60.0);
				y = clampAngle(y, Y_MIN, Y_MAX);
				Quaterniond rot = Space.euler(y, x, 0);
				Vector3d tp = rot.transform(new Vector3d(0, 0, -orbitDistance)).add(originPos);
				camRot.set(rot);
				camPos.set(limitCameraPosition(tp));
			} else {
				Vector3d dir = transformPoint(lastFrameOffset).sub(originPos).normalize();
				camPos.set(limitCameraPosition(new Vector3d(originPos).fma(orbitDistance, dir)));
				camRot.set(lookAt(camPos));
				double[] py = Space.pitchYaw(camRot);
				x = py[1];
				y = py[0];
			}
			lastManualOffset.set(inverseTransformPoint(camPos));
		} else if (timeSinceAdjusted < MANUAL_OVERRIDE_TIME + MANUAL_OVERRIDE_RETURN_TIME) {
			double t = (timeSinceAdjusted - MANUAL_OVERRIDE_TIME) / MANUAL_OVERRIDE_RETURN_TIME;
			targetPos.set(Space.lerp(transformPoint(lastManualOffset), targetPos, t));
			targetRot.set(lookAt(targetPos));
			camPos.set(Space.lerp(camPos, targetPos, dt * FOLLOW_DELTA));
			camRot.set(Space.lerp(camRot, targetRot, dt * FOLLOW_DELTA));
		} else {
			camPos.set(Space.lerp(camPos, targetPos, dt * FOLLOW_DELTA));
			camRot.set(Space.lerp(camRot, targetRot, dt * FOLLOW_DELTA));
		}
		lastFrameOffset.set(inverseTransformPoint(camPos));

		// UpdateFOV
		float speed01 = (float) Math.min(1.0, sim.velocity.length() / sim.settings().topSpeedMs());
		float targetFov = def.fovMinSpeed + (def.fovMaxSpeed - def.fovMinSpeed) * speed01;
		fovMultiplier += (targetFov - fovMultiplier) * Math.min(1f, dt * def.fovChangeRate);

		Vec3 p = Space.toMc(camPos);
		Vector3d f = camRot.transform(new Vector3d(0, 0, 1));
		lastLook = new float[] {Space.mcYaw(f), Space.mcPitch(f)};
		return new double[] {p.x, p.y, p.z, lastLook[0], lastLook[1]};
	}

	private static void setBoardPose(Vector3d pos, Quaterniond rot) {
		boardPos.set(pos);
		boardRot.set(rot);
		originPos.set(rot.transform(new Vector3d(def.cameraOrigin)).add(pos));
	}

	private static double restDistance() {
		return Math.sqrt(def.camHorizontalOffset * def.camHorizontalOffset + def.camVerticalOffset * def.camVerticalOffset);
	}

	/** GetTargetCameraPosition: behind and above the board, along its flattened forward. */
	private static Vector3d targetCameraPosition() {
		Vector3d f = boardRot.transform(new Vector3d(0, 0, 1));
		f.y = 0; // Vector3.ProjectOnPlane(forward, up), not normalised (as in the original)
		return new Vector3d(boardPos).fma(def.camHorizontalOffset, f).add(0, def.camVerticalOffset, 0);
	}

	/** LimitCameraPosition: keep the camera 0.45 m in front of anything between it and the camera origin. */
	private static Vector3d limitCameraPosition(Vector3d target) {
		Minecraft mc = Minecraft.getInstance();
		Vector3d dir = new Vector3d(target).sub(originPos);
		if (dir.lengthSquared() < 1e-9 || mc.level == null) return new Vector3d(target);
		dir.normalize();
		double len = boardPos.distance(target) + 0.45;
		Vec3 from = Space.toMc(originPos);
		Vec3 to = Space.toMc(new Vector3d(originPos).fma(len, dir));
		BlockHitResult hit = mc.level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
		if (hit.getType() == HitResult.Type.BLOCK) {
			return Space.toUnity(hit.getLocation()).fma(-0.45, dir);
		}
		return new Vector3d(target);
	}

	private static Quaterniond lookAt(Vector3d from) {
		Vector3d d = new Vector3d(originPos).sub(from);
		if (d.lengthSquared() < 1e-9) return new Quaterniond(camRot);
		return Space.lookRotation(d, new Vector3d(0, 1, 0));
	}

	private static Vector3d transformPoint(Vector3d local) {
		return boardRot.transform(new Vector3d(local)).add(originPos);
	}

	private static Vector3d inverseTransformPoint(Vector3d world) {
		return boardRot.transformInverse(new Vector3d(world).sub(originPos));
	}

	private static double clampAngle(double a, double min, double max) {
		if (a < -360) a += 360;
		if (a > 360) a -= 360;
		return Math.max(min, Math.min(max, a));
	}
}
