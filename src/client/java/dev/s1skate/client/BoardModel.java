package dev.s1skate.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.s1skate.S1Skate;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/** A skateboard mesh in board-local Unity space, drawn as triangles through Minecraft's entity pipeline. */
public final class BoardModel {
	static final Identifier WHITE = Identifier.fromNamespaceAndPath(S1Skate.MOD_ID, "textures/white.png");

	record Part(RenderType renderType, int argb, float[] pos, float[] nrm, float[] uv, int[] idx) {
	}

	private final List<Part> parts = new ArrayList<>();

	public static BoardModel fromJson(JsonArray json) {
		BoardModel m = new BoardModel();
		for (JsonElement e : json) {
			JsonObject p = e.getAsJsonObject();
			JsonElement tex = p.get("texture");
			Identifier texture = tex == null || tex.isJsonNull() ? WHITE
				: Identifier.fromNamespaceAndPath(S1Skate.MOD_ID, "textures/s1/" + tex.getAsString());
			float[] color = floats(p.getAsJsonArray("color"));
			float[] uv = floats(p.getAsJsonArray("uvs"));
			float[] scale = p.has("uvScale") ? floats(p.getAsJsonArray("uvScale")) : new float[] {1, 1};
			float[] offset = p.has("uvOffset") ? floats(p.getAsJsonArray("uvOffset")) : new float[] {0, 0};
			for (int i = 0; i < uv.length; i += 2) {
				uv[i] = uv[i] * scale[0] + offset[0];
				uv[i + 1] = 1f - (uv[i + 1] * scale[1] + offset[1]); // Unity v is up, Minecraft v is down
			}
			m.parts.add(new Part(RenderTypes.entityCutout(texture), argb(color), floats(p.getAsJsonArray("positions")),
				floats(p.getAsJsonArray("normals")), uv, ints(p.getAsJsonArray("indices"))));
		}
		return m;
	}

	/** Used when the local pack is missing: a plain deck and four wheels, roughly the default board's size. */
	public static BoardModel placeholder() {
		BoardModel m = new BoardModel();
		m.box(-0.125f, -0.012f, -0.43f, 0.125f, 0.057f, 0.43f, 0xFF3B3B3B);
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				m.box(sx * 0.12f - 0.02f, -0.079f, sz * 0.229f - 0.03f, sx * 0.12f + 0.02f, -0.019f, sz * 0.229f + 0.03f, 0xFFE3D4C4);
			}
		}
		return m;
	}

	private void box(float x0, float y0, float z0, float x1, float y1, float z1, int argb) {
		float[][] c = {{x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0}, {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}};
		int[][] faces = {{0, 3, 2, 1}, {4, 5, 6, 7}, {0, 4, 7, 3}, {1, 2, 6, 5}, {3, 7, 6, 2}, {0, 1, 5, 4}};
		float[][] normals = {{0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}, {0, 1, 0}, {0, -1, 0}};
		float[] pos = new float[72], nrm = new float[72], uv = new float[48];
		int[] idx = new int[36];
		for (int f = 0; f < 6; f++) {
			for (int k = 0; k < 4; k++) {
				int v = f * 4 + k;
				System.arraycopy(c[faces[f][k]], 0, pos, v * 3, 3);
				System.arraycopy(normals[f], 0, nrm, v * 3, 3);
			}
			int b = f * 4, o = f * 6;
			idx[o] = b; idx[o + 1] = b + 1; idx[o + 2] = b + 2; idx[o + 3] = b; idx[o + 4] = b + 2; idx[o + 5] = b + 3;
		}
		parts.add(new Part(RenderTypes.entityCutout(WHITE), argb, pos, nrm, uv, idx));
	}

	/** The pose stack must already map board-local Unity space to camera-relative Minecraft space. */
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light) {
		for (Part p : parts) {
			collector.submitCustomGeometry(poseStack, p.renderType(), (pose, buffer) -> emit(p, pose, buffer, light));
		}
	}

	private static void emit(Part p, PoseStack.Pose pose, VertexConsumer buffer, int light) {
		int[] idx = p.idx();
		for (int t = 0; t + 2 < idx.length; t += 3) {
			// entity render types draw quads: send each triangle as a quad with its last corner repeated
			vertex(p, pose, buffer, light, idx[t]);
			vertex(p, pose, buffer, light, idx[t + 1]);
			vertex(p, pose, buffer, light, idx[t + 2]);
			vertex(p, pose, buffer, light, idx[t + 2]);
		}
	}

	private static void vertex(Part p, PoseStack.Pose pose, VertexConsumer buffer, int light, int i) {
		float[] pos = p.pos(), n = p.nrm(), uv = p.uv();
		buffer.addVertex(pose, pos[i * 3], pos[i * 3 + 1], pos[i * 3 + 2])
			.setColor(p.argb())
			.setUv(uv.length > i * 2 + 1 ? uv[i * 2] : 0f, uv.length > i * 2 + 1 ? uv[i * 2 + 1] : 0f)
			.setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(light)
			.setNormal(pose, n[i * 3], n[i * 3 + 1], n[i * 3 + 2]);
	}

	private static int argb(float[] c) {
		int r = Math.round(clamp(c[0]) * 255), g = Math.round(clamp(c[1]) * 255), b = Math.round(clamp(c[2]) * 255);
		int a = c.length > 3 ? Math.round(clamp(c[3]) * 255) : 255;
		return a << 24 | r << 16 | g << 8 | b;
	}

	private static float clamp(float v) {
		return Math.max(0f, Math.min(1f, v));
	}

	private static float[] floats(JsonArray a) {
		float[] out = new float[a.size()];
		for (int i = 0; i < out.length; i++) out[i] = a.get(i).getAsFloat();
		return out;
	}

	private static int[] ints(JsonArray a) {
		int[] out = new int[a.size()];
		for (int i = 0; i < out.length; i++) out[i] = a.get(i).getAsInt();
		return out;
	}
}
