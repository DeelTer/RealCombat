package ru.deelter.realcombat.combat;

import lombok.Getter;

import java.util.List;

/**
 * A blade with a body, for VR players: it follows the controller like a hand on a spring, but it can't pass through
 * a body, a block or another blade, as in Blade &amp; Sorcery. Where the blade stops is where it touched: a body it hit,
 * a blade that parried it, a wall it struck. The same maths runs on the server (for what the blade hurts) and on the
 * VR client (for where the hand is drawn and when the controller buzzes).
 * <p>
 * Only the hand is moved to keep the blade out; the blade keeps the controller's direction, so the sword never turns
 * in the hand on its own.
 */
public final class Blade {
	private static final int SUBSTEPS = 6;

	public record V(double x, double y, double z) {
		public static final V ZERO = new V(0, 0, 0);

		public V add(V o) {
			return new V(x + o.x, y + o.y, z + o.z);
		}

		public V sub(V o) {
			return new V(x - o.x, y - o.y, z - o.z);
		}

		public V scale(double s) {
			return new V(x * s, y * s, z * s);
		}

		public double dot(V o) {
			return x * o.x + y * o.y + z * o.z;
		}

		public double length() {
			return Math.sqrt(dot(this));
		}

		public V lerp(V to, double t) {
			return new V(x + (to.x - x) * t, y + (to.y - y) * t, z + (to.z - z) * t);
		}
	}

	public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
		public Box inflate(double r) {
			return new Box(minX - r, minY - r, minZ - r, maxX + r, maxY + r, maxZ + r);
		}

		/**
		 * the segment from a to b touches the box (slab test)
		 */
		public boolean touches(V a, V b) {
			double t0 = 0, t1 = 1;
			double[] from = {a.x, a.y, a.z};
			double[] d = {b.x - a.x, b.y - a.y, b.z - a.z};
			double[] min = {minX, minY, minZ};
			double[] max = {maxX, maxY, maxZ};
			for (int i = 0; i < 3; i++) {
				if (Math.abs(d[i]) < 1e-9) {
					if (from[i] < min[i] || from[i] > max[i]) return false;
					continue;
				}
				double u = (min[i] - from[i]) / d[i];
				double v = (max[i] - from[i]) / d[i];
				t0 = Math.max(t0, Math.min(u, v));
				t1 = Math.min(t1, Math.max(u, v));
				if (t0 > t1) return false;
			}
			return true;
		}
	}

	/**
	 * a blade (or anything long and thin) from a to b, this thick
	 */
	public record Segment(V a, V b, double radius) {
	}

	public enum Contact {
		NONE,
		/**
		 * a body: one of {@code bodies}
		 */
		BODY,
		/**
		 * another blade: one of {@code blades}
		 */
		BLADE,
		/**
		 * a block
		 */
		SOLID,
		/**
		 * stuck in a body after a stab: only in and out along the blade
		 */
		LODGED,
		/**
		 * pulled out of the body it was stuck in, this step
		 */
		FREED
	}

	/**
	 * @param index which body or blade was touched
	 * @param speed blocks per tick the tip moved into it
	 * @param at    where the tip was when it touched
	 */
	public record Result(Contact contact, int index, double speed, V at) {
		public static final Result NONE = new Result(Contact.NONE, -1, 0, V.ZERO);
	}

	@Getter
	private V hand = V.ZERO;
	@Getter
	private V dir = new V(0, 0, 1);
	@Getter
	private double length;
	private final double radius;
	private boolean placed;
	/**
	 * the hand was pulled free of what held it this step
	 */
	@Getter
	private boolean slipped;
	/**
	 * stuck in a body ({@link #lodge}): the hand where the blade went in, the way it went in, how deep it may go
	 */
	@Getter
	private boolean lodged;
	private V entry = V.ZERO;
	private double maxDepth;
	private double depth;

	public Blade(double length, double radius) {
		this.length = length;
		this.radius = radius;
	}

	public V tip() {
		return hand.add(dir.scale(length));
	}

	public Segment segment() {
		return new Segment(hand, tip(), radius);
	}

	public void place(V hand, V dir) {
		this.hand = hand;
		this.dir = dir;
		placed = true;
	}

	/**
	 * how far the drawn hand is from the real one
	 */
	public V offset(V controller) {
		return hand.sub(controller);
	}

	/**
	 * Moves the blade towards the controller and stops it at the first thing in the way.
	 *
	 * @param follow     the share of the way to the controller made in one step: 1 is no lag
	 * @param maxStretch how far the hand may be held back before it is pulled free, blocks
	 */
	/**
	 * The blade went into a body point first: from now on it moves only along itself, in up to {@code maxDepth} and out
	 * again, until it is pulled out ({@link Contact#FREED}). The body moving carries it ({@link #shift}).
	 */
	public void lodge(double maxDepth) {
		lodged = true;
		entry = hand;
		this.maxDepth = maxDepth;
		depth = 0;
	}

	public void unlodge() {
		lodged = false;
	}

	/**
	 * the body the blade is stuck in moved by {@code delta}
	 */
	public void shift(V delta) {
		if (!lodged) return;
		entry = entry.add(delta);
		hand = hand.add(delta);
	}

	public Result step(V controller, V controllerDir, List<Box> bodies, List<Segment> blades, List<Box> solids, double follow, double maxStretch) {
		slipped = false;
		if (!placed) {
			place(controller, controllerDir);
			return Result.NONE;
		}
		if (lodged) return stuck(controller, controllerDir, maxStretch);
		V fromTip = tip();
		dir = controllerDir;
		V to = hand.lerp(controller, follow);
		Hit hit = sweep(hand, to, bodies, blades, solids);
		if (hit == null) {
			hand = to;
			return Result.NONE;
		}
		hand = hit.free;
		// slide: the part of the way that goes along what was touched is still made, only the part into it is lost,
		// so a blade grinds along a blade and glances off a body instead of sticking to it
		V rest = to.sub(hand);
		double into = rest.dot(hit.normal);
		if (into < 0) {
			V along = rest.sub(hit.normal.scale(into));
			Hit again = sweep(hand, hand.add(along), bodies, blades, solids);
			hand = again == null ? hand.add(along) : again.free;
		}
		if (hand.sub(controller).length() > maxStretch) {
			// held back too far: the hand is pulled free, as a sword pulled out of a body
			hand = controller;
			slipped = true;
		}
		double speed = hit.tip.sub(fromTip).length() * SUBSTEPS / hit.step;
		return new Result(hit.result.contact(), hit.result.index(), speed, hit.tip);
	}

	private Result stuck(V controller, V controllerDir, double maxStretch) {
		V toController = controller.sub(entry);
		double along = toController.dot(dir);
		double side = toController.sub(dir.scale(along)).length();
		if (along < -0.08 || side > maxStretch) {
			// drawn back out, or wrenched sideways out of the wound
			lodged = false;
			hand = hand.lerp(controller, 0.5);
			dir = controllerDir;
			return new Result(Contact.FREED, -1, Math.abs(along - depth), tip());
		}
		double next = Math.clamp(along, 0, maxDepth);
		double moved = Math.abs(next - depth);
		depth = next;
		hand = entry.add(dir.scale(depth));
		return new Result(Contact.LODGED, -1, moved, tip());
	}

	/**
	 * the first touch on the way of the hand from {@code from} to {@code to}, with the last free place before it
	 */
	private record Hit(Result result, V free, V tip, V normal, int step) {
	}

	private Hit sweep(V from, V to, List<Box> bodies, List<Segment> blades, List<Box> solids) {
		for (int i = 1; i <= SUBSTEPS; i++) {
			V h = from.lerp(to, i / (double) SUBSTEPS);
			V t = h.add(dir.scale(length));
			Result hit = touch(h, t, bodies, blades, solids);
			if (hit != null) {
				V free = from.lerp(to, (i - 1) / (double) SUBSTEPS);
				return new Hit(hit, free, t, normal(hit, free, bodies, blades, solids), i);
			}
		}
		return null;
	}

	/**
	 * which way out of what was touched, at the free place before the touch
	 */
	private V normal(Result hit, V free, List<Box> bodies, List<Segment> blades, List<Box> solids) {
		V tip = free.add(dir.scale(length));
		V out;
		if (hit.contact() == Contact.BLADE) {
			Segment other = blades.get(hit.index());
			V[] points = closest(free, tip, other.a(), other.b());
			out = points[0].sub(points[1]);
		} else {
			Box box = hit.contact() == Contact.BODY ? bodies.get(hit.index()) : nearest(solids, free, tip);
			out = box == null ? V.ZERO : boxNormal(box, free, tip);
		}
		double length = out.length();
		return length < 1e-9 ? V.ZERO : out.scale(1 / length);
	}

	private static Box nearest(List<Box> boxes, V a, V b) {
		Box best = null;
		double bestDistance = Double.MAX_VALUE;
		V mid = a.lerp(b, 0.5);
		for (Box box : boxes) {
			V center = new V((box.minX() + box.maxX()) / 2, (box.minY() + box.maxY()) / 2, (box.minZ() + box.maxZ()) / 2);
			double d = center.sub(mid).length();
			if (d < bestDistance) {
				bestDistance = d;
				best = box;
			}
		}
		return best;
	}

	/**
	 * the face of the box the blade is nearest to, as its outward axis
	 */
	private static V boxNormal(Box box, V a, V b) {
		V p = a.lerp(b, 0.5);
		double[] gaps = {p.x() - box.maxX(), box.minX() - p.x(), p.y() - box.maxY(), box.minY() - p.y(), p.z() - box.maxZ(), box.minZ() - p.z()};
		int best = 0;
		for (int i = 1; i < 6; i++) if (gaps[i] > gaps[best]) best = i;
		return switch (best) {
			case 0 -> new V(1, 0, 0);
			case 1 -> new V(-1, 0, 0);
			case 2 -> new V(0, 1, 0);
			case 3 -> new V(0, -1, 0);
			case 4 -> new V(0, 0, 1);
			default -> new V(0, 0, -1);
		};
	}

	private Result touch(V hand, V tip, List<Box> bodies, List<Segment> blades, List<Box> solids) {
		for (int i = 0; i < blades.size(); i++) {
			Segment other = blades.get(i);
			if (distance(hand, tip, other.a(), other.b()) < radius + other.radius()) return new Result(Contact.BLADE, i, 0, tip);
		}
		for (int i = 0; i < bodies.size(); i++) {
			if (bodies.get(i).inflate(radius).touches(hand, tip)) return new Result(Contact.BODY, i, 0, tip);
		}
		for (Box solid : solids) {
			if (solid.inflate(radius).touches(hand, tip)) return new Result(Contact.SOLID, -1, 0, tip);
		}
		return null;
	}

	/**
	 * The shortest distance between the segments p1-q1 and p2-q2.
	 */
	public static double distance(V p1, V q1, V p2, V q2) {
		V[] points = closest(p1, q1, p2, q2);
		return points[0].sub(points[1]).length();
	}

	/**
	 * The closest points of the segments p1-q1 and p2-q2: [on the first, on the second].
	 */
	public static V[] closest(V p1, V q1, V p2, V q2) {
		V d1 = q1.sub(p1);
		V d2 = q2.sub(p2);
		V r = p1.sub(p2);
		double a = d1.dot(d1);
		double e = d2.dot(d2);
		double f = d2.dot(r);
		double s, t;
		if (a <= 1e-12 && e <= 1e-12) return new V[]{p1, p2};
		if (a <= 1e-12) {
			s = 0;
			t = Math.clamp(f / e, 0, 1);
		} else {
			double c = d1.dot(r);
			if (e <= 1e-12) {
				t = 0;
				s = Math.clamp(-c / a, 0, 1);
			} else {
				double b = d1.dot(d2);
				double denom = a * e - b * b;
				s = denom != 0 ? Math.clamp((b * f - c * e) / denom, 0, 1) : 0;
				t = (b * s + f) / e;
				if (t < 0) {
					t = 0;
					s = Math.clamp(-c / a, 0, 1);
				} else if (t > 1) {
					t = 1;
					s = Math.clamp((b - c) / a, 0, 1);
				}
			}
		}
		return new V[]{p1.add(d1.scale(s)), p2.add(d2.scale(t))};
	}
}
