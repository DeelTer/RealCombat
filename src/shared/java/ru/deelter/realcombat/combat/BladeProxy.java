package ru.deelter.realcombat.combat;

import ru.deelter.realcombat.config.CombatConfig;

/**
 * Where the blade of a fighter without VR is, drawn from his phase as the arm is drawn: across the face in a parry,
 * along the arc of the swing in a release, nowhere it could be touched otherwise. A VR blade meets it as it meets
 * another VR blade. Used by the server for players and by the client for the sparring partner.
 */
public final class BladeProxy {
	private static final double LENGTH = 1.3;

	private BladeProxy() {
	}

	/**
	 * @param yaw   the fighter's look, Minecraft degrees
	 * @param pitch the fighter's look, Minecraft degrees
	 */
	public static Blade.Segment of(double eyeX, double eyeY, double eyeZ, float yaw, float pitch, Fighter f, CombatConfig.Rules r, double radius) {
		if (f.getPhase() != Phase.PARRY && f.getPhase() != Phase.RELEASE) return null;
		double ry = Math.toRadians(yaw);
		double rp = Math.toRadians(pitch);
		Blade.V look = new Blade.V(-Math.sin(ry) * Math.cos(rp), -Math.sin(rp), Math.cos(ry) * Math.cos(rp));
		// right of the look, level; and up, square to both
		Blade.V right = new Blade.V(-Math.cos(ry), 0, -Math.sin(ry));
		Blade.V up = cross(right, look);
		Blade.V eye = new Blade.V(eyeX, eyeY, eyeZ);
		if (f.getPhase() == Phase.PARRY) {
			Blade.V center = eye.add(look.scale(0.6)).sub(up.scale(0.05));
			return new Blade.Segment(center.sub(right.scale(0.45)), center.add(right.scale(0.45)), radius);
		}
		Blade.V shoulder = eye.sub(up.scale(0.35)).add(right.scale(0.3));
		double half = r.getSlashArc() / 2;
		double p = f.progress();
		double angle = Math.toRadians(switch (f.getDir()) {
			case RIGHT, UNDER -> half - r.getSlashArc() * p;
			case LEFT, OVERHEAD -> -half + r.getSlashArc() * p;
			case STAB -> 0;
		});
		Blade.V dir = f.getDir().horizontal()
				? look.scale(Math.cos(angle)).add(right.scale(Math.sin(angle)))
				: look.scale(Math.cos(angle)).sub(up.scale(Math.sin(angle)));
		return new Blade.Segment(shoulder, shoulder.add(dir.scale(LENGTH)), radius);
	}

	/**
	 * The blade of a swing meets a blade held in its way this tick: the swinging blade, swept over the arc it went
	 * this tick, passes within the blades' thickness of the held one. A blade only near the line of the blow is not
	 * enough: it has to be in the blade's way.
	 */
	public static boolean meets(double eyeX, double eyeY, double eyeZ, float yaw, float pitch, Fighter f, CombatConfig.Rules r,
								Blade.Segment held, double radius) {
		if (f.getPhase() != Phase.RELEASE || held == null) return false;
		int ticks = f.getTicks();
		for (int i = 0; i <= SAMPLES; i++) {
			// the arc from a tick ago to now
			Fighter at = new Fighter(0);
			at.setDir(f.getDir());
			at.enter(Phase.RELEASE, f.getLength());
			at.setTicks(Math.max(0, ticks - 1));
			Blade.Segment s = of(eyeX, eyeY, eyeZ, yaw, pitch, at, r, radius);
			double t = i / (double) SAMPLES;
			Blade.Segment now = of(eyeX, eyeY, eyeZ, yaw, pitch, f, r, radius);
			Blade.V a = s.a().lerp(now.a(), t);
			Blade.V b = s.b().lerp(now.b(), t);
			if (Blade.distance(a, b, held.a(), held.b()) <= radius + held.radius() + MARGIN) return true;
		}
		return false;
	}

	private static final int SAMPLES = 6;
	private static final double MARGIN = 0.05;

	private static Blade.V cross(Blade.V a, Blade.V b) {
		return new Blade.V(a.y() * b.z() - a.z() * b.y(), a.z() * b.x() - a.x() * b.z(), a.x() * b.y() - a.y() * b.x());
	}
}
