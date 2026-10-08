package dev.s1skate.client.sim;

import org.joml.Vector3d;

/** What the skateboard sim needs from the world, in Unity space (left-handed, metres, y up). */
public interface SkateWorld {
	/** Physics.Raycast against the board's ground mask. Returns null on a miss. */
	Hit raycast(Vector3d origin, Vector3d direction, double maxDistance);

	record Hit(double distance, boolean terrain) {
	}
}
