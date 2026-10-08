package dev.s1skate.client;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * The one mapping between Schedule I (Unity, left-handed) and Minecraft (right-handed) space.
 * 1 metre = 1 block; Unity (x, y, z) -> Minecraft (x, y, -z). Unity forward (+z) is Minecraft north (-z),
 * Unity right (+x) is Minecraft east (+x), so "right" stays on the right.
 */
public final class Space {
	private Space() {
	}

	public static Vec3 toMc(Vector3d u) {
		return new Vec3(u.x, u.y, -u.z);
	}

	public static Vector3d toUnity(Vec3 m) {
		return new Vector3d(m.x, m.y, -m.z);
	}

	public static Vector3d toUnity(double x, double y, double z) {
		return new Vector3d(x, y, -z);
	}

	/** Minecraft yaw (degrees) of a Unity direction. */
	public static float mcYaw(Vector3d unityDir) {
		return (float) Math.toDegrees(Math.atan2(-unityDir.x, -unityDir.z));
	}

	/** Minecraft pitch (degrees, positive = down) of a Unity direction. */
	public static float mcPitch(Vector3d unityDir) {
		double h = Math.sqrt(unityDir.x * unityDir.x + unityDir.z * unityDir.z);
		return (float) -Math.toDegrees(Math.atan2(unityDir.y, h));
	}

	/** Unity forward vector for a Minecraft yaw. */
	public static Vector3d unityForwardFromMcYaw(float yawDeg) {
		double r = Math.toRadians(yawDeg);
		return new Vector3d(-Math.sin(r), 0, -Math.cos(r));
	}

	/** Unity Quaternion.LookRotation(forward, up). */
	public static Quaterniond lookRotation(Vector3d forward, Vector3d up) {
		Vector3d f = new Vector3d(forward).normalize();
		Vector3d r = new Vector3d(up).cross(f);
		if (r.lengthSquared() < 1e-12) r.set(1, 0, 0);
		r.normalize();
		Vector3d u = new Vector3d(f).cross(r);
		// columns: right, up, forward
		org.joml.Matrix3d m = new org.joml.Matrix3d(r.x, r.y, r.z, u.x, u.y, u.z, f.x, f.y, f.z);
		return new Quaterniond().setFromNormalized(m).normalize();
	}

	/** Unity Quaternion.Euler(x, y, z) in degrees (applied z, then x, then y). */
	public static Quaterniond euler(double x, double y, double z) {
		return new Quaterniond().rotationY(Math.toRadians(y)).rotateX(Math.toRadians(x)).rotateZ(Math.toRadians(z));
	}

	/** Unity Quaternion.Lerp (normalised component lerp along the shortest path). */
	public static Quaterniond lerp(Quaterniond a, Quaterniond b, double t) {
		t = Math.max(0, Math.min(1, t));
		double s = a.dot(b) < 0 ? -1 : 1;
		return new Quaterniond(
			a.x + (b.x * s - a.x) * t, a.y + (b.y * s - a.y) * t,
			a.z + (b.z * s - a.z) * t, a.w + (b.w * s - a.w) * t).normalize();
	}

	public static Vector3d lerp(Vector3d a, Vector3d b, double t) {
		t = Math.max(0, Math.min(1, t));
		return new Vector3d(a).lerp(b, t);
	}

	/** (pitch, yaw) of a rotation's forward, Unity eulerAngles.x / .y style (pitch positive = down). */
	public static double[] pitchYaw(Quaterniond q) {
		Vector3d f = q.transform(new Vector3d(0, 0, 1));
		double yaw = Math.toDegrees(Math.atan2(f.x, f.z));
		double pitch = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, -f.y))));
		return new double[] {pitch, yaw};
	}
}
