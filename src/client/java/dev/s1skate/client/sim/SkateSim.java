package dev.s1skate.client.sim;

import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * Port of ScheduleOne.Skating.Skateboard (owner-side logic) plus the parts of Unity's rigidbody it relies on.
 *
 * Everything here is in Unity space: left-handed, metres, y up, +z forward. The host converts to its own
 * axes. One {@link #step} is one Unity FixedUpdate (the game runs at the default 0.02 s fixed step).
 * Forces use ForceMode.Acceleration everywhere, so mass and inertia never enter, as in the original.
 */
public final class SkateSim {
	public static final double FIXED_DT = 0.02;

	// constants from Skateboard
	static final float JUMP_COOLDOWN = 0.3f;
	static final float JUMP_FORCE_BUILD_TIME = 0.5f;
	static final float PUSH_COOLDOWN = 1f;
	static final float PITCH_LIMIT = 60f;
	static final float ROLL_LIMIT = 20f;
	// constants from Skateboard_Equippable
	public static final float DISMOUNT_ANGLE = 80f;
	public static final double SURFACE_SAMPLE_DISTANCE = 0.4, SURFACE_SAMPLE_RAY_LENGTH = 0.7, BOARD_SPAWN_UPWARDS_SHIFT = 0.05;
	public static final float BOARD_SPAWN_ANGLE_LIMIT = 30f;
	public static final double BOARD_MOMENTUM_TRANSFER = 1.2;

	public static final class Input {
		public float horizontal;   // GameInput.MotionAxis.x  (A/D)
		public float vertical;     // GameInput.MotionAxis.y  (W/S), air pitch
		public float drive;        // GameInput.VehicleDriveAxis (W = push, S = brake)
		public boolean jump;       // GameInput.Jump held
	}

	public final BoardDef def;
	private SkateSettings settings;

	// rigidbody state; position is the transform position, velocity is at the centre of mass
	public final Vector3d position = new Vector3d();
	public final Quaterniond rotation = new Quaterniond();
	public final Vector3d velocity = new Vector3d();
	public final Vector3d angularVelocity = new Vector3d();
	private final Vector3d linAcc = new Vector3d(), angAcc = new Vector3d(), pendingClampTorque = new Vector3d();

	// Skateboard fields
	public float currentSteerInput;
	public float currentSpeedKmh;
	private float horizontalInput;
	private boolean jumpReleased;
	private float timeSinceLastJump;
	private float timeGrounded;
	private float timeAirborne = 0.21f;
	private float jumpHeldTime;
	private float frontAxleForce, rearAxleForce, jumpForwardForce;
	private final Pid[] hoverPids;
	private boolean pushQueued;
	private boolean isPushing;
	private float thisFramePushForce;
	private float timeSincePushStart = 2f;
	private boolean braking;
	private float verticalInput;

	// coroutine state (Push / Jump)
	private float pushDelayLeft = -1f, pushI = -1f;
	private float jumpI = -1f, jumpDuration;

	// events for audio/visual hooks
	public boolean firedJump, firedLand, firedPush;

	private final Vector3d expectedPosition = new Vector3d(), stepStartPosition = new Vector3d();

	public SkateSim(BoardDef def) {
		this.def = def;
		this.settings = def.settings;
		this.hoverPids = new Pid[def.hoverPoints.length];
		for (int i = 0; i < hoverPids.length; i++) hoverPids[i] = new Pid(settings.Hover_P, settings.Hover_I, settings.Hover_D);
	}

	/** Skateboard.OnWeatherChange: rain blends the rain override into the default settings. */
	public void setRain(float rainy) {
		settings = rainy > 0f && def.rain != null ? def.settings.blend(def.rain, rainy) : def.settings;
	}

	public SkateSettings settings() {
		return settings;
	}

	public boolean isGrounded() {
		return timeGrounded > 0f;
	}

	public float airTime() {
		return timeAirborne;
	}

	public boolean isPushing() {
		return isPushing;
	}

	public float jumpBuildAmount() {
		return clamp01(jumpHeldTime / JUMP_FORCE_BUILD_TIME);
	}

	/** Skateboard_Equippable.OnMount: SetVelocity(player velocity * 1.2). */
	public void place(Vector3d pos, Quaterniond rot, Vector3d playerVelocity) {
		position.set(pos);
		rotation.set(rot).normalize();
		velocity.set(playerVelocity).mul(BOARD_MOMENTUM_TRANSFER);
		angularVelocity.zero();
	}

	// ------------------------------------------------------------------ one fixed step

	/**
	 * Runs Update (input), FixedUpdate (forces), the physics integration, the coroutines and LateUpdate.
	 * Returns the position the board wants to move to; the host resolves collisions and calls
	 * {@link #resolve(Vector3d)} with where it actually ended up.
	 */
	public Vector3d step(Input in, SkateWorld world) {
		float dt = (float) FIXED_DT;
		firedJump = firedLand = firedPush = false;
		getInput(in, dt);

		linAcc.zero();
		angAcc.set(pendingClampTorque);
		pendingClampTorque.zero();

		// Skateboard.FixedUpdate
		applyInput(dt);
		applyLateralFriction(world);
		updateHover(world, dt);
		checkGrounded(world, dt);
		checkJump(dt);
		applyGravity();

		integrate(dt);

		// coroutines resume after FixedUpdate in Unity's frame loop
		tickPushCoroutine(dt);
		tickJumpCoroutine(dt);
		// LateUpdate
		clampRotation();
		return expectedPosition;
	}

	/** Contact response: whatever the host's collision stopped is removed from the velocity. */
	public void resolve(Vector3d actual) {
		double dt = FIXED_DT, eps = 1e-5;
		double dx = expectedPosition.x - stepStartPosition.x, ax = actual.x - stepStartPosition.x;
		double dz = expectedPosition.z - stepStartPosition.z, az = actual.z - stepStartPosition.z;
		double dy = expectedPosition.y - stepStartPosition.y, ay = actual.y - stepStartPosition.y;
		if (Math.abs(ax) < Math.abs(dx) - eps) velocity.x = ax / dt;
		if (Math.abs(az) < Math.abs(dz) - eps) velocity.z = az / dt;
		if (ay > dy + eps && velocity.y < 0) velocity.y = 0;
		if (ay < dy - eps && velocity.y > 0) velocity.y = 0;
		position.set(actual);
	}

	// ------------------------------------------------------------------ Skateboard.GetInput

	private void getInput(Input in, float dt) {
		horizontalInput = in.horizontal;
		verticalInput = in.vertical;
		jumpReleased = false;
		if (in.jump) {
			jumpHeldTime += dt;
		} else if (jumpHeldTime > 0f) {
			jumpReleased = true;
		}
		braking = in.drive < 0f;
		// (stamina is not modelled: Minecraft has none, so the 12.5 reserve check always passes)
		if (in.drive > 0f && !isPushing && timeGrounded > 0.1f && !braking && timeSincePushStart >= PUSH_COOLDOWN
			&& jumpHeldTime == 0f && !pushQueued) {
			pushQueued = true;
		}
	}

	// ------------------------------------------------------------------ Skateboard.ApplyInput

	private void applyInput(float dt) {
		Vector3d local = inverseTransformVector(velocity);
		if (Math.abs(horizontalInput) > 0.001f) {
			currentSteerInput = moveTowards(currentSteerInput, horizontalInput, dt * settings.TurnChangeRate);
		} else {
			currentSteerInput = moveTowards(currentSteerInput, 0f, dt * settings.TurnReturnToRestRate);
		}
		float turn = currentSteerInput * settings.TurnForce
			* settings.TurnForceMap.evaluate(clamp01(Math.abs(currentSpeedKmh / settings.TopSpeed_Kmh)));
		if (local.z < 0) turn = -turn;
		addTorque(up().mul(turn));
		if (Math.abs(horizontalInput) > 0.001f) {
			addForce(forward().mul(Math.abs(currentSteerInput) * settings.TurnSpeedBoost));
		}
		timeSincePushStart += dt;
		if (pushQueued) push();
		if (isPushing) {
			float mapped = settings.PushForceMultiplierMap.evaluate(clamp01((float) velocity.length() / settings.topSpeedMs()));
			addForce(forward().mul(thisFramePushForce * settings.PushForceMultiplier * mapped));
		}
		if (timeGrounded == 0f && settings.AirMovementEnabled) {
			float k = 1f;
			if (timeAirborne < settings.AirMovementJumpReductionDuration) {
				k = settings.AirMovementJumpReductionCurve.evaluate(timeAirborne / settings.AirMovementJumpReductionDuration);
			}
			addTorque(right().mul(verticalInput * settings.AirMovementForce * k));
		}
		if (braking) {
			float k = 1f;
			if (local.z < 0) k = 1f - clamp01((float) (local.z / -settings.ReverseTopSpeed_Kmh));
			addForce(forward().mul(-settings.BrakeForce * k));
		}
		currentSpeedKmh = (float) velocity.length() * 3.6f;
	}

	// ------------------------------------------------------------------ Skateboard.ApplyLateralFriction

	private void applyLateralFriction(SkateWorld world) {
		if (!settings.FrictionEnabled) return;
		Vector3d local = inverseTransformVector(velocity);
		Vector3d f = right().mul(-(local.x * settings.LateralFrictionForceMultiplier));
		float curve = settings.LongitudinalFrictionCurve.evaluate(clamp01((float) local.z) / settings.topSpeedMs());
		double along = local.z * curve;
		Vector3d flatForward = projectOnPlane(forward(), new Vector3d(0, 1, 0));
		f.add(flatForward.mul(-along));
		addForce(f);
		double smooth = surfaceSmoothness(world);
		addForce(new Vector3d(velocity).mul(-(1.0 - smooth)));
	}

	// ------------------------------------------------------------------ Skateboard.UpdateHover

	private void updateHover(SkateWorld world, float dt) {
		Vector3d up = up();
		Vector3d down = new Vector3d(up).negate();
		for (int i = 0; i < def.hoverPoints.length; i++) {
			Vector3d origin = transformPoint(def.hoverPoints[i]);
			SkateWorld.Hit hit = world.raycast(origin, down, settings.HoverRayLength);
			Pid pid = hoverPids[i];
			if (hit != null) {
				pid.p = settings.Hover_P;
				pid.i = settings.Hover_I;
				pid.d = settings.Hover_D;
				float force = pid.update(settings.HoverHeight, (float) hit.distance(), dt) * settings.HoverForce;
				force = Math.max(force, 0f);
				addForceAtPosition(new Vector3d(up).mul(force), origin);
			} else {
				pid.update(settings.HoverHeight, settings.HoverRayLength, dt);
			}
		}
	}

	private void applyGravity() {
		addForce(new Vector3d(0, -settings.Gravity, 0)); // * sqrt(PlayerMovement.GravityMultiplier) = 1
	}

	// ------------------------------------------------------------------ grounding

	private void checkGrounded(SkateWorld world, float dt) {
		if (groundHit(world) != null) {
			timeGrounded += dt;
			if (timeGrounded > 0.02f) {
				if (timeAirborne > 0.2f) firedLand = true;
				timeAirborne = 0f;
			}
		} else {
			timeAirborne += dt;
			timeGrounded = 0f;
		}
	}

	/** Skateboard.IsGrounded(out hit): front axle ray, then rear axle ray, along the board's down. */
	private SkateWorld.Hit groundHit(SkateWorld world) {
		Vector3d up = up();
		Vector3d down = new Vector3d(up).negate();
		double len = settings.HoverRayLength + 0.02;
		SkateWorld.Hit h = world.raycast(transformPoint(def.frontAxle).add(new Vector3d(up).mul(0.01)), down, len);
		if (h == null) h = world.raycast(transformPoint(def.rearAxle).add(new Vector3d(up).mul(0.01)), down, len);
		return h;
	}

	private double surfaceSmoothness(SkateWorld world) {
		SkateWorld.Hit h = groundHit(world);
		if (h == null) return 1.0;
		return h.terrain() && def.slowOnTerrain ? 0.4 : 1.0;
	}

	// ------------------------------------------------------------------ jumping

	private void checkJump(float dt) {
		timeSinceLastJump += dt;
		if (frontAxleForce > 0f) {
			addForceAtPosition(new Vector3d(0, frontAxleForce, 0), transformPoint(def.frontAxle));
		}
		if (frontAxleForce > 0f) { // sic: the original gates the rear axle on the front axle force too
			addForceAtPosition(new Vector3d(0, rearAxleForce, 0), transformPoint(def.rearAxle));
		}
		if (jumpForwardForce > 0f) {
			addForce(projectOnPlane(forward(), new Vector3d(0, 1, 0)).mul(jumpForwardForce));
		}
		if (jumpReleased) {
			if (timeGrounded > 0.3f) jump();
			jumpHeldTime = 0f;
		}
	}

	private void jump() {
		// SendJump -> ReceiveJump (RunLocally)
		if (timeSinceLastJump >= JUMP_COOLDOWN) {
			timeSinceLastJump = 0f;
			timeGrounded = 0f;
			firedJump = true;
		}
		float t = clamp01(jumpHeldTime / JUMP_FORCE_BUILD_TIME);
		jumpDuration = lerp(settings.JumpDuration_Min, settings.JumpDuration_Max, t);
		jumpI = 0f;
		jumpCoroutineBody(); // runs up to the first yield straight away
	}

	private void jumpCoroutineBody() {
		if (timeGrounded > 0.2f) { // "Breaking jump"
			endJump();
			return;
		}
		float u = jumpI / jumpDuration;
		frontAxleForce = settings.FrontAxleJumpCurve.evaluate(u) * settings.JumpForce;
		rearAxleForce = settings.RearAxleJumpCurve.evaluate(u) * settings.JumpForce;
		jumpForwardForce = settings.JumpForwardForceCurve.evaluate(u) * settings.JumpForwardBoost
			* (1f - clamp01(currentSpeedKmh / settings.TopSpeed_Kmh));
	}

	private void tickJumpCoroutine(float dt) {
		if (jumpI < 0f) return;
		jumpI += dt;
		if (jumpI < jumpDuration) jumpCoroutineBody();
		else endJump();
	}

	private void endJump() {
		jumpI = -1f;
		frontAxleForce = 0f;
		rearAxleForce = 0f; // jumpForwardForce is (sic) left at its last value
	}

	// ------------------------------------------------------------------ pushing

	private void push() {
		pushQueued = false;
		isPushing = true;
		timeSincePushStart = 0f;
		firedPush = true;
		pushDelayLeft = settings.PushDelay;
		pushI = -1f;
	}

	private void tickPushCoroutine(float dt) {
		if (!isPushing) return;
		if (pushDelayLeft >= 0f) {
			pushDelayLeft -= dt;
			if (pushDelayLeft > 0f) return;
			pushDelayLeft = -1f;
			pushI = 0f;
		} else {
			pushI += dt;
		}
		if (pushI < settings.PushForceDuration && !braking && timeGrounded != 0f) {
			thisFramePushForce = settings.PushForceCurve.evaluate(pushI / settings.PushForceDuration);
		} else {
			isPushing = false;
			thisFramePushForce = 0f;
			pushI = -1f;
		}
	}

	// ------------------------------------------------------------------ Skateboard.ClampRotation (LateUpdate)

	private void clampRotation() {
		Vector3d worldUp = new Vector3d(0, 1, 0);
		Vector3d fwd = forward(), right = right();
		Vector3d flatF = projectOnPlane(fwd, worldUp).normalize();
		Vector3d flatR = projectOnPlane(right, worldUp).normalize();
		double pitch = signedAngle(fwd, flatF, right);
		double roll = signedAngle(flatR, right, fwd);
		if (Math.abs(pitch) > PITCH_LIMIT) pendingClampTorque.add(new Vector3d(right).mul(pitch * settings.RotationClampForce));
		if (Math.abs(roll) > ROLL_LIMIT) pendingClampTorque.add(new Vector3d(fwd).mul(-roll * settings.RotationClampForce));
	}

	// ------------------------------------------------------------------ rigidbody

	private void addForce(Vector3d a) {
		linAcc.add(a);
	}

	private void addTorque(Vector3d a) {
		angAcc.add(a);
	}

	private void addForceAtPosition(Vector3d a, Vector3d worldPoint) {
		linAcc.add(a);
		Vector3d r = new Vector3d(worldPoint).sub(worldCenterOfMass());
		angAcc.add(r.cross(a));
	}

	public Vector3d worldCenterOfMass() {
		return transformPoint(def.centerOfMass);
	}

	/** PhysX-style integration: velocities, damping, max angular speed, then pose about the centre of mass. */
	private void integrate(float dt) {
		stepStartPosition.set(position);
		velocity.fma(dt, linAcc);
		angularVelocity.fma(dt, angAcc);
		velocity.mul(Math.max(0.0, 1.0 - def.drag * dt));
		angularVelocity.mul(Math.max(0.0, 1.0 - def.angularDrag * dt));
		double w = angularVelocity.length();
		if (w > def.maxAngularVelocity) angularVelocity.mul(def.maxAngularVelocity / w);

		Vector3d com = worldCenterOfMass().fma(dt, velocity);
		w = angularVelocity.length();
		if (w > 1e-9) {
			Quaterniond dq = new Quaterniond().fromAxisAngleRad(angularVelocity.x / w, angularVelocity.y / w, angularVelocity.z / w, w * dt);
			dq.mul(rotation, rotation);
			rotation.normalize();
		}
		Vector3d comOffset = rotation.transform(new Vector3d(def.centerOfMass));
		expectedPosition.set(com).sub(comOffset);
	}

	// ------------------------------------------------------------------ transform helpers (Unity semantics)

	public Vector3d forward() {
		return rotation.transform(new Vector3d(0, 0, 1));
	}

	public Vector3d up() {
		return rotation.transform(new Vector3d(0, 1, 0));
	}

	public Vector3d right() {
		return rotation.transform(new Vector3d(1, 0, 0));
	}

	public Vector3d transformPoint(Vector3d local) {
		return rotation.transform(new Vector3d(local)).add(position);
	}

	private Vector3d inverseTransformVector(Vector3d v) {
		return rotation.transformInverse(new Vector3d(v));
	}

	public double tiltDegrees() {
		return angle(up(), new Vector3d(0, 1, 0));
	}

	static Vector3d projectOnPlane(Vector3d v, Vector3d n) {
		double nn = n.lengthSquared();
		if (nn < 1e-12) return new Vector3d(v);
		return new Vector3d(v).sub(new Vector3d(n).mul(v.dot(n) / nn));
	}

	/** Vector3.Angle in degrees. */
	static double angle(Vector3d a, Vector3d b) {
		double denom = Math.sqrt(a.lengthSquared() * b.lengthSquared());
		if (denom < 1e-15) return 0;
		double dot = Math.max(-1, Math.min(1, a.dot(b) / denom));
		return Math.toDegrees(Math.acos(dot));
	}

	/** Vector3.SignedAngle in degrees. */
	static double signedAngle(Vector3d from, Vector3d to, Vector3d axis) {
		double a = angle(from, to);
		Vector3d c = new Vector3d(from).cross(to);
		return axis.dot(c) < 0 ? -a : a;
	}

	static float moveTowards(float current, float target, float maxDelta) {
		if (Math.abs(target - current) <= maxDelta) return target;
		return current + Math.signum(target - current) * maxDelta;
	}

	static float clamp01(float v) {
		return v < 0f ? 0f : (v > 1f ? 1f : v);
	}

	static float lerp(float a, float b, float t) {
		return a + (b - a) * clamp01(t);
	}

	/** ScheduleOne.DevUtilities.PID */
	static final class Pid {
		float p, i, d;
		private float integral, lastError;

		Pid(float p, float i, float d) {
			this.p = p;
			this.i = i;
			this.d = d;
		}

		float update(float setpoint, float actual, float dt) {
			float error = setpoint - actual;
			integral += error * dt;
			float derivative = (error - lastError) / dt;
			lastError = error;
			return error * p + integral * i + derivative * d;
		}
	}
}
