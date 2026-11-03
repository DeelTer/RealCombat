package ru.deelter.realcombat.client;

import com.mojang.math.Axis;
import ru.deelter.realcombat.combat.Dir;
import ru.deelter.realcombat.combat.Phase;

/**
 * Key frames of a swing, in third person (the right arm, the left mirrors it) and in first person (the held item).
 * <p>
 * A held item sticks out of the hand at a right angle to the arm: with the arm down the blade points forward, with
 * the arm held forward it points up. So the blade is aimed with the arm: a horizontal slash is a low arm turned
 * sideways (yaw), a vertical one an arm swung up or down (pitch), a parry an arm held forward and rolled (z) so the
 * blade lies across. The windups are wide on purpose: Mordhau and Chivalry are played by reading where the blade goes
 * back, and a blocky arm needs big angles to read at a few blocks.
 */
public final class Poses {
	public static final Arm GUARD = new Arm(-0.6f, -0.1f, 0.1f, 0);
	private static final Arm PARRY = new Arm(-1.5f, 0.35f, 1.35f, -1);
	private static final Arm STUN = new Arm(-0.1f, 0.5f, 0.5f, 0);
	private static final Arm KICK = new Arm(-0.3f, 0.2f, 0.4f, 0);

	private static final Hand HAND_GUARD = new Hand(0, 0, 0, 0, 0, 0);
	private static final Hand HAND_PARRY = new Hand(-0.15f, 0.32f, -0.1f, 0, 0, 80);
	private static final Hand HAND_STUN = new Hand(0.1f, -0.25f, 0, 10, 0, -25);
	private static final Hand HAND_KICK = new Hand(0.15f, -0.15f, 0.05f, 0, 0, -10);

	/**
	 * the axis along the blade in the third person hand and in the first person hold, for {@link #roll}
	 */
	public static Axis THIRD_PERSON_BLADE = Axis.YP;
	public static Axis FIRST_PERSON_BLADE = Axis.YP;

	private Poses() {
	}

	/**
	 * How far the sword is turned about its own length, radians: a side slash cuts with the edge, so the blade lies
	 * flat in the plane of the swing; it turns in during the windup and back in the recovery.
	 */
	public static float roll(CombatPose pose) {
		if (!pose.dir().horizontal()) return 0;
		// turned about the blade's own length, in the fist: the edge leads
		float quarter = (float) (Math.PI / 2);
		float p = pose.progress();
		return switch (pose.phase()) {
			case WINDUP -> quarter * easeOut(p);
			case RELEASE -> quarter;
			case RECOVERY -> quarter * (1 - easeOut(p));
			default -> 0;
		};
	}

	private interface Key<T> {
		T lerp(T to, float t);
	}

	/**
	 * Euler angles of an arm, radians, and how far it is pushed back (model pixels, negative forward) for a stab.
	 */
	public record Arm(float x, float y, float z, float push) implements Key<Arm> {
		@Override
		public Arm lerp(Arm to, float t) {
			return new Arm(x + (to.x - x) * t, y + (to.y - y) * t, z + (to.z - z) * t, push + (to.push - push) * t);
		}
	}

	/**
	 * A shift of the held item in first person (blocks) and its turn (degrees), on top of the vanilla hold.
	 */
	public record Hand(float tx, float ty, float tz, float rx, float ry, float rz) implements Key<Hand> {
		@Override
		public Hand lerp(Hand to, float t) {
			return new Hand(tx + (to.tx - tx) * t, ty + (to.ty - ty) * t, tz + (to.tz - tz) * t,
					rx + (to.rx - rx) * t, ry + (to.ry - ry) * t, rz + (to.rz - rz) * t);
		}
	}

	private static Arm armWindup(Dir dir) {
		return switch (dir) {
			// up and back over the head, the blade behind it
			case OVERHEAD -> new Arm(-2.95f, -0.2f, 0.25f, 0);
			// down and back by the hip, the blade low behind
			case UNDER -> new Arm(0.85f, -0.35f, 0.35f, 0);
			// out to the right, the blade pointing right
			case RIGHT -> new Arm(-0.9f, 1.3f, 0.7f, 0);
			// across the chest, the blade pointing left
			case LEFT -> new Arm(-1.0f, -1.3f, -0.2f, 0);
			// drawn back, the blade level
			case STAB -> new Arm(0.15f, -0.15f, 0.1f, 3.5f);
		};
	}

	private static Arm armEnd(Dir dir) {
		return switch (dir) {
			// as recorded: the blade ends low, by the waist, its point down
			case OVERHEAD -> new Arm(0.35f, 0.15f, 0f, 0);
			case UNDER -> new Arm(-1.75f, 0.15f, 0.1f, 0);
			case RIGHT -> new Arm(-0.8f, -1.25f, -0.1f, 0);
			case LEFT -> new Arm(-0.8f, 1.25f, 0.45f, 0);
			case STAB -> new Arm(-0.55f, 0f, 0f, -3f);
		};
	}

	private static Hand handWindup(Dir dir) {
		return switch (dir) {
			case OVERHEAD -> new Hand(0.05f, 0.5f, 0.1f, 20, 0, 0);
			case UNDER -> new Hand(0.1f, -0.45f, 0.1f, 30, 0, 0);
			case RIGHT -> new Hand(0.4f, 0.15f, 0.05f, 0, -55, -60);
			case LEFT -> new Hand(-0.7f, 0.1f, -0.15f, 0, 45, 50);
			case STAB -> new Hand(0.05f, -0.08f, 0.25f, -55, 0, 0);
		};
	}

	private static Hand handEnd(Dir dir) {
		return switch (dir) {
			case OVERHEAD -> new Hand(-0.1f, -0.4f, -0.2f, -100, 0, 0);
			case UNDER -> new Hand(-0.1f, 0.3f, -0.3f, -30, 0, 0);
			case RIGHT -> new Hand(-0.8f, -0.05f, -0.15f, 0, 55, 70);
			case LEFT -> new Hand(0.5f, -0.08f, -0.15f, 0, -55, -65);
			case STAB -> new Hand(-0.1f, 0.05f, -0.45f, -65, 0, 0);
		};
	}

	public static Arm arm(CombatPose pose) {
		return frame(pose, GUARD, armWindup(pose.dir()), armEnd(pose.dir()), PARRY, STUN, KICK);
	}

	public static Hand hand(CombatPose pose) {
		return frame(pose, HAND_GUARD, handWindup(pose.dir()), handEnd(pose.dir()), HAND_PARRY, HAND_STUN, HAND_KICK);
	}

	private static <T extends Key<T>> T frame(CombatPose pose, T guard, T windup, T end, T parry, T stun, T kick) {
		float p = pose.progress();
		return switch (pose.phase()) {
			case IDLE -> guard;
			case WINDUP -> guard.lerp(windup, windupCurve(p));
			case RELEASE -> windup.lerp(end, releaseCurve(p));
			case RECOVERY -> end.lerp(guard, smooth(p));
			case PARRY -> guard.lerp(parry, Math.min(1f, p * 3f));
			case PARRY_RECOVERY -> parry.lerp(guard, p);
			case KICK -> guard.lerp(kick, (float) Math.sin(p * Math.PI));
			case STUN -> stun.lerp(guard, p * p);
		};
	}

	/**
	 * How far the body turns, radians: a side slash winds the shoulders back and unwinds them through the blow, an
	 * overhead and an underhand lean a little into it.
	 */
	public static float twist(CombatPose pose) {
		float windup = switch (pose.dir()) {
			case RIGHT -> TWIST;
			case LEFT -> -TWIST;
			case STAB -> TWIST * 0.4f;
			default -> TWIST * 0.25f;
		};
		float end = pose.dir().horizontal() ? -windup : pose.dir() == Dir.STAB ? -windup * 0.5f : -windup * 0.5f;
		float p = pose.progress();
		return switch (pose.phase()) {
			case WINDUP -> windup * easeOut(p);
			case RELEASE -> windup + (end - windup) * releaseCurve(p);
			case RECOVERY -> end * (1 - easeOut(p));
			default -> 0;
		};
	}

	public static float TWIST = 0.5f;

	/**
	 * the kicking leg, radians forward
	 */
	public static float kickLeg(CombatPose pose) {
		return pose.phase() == Phase.KICK ? -1.5f * (float) Math.sin(Math.min(1f, pose.progress() * 1.6f) * Math.PI) : 0;
	}

	/**
	 * The windup snaps most of the way back at once and then creeps on, held like a drawn bow: the blow reads early
	 * and the arm looks quick, in the same ticks.
	 */
	static float windupCurve(float t) {
		if (t < 0.4f) {
			float u = t / 0.4f;
			return 0.88f * (1 - (1 - u) * (1 - u) * (1 - u));
		}
		return 0.88f + 0.12f * (t - 0.4f) / 0.6f;
	}

	/**
	 * The release gathers for a moment (a little further back), cuts through fast, and follows through past its end
	 * before it settles: the speed is in the middle, where the eye does not follow anyway.
	 */
	static float releaseCurve(float t) {
		if (t < 0.15f) return -0.06f * smooth(t / 0.15f);
		if (t < 0.6f) return -0.06f + 1.14f * smooth((t - 0.15f) / 0.45f);
		return 1.08f - 0.08f * smooth((t - 0.6f) / 0.4f);
	}

	private static float smooth(float t) {
		t = Math.clamp(t, 0f, 1f);
		return t * t * (3 - 2 * t);
	}

	private static float easeOut(float t) {
		return 1 - (1 - t) * (1 - t);
	}

	private static float easeIn(float t) {
		return t * t;
	}
}
