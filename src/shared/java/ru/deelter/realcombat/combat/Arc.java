package ru.deelter.realcombat.combat;

import ru.deelter.realcombat.config.CombatConfig;

/**
 * Where the blade is during a release, as an angle around the attacker's look, and whether it crossed a target this
 * tick. The angles are measured against the look of this very tick, so turning the view during a release carries
 * the blade with it: dragging a slash makes it land later, turning into it sooner, as in Mordhau. The same maths on
 * the Fabric server, the Paper plugin and the client.
 */
public final class Arc {
	private Arc() {
	}

	/**
	 * Whether the blade crossed the target between two moments of the release.
	 *
	 * @param yaw   degrees from the look to the target, positive to the right
	 * @param pitch degrees from the look to the target, positive downwards
	 */
	public static boolean crosses(Dir dir, float from, float to, double yaw, double pitch, CombatConfig.Rules r) {
		double arc = r.getSlashArc();
		double half = arc / 2;
		double narrow = r.getNarrowArc() / 2;
		// a little slack, so a target is not missed between two ticks of a fast blade
		double slack = 12;
		return switch (dir) {
			case STAB -> Math.abs(yaw) <= narrow && Math.abs(pitch) <= narrow + 10;
			case RIGHT -> Math.abs(pitch) <= half && between(yaw, half - arc * from, half - arc * to, slack);
			case LEFT -> Math.abs(pitch) <= half && between(yaw, -half + arc * from, -half + arc * to, slack);
			case OVERHEAD -> Math.abs(yaw) <= narrow + 10 && between(pitch, -half + arc * from, -half + arc * to, slack);
			case UNDER -> Math.abs(yaw) <= narrow + 10 && between(pitch, half - arc * from, half - arc * to, slack);
		};
	}

	private static boolean between(double value, double a, double b, double slack) {
		return value >= Math.min(a, b) - slack && value <= Math.max(a, b) + slack;
	}

	/**
	 * Degrees from a look (Minecraft yaw and pitch) to a point: [yaw, positive to the right; pitch, positive down].
	 */
	public static double[] angles(double eyeX, double eyeY, double eyeZ, float lookYaw, float lookPitch, double x, double y, double z) {
		double dx = x - eyeX;
		double dy = y - eyeY;
		double dz = z - eyeZ;
		double yaw = Math.toDegrees(Math.atan2(-dx, dz));
		double pitch = Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		return new double[]{wrap(yaw - lookYaw), pitch - lookPitch};
	}

	public static double wrap(double degrees) {
		degrees %= 360;
		if (degrees >= 180) degrees -= 360;
		if (degrees < -180) degrees += 360;
		return degrees;
	}
}
