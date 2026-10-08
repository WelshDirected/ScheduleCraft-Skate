package dev.s1skate.client.sim;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Port of ScheduleOne.Experimental.SkateboardSettings. Values are loaded from the boards.json the
 * converter writes from the user's own install; the built-in values below are an approximation used only
 * when that pack is missing.
 */
public final class SkateSettings {
	public float TurnForce = 25f;
	public float TurnChangeRate = 4f;
	public float TurnReturnToRestRate = 1.5f;
	public float TurnSpeedBoost = 1f;
	public UnityCurve TurnForceMap = new UnityCurve(new float[][] {{0, 0, 0, 10}, {0.1f, 1, 0, 0}, {1, 1, 0, 0}});
	public float Gravity = 12f;
	public float BrakeForce = 4f;
	public float ReverseTopSpeed_Kmh = 1f;
	public float RotationClampForce = 5f;
	public boolean FrictionEnabled = true;
	public UnityCurve LongitudinalFrictionCurve = UnityCurve.linear(0, -0.5f, 1, -1.4f);
	public float LongitudinalFrictionMultiplier = 1f;
	public float LateralFrictionForceMultiplier = 10f;
	public float JumpForce = 35f;
	public float JumpDuration_Min = 0.17f;
	public float JumpDuration_Max = 0.27f;
	public UnityCurve FrontAxleJumpCurve = UnityCurve.linear(0, 1, 1, 0);
	public UnityCurve RearAxleJumpCurve = new UnityCurve(new float[][] {{0, 0, 2, 2}, {0.7f, 1.58f, 0, 0}, {1, 0, -5, -5}});
	public UnityCurve JumpForwardForceCurve = UnityCurve.linear(0, 1, 1, 0);
	public float JumpForwardBoost = 15f;
	public float HoverForce = 1f;
	public float HoverRayLength = 0.2f;
	public float HoverHeight = 0.096f;
	public float Hover_P = 150f;
	public float Hover_I = 1f;
	public float Hover_D = 10f;
	public float TopSpeed_Kmh = 24f;
	public float PushForceMultiplier = 12f;
	public UnityCurve PushForceMultiplierMap = UnityCurve.linear(0, 1, 1, 0);
	public float PushForceDuration = 0.5f;
	public float PushDelay = 0.35f;
	public UnityCurve PushForceCurve = new UnityCurve(new float[][] {{0, 0, 10, 10}, {0.1f, 1, -0.1f, -0.1f}, {1, 0, -0.7f, -0.7f}});
	public boolean AirMovementEnabled = true;
	public float AirMovementForce = 10f;
	public float AirMovementJumpReductionDuration = 0.25f;
	public UnityCurve AirMovementJumpReductionCurve = UnityCurve.linear(0, 0, 1, 1);

	public float topSpeedMs() {
		return TopSpeed_Kmh / 3.6f;
	}

	public static SkateSettings fromJson(JsonObject o) {
		SkateSettings s = new SkateSettings();
		s.TurnForce = f(o, "TurnForce", s.TurnForce);
		s.TurnChangeRate = f(o, "TurnChangeRate", s.TurnChangeRate);
		s.TurnReturnToRestRate = f(o, "TurnReturnToRestRate", s.TurnReturnToRestRate);
		s.TurnSpeedBoost = f(o, "TurnSpeedBoost", s.TurnSpeedBoost);
		s.TurnForceMap = c(o, "TurnForceMap", s.TurnForceMap);
		s.Gravity = f(o, "Gravity", s.Gravity);
		s.BrakeForce = f(o, "BrakeForce", s.BrakeForce);
		s.ReverseTopSpeed_Kmh = f(o, "ReverseTopSpeed_Kmh", s.ReverseTopSpeed_Kmh);
		s.RotationClampForce = f(o, "RotationClampForce", s.RotationClampForce);
		s.FrictionEnabled = f(o, "FrictionEnabled", 1) != 0;
		s.LongitudinalFrictionCurve = c(o, "LongitudinalFrictionCurve", s.LongitudinalFrictionCurve);
		s.LongitudinalFrictionMultiplier = f(o, "LongitudinalFrictionMultiplier", s.LongitudinalFrictionMultiplier);
		s.LateralFrictionForceMultiplier = f(o, "LateralFrictionForceMultiplier", s.LateralFrictionForceMultiplier);
		s.JumpForce = f(o, "JumpForce", s.JumpForce);
		s.JumpDuration_Min = f(o, "JumpDuration_Min", s.JumpDuration_Min);
		s.JumpDuration_Max = f(o, "JumpDuration_Max", s.JumpDuration_Max);
		s.FrontAxleJumpCurve = c(o, "FrontAxleJumpCurve", s.FrontAxleJumpCurve);
		s.RearAxleJumpCurve = c(o, "RearAxleJumpCurve", s.RearAxleJumpCurve);
		s.JumpForwardForceCurve = c(o, "JumpForwardForceCurve", s.JumpForwardForceCurve);
		s.JumpForwardBoost = f(o, "JumpForwardBoost", s.JumpForwardBoost);
		s.HoverForce = f(o, "HoverForce", s.HoverForce);
		s.HoverRayLength = f(o, "HoverRayLength", s.HoverRayLength);
		s.HoverHeight = f(o, "HoverHeight", s.HoverHeight);
		s.Hover_P = f(o, "Hover_P", s.Hover_P);
		s.Hover_I = f(o, "Hover_I", s.Hover_I);
		s.Hover_D = f(o, "Hover_D", s.Hover_D);
		s.TopSpeed_Kmh = f(o, "TopSpeed_Kmh", s.TopSpeed_Kmh);
		s.PushForceMultiplier = f(o, "PushForceMultiplier", s.PushForceMultiplier);
		s.PushForceMultiplierMap = c(o, "PushForceMultiplierMap", s.PushForceMultiplierMap);
		s.PushForceDuration = f(o, "PushForceDuration", s.PushForceDuration);
		s.PushDelay = f(o, "PushDelay", s.PushDelay);
		s.PushForceCurve = c(o, "PushForceCurve", s.PushForceCurve);
		s.AirMovementEnabled = f(o, "AirMovementEnabled", 1) != 0;
		s.AirMovementForce = f(o, "AirMovementForce", s.AirMovementForce);
		s.AirMovementJumpReductionDuration = f(o, "AirMovementJumpReductionDuration", s.AirMovementJumpReductionDuration);
		s.AirMovementJumpReductionCurve = c(o, "AirMovementJumpReductionCurve", s.AirMovementJumpReductionCurve);
		return s;
	}

	/** Port of SkateboardSettings.Blend: an override value of -1 means "leave alone", otherwise it multiplies. */
	public SkateSettings blend(SkateSettings other, float k) {
		SkateSettings s = copy();
		s.TurnForce *= m(other.TurnForce, k);
		s.TurnChangeRate *= m(other.TurnChangeRate, k);
		s.TurnReturnToRestRate *= m(other.TurnReturnToRestRate, k);
		s.TurnSpeedBoost *= m(other.TurnSpeedBoost, k);
		s.Gravity *= m(other.Gravity, k);
		s.BrakeForce *= m(other.BrakeForce, k);
		s.ReverseTopSpeed_Kmh *= m(other.ReverseTopSpeed_Kmh, k);
		s.RotationClampForce *= m(other.RotationClampForce, k);
		s.LongitudinalFrictionMultiplier *= m(other.LongitudinalFrictionMultiplier, k);
		s.LateralFrictionForceMultiplier *= m(other.LateralFrictionForceMultiplier, k);
		s.JumpForce *= m(other.JumpForce, k);
		s.JumpDuration_Min *= m(other.JumpDuration_Min, k);
		s.JumpDuration_Max *= m(other.JumpDuration_Max, k);
		s.JumpForwardBoost *= m(other.JumpForwardBoost, k);
		s.HoverForce *= m(other.HoverForce, k);
		s.HoverRayLength *= m(other.HoverRayLength, k);
		s.HoverHeight *= m(other.HoverHeight, k);
		s.Hover_P *= m(other.Hover_P, k);
		s.Hover_I *= m(other.Hover_I, k);
		s.Hover_D *= m(other.Hover_D, k);
		s.TopSpeed_Kmh *= m(other.TopSpeed_Kmh, k);
		s.PushForceMultiplier *= m(other.PushForceMultiplier, k);
		s.PushForceDuration *= m(other.PushForceDuration, k);
		s.PushDelay *= m(other.PushDelay, k);
		s.AirMovementForce *= m(other.AirMovementForce, k);
		s.AirMovementJumpReductionDuration *= m(other.AirMovementJumpReductionDuration, k);
		return s;
	}

	private static float m(float other, float k) {
		return other != -1f ? 1f + (other - 1f) * k : 1f;
	}

	private SkateSettings copy() {
		SkateSettings s = new SkateSettings();
		for (var field : SkateSettings.class.getFields()) {
			try {
				field.set(s, field.get(this));
			} catch (IllegalAccessException e) {
				throw new IllegalStateException(e);
			}
		}
		return s;
	}

	private static float f(JsonObject o, String key, float def) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsFloat() : def;
	}

	private static UnityCurve c(JsonObject o, String key, UnityCurve def) {
		JsonElement e = o.get(key);
		if (e == null || !e.isJsonArray() || e.getAsJsonArray().isEmpty()) return def;
		JsonArray keys = e.getAsJsonArray();
		float[][] out = new float[keys.size()][];
		for (int i = 0; i < keys.size(); i++) {
			JsonArray k = keys.get(i).getAsJsonArray();
			out[i] = new float[k.size()];
			for (int j = 0; j < k.size(); j++) out[i][j] = k.get(j).getAsFloat();
		}
		return new UnityCurve(out);
	}
}
