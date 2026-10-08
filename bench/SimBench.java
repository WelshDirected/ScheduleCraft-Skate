import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.s1skate.client.sim.BoardDef;
import dev.s1skate.client.sim.SkateSim;
import dev.s1skate.client.sim.SkateWorld;
import java.io.FileReader;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * Headless oracle for the skateboard sim: no Minecraft, a flat floor (optionally a step and grass), the
 * same SkateSim the mod runs. Prints what a rider would feel: hover height, push speeds, top speed,
 * ollie height/airtime, turn rate, braking. Collisions with the floor are resolved like the host does.
 *
 * usage: java -cp <classes;joml;gson> bench/SimBench.java [boards.json] [board key]
 */
public class SimBench {
	static final double FLOOR = 0.0;

	/** Flat ground at y=0; "grass" (terrain) for x > 1000. */
	static final SkateWorld FLAT = (o, d, max) -> {
		if (d.y >= 0) return null;
		double t = (FLOOR - o.y) / d.y;
		if (t < 0 || t > max) return null;
		return new SkateWorld.Hit(t, o.x > 1000);
	};

	public static void main(String[] args) throws Exception {
		BoardDef def = new BoardDef();
		if (args.length > 0) {
			JsonObject all = JsonParser.parseReader(new FileReader(args[0])).getAsJsonObject().getAsJsonObject("boards");
			String key = args.length > 1 ? args[1] : "board";
			def = BoardDef.fromJson(key, all.getAsJsonObject(key));
		}
		System.out.printf("board=%s top=%.1f km/h hoverHeight=%.3f P=%.0f I=%.1f D=%.1f%n", def.key,
			def.settings.TopSpeed_Kmh, def.settings.HoverHeight, def.settings.Hover_P, def.settings.Hover_I, def.settings.Hover_D);

		SkateSim sim = new SkateSim(def);
		sim.place(new Vector3d(0, 0.15, 0), new Quaterniond(), new Vector3d());
		SkateSim.Input idle = new SkateSim.Input();

		// 1. settle
		run(sim, idle, 2.0);
		System.out.printf("settle: y=%.4f grounded=%s tilt=%.2f deg vy=%.4f%n", sim.position.y, sim.isGrounded(),
			sim.tiltDegrees(), sim.velocity.y);

		// 2. hold W: pushes, speed over time
		SkateSim.Input push = new SkateSim.Input();
		push.drive = 1; push.vertical = 1;
		int pushes = 0;
		for (int i = 1; i <= 500; i++) {
			step(sim, push);
			if (sim.firedPush) pushes++;
			if (i % 50 == 0) System.out.printf("push t=%4.1fs speed=%5.2f km/h pushes=%d y=%.3f pitch=%.1f%n", i * 0.02,
				sim.currentSpeedKmh, pushes, sim.position.y, sim.tiltDegrees());
		}
		// 3. coast
		run(sim, idle, 3.0);
		System.out.printf("coast 3s: speed=%.2f km/h%n", sim.currentSpeedKmh);

		// 4. ollie: hold space 0.5 s then release
		SkateSim.Input charge = new SkateSim.Input();
		charge.jump = true;
		run(sim, charge, 0.5);
		double y0 = sim.position.y, maxY = y0;
		int airSteps = 0;
		boolean left = false;
		for (int i = 0; i < 150; i++) {
			step(sim, idle);
			maxY = Math.max(maxY, sim.position.y);
			if (!sim.isGrounded()) { airSteps++; left = true; }
			if (left && sim.isGrounded() && i > 10) break;
		}
		System.out.printf("ollie (full charge): rise=%.3f m air=%.2fs tilt-after=%.1f%n", maxY - y0, airSteps * 0.02, sim.tiltDegrees());

		// 5. carve: hold D at speed, measure yaw rate
		sim.place(new Vector3d(0, sim.position.y, 0), new Quaterniond(), new Vector3d(0, 0, 5));
		run(sim, idle, 0.5);
		SkateSim.Input right = new SkateSim.Input();
		right.horizontal = 1;
		double yaw0 = yaw(sim);
		run(sim, right, 1.0);
		System.out.printf("carve right 1s at %.1f km/h: yaw change=%.1f deg (positive = right) lateral slip=%.3f m/s%n",
			sim.currentSpeedKmh, yaw(sim) - yaw0, Math.abs(sim.rotation.transformInverse(new Vector3d(sim.velocity)).x));

		// 6. brake
		sim.place(new Vector3d(0, sim.position.y, 0), new Quaterniond(), new Vector3d(0, 0, 6));
		SkateSim.Input brake = new SkateSim.Input();
		brake.drive = -1;
		double t = 0;
		while (sim.currentSpeedKmh > 0.5 && t < 10) { step(sim, brake); t += 0.02; }
		System.out.printf("brake from 21.6 km/h: stopped in %.2fs%n", t);

		// 7. grass (terrain) slows the default board
		sim.place(new Vector3d(2000, sim.position.y, 0), new Quaterniond(), new Vector3d(0, 0, 6));
		run(sim, idle, 1.0);
		System.out.printf("coast on grass 1s from 21.6 km/h: %.2f km/h (slowOnTerrain=%s)%n", sim.currentSpeedKmh, def.slowOnTerrain);
	}

	static double yaw(SkateSim s) {
		Vector3d f = s.forward();
		return Math.toDegrees(Math.atan2(f.x, f.z));
	}

	static void run(SkateSim sim, SkateSim.Input in, double seconds) {
		for (int i = 0; i < Math.round(seconds / SkateSim.FIXED_DT); i++) step(sim, in);
	}

	/** Host-side collision like Minecraft's move(): the floor stops the board from going below y = 0. */
	static void step(SkateSim sim, SkateSim.Input in) {
		Vector3d want = new Vector3d(sim.step(in, FLAT));
		if (want.y < FLOOR) want.y = FLOOR;
		sim.resolve(want);
	}
}
