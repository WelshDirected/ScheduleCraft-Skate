package dev.s1skate.client.sim;

/**
 * Evaluates a Unity AnimationCurve: cubic Hermite segments, or cubic Bezier segments when a key's tangent
 * is weighted (weightedMode bit 1 = in, bit 2 = out). Infinite slopes are steps. Outside the key range the
 * curve clamps (Unity's default pre/post wrap mode for these assets).
 */
public final class UnityCurve {
	private static final float DEFAULT_WEIGHT = 1f / 3f;

	private final float[] time, value, inSlope, outSlope, inWeight, outWeight;
	private final int[] mode;

	/** keys: [time, value, inSlope, outSlope, weightedMode, inWeight, outWeight] */
	public UnityCurve(float[][] keys) {
		int n = keys.length;
		time = new float[n]; value = new float[n]; inSlope = new float[n]; outSlope = new float[n];
		inWeight = new float[n]; outWeight = new float[n]; mode = new int[n];
		for (int i = 0; i < n; i++) {
			float[] k = keys[i];
			time[i] = k[0]; value[i] = k[1]; inSlope[i] = k[2]; outSlope[i] = k[3];
			mode[i] = k.length > 4 ? (int) k[4] : 0;
			inWeight[i] = k.length > 5 ? k[5] : DEFAULT_WEIGHT;
			outWeight[i] = k.length > 6 ? k[6] : DEFAULT_WEIGHT;
		}
	}

	public static UnityCurve linear(float t0, float v0, float t1, float v1) {
		float s = (v1 - v0) / (t1 - t0);
		return new UnityCurve(new float[][] {{t0, v0, s, s}, {t1, v1, s, s}});
	}

	public static UnityCurve constant(float v) {
		return new UnityCurve(new float[][] {{0, v, 0, 0}});
	}

	public float evaluate(float t) {
		int n = time.length;
		if (n == 0) return 0f;
		if (n == 1 || t <= time[0]) return value[0];
		if (t >= time[n - 1]) return value[n - 1];
		int i = 0;
		while (i < n - 2 && t >= time[i + 1]) i++;
		return segment(i, t);
	}

	private float segment(int i, float t) {
		float t0 = time[i], t1 = time[i + 1];
		float v0 = value[i], v1 = value[i + 1];
		float m0 = outSlope[i], m1 = inSlope[i + 1];
		float dt = t1 - t0;
		if (dt <= 0f) return v1;
		if (Float.isInfinite(m0) || Float.isInfinite(m1)) return v0;

		boolean outWeighted = (mode[i] & 2) != 0;
		boolean inWeighted = (mode[i + 1] & 1) != 0;
		if (!outWeighted && !inWeighted) {
			float u = (t - t0) / dt;
			float u2 = u * u, u3 = u2 * u;
			float h00 = 2 * u3 - 3 * u2 + 1, h10 = u3 - 2 * u2 + u, h01 = -2 * u3 + 3 * u2, h11 = u3 - u2;
			return h00 * v0 + h10 * dt * m0 + h01 * v1 + h11 * dt * m1;
		}

		float w0 = outWeighted ? outWeight[i] : DEFAULT_WEIGHT;
		float w1 = inWeighted ? inWeight[i + 1] : DEFAULT_WEIGHT;
		// Bezier control points in (time, value)
		float x1 = t0 + w0 * dt, y1 = v0 + w0 * dt * m0;
		float x2 = t1 - w1 * dt, y2 = v1 - w1 * dt * m1;
		float u = solveBezierX(t, t0, x1, x2, t1);
		return bezier(u, v0, y1, y2, v1);
	}

	private static float bezier(float u, float p0, float p1, float p2, float p3) {
		float iu = 1 - u;
		return iu * iu * iu * p0 + 3 * iu * iu * u * p1 + 3 * iu * u * u * p2 + u * u * u * p3;
	}

	/** x(u) is monotonic for Unity's clamped weights; bisection is robust and cheap enough. */
	private static float solveBezierX(float x, float p0, float p1, float p2, float p3) {
		float lo = 0f, hi = 1f, u = (x - p0) / (p3 - p0);
		for (int it = 0; it < 32; it++) {
			float bx = bezier(u, p0, p1, p2, p3);
			if (Math.abs(bx - x) < 1e-6f) break;
			if (bx < x) lo = u; else hi = u;
			u = (lo + hi) * 0.5f;
		}
		return u;
	}
}
