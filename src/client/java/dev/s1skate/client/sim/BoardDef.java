package dev.s1skate.client.sim;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.joml.Vector3d;

/** One skateboard prefab: its physics layout, settings, camera and visuals, all in board-local Unity space. */
public final class BoardDef {
	public String key = "builtin";
	public String displayName = "Skateboard";
	public SkateSettings settings = new SkateSettings();
	public SkateSettings rain;
	public boolean slowOnTerrain = true;

	// Rigidbody
	public double drag = 0.6, angularDrag = 15.0;
	public double maxAngularVelocity = 7.0; // Unity default, prefabs don't change it
	public Vector3d centerOfMass = new Vector3d(0, -0.02, 0);
	public Vector3d[] hoverPoints = {
		new Vector3d(0.1, 0, 0.3), new Vector3d(-0.1, 0, 0.3), new Vector3d(0.1, 0, -0.3), new Vector3d(-0.1, 0, -0.3)
	};
	public Vector3d frontAxle = new Vector3d(0, 0, 0.275), rearAxle = new Vector3d(0, 0, -0.275);

	// SkateboardCamera
	public double camHorizontalOffset = -2.0, camVerticalOffset = 1.7;
	public Vector3d cameraOrigin = new Vector3d(0, 1.2, 0);
	public float fovMinSpeed = 1.0f, fovMaxSpeed = 1.25f, fovChangeRate = 3.0f;

	// SkateboardVisuals
	public float maxBoardLean = 9f, boardLeanRate = 5f;

	public String model; // mesh json name in assets/s1skate/s1/, null = built-in placeholder

	public static BoardDef fromJson(String key, JsonObject o) {
		BoardDef d = new BoardDef();
		d.key = key;
		d.displayName = prettify(key);
		d.settings = SkateSettings.fromJson(o.getAsJsonObject("settings"));
		if (o.has("rain") && o.get("rain").isJsonObject()) d.rain = SkateSettings.fromJson(o.getAsJsonObject("rain"));
		if (o.has("slowOnTerrain")) d.slowOnTerrain = o.get("slowOnTerrain").getAsBoolean();
		if (o.has("rigidbody")) {
			JsonObject rb = o.getAsJsonObject("rigidbody");
			d.drag = rb.get("drag").getAsDouble();
			d.angularDrag = rb.get("angularDrag").getAsDouble();
		}
		if (o.has("centerOfMass")) d.centerOfMass = vec(o.getAsJsonArray("centerOfMass"));
		if (o.has("hoverPoints")) {
			JsonArray hp = o.getAsJsonArray("hoverPoints");
			d.hoverPoints = new Vector3d[hp.size()];
			for (int i = 0; i < hp.size(); i++) d.hoverPoints[i] = vec(hp.get(i).getAsJsonArray());
		}
		if (o.has("frontAxle")) d.frontAxle = vec(o.getAsJsonArray("frontAxle"));
		if (o.has("rearAxle")) d.rearAxle = vec(o.getAsJsonArray("rearAxle"));
		if (o.has("camera")) {
			JsonObject c = o.getAsJsonObject("camera");
			d.camHorizontalOffset = c.get("HorizontalOffset").getAsDouble();
			d.camVerticalOffset = c.get("VerticalOffset").getAsDouble();
			d.fovMinSpeed = c.get("FOVMultiplier_MinSpeed").getAsFloat();
			d.fovMaxSpeed = c.get("FOVMultiplier_MaxSpeed").getAsFloat();
			d.fovChangeRate = c.get("FOVMultiplierChangeRate").getAsFloat();
			if (c.has("origin")) d.cameraOrigin = vec(c.getAsJsonArray("origin"));
		}
		if (o.has("visuals")) {
			JsonObject v = o.getAsJsonObject("visuals");
			d.maxBoardLean = v.get("MaxBoardLean").getAsFloat();
			d.boardLeanRate = v.get("BoardLeanRate").getAsFloat();
		}
		if (o.has("model")) d.model = o.get("model").getAsString();
		return d;
	}

	private static Vector3d vec(JsonArray a) {
		return new Vector3d(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
	}

	private static String prettify(String key) {
		StringBuilder sb = new StringBuilder();
		for (String w : key.split("_")) {
			if (w.isEmpty()) continue;
			if (!sb.isEmpty()) sb.append(' ');
			sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
		}
		String s = sb.toString().replace("Board", "Skateboard");
		return s.equals("Skateboard") || s.isEmpty() ? "Skateboard" : s;
	}
}
